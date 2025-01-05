package net.yacy.peers;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.kelondro.data.word.Word;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewsPoolTest {

    static NewsPool newsPool;

    public NewsPoolTest() {

    }

    @BeforeClass
    public static void setUpClass() {
        String networkpath = "test/DATA/INDEX/webportal/NETWORK";
        newsPool = new NewsPool(new File(networkpath), false, true);
    }

    @AfterClass
    public static void tearDownClass() {
        newsPool.close();
    }

    /**
     * Test of myPublication method, of class NewsPool.
     */
    @Test
    public void testMyPublication() throws Exception {

        // create a test seed as sender
        ConcurrentMap<String, String> dna = new ConcurrentHashMap<String, String>();
        byte[] hash = Word.word2hash("myseed"); // just generate any hash for testing
        Seed myseed = new Seed(ASCII.String(hash), dna);

        // generate 3 test messages and simulate publish (put in outgoing queuq
        Map<String, String> msgattr = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            msgattr.put("text", "message " + Integer.toString(i));
            msgattr.put("#", Integer.toString(i)); // use id modificator attribute (to generate unique id for same creation second, used in id)
            newsPool.publishMyNews(myseed, "TestCat", msgattr); // add msg to outgoing queue
        }

        // test the distribution process
        Set<String> resultmemory = new LinkedHashSet<>();
        NewsDB.Record rec = newsPool.myPublication();
        int cnt = 3 * 30 + 5; // end condition (3 msg * 30 distribution) for loop (+5 > as expected count)
        while (rec != null && cnt > 0) {
            // System.out.println(rec.toString());
            assertTrue(rec.distributed() > 0);
            resultmemory.add(rec.id());

            cnt--;
            rec = newsPool.myPublication();
        }
        assertTrue(cnt == 5); // test total counter

        // test news record in published queue
        cnt = 1;
        for (String msgid : resultmemory) {
            NewsDB.Record msg = newsPool.getByID(NewsPool.PUBLISHED_DB, msgid);
            System.out.println(cnt + ". news published: \"" + msg.attribute("text", "***missing***") + "\" distributed=" + msg.distributed());
            assertEquals("default distributin count", 30, msg.distributed()); // test expected distribution count
            cnt++;
        }

    }

}
