package net.yacy.htroot.yacy;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;

public class SearchPeerResolutionTest {

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
    public void iamHashDisambiguatesPeersSharingOneNatAddress() throws Exception {
        final Seed firstPeer = seed("AAAAAAAAAAAA", "first-nat-peer", "198.51.100.10", 8090);
        final Seed secondPeer = seed("BBBBBBBBBBBB", "second-nat-peer", "198.51.100.10", 8091);
        this.seedDB.addConnected(firstPeer);
        this.seedDB.addConnected(secondPeer);

        final Seed resolved = search.resolveRemotePeer(this.seedDB, secondPeer.hash, "198.51.100.10");

        assertEquals(secondPeer.hash, resolved.hash);
    }

    @Test
    public void unknownOrInvalidIamHashFallsBackToClientIp() throws Exception {
        final Seed peer = seed("CCCCCCCCCCCC", "fallback-peer", "198.51.100.20", 8090);
        this.seedDB.addConnected(peer);

        assertEquals(peer.hash,
                search.resolveRemotePeer(this.seedDB, "DDDDDDDDDDDD", "198.51.100.20").hash);
        assertEquals(peer.hash,
                search.resolveRemotePeer(this.seedDB, "not-a-hash", "198.51.100.20").hash);
        assertEquals(peer.hash,
                search.resolveRemotePeer(this.seedDB, "", "198.51.100.20").hash);
    }

    private static Seed seed(final String hash, final String name, final String ip, final int port) {
        final ConcurrentMap<String, String> dna = new ConcurrentHashMap<String, String>();
        final Seed seed = new Seed(hash, dna);
        seed.setName(name);
        seed.setType(Seed.PEERTYPE_SENIOR);
        seed.setIP(ip);
        seed.setPort(port);
        return seed;
    }
}
