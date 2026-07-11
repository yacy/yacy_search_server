package net.yacy.http;

import static org.junit.Assert.assertEquals;

import org.eclipse.jetty.util.log.Log;
import org.junit.Test;

/** Jetty 9 baseline only; replace this test when Jetty9HttpServerImpl is removed. */
public class Jetty9LoggingFacadeTest {

    @Test
    public void jetty9UsesItsSlf4jFacade() {
        assertEquals("org.eclipse.jetty.util.log.Slf4jLog",
                Log.getLogger("org.eclipse.jetty.yacy.logging.test").getClass().getName());
    }
}
