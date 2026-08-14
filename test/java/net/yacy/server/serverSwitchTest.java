package net.yacy.server;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.search.SwitchboardConstants;

public class serverSwitchTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publicPortOverridesUpnpAndLocalPortAndSurvivesReload() throws Exception {
        final File root = this.temporaryFolder.newFolder("peer");
        serverSwitch peer = new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        peer.setConfig(SwitchboardConstants.SERVER_PORT, "8090");
        peer.setConfig(SwitchboardConstants.SERVER_SSLPORT, "8443");
        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "443");
        peer.setConnectedViaUpnp(true);
        peer.setUpnpPorts(SwitchboardConstants.SERVER_PORT, 18090);
        peer.setUpnpPorts(SwitchboardConstants.SERVER_SSLPORT, 18443);

        assertEquals(443, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));
        assertEquals(18443, peer.getPublicPort(SwitchboardConstants.SERVER_SSLPORT, 8443));

        peer = new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        assertEquals(443, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));
    }

    @Test
    public void missingOrInvalidPublicPortFallsBackToUpnpThenLocalPort() throws Exception {
        final File root = this.temporaryFolder.newFolder("fallback-peer");
        serverSwitch peer =
                new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        peer.setConfig(SwitchboardConstants.SERVER_PORT, "8090");
        peer.setConnectedViaUpnp(true);
        peer.setUpnpPorts(SwitchboardConstants.SERVER_PORT, 18090);

        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "443");
        assertEquals(443, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "");
        assertEquals(18090, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "0");
        assertEquals(18090, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "65535");
        assertEquals(65535, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer.setConfig(SwitchboardConstants.SERVER_PUBLICPORT, "65536");
        assertEquals(18090, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer.setConnectedViaUpnp(false);
        assertEquals(8090, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));

        peer = new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        assertEquals(8090, peer.getPublicPort(SwitchboardConstants.SERVER_PORT, 8090));
    }

    @Test
    public void concurrentConfigUpdatesRemainConsistentOnDisk() throws Exception {
        final File root = this.temporaryFolder.newFolder("concurrent-peer");
        final serverSwitch peer =
                new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        final int writerCount = 16;
        final int updateCount = 8;
        final CountDownLatch writersReady = new CountDownLatch(writerCount);
        final CountDownLatch startWriters = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        final List<Future<?>> updates = new ArrayList<>();

        try {
            for (int writer = 0; writer < writerCount; writer++) {
                final int writerId = writer;
                updates.add(executor.submit(() -> {
                    writersReady.countDown();
                    startWriters.await();
                    for (int update = 0; update < updateCount; update++) {
                        peer.setConfig("concurrent." + writerId, Integer.toString(update));
                    }
                    return null;
                }));
            }
            writersReady.await();
            startWriters.countDown();
            for (final Future<?> update : updates) {
                update.get();
            }
        } finally {
            executor.shutdownNow();
        }

        final serverSwitch reloaded =
                new serverSwitch(root, root, "missing.init", "settings/yacy.conf");
        for (int writer = 0; writer < writerCount; writer++) {
            assertEquals(Integer.toString(updateCount - 1),
                    reloaded.getConfig("concurrent." + writer, null));
        }
    }
}
