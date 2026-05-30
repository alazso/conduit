package so.alaz.conduit.testing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.economy.BankingEconomy;
import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.economy.TransactionalEconomy;
import so.alaz.conduit.api.exception.IdempotencyMismatchException;
import so.alaz.conduit.api.model.AccountPermission;
import so.alaz.conduit.api.model.Balance;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.model.Transaction;
import so.alaz.conduit.api.model.TransactionFilter;
import so.alaz.conduit.api.model.TransactionType;
import so.alaz.conduit.api.capability.Capability;
import so.alaz.conduit.api.result.EconomyResult;
import so.alaz.conduit.api.result.OperationResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, fully-featured economy for tests. Implements the base
 * {@link so.alaz.conduit.api.economy.Economy} plus {@link BankingEconomy},
 * {@link MultiCurrencyEconomy}, and {@link TransactionalEconomy} so it can back
 * every conformance suite.
 *
 * <p>All operations complete synchronously (the returned futures are already
 * complete); tests {@code join()} them explicitly. Monetary values are
 * {@link BigDecimal} throughout — no {@code double} anywhere.
 */
public final class MockEconomy implements BankingEconomy, MultiCurrencyEconomy, TransactionalEconomy {

    private static final UUID NIL = new UUID(0L, 0L);

    private final String name;
    private final Currency defaultCurrency;
    private final Map<String, Currency> currencies;
    private final Set<Capability> capabilities;

    private final Map<UUID, Map<String, BigDecimal>> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, List<Transaction>> history = new ConcurrentHashMap<>();
    private final Map<UUID, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();
    private final Map<String, Bank> banks = new ConcurrentHashMap<>();

