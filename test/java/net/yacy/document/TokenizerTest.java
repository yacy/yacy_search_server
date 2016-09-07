
package net.yacy.document;

import java.net.MalformedURLException;
import java.util.Map;
import net.yacy.cora.document.WordCache;
import net.yacy.kelondro.data.word.Word;
import org.junit.Test;
import static org.junit.Assert.*;


public class TokenizerTest {

    /**
     * Test of words method, of class Tokenizer.
     */
    @Test
    public void testWords() throws MalformedURLException {
        //  pos  =      1   2   3   4       5        6      7    8   9    10     // 1-letter words don't count
        String text = "One word is not a sentence because words are just words.";
        WordCache meaningLib = new WordCache(null);
        boolean doAutotagging = false;
        VocabularyScraper scraper = null;

        Tokenizer t = new Tokenizer(null, text, meaningLib, doAutotagging, scraper);

        Map<String, Word> words = t.words;

        // test extracted word information (position)
        Word w = words.get("word");
        assertEquals("position of 'word' ", 2, w.posInText);
        assertEquals("occurence of 'word' ", 1, w.occurrences());

        w = words.get("words");
        assertEquals("position of 'words' ", 7, w.posInText);
        assertEquals("occurence of 'words' ", 2, w.occurrences());
    }

}
