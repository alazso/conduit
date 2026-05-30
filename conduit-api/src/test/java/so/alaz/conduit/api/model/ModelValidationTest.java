package so.alaz.conduit.api.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelValidationTest {

    @Test
    void transactionFilter_rejects_negative_limit() {
        assertThatThrownBy(() -> TransactionFilter.recent(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void transactionFilter_rejects_inverted_window() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new TransactionFilter(null, null, now.plusSeconds(10), now, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simpleCurrency_rejects_negative_decimal_places() {
        assertThatThrownBy(() -> new SimpleCurrency("c", "c", "c", "$", -1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimalPlaces");
    }

    @Test
    void simpleCurrency_formats_with_grouping_and_symbol() {
        SimpleCurrency currency = SimpleCurrency.ofDefault("coins", "$", 2);
        assertThat(currency.format(new BigDecimal("1234.5"))).isEqualTo("$1,234.50");
    }

    @Test
    void rankedBalance_rejects_non_positive_rank() {
        assertThatThrownBy(() -> new RankedBalance(0, new java.util.UUID(0, 0),
                SimpleCurrency.ofDefault("c", "$", 2), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
