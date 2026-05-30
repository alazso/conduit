package so.alaz.conduit.core.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.economy.BankingEconomy;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.economy.LeaderboardEconomy;
import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.economy.TransactionContext;
import so.alaz.conduit.api.economy.TransactionalEconomy;
import so.alaz.conduit.api.event.EconomyAccountEvent;
import so.alaz.conduit.api.event.EconomyTransactionEvent;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor.InterceptContext;
import so.alaz.conduit.api.exception.CapabilityNotSupportedException;
import so.alaz.conduit.api.model.AccountEventType;
import so.alaz.conduit.api.model.AccountPermission;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.RankedBalance;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionFilter;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.api.result.OperationResult;
import so.alaz.conduit.core.events.EventPublisher;
import so.alaz.conduit.core.interceptor.InterceptorBus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>The dispatcher implements <em>every</em> Conduit economy interface
 * ({@link MultiCurrencyEconomy}, {@link TransactionalEconomy},
 * {@link BankingEconomy}, {@link LeaderboardEconomy}) and routes each mutating
 * method through the same pipeline, so dispatch is uniform regardless of which
 * interface a consumer resolves. Extension methods delegate only when the
 * wrapped provider actually implements the interface; calling one against a
 * provider that does not throws {@link UnsupportedOperationException} with a
 * clear message rather than a {@link ClassCastException}. The registry hands out
 * this dispatcher only for interfaces the delegate genuinely supports (resolution
 * is by registered type), so the guards are defensive against manual casts.
 *
 * <p>The caller identity and builder metadata are captured synchronously (from
 * the {@link CallerToken} / {@link TransactionContext} scopes) before the async
 * operation starts, then used to attribute and annotate the post-commit event.
 */
