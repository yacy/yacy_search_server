
package net.yacy.cora.sorting;

import java.util.Iterator;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class ConcurrentScoreMapTest {

    /**
     * Test of totalCount method, of class ConcurrentScoreMap.
     */
    @Test
    public void testTotalCount() {
        final ConcurrentScoreMap<String> csm = new ConcurrentScoreMap<String>();
        csm.set("first", 10);
        csm.set("second", 5);
        csm.set("third", 13);

        csm.set("first", 100);

        final Iterator<String> it = csm.keys(true);
        long sum = 0;
        while (it.hasNext()) {
            String x = it.next();
            long val = csm.get(x);
            sum += val;
        }

        assertEquals(sum, csm.totalCount());
    }

}
