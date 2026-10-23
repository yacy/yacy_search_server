package net.yacy.peers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.yacy.crawler.focused.FocusedResourceGuard;

public class RemoteSearchResourceGuardTest {

    @Test
    public void peerSearchRequiresFocusedRecoveryHeadroom() {
        final long maximum = 4L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.recoveryThreshold(maximum);

        assertFalse(RemoteSearch.hasPeerSearchHeadroom(threshold - 1L, maximum, false));
        assertTrue(RemoteSearch.hasPeerSearchHeadroom(threshold, maximum, false));
    }

    @Test
    public void failedMemoryRequestAlwaysBlocksNewPeerSearches() {
        final long maximum = 4L * 1024L * 1024L * 1024L;
        assertFalse(RemoteSearch.hasPeerSearchHeadroom(maximum, maximum, true));
    }
}
