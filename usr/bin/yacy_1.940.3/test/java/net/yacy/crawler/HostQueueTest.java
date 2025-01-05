
package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import static org.junit.Assert.*;
import org.junit.Test;

/**
* test HostQueue
* directorylayout is
*
*   stackDir                      (dir)
*      +-- hostDir                (dir)
*           +-- crawldepth.stack  (file)
*/
public class HostQueueTest {
    final String stackDir = "test/DATA/INDEX/QUEUE/CrawlerCoreStacks";

    /**
     * Test of clear method, of class HostQueue.
     */
    @Test
    public void testClear() throws MalformedURLException, IOException, SpaceExceededException {
        File stackDirFile = new File(stackDir);
        String hostDir = "a.com";
        String urlstr = "http://" + hostDir + "/test.html";
        DigestURL url = new DigestURL(urlstr);
        
        // open queue
        HostQueue testhq = new HostQueue(stackDirFile, url, true, true);

        // add a url
        Request req = new Request(url, null);
        testhq.push(req, null, null);

        int sizeA = testhq.size();
        assertTrue (sizeA > 0);

        testhq.clear(); // clear the complete host queue (should delete all files in stackDir)

        int sizeB = testhq.size();
        assertEquals (0,sizeB);

        // verify stackDir empty (double check)
        String[] filelist = stackDirFile.list();
        assertEquals ("host files in queue dir",0,filelist.length);

        testhq.close();

        // verify stackDir empty
        filelist = stackDirFile.list();
        assertEquals ("host files in queue dir",0,filelist.length);

    }

}
