package so.alaz.conduit.bridge.essentialsx;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money-integrity tests for the EssentialsX bridge: transfer atomicity under a
 * failing credit, account guards, and concurrent read-modify-write safety.
 */
class EssentialsXEconomyIntegrityTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Test
    void transfer_rolls_back_debit_when_credit_fails() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        // Backend that fails the credit leg (writes to `to`).
        FakeEssentialsBackend backend = new FakeEssentialsBackend() {
            @Override
            public void setBalance(@NotNull UUID uuid, @NotNull BigDecimal balance) {
                if (uuid.equals(to)) {
                    throw new EssentialsBackendException("simulated credit failure", null);
                }
                super.setBalance(uuid, balance);
            }
        };
        backend.ensureAccount(from);
        backend.setBalance(from, new BigDecimal("100.00"));
        backend.ensureAccount(to);

        Economy economy = new EssentialsXEconomy(backend, Runnable::run);
        EconomyResult result = economy.transfer(from, to, new BigDecimal("40.00")).join();

        assertThat(result).isInstanceOf(EconomyResult.ProviderError.class);
        // The debit must have been rolled back — no money destroyed.
        assertThat(backend.balance(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void mutation_on_unknown_account_resolves_to_AccountNotFound() {
        FakeEssentialsBackend backend = new FakeEssentialsBackend();
        Economy economy = new EssentialsXEconomy(backend, Runnable::run);

        EconomyResult deposit = economy.deposit(UUID.randomUUID(), TEN).join();
        EconomyResult set = economy.set(UUID.randomUUID(), TEN).join();

        assertThat(deposit).isInstanceOf(EconomyResult.AccountNotFound.class);
        assertThat(set).isInstanceOf(EconomyResult.AccountNotFound.class);
    }

    @Test
    void concurrent_withdrawals_never_overdraw() throws Exception {
        UUID account = UUID.randomUUID();
        FakeEssentialsBackend backend = new FakeEssentialsBackend();
        backend.ensureAccount(account);
        backend.setBalance(account, new BigDecimal("100.00")); // exactly 10 withdrawals of 10

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Economy economy = new EssentialsXEconomy(backend, pool);
            int attempts = 50; // far more than the balance allows

            CompletableFuture<?>[] futures = new CompletableFuture<?>[attempts];
            for (int i = 0; i < attempts; i++) {
                futures[i] = economy.withdraw(account, TEN);
            }
            CompletableFuture.allOf(futures).join();

            long successes = 0;
            for (CompletableFuture<?> future : futures) {
                if (((EconomyResult) future.join()) instanceof EconomyResult.Success) {
                    successes++;
                }
            }
            // Without per-account locking this races and overdraws; with it,
            // exactly 10 succeed and the balance never goes negative.
            assertThat(successes).isEqualTo(10);
            assertThat(backend.balance(account)).isEqualByComparingTo("0.00");
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
