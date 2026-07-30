package net.yacy.peers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

public class ProtocolTest {

    @Test
    public void transferTargetIpUsesRecordedAttemptAddress() {
        final Seed seed = seed();
        seed.setIP("192.0.2.10");
        seed.setIP("2001:db8::10");
        final Map<String, String> response = new HashMap<String, String>();
        response.put(Seed.IP, "2001:db8::10");

        assertEquals("2001:db8::10", Protocol.transferTargetIP(response, seed));
    }

    @Test
    public void transferTargetIpFallsBackToCanonicalIpv6OnlyAddress() {
        final Seed seed = seed();
        seed.put(Seed.IP, "");
        seed.put(Seed.IP6, "2001:db8::20");

        assertEquals("2001:db8::20", Protocol.transferTargetIP(Collections.emptyMap(), seed));
    }

    @Test
    public void transferTargetIpIsNullWhenPeerHasNoAddress() {
        assertNull(Protocol.transferTargetIP(Collections.emptyMap(), seed()));
    }

    private static Seed seed() {
        final ConcurrentMap<String, String> dna = new ConcurrentHashMap<String, String>();
        return new Seed("testseedhash", dna);
    }
}
