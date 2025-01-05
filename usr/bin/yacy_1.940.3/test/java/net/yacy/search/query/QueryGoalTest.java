package net.yacy.search.query;

import java.util.HashMap;
import java.util.Iterator;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class QueryGoalTest {

    /**
     * Test of getIncludeString method, of class QueryGoal.
     */
    @Test
    public void testGetIncludeString() {
        HashMap<String, String[]> testdata = new HashMap<String, String[]>();
        // Prameter:  (Query, [result_term1, result_term2 ..])
        testdata.put("O'Reily's book", new String[]{"o'reily's", "book"});
        testdata.put("\"O'Reily's book\"", new String[]{"o'reily's book"}); // quoted term
        testdata.put("\"O'Reily's\" +book", new String[]{"o'reily's", "book"}); // +word
        testdata.put("Umphrey's + McGee", new String[]{"umphrey's", "mcgee"});
        testdata.put("'The Book' library", new String[]{"the book","library"}); //single quoted term

        for (String testquery : testdata.keySet()) {
            QueryGoal qg = new QueryGoal(testquery); // get test query
            String[] singlestr = testdata.get(testquery); // get result strings

            Iterator<String> it = qg.getIncludeStrings();
            int i = 0;
            while (it.hasNext()) {
                String s = it.next();
                System.out.println(singlestr[i] + " = " + s);
                assertEquals(s, singlestr[i]);
                i++;
            }
        }
    }

}
