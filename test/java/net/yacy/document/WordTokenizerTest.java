package net.yacy.document;

import org.junit.Test;
import static org.junit.Assert.*;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

public class WordTokenizerTest {

    /**
     * Test of nextElement method, of class WordTokenizer.
     */
    @Test
    public void testNextElement() {
        // test sentences containing 10x the word "word"
        String[] testTxtArr = new String[]{
            "  word word..... (word) [word] . 'word word' \"word word\" word ?  word! ",
            "word-word word . word.word@word.word ....word... word,word word word"
            // !! 12x the word "word" because "word-word" and "word,word" is one word
        };
        
        for (String testTxt : testTxtArr) {
            SentenceReader sr = new SentenceReader(testTxt);
            WordTokenizer wt = new WordTokenizer(sr, null);
            int cnt = 0;
            while (wt.hasMoreElements()) {
                StringBuilder sb = wt.nextElement();
                if (sb.length() > 1) { // skip punktuation
                    MatcherAssert.assertThat(sb.toString(),
                        CoreMatchers.either(CoreMatchers.is("word"))
                            .or(CoreMatchers.is("word-word"))
                            .or(CoreMatchers.is("word,word")));
                    cnt++;
                } else {
                    assertTrue("punktuation", SentenceReader.punctuation(sb.charAt(0)));
                }
            }
            wt.close();
            assertEquals(10, cnt);
        }
    }

}