@ApiStatus.Internal
public final class EconomyDispatcher implements
        MultiCurrencyEconomy, TransactionalEconomy, BankingEconomy, LeaderboardEconomy {

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
    public @NotNull CompletableFuture<Map<UUID, Balance>> getBalances(@NotNull Collection<UUID> uuids) {
        return delegate.getBalances(uuids);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        requireCapability(Capability.ECONOMY_PREFLIGHT);
        return delegate.canDeposit(uuid, amount);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        requireCapability(Capability.ECONOMY_PREFLIGHT);
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
        return delegate.renameAccount(uuid, newName).thenApply(result -> {
            if (result.isSuccess()) {
                events.publish(new EconomyAccountEvent(uuid, AccountEventType.RENAMED));
            }
            return result;
        });
    }

    // --- Base mutations (validation + interceptor + transaction events) ---

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
        return mutate(uuid, amount, defaultCurrency(), TransactionType.DEPOSIT, reason,
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
        return mutate(uuid, amount, defaultCurrency(), TransactionType.WITHDRAWAL, reason,
                reason == null ? () -> delegate.withdraw(uuid, amount) : () -> delegate.withdraw(uuid, amount, reason));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        AmountValidator.validateAbsolute(amount, defaultCurrency());
        return mutate(uuid, amount, defaultCurrency(), TransactionType.ADMIN_SET, null, () -> delegate.set(uuid, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        return transferInternal(from, to, amount, defaultCurrency(), null,
                () -> delegate.transfer(from, to, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transferInternal(from, to, amount, defaultCurrency(), reason,
                () -> delegate.transfer(from, to, amount, reason));
    }

    // --- Multi-currency ---

    @Override
    public @NotNull Set<Currency> supportedCurrencies() {
        return multi().supportedCurrencies();
    }

    @Override
    public @NotNull CompletableFuture<Boolean> accountSupportsCurrency(@NotNull UUID uuid, @NotNull Currency currency) {
        return multi().accountSupportsCurrency(uuid, currency);
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid, @NotNull Currency currency) {
        return multi().getBalance(uuid, currency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency) {
        AmountValidator.validateMagnitude(amount, currency);
        MultiCurrencyEconomy multi = multi();
        return mutate(uuid, amount, currency, TransactionType.DEPOSIT, null, () -> multi.deposit(uuid, amount, currency));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency) {
        AmountValidator.validateMagnitude(amount, currency);
        MultiCurrencyEconomy multi = multi();
        return mutate(uuid, amount, currency, TransactionType.WITHDRAWAL, null, () -> multi.withdraw(uuid, amount, currency));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull Currency currency) {
        AmountValidator.validateMagnitude(amount, currency);
        MultiCurrencyEconomy multi = multi();
        return transferInternal(from, to, amount, currency, null, () -> multi.transfer(from, to, amount, currency));
    }

    // --- Transactional ---

    @Override
    public @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, int limit) {
        return transactional().getTransactionHistory(uuid, limit);
    }

    @Override
    public @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, @NotNull TransactionFilter filter) {
        return transactional().getTransactionHistory(uuid, filter);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> depositIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        TransactionalEconomy tx = transactional();
        return idempotent(uuid, amount, TransactionType.DEPOSIT, () -> tx.depositIdempotent(uuid, amount, operationId));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdrawIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        TransactionalEconomy tx = transactional();
        return idempotent(uuid, amount, TransactionType.WITHDRAWAL, () -> tx.withdrawIdempotent(uuid, amount, operationId));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transferIdempotent(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        TransactionalEconomy tx = transactional();
        return idempotent(from, amount, TransactionType.TRANSFER_OUT, () -> tx.transferIdempotent(from, to, amount, operationId));
    }

    // --- Banking ---

    @Override
    public @NotNull CompletableFuture<EconomyResult> createBank(@NotNull String name, @NotNull UUID owner) {
        return banking().createBank(name, owner);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteBank(@NotNull String name) {
        return banking().deleteBank(name);
    }

    @Override
    public @NotNull CompletableFuture<List<String>> getBanks() {
        return banking().getBanks();
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBankBalance(@NotNull String name) {
        return banking().getBankBalance(name);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> bankDeposit(@NotNull String name, @NotNull UUID depositor, @NotNull BigDecimal amount) {
        AmountValidator.validateMagnitude(amount, defaultCurrency());
        BankingEconomy banking = banking();
        return mutate(depositor, amount, defaultCurrency(), TransactionType.BANK_DEPOSIT, null,
                () -> banking.bankDeposit(name, depositor, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> bankWithdraw(@NotNull String name, @NotNull UUID withdrawer, @NotNull BigDecimal amount) {
        AmountValidator.validateMagnitude(amount, defaultCurrency());
        BankingEconomy banking = banking();
        return mutate(withdrawer, amount, defaultCurrency(), TransactionType.BANK_WITHDRAWAL, null,
                () -> banking.bankWithdraw(name, withdrawer, amount));
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isBankOwner(@NotNull String name, @NotNull UUID uuid) {
        return banking().isBankOwner(name, uuid);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isBankMember(@NotNull String name, @NotNull UUID uuid) {
        return banking().isBankMember(name, uuid);
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> getBankMembers(@NotNull String name) {
        return banking().getBankMembers(name);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> playerHasBankPermission(@NotNull String bank, @NotNull UUID uuid, @NotNull AccountPermission permission) {
        return banking().playerHasBankPermission(bank, uuid, permission);
    }

    @Override
    public @NotNull CompletableFuture<OperationResult> setBankMemberPermission(
            @NotNull String bank, @NotNull UUID member, @NotNull AccountPermission permission, boolean granted) {
        return banking().setBankMemberPermission(bank, member, permission, granted);
    }

    @Override
    public @NotNull CompletableFuture<Set<AccountPermission>> getBankMemberPermissions(@NotNull String bank, @NotNull UUID uuid) {
        return banking().getBankMemberPermissions(bank, uuid);
    }

    // --- Leaderboard ---

    @Override
    public @NotNull CompletableFuture<List<RankedBalance>> getTopBalances(int offset, int limit) {
        return leaderboard().getTopBalances(offset, limit);
    }

    // --- Shared dispatch ---

    private CompletableFuture<EconomyResult> mutate(
            UUID target, BigDecimal amount, Currency currency, TransactionType type, @Nullable String reason,
            Supplier<CompletableFuture<EconomyResult>> op) {

        CallerToken caller = CallerToken.current();
        Map<String, String> metadata = TransactionContext.currentMetadata();

        if (!interceptors.preAuthorize(new InterceptContext(target, amount, type, currency, caller))) {
            return CompletableFuture.completedFuture(
                    new EconomyResult.Rejected("Aborted by pre-authorisation interceptor"));
        }

        return op.get().thenApply(result -> {
            if (result instanceof EconomyResult.Success success) {
                publishTransaction(success, target, amount, type, reason, caller, metadata);
            }
            return result;
        });
    }

    /**
     * Transfers fire two events — {@code TRANSFER_OUT} for the source and
     * {@code TRANSFER_IN} for the recipient — and run the interceptor for both
     * legs, so either endpoint can be vetoed. The recipient leg's transaction is
     * built from a real post-commit balance read (never a fabricated balance);
     * failures of that read are swallowed so they cannot mask a successful
     * transfer.
     */
    private CompletableFuture<EconomyResult> transferInternal(
            UUID from, UUID to, BigDecimal amount, Currency currency, @Nullable String reason,
            Supplier<CompletableFuture<EconomyResult>> op) {

        AmountValidator.validateMagnitude(amount, currency);
        CallerToken caller = CallerToken.current();
        Map<String, String> metadata = TransactionContext.currentMetadata();

        if (!interceptors.preAuthorize(new InterceptContext(from, amount, TransactionType.TRANSFER_OUT, currency, caller))
                || !interceptors.preAuthorize(new InterceptContext(to, amount, TransactionType.TRANSFER_IN, currency, caller))) {
            return CompletableFuture.completedFuture(
                    new EconomyResult.Rejected("Aborted by pre-authorisation interceptor"));
        }

        return op.get().thenCompose(result -> {
            if (!(result instanceof EconomyResult.Success success)) {
                return CompletableFuture.completedFuture(result);
            }
            publishTransaction(success, from, amount, TransactionType.TRANSFER_OUT, reason, caller, metadata);
            return publishTransferIn(to, amount, success.currency(), reason, caller, metadata)
                    .handle((ignored, ex) -> result);
        });
    }

    private CompletableFuture<EconomyResult> idempotent(
            UUID target, BigDecimal amount, TransactionType type, Supplier<CompletableFuture<EconomyResult>> op) {

        AmountValidator.validateMagnitude(amount, defaultCurrency());
        CallerToken caller = CallerToken.current();

        if (!interceptors.preAuthorize(new InterceptContext(target, amount, type, defaultCurrency(), caller))) {
            return CompletableFuture.completedFuture(
                    new EconomyResult.Rejected("Aborted by pre-authorisation interceptor"));
        }
        // Idempotent operations are ledger-authoritative: a resubmission returns
        // the original result without re-executing, so the dispatcher cannot tell
        // a fresh execution from a replay and deliberately does not publish a
        // transaction event here. Consumers observe idempotent history through
        // getTransactionHistory instead.
        return op.get();
    }

    private CompletableFuture<Void> publishTransferIn(
            UUID to, BigDecimal amount, Currency currency, @Nullable String reason,
            CallerToken caller, Map<String, String> metadata) {

        return currentBalance(to, currency).thenAccept(balance -> {
            BigDecimal after = balance.amount();
            Transaction in = new Transaction(
                    UUID.randomUUID(), TransactionType.TRANSFER_IN, null, to, currency, amount,
                    after.subtract(amount), after, reason, metadata, Instant.now());
            events.publish(new EconomyTransactionEvent(in, caller));
        });
    }

    private CompletableFuture<Balance> currentBalance(UUID uuid, Currency currency) {
        if (delegate instanceof MultiCurrencyEconomy m && !currency.id().equals(delegate.defaultCurrency().id())) {
            return m.getBalance(uuid, currency);
        }
        return delegate.getBalance(uuid);
    }

    private void publishTransaction(
            EconomyResult.Success success, UUID target, BigDecimal amount, TransactionType type,
            @Nullable String reason, CallerToken caller, Map<String, String> metadata) {

        Transaction transaction = success.transaction();
        if (transaction == null) {
            if (type == TransactionType.ADMIN_SET) {
                // Provider did not record a transaction for an absolute set; the
                // before-balance is unknown and we do not fabricate one.
                return;
            }
            transaction = synthesize(success, target, amount, type, reason, metadata);
        } else if (!metadata.isEmpty()) {
            transaction = withMetadata(transaction, metadata);
        }
        events.publish(new EconomyTransactionEvent(transaction, caller));
    }

    private Transaction synthesize(
            EconomyResult.Success success, UUID target, BigDecimal amount, TransactionType type,
            @Nullable String reason, Map<String, String> metadata) {

        BigDecimal after = success.newBalance();
        BigDecimal before = switch (type) {
            case DEPOSIT, BANK_DEPOSIT, TRANSFER_IN -> after.subtract(amount);
            case WITHDRAWAL, BANK_WITHDRAWAL, TRANSFER_OUT -> after.add(amount);
            default -> after;
        };
        // actor is intentionally null: the CallerToken identifies the initiating
        // plugin, not a player UUID, so there is no reliable player actor to record.
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
                metadata,
                Instant.now());
    }

    private static Transaction withMetadata(Transaction original, Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(original.metadata());
        merged.putAll(extra);
        return new Transaction(
                original.id(), original.type(), original.actor(), original.target(), original.currency(),
                original.amount(), original.balanceBefore(), original.balanceAfter(), original.reason(),
                merged, original.timestamp());
    }

    private void requireCapability(Capability capability) {
        if (!delegate.supports(capability)) {
            throw new CapabilityNotSupportedException(capability, delegate.getName());
        }
    }

    private MultiCurrencyEconomy multi() {
        if (delegate instanceof MultiCurrencyEconomy m) {
            return m;
        }
        throw unsupported(MultiCurrencyEconomy.class);
    }

    private TransactionalEconomy transactional() {
        if (delegate instanceof TransactionalEconomy t) {
            return t;
        }
        throw unsupported(TransactionalEconomy.class);
    }

    private BankingEconomy banking() {
        if (delegate instanceof BankingEconomy b) {
            return b;
        }
        throw unsupported(BankingEconomy.class);
    }

    private LeaderboardEconomy leaderboard() {
        if (delegate instanceof LeaderboardEconomy l) {
            return l;
        }
        throw unsupported(LeaderboardEconomy.class);
    }

    private UnsupportedOperationException unsupported(Class<?> iface) {
        return new UnsupportedOperationException(
                "Provider '" + delegate.getName() + "' does not implement " + iface.getSimpleName());
    }
}
