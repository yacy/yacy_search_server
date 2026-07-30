package net.yacy.http.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletResponse;

import org.apache.solr.common.SolrException;
import org.junit.Test;

/** Focused regression tests for {@link SolrSelectServlet}'s error response path. */
public class SolrSelectServletTest {

    @Test
    public void preservesOriginalFailureAfterResponseIsCommitted() throws Exception {
        final AtomicBoolean sendErrorCalled = new AtomicBoolean();
        final HttpServletResponse response = response(true, sendErrorCalled,
                new AtomicInteger(), new AtomicReference<>());
        final IOException original = new IOException("Broken pipe");

        try {
            SolrSelectServlet.sendError(response, original);
            fail("Expected the original write failure");
        } catch (final IOException failure) {
            assertSame(original, failure);
        }
        assertFalse(sendErrorCalled.get());
    }

    @Test
    public void sendsSolrStatusWhenResponseIsNotCommitted() throws Exception {
        final AtomicBoolean sendErrorCalled = new AtomicBoolean();
        final AtomicInteger status = new AtomicInteger();
        final AtomicReference<String> message = new AtomicReference<>();
        final HttpServletResponse response = response(false, sendErrorCalled, status, message);

        SolrSelectServlet.sendError(response,
                new SolrException(SolrException.ErrorCode.BAD_REQUEST, "bad query"));

        assertTrue(sendErrorCalled.get());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, status.get());
        assertTrue(message.get().contains("bad query"));
    }

    private static HttpServletResponse response(final boolean committed,
            final AtomicBoolean sendErrorCalled, final AtomicInteger status,
            final AtomicReference<String> message) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                SolrSelectServletTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("isCommitted".equals(method.getName())) {
                        return committed;
                    }
                    if ("sendError".equals(method.getName())) {
                        sendErrorCalled.set(true);
                        status.set((Integer) args[0]);
                        if (args.length > 1) {
                            message.set((String) args[1]);
                        }
                        return null;
                    }
                    final Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
