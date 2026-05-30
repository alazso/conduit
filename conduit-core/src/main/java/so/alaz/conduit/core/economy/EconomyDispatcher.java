package so.alaz.conduit.core.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.event.EconomyAccountEvent;
import so.alaz.conduit.api.event.EconomyTransactionEvent;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor.InterceptContext;
import so.alaz.conduit.api.model.AccountEventType;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.core.events.EventPublisher;
import so.alaz.conduit.core.interceptor.InterceptorBus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Decorates an {@link Economy} provider with Conduit's cross-cutting dispatch
 * behaviour:
 *
 * <ul>
 *   <li>synchronous amount validation at the call boundary;</li>
 *   <li>a synchronous pre-authorisation interceptor pass before the async op;</li>
 *   <li>post-commit, non-cancellable economy events after the future resolves.</li>
 * </ul>
 *
 * <p>The caller identity is captured synchronously (from the
 * {@link CallerToken} scope) before the async operation starts, then used to
 * attribute the post-commit event.
 */
@ApiStatus.Internal
public final class EconomyDispatcher implements Economy {

    private final Economy delegate;
    private final InterceptorBus interceptors;
    private final EventPublisher events;

    /**
     * @param delegate     the wrapped provider
     * @param interceptors the shared interceptor bus
     * @param events       the event publisher
     */
    public EconomyDispatcher(@NotNull Economy delegate, @NotNull InterceptorBus interceptors, @NotNull EventPublisher events) {
        this.delegate = delegate;
        this.interceptors = interceptors;
        this.events = events;
    }

    /**
     * @return the wrapped provider (unwrapped, for identity comparisons)
     */
    public @NotNull Economy delegate() {
        return delegate;
    }

    // --- Pass-through, non-mutating ---

    @Override
    public @NotNull String getName() {
        return delegate.getName();
    }

    @Override
    public @NotNull Currency defaultCurrency() {
        return delegate.defaultCurrency();
    }

    @Override
    public @NotNull String requiredApiVersion() {
        return delegate.requiredApiVersion();
    }

    @Override
    public @NotNull Set<Capability> capabilities() {
        return delegate.capabilities();
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid) {
        return delegate.hasAccount(uuid);
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithOwnerOf(@NotNull UUID uuid) {
        return delegate.accountsWithOwnerOf(uuid);
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithMembershipTo(@NotNull UUID uuid) {
        return delegate.accountsWithMembershipTo(uuid);
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithAccessTo(@NotNull UUID uuid) {
        return delegate.accountsWithAccessTo(uuid);
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid) {
        return delegate.getBalance(uuid);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return delegate.canDeposit(uuid, amount);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return delegate.canWithdraw(uuid, amount);
    }

    // --- Account lifecycle (account events) ---

    @Override
    public @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid) {
        return delegate.createAccount(uuid).thenApply(result -> {
            if (result.isSuccess()) {
                events.publish(new EconomyAccountEvent(uuid, AccountEventType.CREATED));
            }
            return result;
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid) {
        return delegate.deleteAccount(uuid).thenApply(result -> {
            if (result.isSuccess()) {
                events.publish(new EconomyAccountEvent(uuid, AccountEventType.DELETED));
            }
            return result;
        });
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName) {
        return delegate.renameAccount(uuid, newName);
    }

    // --- Mutations (validation + interceptor + transaction events) ---

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return depositInternal(uuid, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return depositInternal(uuid, amount, reason);
    }

    private CompletableFuture<EconomyResult> depositInternal(UUID uuid, BigDecimal amount, @Nullable String reason) {
        AmountValidator.validateMagnitude(amount, defaultCurrency());
        return mutate(uuid, amount, TransactionType.DEPOSIT, reason,
                reason == null ? () -> delegate.deposit(uuid, amount) : () -> delegate.deposit(uuid, amount, reason));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return withdrawInternal(uuid, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return withdrawInternal(uuid, amount, reason);
    }

    private CompletableFuture<EconomyResult> withdrawInternal(UUID uuid, BigDecimal amount, @Nullable String reason) {
        AmountValidator.validateMagnitude(amount, defaultCurrency());
        return mutate(uuid, amount, TransactionType.WITHDRAWAL, reason,
                reason == null ? () -> delegate.withdraw(uuid, amount) : () -> delegate.withdraw(uuid, amount, reason));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        AmountValidator.validateAbsolute(amount, defaultCurrency());
        return mutate(uuid, amount, TransactionType.ADMIN_SET, null, () -> delegate.set(uuid, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        return transferInternal(from, to, amount, null);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transferInternal(from, to, amount, reason);
    }

    private CompletableFuture<EconomyResult> transferInternal(UUID from, UUID to, BigDecimal amount, @Nullable String reason) {
        AmountValidator.validateMagnitude(amount, defaultCurrency());
        return mutate(from, amount, TransactionType.TRANSFER_OUT, reason,
                reason == null ? () -> delegate.transfer(from, to, amount) : () -> delegate.transfer(from, to, amount, reason));
    }

    // --- Shared dispatch ---

    private CompletableFuture<EconomyResult> mutate(
            UUID target, BigDecimal amount, TransactionType type, @Nullable String reason,
            Supplier<CompletableFuture<EconomyResult>> op) {

        Currency currency = defaultCurrency();
        CallerToken caller = CallerToken.current();

        if (!interceptors.preAuthorize(new InterceptContext(target, amount, type, currency, caller))) {
            return CompletableFuture.completedFuture(
                    new EconomyResult.ProviderError("Aborted by pre-authorisation interceptor", null));
        }

        return op.get().thenApply(result -> {
            if (result instanceof EconomyResult.Success success) {
                publishTransaction(success, target, amount, type, reason, caller);
            }
            return result;
        });
    }

    private void publishTransaction(
            EconomyResult.Success success, UUID target, BigDecimal amount, TransactionType type,
            @Nullable String reason, CallerToken caller) {

        Transaction transaction = success.transaction();
        if (transaction == null) {
            if (type == TransactionType.ADMIN_SET) {
                // Provider did not record a transaction for an absolute set; the
                // before-balance is unknown and we do not fabricate one.
                return;
            }
            transaction = synthesize(success, target, amount, type, reason);
        }
        events.publish(new EconomyTransactionEvent(transaction, caller));
    }

    private Transaction synthesize(EconomyResult.Success success, UUID target, BigDecimal amount, TransactionType type, @Nullable String reason) {
        BigDecimal after = success.newBalance();
        BigDecimal before = switch (type) {
            case DEPOSIT -> after.subtract(amount);
            case WITHDRAWAL, TRANSFER_OUT -> after.add(amount);
            default -> after;
        };
        return new Transaction(
                UUID.randomUUID(),
                type,
                null,
                target,
                success.currency(),
                amount,
                before,
                after,
                reason,
                Map.of(),
                Instant.now());
    }
}
