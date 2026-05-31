package so.alaz.conduit.core.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.economy.BankingEconomy;
import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.economy.LeaderboardEconomy;
import so.alaz.conduit.api.economy.MultiCurrencyEconomy;
import so.alaz.conduit.api.economy.TransactionBuilder;
import so.alaz.conduit.api.economy.TransactionalEconomy;
import so.alaz.conduit.core.events.EventPublisher;
import so.alaz.conduit.core.interceptor.InterceptorBus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Invocation handler behind every dispatch-decorated economy handle.
 *
 * <p>The registry hands consumers a {@link Proxy} that declares <em>only</em> the
 * Conduit economy interfaces the wrapped provider actually implements, so
 * {@code instanceof}/casts on the resolved economy are truthful. Each call is
 * forwarded to a concrete {@link EconomyDispatcher} (which implements every
 * interface and owns the validation / interceptor / event pipeline); the proxy
 * exists purely to make the visible interface set honest.
 */
@ApiStatus.Internal
public final class DispatchInvocationHandler implements InvocationHandler {

    private static final Class<?>[] EXTENSION_INTERFACES = {
            MultiCurrencyEconomy.class,
            TransactionalEconomy.class,
            BankingEconomy.class,
            LeaderboardEconomy.class
    };

    private final EconomyDispatcher dispatcher;

    private DispatchInvocationHandler(@NotNull EconomyDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Build a dispatch-decorated handle over {@code delegate} that declares only
     * the Conduit economy interfaces the delegate genuinely implements.
     *
     * @param delegate     the raw provider
     * @param interceptors the shared interceptor bus
     * @param events       the event publisher
     * @return a proxy implementing {@link Economy} plus the delegate's extension interfaces
     */
    public static @NotNull Economy decorate(@NotNull Economy delegate, @NotNull InterceptorBus interceptors, @NotNull EventPublisher events) {
        EconomyDispatcher dispatcher = new EconomyDispatcher(delegate, interceptors, events);
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(Economy.class);
        for (Class<?> extension : EXTENSION_INTERFACES) {
            if (extension.isInstance(delegate)) {
                interfaces.add(extension);
            }
        }
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                interfaces.toArray(Class<?>[]::new),
                new DispatchInvocationHandler(dispatcher));
    }

    /**
     * @param economy any economy reference
     * @return {@code true} if {@code economy} is a dispatch-decorated handle
     */
    public static boolean isDecorated(@NotNull Economy economy) {
        return Proxy.isProxyClass(economy.getClass())
                && Proxy.getInvocationHandler(economy) instanceof DispatchInvocationHandler;
    }

    /**
     * @param economy any economy reference
     * @return the raw delegate if {@code economy} is a dispatch handle, else {@code economy} unchanged
     */
    public static @NotNull Economy unwrap(@NotNull Economy economy) {
        if (Proxy.isProxyClass(economy.getClass())
                && Proxy.getInvocationHandler(economy) instanceof DispatchInvocationHandler handler) {
            return handler.dispatcher.delegate();
        }
        return economy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "ConduitDispatch[" + dispatcher.getName() + "]";
                default -> invokeOnDispatcher(method, args);
            };
        }
        // Bind the fluent builder to the proxy (honest interface set) rather than
        // to the dispatcher (which implements every interface), so the builder's
        // currency()/idempotencyKey() capability guards reflect the real provider.
        if (method.getParameterCount() == 0 && method.getName().equals("transaction")) {
            return TransactionBuilder.forEconomy((Economy) proxy);
        }
        return invokeOnDispatcher(method, args);
    }

    private Object invokeOnDispatcher(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(dispatcher, args);
        } catch (InvocationTargetException e) {
            // Surface the provider's real exception (e.g. synchronous
            // IllegalArgumentException / CapabilityNotSupportedException) unchanged.
            throw e.getCause();
        }
    }
}
