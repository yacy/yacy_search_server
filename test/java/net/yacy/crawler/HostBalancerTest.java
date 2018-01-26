package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.util.FileUtils;

import org.junit.Test;
import static org.junit.Assert.*;

public class HostBalancerTest {

    final File queuesRoot = new File("test/DATA/INDEX/QUEUES");
    final File datadir = new File("test/DATA");
    
    private static final boolean EXCEED_134217727 = true;
    private static final int ON_DEMAND_LIMIT = 1000;
    
    /**
     * Test of reopen existing HostBalancer cache to test/demonstrate issue with
     * HostQueue for file: protocol
     */
    @Test
    public void testReopen() throws IOException, SpaceExceededException, InterruptedException {
        String hostDir = "C:\\filedirectory";

        // prepare one urls for push test
        String urlstr = "file:///" + hostDir;
        DigestURL url = new DigestURL(urlstr);
        Request req = new Request(url, null);

        FileUtils.deletedelete(queuesRoot); // start clean test

        HostBalancer hb = new HostBalancer(queuesRoot, ON_DEMAND_LIMIT, EXCEED_134217727, false);
        hb.clear();

        Thread.sleep(100);
        assertEquals("After clear", 0, hb.size());

        WorkTables wt = new WorkTables(datadir);
        RobotsTxt rob = new RobotsTxt(wt, null, 10);

        String res = hb.push(req, null, rob); // push url
        assertNull(res); // should have no error text
        assertTrue(hb.has(url.hash())); // check existence
        assertEquals("first push of one url", 1, hb.size()); // expected size=1

        res = hb.push(req, null, rob); // push same url (should be rejected = double occurence)
        assertNotNull(res); // should state double occurrence
        assertTrue(hb.has(url.hash()));
        assertEquals("second push of same url", 1, hb.size());

        hb.close(); // close

        Thread.sleep(200); // wait a bit for file operation

        hb = new HostBalancer(queuesRoot, ON_DEMAND_LIMIT, EXCEED_134217727, false); // reopen balancer

        assertEquals("size after reopen (with one existing url)", 1, hb.size()); // expect size=1 from previous push
        assertTrue("check existance of pushed url", hb.has(url.hash())); // check url exists (it fails as after reopen internal queue.hosthash is wrong)

        res = hb.push(req, null, rob); // push same url as before (should be rejected, but isn't due to hosthash mismatch afte reopen)
        assertNotNull("should state double occurence", res);
        assertEquals("first push of same url after reopen", 1, hb.size()); // should stay size=1
        assertTrue("check existance of pushed url", hb.has(url.hash()));

        res = hb.push(req, null, rob);
        assertNotNull("should state double occurence", res);
        assertTrue("check existance of pushed url", hb.has(url.hash()));
        assertEquals("second push of same url after reopen", 1, hb.size()); // double check, should stay size=1

        // list all urls in hostbalancer
        Iterator<Request> it = hb.iterator();
        while (it.hasNext()) {
            Request rres = it.next();
            System.out.println(rres.toString());
        }
        hb.close();

    }

}
