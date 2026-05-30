package so.alaz.conduit.core.support;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;

/**
 * Creates lightweight {@link Plugin} stubs for tests via dynamic proxies. Only
 * {@code getName()} is meaningful; all other methods return defaults.
 */
public final class TestPlugins {

    private TestPlugins() {
    }

    public static Plugin named(String name) {
        return (Plugin) Proxy.newProxyInstance(
                TestPlugins.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "StubPlugin[" + name + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
