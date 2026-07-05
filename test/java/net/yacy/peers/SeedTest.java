package net.yacy.peers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

public class SeedTest {

    private static Seed seed() {
        final ConcurrentMap<String, String> dna = new ConcurrentHashMap<String, String>();
        return new Seed("testseedhash", dna);
    }

    @Test
    public void setIPAddsIPv6WithoutReplacingExistingIPv6Entries() {
        final Seed seed = seed();
        seed.setIP("192.0.2.10");
        seed.setIP("2001:db8::1");
        seed.setIP("2001:db8::2");

        assertEquals("192.0.2.10", seed.get(Seed.IP, ""));
        assertTrue(seed.getIPs().contains("2001:db8::1"));
        assertTrue(seed.getIPs().contains("2001:db8::2"));
        assertEquals(3, seed.countIPs());
    }

    @Test
    public void setIPPreservesIPv6OnlyPrimaryWhenIPv4BecomesAvailable() {
        final Seed seed = seed();
        seed.setIP("2001:db8::1");
        seed.setIP("192.0.2.10");

        assertEquals("192.0.2.10", seed.get(Seed.IP, ""));
        assertTrue(seed.getIPs().contains("2001:db8::1"));
        assertTrue(seed.getIPs().contains("192.0.2.10"));
        assertEquals(2, seed.countIPs());
    }

    @Test
    public void setIPNormalizesZoneIdsAndAvoidsDuplicateIPv6Entries() {
        final Seed seed = seed();
        seed.setIP("192.0.2.10");
        seed.setIP("fe80::1%en0");
        seed.setIP("fe80::1%eth0");

        final Set<String> ips = seed.getIPs();
        assertTrue(ips.contains("fe80::1"));
        assertFalse(ips.contains("fe80::1%en0"));
        assertFalse(ips.contains("fe80::1%eth0"));
        assertEquals(2, seed.countIPs());
    }

    @Test
    public void clashMatchesIPv6WithZoneId() {
        final Seed seed = seed();
        seed.setIP("192.0.2.10");
        seed.setIP("fe80::1");

        final Set<String> otherIPs = new HashSet<String>();
        otherIPs.add("fe80::1%en0");

        assertTrue(seed.clash(otherIPs));
    }

    @Test
    public void clashMatchesEquivalentIPv6TextForms() {
        final Seed seed = seed();
        seed.setIP("192.0.2.10");
        seed.setIP("2001:db8::1");

        final Set<String> otherIPs = new HashSet<String>();
        otherIPs.add("2001:db8:0:0:0:0:0:1");

        assertTrue(seed.clash(otherIPs));
    }
}
