package so.alaz.conduit.api.result;

import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.exception.OperationException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTypesTest {

    @Test
    void success_exposes_value_and_optional() {
        Result<String> r = new Result.Success<>("ok");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOrThrow()).isEqualTo("ok");
        assertThat(r.getOrDefault("fallback")).isEqualTo("ok");
        assertThat(r.toOptional()).contains("ok");
    }

    @Test
    void failure_throws_on_getOrThrow_and_falls_back() {
        Result<String> r = new Result.Failure<>("boom", null);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getOrDefault("fallback")).isEqualTo("fallback");
        assertThat(r.toOptional()).isEmpty();
        assertThatThrownBy(r::getOrThrow).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void operationResult_success_orThrow_is_noop() {
        OperationResult r = OperationResult.success();
        assertThat(r.isSuccess()).isTrue();
        r.orThrow();
    }

    @Test
    void operationResult_failure_orThrow_throws_operationException() {
        OperationResult r = OperationResult.failure("denied");
        assertThat(r.isSuccess()).isFalse();
        assertThatThrownBy(r::orThrow)
                .isInstanceOf(OperationException.class)
                .hasMessageContaining("denied");
    }

    @Test
    void result_map_transforms_success_and_preserves_failure() {
        Result<Integer> success = new Result.Success<>(2);
        assertThat(success.map(v -> v * 10).getOrThrow()).isEqualTo(20);

        Result<Integer> failure = new Result.Failure<>("boom", null);
        assertThat(failure.map(v -> v * 10).isSuccess()).isFalse();
    }

    @Test
    void result_fold_and_ifSuccess() {
        Result<String> success = new Result.Success<>("hi");
        String folded = success.fold(v -> "ok:" + v, (reason, cause) -> "err:" + reason);
        assertThat(folded).isEqualTo("ok:hi");

        StringBuilder seen = new StringBuilder();
        success.ifSuccess(seen::append);
        assertThat(seen.toString()).isEqualTo("hi");

        Result<String> failure = new Result.Failure<>("boom", null);
        String foldedFailure = failure.fold(v -> "ok", (reason, cause) -> "err:" + reason);
        assertThat(foldedFailure).isEqualTo("err:boom");
    }

    @Test
    void operationResult_ifSuccess_and_ifFailure() {
        StringBuilder log = new StringBuilder();
        OperationResult.success().ifSuccess(() -> log.append("ok")).ifFailure(f -> log.append("no"));
        OperationResult.failure("nope").ifSuccess(() -> log.append("no")).ifFailure(f -> log.append(f.reason()));
        assertThat(log.toString()).isEqualTo("oknope");
    }
}
