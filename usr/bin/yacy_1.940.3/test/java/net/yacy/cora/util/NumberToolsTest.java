package net.yacy.cora.util;

import static net.yacy.cora.util.NumberTools.parseIntDecSubstring;
import org.junit.Test;
import static org.junit.Assert.*;

public class NumberToolsTest {

    /**
     * Test of parseLongDecSubstring method, of class NumberTools.
     */
    @Test
    public void testParseIntDecSubstring() {
        String[] TestNumbers = new String[]{
            "101", " 102", " 103", " 104 ",
            "+105", " -106", " +107 ", " -108 ",
            "109px", " 110px"};

        int i=101;
        for (String s : TestNumbers) {
            int result = parseIntDecSubstring(s);
            assertEquals (s + " = " + Integer.toString(i),i,Math.abs(result));
            i++;
        }

    }
}
