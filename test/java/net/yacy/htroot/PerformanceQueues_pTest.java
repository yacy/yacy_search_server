package net.yacy.htroot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/** Focused tests for persisted Performance Queues settings. */
public class PerformanceQueues_pTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incomingRequestMaximumPersistsUnderStartupConfigurationKey() throws Exception {
        final int previousMaximum = ConnectionInfo.getServerMaxcount();
        final File root = this.temporaryFolder.newFolder("peer");
        try {
            final serverSwitch firstRun = config(root);
            assertTrue(PerformanceQueues_p.applyServerMaxcount(firstRun, 73));
            assertEquals(73, ConnectionInfo.getServerMaxcount());

            final serverSwitch restarted = config(root);
            assertEquals(73, restarted.getConfigInt(
                    SwitchboardConstants.SERVER_MAX_BUSY_SESSIONS, -1));

            assertFalse(PerformanceQueues_p.applyServerMaxcount(restarted, 0));
            assertEquals("An invalid update must preserve the saved value", 73,
                    restarted.getConfigInt(SwitchboardConstants.SERVER_MAX_BUSY_SESSIONS, -1));
            assertEquals("An invalid update must preserve the active value", 73,
                    ConnectionInfo.getServerMaxcount());
        } finally {
            ConnectionInfo.setServerMaxcount(previousMaximum);
        }
    }

    @Test
    public void correctedRequestParameterTakesPrecedenceAndLegacyNameRemainsAccepted() {
        final serverObjects legacyPost = new serverObjects();
        legacyPost.put(PerformanceQueues_p.LEGACY_HTTPD_SESSION_POOL_MAX_ACTIVE_PARAM, "71");
        assertEquals(71, PerformanceQueues_p.postedServerMaxcount(legacyPost));

        final serverObjects correctedPost = new serverObjects();
        correctedPost.put(PerformanceQueues_p.LEGACY_HTTPD_SESSION_POOL_MAX_ACTIVE_PARAM, "71");
        correctedPost.put(PerformanceQueues_p.INCOMING_HTTP_REQUESTS_MAX_ACTIVE_PARAM, "72");
        assertEquals(72, PerformanceQueues_p.postedServerMaxcount(correctedPost));
    }

    private static serverSwitch config(final File root) {
        return new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
    }
}
