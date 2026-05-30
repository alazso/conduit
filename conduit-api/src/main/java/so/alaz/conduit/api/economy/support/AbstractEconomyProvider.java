package so.alaz.conduit.api.economy.support;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience base class for {@link Economy} providers and bridges.
 *
 * <p>Handles the boilerplate every provider repeats — name, default currency,
 * and capability-set plumbing — and offers factory helpers for building
 * {@link EconomyResult.Success} outcomes and synthesising {@link Transaction}
 * records with correctly derived before/after balances. The account/balance
 * storage model is left entirely to the subclass; this base imposes none.
 *
 * <p>Providers do <em>not</em> publish events or run interceptors — Conduit's
 * dispatch layer owns those cross-cutting concerns. Providers only return
 * accurate {@link EconomyResult}s; this base helps them do so consistently.
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract class AbstractEconomyProvider implements Economy {

    private final String name;
    private final Currency defaultCurrency;
    private final Set<Capability> capabilities;

    /**
     * @param name            the provider display name
     * @param defaultCurrency the default currency
     * @param capabilities    the declared fine-grained capabilities
     */
    protected AbstractEconomyProvider(@NotNull String name, @NotNull Currency defaultCurrency,
                                      @NotNull Set<Capability> capabilities) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.capabilities = Set.copyOf(capabilities);
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull Currency defaultCurrency() {
        return defaultCurrency;
    }

    @Override
    public @NotNull Set<Capability> capabilities() {
        return capabilities;
    }

    /**
     * Build a success in the default currency.
     *
     * @param account    the affected account
     * @param newBalance the resulting balance
     * @param transaction the recorded transaction, or {@code null}
     * @return a success result
     */
    protected @NotNull EconomyResult.Success success(@NotNull UUID account, @NotNull BigDecimal newBalance,
                                                     @Nullable Transaction transaction) {
        return new EconomyResult.Success(account, defaultCurrency, newBalance, transaction);
    }

    /**
     * Build a success in a specific currency.
     *
     * @param account     the affected account
     * @param currency    the currency involved
     * @param newBalance  the resulting balance
     * @param transaction the recorded transaction, or {@code null}
     * @return a success result
     */
    protected @NotNull EconomyResult.Success success(@NotNull UUID account, @NotNull Currency currency,
                                                     @NotNull BigDecimal newBalance, @Nullable Transaction transaction) {
        return new EconomyResult.Success(account, currency, newBalance, transaction);
    }

    /**
     * @param balance   the current balance
     * @param requested the requested amount
     * @return an insufficient-funds result in the default currency
     */
    protected @NotNull EconomyResult insufficient(@NotNull BigDecimal balance, @NotNull BigDecimal requested) {
        return new EconomyResult.InsufficientFunds(balance, requested, defaultCurrency);
    }

    /**
     * Synthesise a transaction record with before/after derived from the type.
     *
     * @param type     the transaction type
     * @param target   the affected account
     * @param currency the currency involved
     * @param amount   the magnitude moved
     * @param after    the post-operation balance
     * @param actor    the initiating player, or {@code null}
     * @param reason   a human-readable reason, or {@code null}
     * @param metadata economy-scoped audit tags
     * @return a fresh transaction
     */
    protected @NotNull Transaction transaction(
            @NotNull TransactionType type, @NotNull UUID target, @NotNull Currency currency,
            @NotNull BigDecimal amount, @NotNull BigDecimal after, @Nullable UUID actor,
            @Nullable String reason, @NotNull Map<String, String> metadata) {

        BigDecimal before = switch (type) {
            case DEPOSIT, BANK_DEPOSIT, TRANSFER_IN -> after.subtract(amount);
            case WITHDRAWAL, BANK_WITHDRAWAL, TRANSFER_OUT -> after.add(amount);
            default -> after;
        };
        return new Transaction(UUID.randomUUID(), type, actor, target, currency, amount, before, after,
                reason, metadata, Instant.now());
    }
}
