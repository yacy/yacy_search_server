
package net.yacy.document;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.yacy.cora.document.WordCache;
import net.yacy.kelondro.data.word.Word;
import org.junit.Test;
import static org.junit.Assert.*;


public class TokenizerTest {

    /**
     * Test of words method, of class Tokenizer.
     */
    @Test
    public void testWords() {
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

    /**
     * Test of RESULT_NUMB_SENTENCES, of class Tokenizer.
     */
    @Test
    public void testNumberOfSentences() {
        Set<String> testText = new HashSet<>();
        // text with 5 sentences
        testText.add("Sentence One. Sentence Two. Comment on this. This is sentence four! Good By................");
        testText.add("Sentence One. Sentence two. Sentence 3? Sentence 4! Sentence w/o punktuation at end of text");
        testText.add("!!! ! ! ! Sentence One. Sentence two. Sentence 3? Sentence 4! Sentence 5 ! ! ! !!!");

        WordCache meaningLib = new WordCache(null);
        boolean doAutotagging = false;
        VocabularyScraper scraper = null;
        for (String text : testText) {
            Tokenizer t = new Tokenizer(null, text, meaningLib, doAutotagging, scraper);
            assertEquals("Tokenizer.RESULT_NUMB_SENTENCES", 5, t.RESULT_NUMB_SENTENCES);
        }
    }
}
