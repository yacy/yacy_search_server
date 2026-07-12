package net.yacy.http.servlets;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;

import javax.servlet.ServletRequest;

import org.junit.Test;

public class Jetty12QoSFilterTest {

    @Test
    public void givesLocalhostServerNameHighestPriority() {
        final ServletRequest request = (ServletRequest) Proxy.newProxyInstance(
                ServletRequest.class.getClassLoader(), new Class<?>[] {ServletRequest.class},
                (proxy, method, arguments) -> "getServerName".equals(method.getName())
                        ? "localhost" : defaultValue(method.getReturnType()));
        assertEquals(10, new YaCyQoSFilter().getPriority(request));
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
