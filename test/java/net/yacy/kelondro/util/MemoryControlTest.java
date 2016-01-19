
package net.yacy.kelondro.util;

import static org.junit.Assert.assertTrue;
import org.junit.Test;


public class MemoryControlTest {

    final int onemb = 1024 * 1024;

    /**
     * Test of request method, of class MemoryControl.
     */
    @Test
    public void testRequest_StandardStrategy() {
        MemoryControl.setStandardStrategy(true);
        MemoryControl.setProperMbyte(24);

        int memblock = onemb * 13; // memsize to allocate

        int iterations = (int) MemoryControl.available() / memblock;
        int arraysize = (int) MemoryControl.maxMemory() / memblock + 10;

        byte[][] x = new byte[arraysize][];

        int i = 0;
        
        while (i < arraysize && MemoryControl.request(memblock, false)) {
            x[i] = new byte[memblock];
            // for realistic test produce some memory avail to GC
            if (MemoryControl.request(memblock, false)) {
                x[i] = new byte[memblock];
            }
        
            i++;
        }
        System.out.println("allocated " + i + " * " + memblock/onemb + " MB = " + i*memblock/onemb + " MB");

        assertTrue(i >= iterations);
    }

}
