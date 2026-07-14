package net.yacy.server;

import static org.junit.Assert.assertEquals;

import java.io.File;

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
}
