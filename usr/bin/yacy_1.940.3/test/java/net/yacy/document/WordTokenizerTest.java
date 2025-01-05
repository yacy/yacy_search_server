package net.yacy.document;

import org.junit.Test;
import static org.junit.Assert.*;

public class WordTokenizerTest {

    /**
     * Test of nextElement method, of class WordTokenizer.
     */
    @Test
    public void testNextElement() {
        // test sentences containing 10x the word "word"
        String[] testTxtArr = new String[]{
            "  word word..... (word) [word] . 'word word' \"word word\" word ?  word! ",
            "word-word word . word.word@word.word ....word... word,word "
        };
        
        for (String testTxt : testTxtArr) {
            SentenceReader sr = new SentenceReader(testTxt);
            WordTokenizer wt = new WordTokenizer(sr, null);
            int cnt = 0;
            while (wt.hasMoreElements()) {
                StringBuilder sb = wt.nextElement();
                if (sb.length() > 1) { // skip punktuation
                    assertEquals("word", sb.toString());
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
