package net.yacy.search.index;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Tokenizer;
import net.yacy.document.VocabularyScraper;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.search.query.QueryGoal;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SegmentTest {

    Segment index;

    /**
     * Setup RWI index
     *
     * @throws IOException
     */
    @Before
    public void setUp() throws IOException {
        // setup a index segment
        index = new Segment(new ConcurrentLog("SegmentTest"),
                new File("test/DATA/INDEX/webportal/SEGMENTS"),
                new File("test/DATA/INDEX/webportal/ARCHIVE"),
                null, null);
        
        /* Warning : ensure the size is larger than the maximum number of test terms added to the index, otherwise
         * query tests might randomly fail depending on when the index dump job (IndexCell.FlushThread) is run */
        final int entityCacheMaxSize = 20;

        // connect RWI index
        index.connectRWI(entityCacheMaxSize, 1024);
    }

    @After
    public void tearDown() {
    	if(index != null) {
    		try {
    			index.clear();
    		} finally {
    			index.close();
    		}
    	}
    }
    
    @AfterClass
    public static void tearDownClass() {
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

    /**
     * Helper to store a text to the rwi index. This was derived from the
     * Segment.storeDocument() procedure.
     *
     * @param text of the document
     * @throws IOException
     * @throws SpaceExceededException
     */
    private void storeTestDocTextToTermIndex(DigestURL url, String text) throws IOException, SpaceExceededException {

        // set a pseudo url for the simulated test document
        final String urlNormalform = url.toNormalform(true);
        String dc_title = "Test Document";
        // STORE PAGE INDEX INTO WORD INDEX DB
        // create a word prototype which is re-used for all entries
        if (index.termIndex != null) {
            final int outlinksSame = 0;
            final int outlinksOther = 0;
            final int urlLength = urlNormalform.length();
            final int urlComps = MultiProtocolURL.urlComps(url.toNormalform(false)).length;
            final int wordsintitle = CommonPattern.SPACES.split(dc_title).length; // same calculation as for CollectionSchema.title_words_val

            WordCache meaningLib = new WordCache(null);
            boolean doAutotagging = false;
            VocabularyScraper scraper = null;

            Tokenizer t = new Tokenizer(url, text, meaningLib, doAutotagging, scraper);

            // create a WordReference template
            final WordReferenceRow ientry = new WordReferenceRow(
                    url.hash(), urlLength, urlComps, wordsintitle,
                    t.RESULT_NUMB_WORDS, t.RESULT_NUMB_SENTENCES,
                    System.currentTimeMillis(), System.currentTimeMillis(),
                    UTF8.getBytes("en"), Response.DT_TEXT,
                    outlinksSame, outlinksOther);

            // add the words to rwi index
            Word wprop = null;
            byte[] wordhash;
            String word;
            for (Map.Entry<String, Word> wentry : t.words().entrySet()) {
                word = wentry.getKey();
                wprop = wentry.getValue();
                assert (wprop.flags != null);
                ientry.setWord(wprop);
                wordhash = Word.word2hash(word);
                if (this.index != null) {
                    index.termIndex.add(wordhash, ientry);
                }

            }
        }
    }

    /**
     * Simulates a multi word query for the rwi termIndex
     *
     * @throws SpaceExceededException
     * @throws MalformedURLException
     * @throws IOException
     */
    @Test
    public void testQuery_MultiWordQuery() throws SpaceExceededException, MalformedURLException, IOException {

        // creates one test url with this text in the rwi index
        DigestURL url = new DigestURL("http://test.org/test.html");
        storeTestDocTextToTermIndex(url, "One Two Three Four Five. This is a test text. One two three for five");
        // posintext                       1   2    3    4    5     6    7    8    9
        // hitcount ("five")                                  1               1                             2
        // posofphrase                    |-------100------------| |------101---------| |--------102----------|
        // posinphrase                     1   2    3    4    5     1    2    3    4     1   2    3    4    5

        // create a query to get the search word hashsets
        QueryGoal qg = new QueryGoal("five test ");
        HandleSet queryHashes = qg.getIncludeHashes();
        HandleSet excludeHashes = qg.getExcludeHashes();
        HandleSet urlselection = null;
        ReferenceFactory<WordReference> termFactory = Segment.wordReferenceFactory;

        // do the search
        TermSearch<WordReference> result = index.termIndex.query(queryHashes, excludeHashes, urlselection, termFactory, Integer.MAX_VALUE);

        // get the joined results
        ReferenceContainer<WordReference> wc = result.joined();

        // we should have now one result (stored to index above)
        assertTrue("test url hash in result set", wc.has(url.hash()));

        // the returned WordReference is expected to be a joined Reference with properties set used in ranking
        WordReference r = wc.getReference(url.hash());

        // min position of search word in text (posintext)
        assertEquals("min posintext('five')", 5, r.posintext());
        // occurence of search words in text
        assertEquals("hitcount('five')", 2, r.hitcount());

        // phrase counts
        assertEquals("phrasesintext", 3, r.phrasesintext());
        assertEquals("posofphrase", 100, r.posofphrase());
        assertEquals("posinphrase", 5, r.posinphrase());

    }

}
