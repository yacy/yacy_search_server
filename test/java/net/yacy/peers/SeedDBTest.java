package net.yacy.peers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SeedDBTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private SeedDB seedDB;

    @Before
    public void setUp() throws Exception {
        final File networkRoot = this.temporaryFolder.newFolder("network");
        this.seedDB = new SeedDB(
                networkRoot,
                "seedActive.db",
                "seedPassive.db",
                "seedPotential.db",
                new File(networkRoot, SeedDB.DBFILE_OWN_SEED),
                1,
                4,
                false,
                false);
    }

    @After
    public void tearDown() {
        if (this.seedDB != null) this.seedDB.close();
    }

    @Test
    public void lookupByIPFindsIPv6StoredInConnectedSeedIP6() throws Exception {
        final Seed seed = seed("AAAAAAAAAAAA", "connected-peer", 8090);
        seed.setIP("192.0.2.10");
        seed.setIP("2001:db8::10");
        this.seedDB.addConnected(seed);

        final Seed found = this.seedDB.lookupByIP(InetAddress.getByName("2001:db8::10"), 8090, true, false, false);

        assertEquals(seed.hash, found.hash);
    }

    @Test
    public void lookupByIPFindsIPv6StoredInDisconnectedSeedIP6() throws Exception {
        final Seed seed = seed("BBBBBBBBBBBB", "disconnected-peer", 8091);
        seed.setIP("192.0.2.11");
        seed.setIP("2001:db8::11");
        this.seedDB.addDisconnected(seed);

        final Seed found = this.seedDB.lookupByIP(InetAddress.getByName("2001:db8::11"), 8091, false, true, false);

        assertEquals(seed.hash, found.hash);
    }

    @Test
    public void lookupByIPFindsIPv6StoredInPotentialSeedIP6() throws Exception {
        final Seed seed = seed("CCCCCCCCCCCC", "potential-peer", 8092);
        seed.setIP("192.0.2.12");
        seed.setIP("2001:db8::12");
        this.seedDB.addPotential(seed);

        final Seed found = this.seedDB.lookupByIP(InetAddress.getByName("2001:db8::12"), 8092, false, false, true);

        assertEquals(seed.hash, found.hash);
    }

    @Test
    public void lookupByIPHonorsPortForIPv6FallbackMatches() throws Exception {
        final Seed seed = seed("DDDDDDDDDDDD", "port-peer", 8093);
        seed.setIP("192.0.2.13");
        seed.setIP("2001:db8::13");
        this.seedDB.addConnected(seed);

        final Seed found = this.seedDB.lookupByIP(InetAddress.getByName("2001:db8::13"), 8094, true, false, false);

        assertNull(found);
    }

    private static Seed seed(final String hash, final String name, final int port) {
        final ConcurrentMap<String, String> dna = new ConcurrentHashMap<String, String>();
        final Seed seed = new Seed(hash, dna);
        seed.setName(name);
        seed.setType(Seed.PEERTYPE_SENIOR);
        seed.setPort(port);
        return seed;
    }
}
