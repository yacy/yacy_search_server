
package net.yacy.kelondro.io;

import java.io.File;
import net.yacy.cora.document.encoding.ASCII;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class RecordsTest {

    final String tesDir = "test/DATA/INDEX/QUEUE";

    /**
     * Test of cleanLast method, of class Records.
     */
    @Test
    public void testCleanLast_byteArr_int() throws Exception {

        File tablefile = new File(tesDir, "test.stack");

        byte[] b = ASCII.getBytes("testDataString");
        Records rec = new Records(tablefile, b.length);

        rec.add(b, 0); // add some data

        for (int i = 0; i < 5; i++) { // multiple cleanlast
            rec.cleanLast(b, 0);
        }
        assertEquals(0,rec.size());
        rec.close();
    }

    /**
     * Test of cleanLast method, of class Records.
     */
    @Test
    public void testCleanLast() throws Exception {
        
        File tablefile = new File (tesDir,"test.stack");

        byte[] b = ASCII.getBytes("testdata");
        Records rec = new Records(tablefile, b.length);

        rec.add(b, 0); // add data
        for (int i = 0; i < 5; i++) { // multiple cleanLast
            rec.cleanLast();
        }
        assertEquals(0,rec.size());
        rec.close();
    }
}
