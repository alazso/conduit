package so.alaz.conduit.api.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.capability.Capability;

/**
 * Thrown when a fine-grained, capability-gated method is called on a provider
 * that does not declare the required {@link Capability}.
 *
 * <p>This never applies to structural-capability (extension-interface) methods;
 * those are unconditionally callable once the consumer holds the interface.
 */
@ApiStatus.AvailableSince("1.0.0")
public class CapabilityNotSupportedException extends RuntimeException {

    private final Capability capability;
    private final String providerName;

    /**
     * @param capability   the missing capability
     * @param providerName the offending provider's name
     */
    public CapabilityNotSupportedException(@NotNull Capability capability, @NotNull String providerName) {
        super("Provider '" + providerName + "' does not support Capability." + capability.name());
        this.capability = capability;
        this.providerName = providerName;
    }

    /**
     * @return the missing capability
     */
    public @NotNull Capability capability() {
        return capability;
    }

    /**
     * @return the offending provider's name
     */
    public @NotNull String providerName() {
        return providerName;
    }
}
