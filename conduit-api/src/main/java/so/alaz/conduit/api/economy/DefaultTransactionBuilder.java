package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Default {@link TransactionBuilder} that records a single operation and, on
 * {@link #execute()}, dispatches it through the owning {@link Economy}'s own
 * async methods.
 *
 * <p>Package-private and lives in {@code conduit-api} (not {@code conduit-core})
 * because {@link Economy#transaction()} is a default method and the API module
 * must not depend on the runtime module.
 *
 * <p>Refinements are honoured rather than dropped: {@link #metadata} is bound
 * via {@link TransactionContext} so the dispatch layer attaches it to the
 * published event; {@link #currency} routes to the {@link MultiCurrencyEconomy}
 * overloads; {@link #idempotencyKey} routes to the {@link TransactionalEconomy}
 * idempotent methods. Requesting a refinement the bound economy cannot satisfy
 * fails fast at {@link #execute()}.
 */
final class DefaultTransactionBuilder implements TransactionBuilder {

    private enum Kind { NONE, WITHDRAW, DEPOSIT, TRANSFER }

    private final Economy economy;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private Kind kind = Kind.NONE;
    private UUID primary;
    private UUID destination;
    private BigDecimal amount;
    private String reason;
    private Currency currency;
    private UUID operationId;

    DefaultTransactionBuilder(@NotNull Economy economy) {
        this.economy = economy;
    }

    @Override
    public @NotNull TransactionBuilder withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.WITHDRAW;
        this.primary = uuid;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.DEPOSIT;
        this.primary = uuid;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        ensureUnset();
        this.kind = Kind.TRANSFER;
        this.primary = from;
        this.destination = to;
        this.amount = amount;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder reason(@NotNull String reason) {
        this.reason = reason;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder metadata(@NotNull String key, @NotNull String value) {
        this.metadata.put(key, value);
        return this;
    }

    @Override
    public @NotNull TransactionBuilder currency(@NotNull Currency currency) {
        this.currency = currency;
        return this;
    }

    @Override
    public @NotNull TransactionBuilder idempotencyKey(@NotNull UUID operationId) {
        this.operationId = operationId;
        return this;
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> execute() {
        Supplier<CompletableFuture<EconomyResult>> op = this::dispatch;
        if (metadata.isEmpty()) {
            return op.get();
        }
        return TransactionContext.supplyWith(metadata, op);
    }

    private CompletableFuture<EconomyResult> dispatch() {
        if (operationId != null) {
            return dispatchIdempotent();
        }
        if (currency != null) {
            return dispatchCurrency();
        }
        return dispatchDefault();
    }

    private CompletableFuture<EconomyResult> dispatchDefault() {
        return switch (kind) {
            case WITHDRAW -> reason != null
                    ? economy.withdraw(primary, amount, reason)
                    : economy.withdraw(primary, amount);
            case DEPOSIT -> reason != null
                    ? economy.deposit(primary, amount, reason)
                    : economy.deposit(primary, amount);
            case TRANSFER -> reason != null
                    ? economy.transfer(primary, destination, amount, reason)
                    : economy.transfer(primary, destination, amount);
            case NONE -> throw noOperation();
        };
    }

    private CompletableFuture<EconomyResult> dispatchCurrency() {
        if (!(economy instanceof MultiCurrencyEconomy multi)) {
            throw new IllegalStateException(
                    "currency() requires a MultiCurrencyEconomy, but " + economy.getName() + " is not one");
        }
        return switch (kind) {
            case WITHDRAW -> multi.withdraw(primary, amount, currency);
            case DEPOSIT -> multi.deposit(primary, amount, currency);
            case TRANSFER -> multi.transfer(primary, destination, amount, currency);
            case NONE -> throw noOperation();
        };
    }

    private CompletableFuture<EconomyResult> dispatchIdempotent() {
        if (currency != null) {
            throw new IllegalStateException(
                    "idempotencyKey() cannot be combined with currency(): the idempotent API operates in the "
                            + "default currency only");
        }
        if (!(economy instanceof TransactionalEconomy transactional)) {
            throw new IllegalStateException(
                    "idempotencyKey() requires a TransactionalEconomy, but " + economy.getName() + " is not one");
        }
        return switch (kind) {
            case WITHDRAW -> transactional.withdrawIdempotent(primary, amount, operationId);
            case DEPOSIT -> transactional.depositIdempotent(primary, amount, operationId);
            case TRANSFER -> transactional.transferIdempotent(primary, destination, amount, operationId);
            case NONE -> throw noOperation();
        };
    }

    private void ensureUnset() {
        if (kind != Kind.NONE) {
            throw new IllegalStateException("TransactionBuilder already has a configured operation: " + kind);
        }
    }

    private static IllegalStateException noOperation() {
        return new IllegalStateException(
                "No operation configured on TransactionBuilder; call withdraw/deposit/transfer first");
    }
}
