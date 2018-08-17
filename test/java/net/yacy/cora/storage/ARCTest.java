package net.yacy.cora.storage;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/** TODO this is just a copy of the main() that was in ConcurrentARC but it can be developed into some unit tests */
public class ARCTest {

    @Test
    public void testConcurrentARC() {
        int cacheSize = 10000 * 3;
        int concurrency = Runtime.getRuntime().availableProcessors();
        final ARC<String, String> a = new ConcurrentARC<String, String>(cacheSize, concurrency);
        testARC(a, 10000);
    }
    @Test
    public void testHashARC() {
        int cacheSize = 10000 * 3;
        final ARC<String, String> a = new HashARC<String, String>(cacheSize);
        testARC(a, 10000);
    }

    private static void testARC(ARC<String, String> a, int testsize) {
        final Random r = new Random(1);
        final Map<String, String> b = new HashMap<String, String>();
        String key, value;
        for (int i = 0; i < testsize; i++) {
            key = "k" + r.nextInt();
            value = "v" + r.nextInt();
            a.insertIfAbsent(key, value);
            b.put(key, value);
        }

        // now put half of the entries AGAIN into the ARC
        int h = testsize / 2;
        for (final Map.Entry<String, String> entry : b.entrySet()) {
            a.put(entry.getKey(), entry.getValue());
            if (h-- <= 0) break;
        }

        // test correctness
        for (final Map.Entry<String, String> entry : b.entrySet()) {
            if (!a.containsKey(entry.getKey())) {
                fail("missing: " + entry.getKey());
                continue;
            }
            if (!a.get(entry.getKey()).equals(entry.getValue())) {
                fail("wrong: a = " + entry.getKey() + "," + a.get(entry.getKey()) + "; v = " + entry.getValue());
            }
        }
        //System.out.println("finished test!");
    }
}