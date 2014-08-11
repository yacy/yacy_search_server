package net.yacy.cora.document.id;

import java.net.MalformedURLException;
import junit.framework.TestCase;
import org.junit.Test;

public class DigestURLTest extends TestCase {

    @Test
    public void testIdentPort() throws MalformedURLException {
        String[][] testStrings = new String[][]{
            new String[]{"http://www.yacy.net:", "http://www.yacy.net/"},
            new String[]{"http://www.yacy.net:80", "http://www.yacy.net/"},
            new String[]{"http://www.yacy.net:/", "http://www.yacy.net/"},
            new String[]{"http://www.yacy.net: /", "http://www.yacy.net/"}
        };

        for (int i = 0; i < testStrings.length; i++) {
            // desired conversion result
            System.out.print("testIdentPort: " + testStrings[i][0]);
            String shouldBe = testStrings[i][1];

            // conversion result
            String resolvedURL = (new DigestURL(testStrings[i][0])).toNormalform(false);

            // test if equal
            assertEquals(shouldBe, resolvedURL);
            System.out.println(" -> " + resolvedURL);

        }
    }

}
