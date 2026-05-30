package so.alaz.conduit.api.economy.support;

import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.exception.IdempotencyMismatchException;
import so.alaz.conduit.api.model.SimpleCurrency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyStoreTest {

    private final IdempotencyStore store = new IdempotencyStore();

    private EconomyResult ok(UUID account) {
        return new EconomyResult.Success(account, SimpleCurrency.ofDefault("c", "$", 2), BigDecimal.ONE, null);
    }

    @Test
    void replay_returns_original_without_reexecuting() {
        UUID account = UUID.randomUUID();
        UUID op = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();
        Object fp = IdempotencyStore.fingerprint("DEPOSIT", new BigDecimal("10.00"));

        EconomyResult first = store.execute(account, op, fp, () -> {
            executions.incrementAndGet();
            return ok(account);
        });
        EconomyResult second = store.execute(account, op, fp, () -> {
            executions.incrementAndGet();
            return ok(account);
        });

        assertThat(executions.get()).isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void different_fingerprint_for_same_id_throws_mismatch() {
        UUID account = UUID.randomUUID();
        UUID op = UUID.randomUUID();
        store.execute(account, op, IdempotencyStore.fingerprint("DEPOSIT", new BigDecimal("10")), () -> ok(account));

        assertThatThrownBy(() -> store.execute(account, op,
                IdempotencyStore.fingerprint("DEPOSIT", new BigDecimal("99")), () -> ok(account)))
                .isInstanceOf(IdempotencyMismatchException.class);
    }

    @Test
    void same_id_on_different_accounts_executes_independently() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID op = UUID.randomUUID();
        Object fp = IdempotencyStore.fingerprint("DEPOSIT", new BigDecimal("10"));

        store.execute(a, op, fp, () -> ok(a));
        store.execute(b, op, fp, () -> ok(b));

        assertThat(store.size()).isEqualTo(2);
    }
}
