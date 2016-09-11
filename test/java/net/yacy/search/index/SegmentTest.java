package net.yacy.search.index;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Response;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.util.Bitfield;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class SegmentTest {

    static Segment index;

    /**
     * Setup RWI index
     * @throws IOException
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        // setup a index segment
        index = new Segment(new ConcurrentLog("SegmentTest"),
                new File("test/DATA/INDEX/webportal/SEGMENTS"),
                new File("test/DATA/INDEX/webportal/ARCHIVE"),
                null, null);

        // connect RWI index
        index.connectRWI(10, 1024);
    }

    @AfterClass
    public static void tearDownClass() {
        index.close();
        ConcurrentLog.shutdown();
    }

    /**
     * Test of clear method (for RWI), of class Segment.
     */
    @Test
    public void testClear() throws MalformedURLException, IOException, SpaceExceededException {
        DigestURL url = new DigestURL("http://test.org/test.html");
        int urlComps = MultiProtocolURL.urlComps(url.toNormalform(true)).length;
        int urlLength = url.toNormalform(true).length();

        byte[] termHash = Word.word2hash("test");
        Word word = new Word(1, 1, 1);
        word.flags = new Bitfield(4); // flags must not be null

        WordReferenceRow ientry = new WordReferenceRow(
                url.hash(), urlLength, urlComps, 0, 1, 1,
                System.currentTimeMillis(), System.currentTimeMillis(),
                UTF8.getBytes("en"), Response.DT_TEXT, 0, 0);
        ientry.setWord(word);

        // add a dummy Word and WordReference
        index.termIndex.add(termHash, ientry);

        // check index count
        long cnt = index.RWICount();
        assertTrue(cnt > 0);

        index.clear();

        // check index count after clear
        cnt = index.RWICount();
        assertTrue(cnt == 0);
    }

}
