package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.result.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for a single economy operation.
 *
 * <p>Obtain one via {@link Economy#transaction()}. Exactly one of
 * {@link #withdraw}, {@link #deposit}, or {@link #transfer} must be configured
 * before {@link #execute()}.
 *
 * <h3>Optional refinements</h3>
 * <ul>
 *   <li>{@link #currency(Currency)} targets a non-default currency; the bound
 *       economy must be a {@link MultiCurrencyEconomy} or {@link #execute()}
 *       fails fast with {@link IllegalStateException}.</li>
 *   <li>{@link #idempotencyKey(UUID)} makes execution idempotent; the bound
 *       economy must be a {@link TransactionalEconomy} or {@link #execute()}
 *       fails fast with {@link IllegalStateException}.</li>
 *   <li>{@link #metadata(String, String)} attaches economy-scoped audit tags
 *       that are carried into the published transaction event (not a silent
 *       no-op).</li>
 * </ul>
 */
@ApiStatus.AvailableSince("1.0.0")
public interface TransactionBuilder {

    /**
     * Configure a withdrawal.
     *
     * @param uuid   the account to debit
     * @param amount the positive magnitude to withdraw
     * @return this builder
     */
    @NotNull TransactionBuilder withdraw(@NotNull UUID uuid, @NotNull BigDecimal amount);

    /**
     * Configure a deposit.
     *
     * @param uuid   the account to credit
     * @param amount the positive magnitude to deposit
     * @return this builder
     */
    @NotNull TransactionBuilder deposit(@NotNull UUID uuid, @NotNull BigDecimal amount);

    /**
     * Configure an atomic transfer.
     *
     * @param from   the source account
     * @param to     the destination account
     * @param amount the positive magnitude to transfer
     * @return this builder
     */
    @NotNull TransactionBuilder transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount);

    /**
     * Attach a human-readable reason.
     *
     * @param reason the reason
     * @return this builder
     */
    @NotNull TransactionBuilder reason(@NotNull String reason);

    /**
     * Attach an economy-scoped audit tag. Tags are carried into the published
     * {@link so.alaz.conduit.api.event.EconomyTransactionEvent}'s transaction.
     *
     * @param key   the tag key
     * @param value the tag value
     * @return this builder
     */
    @NotNull TransactionBuilder metadata(@NotNull String key, @NotNull String value);

    /**
     * Target a specific currency instead of the economy's default.
     *
     * @param currency the currency to operate in
     * @return this builder
     */
    @NotNull TransactionBuilder currency(@NotNull Currency currency);

    /**
     * Make execution idempotent under the given key. Requires the bound economy
     * to implement {@link TransactionalEconomy}.
     *
     * @param operationId the idempotency key
     * @return this builder
     */
    @NotNull TransactionBuilder idempotencyKey(@NotNull UUID operationId);

    /**
     * Execute the configured operation.
     *
     * @return a future completing with the economy result
     * @throws IllegalStateException if no operation was configured, or a
     *                               refinement was requested that the bound
     *                               economy cannot satisfy
     */
    @NotNull CompletableFuture<EconomyResult> execute();
}
