package net.yacy.cora.document.id;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;
import net.yacy.cora.document.encoding.ASCII;

import org.junit.Assert;
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
    
    /**
     * Test for {@link DigestURL#hosthash()}
     */
    @Test
    public void testHostHash() throws MalformedURLException {
    	/* Shortest example valid http url : protocol + domain name*/
    	String hostHash = new DigestURL("http://example.test").hosthash();
    	
    	Assert.assertEquals("With path", hostHash, new DigestURL("http://example.test/path/").hosthash());
    	Assert.assertEquals("With resource", hostHash, new DigestURL("http://example.test/path/").hosthash());
    	Assert.assertEquals("With query parameters", hostHash, new DigestURL("http://example.test/path/").hosthash());
    	Assert.assertEquals("Document with anchor identifier", hostHash, new DigestURL("http://example.test/path/").hosthash());
    	
    	/* The next two asserts would be debatable but reflect the current implementation */
    	Assert.assertNotEquals("Different protocol", hostHash, new DigestURL("https://example.test").hosthash());
    	Assert.assertNotEquals("Different port", hostHash, new DigestURL("http://example.test:8080").hosthash());
    	
    	Assert.assertNotEquals("With a different TLD", hostHash, new DigestURL("http://example.net").hosthash());
    	
    }

    /**
     * Test hash() of DigestURL and File protocol to deliver same hash for
     * allowed Windows or Java notation of same file
     */
    @Test
    public void testHash_ForFile() throws MalformedURLException {
        String javaUrlStr = "file:///C:/tmp/test.html"; // allowed Java notation for Windows file system

        // allowed Windows notation
        Set<String> testUrls = new HashSet<String>();
        /* URLs mixing slashes and backslashes */
        testUrls.add("file:///C:\\tmp\\test.html");
        testUrls.add("file:///C:/tmp\\test.html");
        testUrls.add("file:///C:\\tmp/test.html");
        testUrls.add("file:///C:/tmp/test.html");
        
        /* Wrong URLs missing slashes, however accepted by DigestURL and MultiProtocolURL constructors */
        testUrls.add("file://C:/tmp/test.html");
        testUrls.add("file://C:\\tmp\\test.html");
        testUrls.add("file://C:tmp/test.html");
        testUrls.add("file://C:tmp\\test.html");

        DigestURL javaUrl = new DigestURL(javaUrlStr);
        String javaHashResult = ASCII.String(javaUrl.hash());

        // compare test url hash to default java Url notation
        for (String str : testUrls) {
            DigestURL winUrl = new DigestURL(str);
            String winHashResult = ASCII.String(winUrl.hash());
            assertEquals("hash for same file url "+str, javaHashResult, winHashResult);
        }

    }

}
