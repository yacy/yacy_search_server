package net.yacy.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.Test;
import org.slf4j.LoggerFactory;

/** Version-neutral contract for the public SLF4J 2 to JUL logging path. */
public class Slf4jJulBridgeTest {

    @Test
    public void publicSlf4jProviderRoutesJettyNamespaceToJul() {
        assertEquals("org.slf4j.jul.JDK14LoggerFactory",
                LoggerFactory.getILoggerFactory().getClass().getName());

        final String loggerName = "org.eclipse.jetty.yacy.logging.test";
        final Logger julLogger = Logger.getLogger(loggerName);
        final Level previousLevel = julLogger.getLevel();
        final boolean previousUseParentHandlers = julLogger.getUseParentHandlers();
        final AtomicReference<LogRecord> received = new AtomicReference<>();
        final Handler capture = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                received.set(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        try {
            julLogger.setUseParentHandlers(false);
            julLogger.setLevel(Level.INFO);
            capture.setLevel(Level.ALL);
            julLogger.addHandler(capture);

            LoggerFactory.getLogger(loggerName).info("Jetty SLF4J to JUL bridge test");

            assertTrue("SLF4J log record did not reach java.util.logging", received.get() != null);
            assertEquals("Jetty SLF4J to JUL bridge test", received.get().getMessage());
        } finally {
            julLogger.removeHandler(capture);
            julLogger.setLevel(previousLevel);
            julLogger.setUseParentHandlers(previousUseParentHandlers);
        }
    }
}
