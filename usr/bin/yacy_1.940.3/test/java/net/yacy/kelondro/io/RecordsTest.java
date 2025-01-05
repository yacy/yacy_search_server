
package net.yacy.kelondro.io;

import java.io.File;
import net.yacy.cora.document.encoding.ASCII;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class RecordsTest {

    /**
     * Test of cleanLast method, of class Records.
     */
    @Test
    public void testCleanLast_byteArr_int() throws Exception {

        File tablefile = new File(System.getProperty("java.io.tmpdir"), "test1.stack");
        byte[] b = ASCII.getBytes("testDataString");
        Records rec = new Records(tablefile, b.length);
        
        try {
        	rec.add(b, 0); // add some data

        	for (int i = 0; i < 5; i++) { // multiple cleanlast
        		rec.cleanLast(b, 0);
        	}
        	assertEquals(0,rec.size());
        } finally {
        	rec.close();
        }
    }

    /**
     * Test of cleanLast method, of class Records.
     */
    @Test
    public void testCleanLast() throws Exception {
        
        File tablefile = new File (System.getProperty("java.io.tmpdir"),"test2.stack");

        byte[] b = ASCII.getBytes("testdata");
        Records rec = new Records(tablefile, b.length);

        try {
        	rec.add(b, 0); // add data
        	for (int i = 0; i < 5; i++) { // multiple cleanLast
        		rec.cleanLast();
        	}
        	assertEquals(0,rec.size());
        } finally {
        	rec.close();
        }
    }
}