    private MockEconomy(Builder builder) {
        this.name = builder.name;
        this.defaultCurrency = builder.defaultCurrency;
        this.currencies = Map.copyOf(builder.currencies);
        this.capabilities = Set.copyOf(builder.capabilities);
        builder.seededAccounts.forEach((uuid, amount) -> {
            Map<String, BigDecimal> acct = new ConcurrentHashMap<>();
            acct.put(defaultCurrency.id(), amount);
            balances.put(uuid, acct);
        });
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    // --- Base economy ---

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

    @Override
    public @NotNull CompletableFuture<Boolean> hasAccount(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> createAccount(@NotNull UUID uuid) {
        balances.computeIfAbsent(uuid, k -> defaultAccount());
        return done(success(uuid, defaultCurrency, balanceOf(uuid, defaultCurrency), null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteAccount(@NotNull UUID uuid) {
        if (!balances.containsKey(uuid)) {
            return done(new EconomyResult.AccountNotFound(uuid));
        }
        BigDecimal last = balanceOf(uuid, defaultCurrency);
        balances.remove(uuid);
        names.remove(uuid);
        return done(success(uuid, defaultCurrency, last, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> renameAccount(@NotNull UUID uuid, @NotNull String newName) {
        ensure(uuid);
        names.put(uuid, newName);
        return done(success(uuid, defaultCurrency, balanceOf(uuid, defaultCurrency), null));
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithOwnerOf(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithMembershipTo(@NotNull UUID uuid) {
        return done(Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> accountsWithAccessTo(@NotNull UUID uuid) {
        return done(balances.containsKey(uuid) ? Set.of(uuid) : Set.of());
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid) {
        return getBalance(uuid, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canDeposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return done(true);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> canWithdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return done(balanceOf(uuid, defaultCurrency).compareTo(amount) >= 0);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return deposit(uuid, amount, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return deposit(uuid, amount, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        return withdraw(uuid, amount, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull String reason) {
        return withdraw(uuid, amount, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> set(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        ensure(uuid);
        balances.get(uuid).put(defaultCurrency.id(), amount);
        return done(success(uuid, defaultCurrency, amount, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        return transfer(from, to, amount, defaultCurrency);
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull String reason) {
        return transfer(from, to, amount, defaultCurrency);
    }

    // --- Multi-currency ---

    @Override
    public @NotNull Set<Currency> supportedCurrencies() {
        return Set.copyOf(currencies.values());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> accountSupportsCurrency(@NotNull UUID uuid, @NotNull Currency currency) {
        return done(currencies.containsKey(currency.id()));
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBalance(@NotNull UUID uuid, @NotNull Currency currency) {
        return done(new Balance(uuid, currency, balanceOf(uuid, currency)));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deposit(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency) {
        if (!currencies.containsKey(currency.id())) {
            return done(new EconomyResult.CurrencyNotSupported(currency));
        }
        ensure(uuid);
        BigDecimal before = balanceOf(uuid, currency);
        BigDecimal after = before.add(amount);
        balances.get(uuid).put(currency.id(), after);
        Transaction txn = record(TransactionType.DEPOSIT, uuid, currency, amount, before, after);
        return done(success(uuid, currency, after, txn));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull Currency currency) {
        if (!currencies.containsKey(currency.id())) {
            return done(new EconomyResult.CurrencyNotSupported(currency));
        }
        ensure(uuid);
        BigDecimal before = balanceOf(uuid, currency);
        if (before.compareTo(amount) < 0) {
            return done(new EconomyResult.InsufficientFunds(before, amount, currency));
        }
        BigDecimal after = before.subtract(amount);
        balances.get(uuid).put(currency.id(), after);
        Transaction txn = record(TransactionType.WITHDRAWAL, uuid, currency, amount, before, after);
        return done(success(uuid, currency, after, txn));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull Currency currency) {
        if (!currencies.containsKey(currency.id())) {
            return done(new EconomyResult.CurrencyNotSupported(currency));
        }
        ensure(from);
        ensure(to);
        BigDecimal fromBefore = balanceOf(from, currency);
        if (fromBefore.compareTo(amount) < 0) {
            return done(new EconomyResult.InsufficientFunds(fromBefore, amount, currency));
        }
        BigDecimal fromAfter = fromBefore.subtract(amount);
        BigDecimal toBefore = balanceOf(to, currency);
        BigDecimal toAfter = toBefore.add(amount);
        balances.get(from).put(currency.id(), fromAfter);
        balances.get(to).put(currency.id(), toAfter);
        record(TransactionType.TRANSFER_OUT, from, currency, amount, fromBefore, fromAfter);
        record(TransactionType.TRANSFER_IN, to, currency, amount, toBefore, toAfter);
        Transaction txn = lastTransaction(from);
        return done(success(from, currency, fromAfter, txn));
    }

    // --- Transactional ---

    @Override
    public @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, int limit) {
        return getTransactionHistory(uuid, TransactionFilter.recent(limit));
    }

    @Override
    public @NotNull CompletableFuture<List<Transaction>> getTransactionHistory(@NotNull UUID uuid, @NotNull TransactionFilter filter) {
        List<Transaction> all = new ArrayList<>(history.getOrDefault(uuid, List.of()));
        all.sort(Comparator.comparing(Transaction::timestamp).reversed());
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : all) {
            if (filter.type() != null && t.type() != filter.type()) continue;
            if (filter.currency() != null && !t.currency().id().equals(filter.currency().id())) continue;
            if (filter.after() != null && t.timestamp().isBefore(filter.after())) continue;
            if (filter.before() != null && t.timestamp().isAfter(filter.before())) continue;
            filtered.add(t);
            if (filtered.size() >= filter.limit()) break;
        }
        return done(List.copyOf(filtered));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> depositIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        return idempotent(operationId, TransactionType.DEPOSIT, uuid, null, amount, () -> deposit(uuid, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> withdrawIdempotent(@NotNull UUID uuid, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        return idempotent(operationId, TransactionType.WITHDRAWAL, uuid, null, amount, () -> withdraw(uuid, amount));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> transferIdempotent(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount, @NotNull UUID operationId) {
        return idempotent(operationId, TransactionType.TRANSFER_OUT, from, to, amount, () -> transfer(from, to, amount));
    }

    // --- Banking ---

    @Override
    public @NotNull CompletableFuture<EconomyResult> createBank(@NotNull String name, @NotNull UUID owner) {
        if (banks.containsKey(name)) {
            return done(new EconomyResult.ProviderError("Bank already exists: " + name, null));
        }
        Bank bank = new Bank(owner);
        bank.members.put(owner, EnumSet.of(AccountPermission.OWNER));
        banks.put(name, bank);
        return done(success(owner, defaultCurrency, BigDecimal.ZERO, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> deleteBank(@NotNull String name) {
        Bank removed = banks.remove(name);
        if (removed == null) {
            return done(new EconomyResult.ProviderError("No such bank: " + name, null));
        }
        return done(success(removed.owner, defaultCurrency, removed.balance, null));
    }

    @Override
    public @NotNull CompletableFuture<List<String>> getBanks() {
        return done(List.copyOf(banks.keySet()));
    }

    @Override
    public @NotNull CompletableFuture<Balance> getBankBalance(@NotNull String name) {
        Bank bank = banks.get(name);
        return done(new Balance(bank == null ? NIL : bank.owner, defaultCurrency, bank == null ? BigDecimal.ZERO : bank.balance));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> bankDeposit(@NotNull String name, @NotNull UUID depositor, @NotNull BigDecimal amount) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return done(new EconomyResult.ProviderError("No such bank: " + name, null));
        }
        bank.balance = bank.balance.add(amount);
        return done(success(depositor, defaultCurrency, bank.balance, null));
    }

    @Override
    public @NotNull CompletableFuture<EconomyResult> bankWithdraw(@NotNull String name, @NotNull UUID withdrawer, @NotNull BigDecimal amount) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return done(new EconomyResult.ProviderError("No such bank: " + name, null));
        }
        if (bank.balance.compareTo(amount) < 0) {
            return done(new EconomyResult.InsufficientFunds(bank.balance, amount, defaultCurrency));
        }
        bank.balance = bank.balance.subtract(amount);
        return done(success(withdrawer, defaultCurrency, bank.balance, null));
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isBankOwner(@NotNull String name, @NotNull UUID uuid) {
        Bank bank = banks.get(name);
        return done(bank != null && bank.owner.equals(uuid));
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isBankMember(@NotNull String name, @NotNull UUID uuid) {
        Bank bank = banks.get(name);
        return done(bank != null && bank.members.containsKey(uuid));
    }

    @Override
    public @NotNull CompletableFuture<Set<UUID>> getBankMembers(@NotNull String name) {
        Bank bank = banks.get(name);
        return done(bank == null ? Set.of() : Set.copyOf(bank.members.keySet()));
    }

    @Override
    public @NotNull CompletableFuture<Boolean> playerHasBankPermission(@NotNull String name, @NotNull UUID uuid, @NotNull AccountPermission permission) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return done(false);
        }
        Set<AccountPermission> held = bank.members.get(uuid);
        if (held == null) {
            return done(false);
        }
        return done(held.stream().anyMatch(p -> p.includes(permission)));
    }

    @Override
    public @NotNull CompletableFuture<OperationResult> setBankMemberPermission(@NotNull String name, @NotNull UUID member, @NotNull AccountPermission permission, boolean granted) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return done(OperationResult.failure("No such bank: " + name));
        }
        Set<AccountPermission> held = bank.members.computeIfAbsent(member, k -> EnumSet.noneOf(AccountPermission.class));
        if (granted) {
            held.add(permission);
        } else if (permission == AccountPermission.ALL) {
            held.removeIf(p -> p != AccountPermission.OWNER);
        } else {
            held.remove(permission);
        }
        return done(OperationResult.success());
    }

    @Override
    public @NotNull CompletableFuture<Set<AccountPermission>> getBankMemberPermissions(@NotNull String name, @NotNull UUID uuid) {
        Bank bank = banks.get(name);
        if (bank == null || !bank.members.containsKey(uuid)) {
            return done(Set.of());
        }
        return done(Set.copyOf(bank.members.get(uuid)));
    }

    // --- Internals ---

    private CompletableFuture<EconomyResult> idempotent(
            UUID operationId, TransactionType type, UUID primary, @Nullable UUID secondary, BigDecimal amount,
            java.util.function.Supplier<CompletableFuture<EconomyResult>> op) {

        IdempotencyRecord existing = idempotency.get(operationId);
        if (existing != null) {
            if (existing.matches(type, primary, secondary, amount, defaultCurrency.id())) {
                return done(existing.result());
            }
            return CompletableFuture.failedFuture(new IdempotencyMismatchException(operationId,
                    "parameters differ from the original submission"));
        }
        EconomyResult result = op.get().join();
        idempotency.put(operationId, new IdempotencyRecord(type, primary, secondary, amount, defaultCurrency.id(), result));
        return done(result);
    }

    private Map<String, BigDecimal> defaultAccount() {
        Map<String, BigDecimal> acct = new ConcurrentHashMap<>();
        acct.put(defaultCurrency.id(), BigDecimal.ZERO);
        return acct;
    }

    private void ensure(UUID uuid) {
        balances.computeIfAbsent(uuid, k -> defaultAccount());
    }

    private BigDecimal balanceOf(UUID uuid, Currency currency) {
        Map<String, BigDecimal> acct = balances.get(uuid);
        if (acct == null) {
            return BigDecimal.ZERO;
        }
        return acct.getOrDefault(currency.id(), BigDecimal.ZERO);
    }

    private Transaction record(TransactionType type, UUID target, Currency currency, BigDecimal amount, BigDecimal before, BigDecimal after) {
        Transaction txn = new Transaction(UUID.randomUUID(), type, null, target, currency, amount, before, after, null, Map.of(), Instant.now());
        history.computeIfAbsent(target, k -> new ArrayList<>()).add(txn);
        return txn;
    }

    private Transaction lastTransaction(UUID uuid) {
        List<Transaction> list = history.get(uuid);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private static EconomyResult.Success success(UUID account, Currency currency, BigDecimal newBalance, @Nullable Transaction txn) {
        return new EconomyResult.Success(account, currency, newBalance, txn);
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static final class Bank {
        private final UUID owner;
        private BigDecimal balance = BigDecimal.ZERO;
        private final Map<UUID, Set<AccountPermission>> members = new ConcurrentHashMap<>();

        private Bank(UUID owner) {
            this.owner = owner;
        }
    }

    private record IdempotencyRecord(TransactionType type, UUID primary, @Nullable UUID secondary, BigDecimal amount, String currencyId, EconomyResult result) {
        boolean matches(TransactionType type, UUID primary, @Nullable UUID secondary, BigDecimal amount, String currencyId) {
            return this.type == type
                    && this.primary.equals(primary)
                    && java.util.Objects.equals(this.secondary, secondary)
                    && this.amount.compareTo(amount) == 0
                    && this.currencyId.equals(currencyId);
        }
    }

    /** Builder for {@link MockEconomy}. */
    public static final class Builder {
        private String name = "MockEconomy";
        private Currency defaultCurrency;
        private final Map<String, Currency> currencies = new LinkedHashMap<>();
        private final Set<Capability> capabilities = EnumSet.allOf(Capability.class);
        private final Map<UUID, BigDecimal> seededAccounts = new LinkedHashMap<>();

        public @NotNull Builder name(@NotNull String name) {
            this.name = name;
            return this;
        }

        public @NotNull Builder withCurrency(@NotNull String id, @NotNull String symbol, int decimalPlaces) {
            boolean isDefault = currencies.isEmpty();
            Currency currency = new SimpleCurrency(id, id, id, symbol, decimalPlaces, isDefault);
            currencies.put(id, currency);
            if (isDefault) {
                defaultCurrency = currency;
            }
            return this;
        }

        public @NotNull Builder withCurrency(@NotNull Currency currency) {
            currencies.put(currency.id(), currency);
            if (currency.isDefault() || defaultCurrency == null) {
                defaultCurrency = currency;
            }
            return this;
        }

        public @NotNull Builder withAccount(@NotNull UUID uuid, @NotNull BigDecimal amount) {
            seededAccounts.put(uuid, amount);
            return this;
        }

        public @NotNull Builder withCapabilities(@NotNull Set<Capability> capabilities) {
            this.capabilities.clear();
            this.capabilities.addAll(capabilities);
            return this;
        }

        public @NotNull MockEconomy build() {
            if (currencies.isEmpty()) {
                withCurrency("coins", "$", 2);
            }
            return new MockEconomy(this);
        }
    }
}
