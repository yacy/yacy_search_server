package net.yacy.cora.document.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import org.junit.Test;
//import junit.framework.TestCase;

public class MultiProtocolURLTest {

    @Test
    public void testSessionIdRemoval() throws MalformedURLException {
        String[][] testURIs = new String[][]{
            // meaning:  original uri, stripped version
            new String[]{"http://test.de/bla.php?phpsessionid=asdf", "http://test.de/bla.php"},
            new String[]{"http://test.de/bla?phpsessionid=asdf&fdsa=asdf", "http://test.de/bla?fdsa=asdf"},
            new String[]{"http://test.de/bla?asdf=fdsa&phpsessionid=asdf", "http://test.de/bla?asdf=fdsa"},
            new String[]{"http://test.de/bla?asdf=fdsa&phpsessionid=asdf&fdsa=asdf", "http://test.de/bla?asdf=fdsa&fdsa=asdf"},};
        TreeSet<String> idNames = new TreeSet<String>();
        idNames.add("phpsessionid");

        MultiProtocolURL.initSessionIDNames(idNames);

        for (int i = 0; i < testURIs.length; i++) {
            MultiProtocolURL uri = new MultiProtocolURL(testURIs[i][0]);

            assertEquals(uri.toNormalform(true, true), testURIs[i][1]);
        }
    }

    @Test
    public void testResolveBackpath() {
        String[][] testStrings = new String[][]{
            new String[]{"/..home", "/..home"},
            new String[]{"/test/..home/test.html", "/test/..home/test.html"},
            new String[]{"/test/..", "/"},
            new String[]{"/test/../", "/"},
            new String[]{"/test/test2/..", "/test"},
            new String[]{"/test/test2/../", "/test/"},
            new String[]{"/test/test2/../hallo", "/test/hallo"},
            new String[]{"/test/test2/../hallo/", "/test/hallo/"},
            new String[]{"/home/..test/../hallo/../", "/home/"},
            /* No path segments prior to the '..' segment : '..' still has to be removed (See https://tools.ietf.org/html/rfc3986#section-5.2.4 -> parts 2.C and 2.D )*/
            new String[]{"/../", "/"},
            new String[]{"/..", "/"},
            new String[]{"/../../../image.jpg", "/image.jpg"}
        };
        String testhost = "http://localhost";
        for (int i = 0; i < testStrings.length; i++) {
            // desired conversion result
            System.out.print("testResolveBackpath: " + testStrings[i][0]);
            String shouldBe = testhost + testStrings[i][1];

            // conversion result
            String resolvedURL = "";
            try {
                resolvedURL = (new MultiProtocolURL(testhost + testStrings[i][0])).toNormalform(false);
            } catch (final MalformedURLException ex) {
                fail("malformed URL");
            }
            // test if equal
            assertEquals(shouldBe, resolvedURL);
            System.out.println(" -> " + resolvedURL);

        }
    }
        
    /**
     * Test of isLocal method, of class MultiProtocolURL. including IPv6 url
     * data
     *
     * @throws java.net.MalformedURLException
     */
    @Test
    public void testIsLocal() throws MalformedURLException {
        LinkedHashMap<String,Boolean> testurls = new LinkedHashMap<String,Boolean>(); // <url,expected_result>

        // valid IPv6 local loopback addresses
        testurls.put("http://[0:0:0:0:0:0:0:1]/index.html", Boolean.TRUE);
        testurls.put("http://[::1]/index.html", Boolean.TRUE);
        testurls.put("http://[::1]:8090/index.html", Boolean.TRUE);
        testurls.put("http://[0::1]/index.html", Boolean.TRUE);
        testurls.put("http://[::0:1]:80/index.html", Boolean.TRUE);

        testurls.put("http://[fc00:0:0:0:0:0:0:1]/index.html", Boolean.TRUE);
        testurls.put("http://[fc00::fa01:ff]/index.html", Boolean.TRUE);
        testurls.put("http://[fD00:0:0:0:0:0:0:1]/index.html", Boolean.TRUE);
        testurls.put("http://[fe80:0:0:0:0:0:0:1]/index.html", Boolean.TRUE);
        testurls.put("http://[fe80::1]/index.html", Boolean.TRUE);

        // test urls for possible issue with IPv6 misinterpretation
        testurls.put("http://fcedit.com", Boolean.FALSE);
        testurls.put("http://fdedit.com", Boolean.FALSE);
        testurls.put("http://fe8edit.com", Boolean.FALSE);

        // valid IPv6 examples taken from http://www.ietf.org/rfc/rfc2732.txt  cap 2.
        testurls.put("http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]:80/index.html", Boolean.TRUE); // ip is link-local (fe80/10)
        testurls.put("http://[1080:0:0:0:8:800:200C:417A]/index.html", Boolean.FALSE);
        testurls.put("http://[3ffe:2a00:100:7031::1]", Boolean.FALSE);
        testurls.put("http://[1080::8:800:200C:417A]/foo", Boolean.FALSE);
        testurls.put("http://[::192.9.5.5]/ipng", Boolean.FALSE);
        testurls.put("http://[::FFFF:129.144.52.38]:80/index.html", Boolean.FALSE);
        testurls.put("http://[2010:836B:4179::836B:4179]", Boolean.FALSE);

        for (String u : testurls.keySet()) {

            MultiProtocolURL uri = new MultiProtocolURL(u);
            boolean result = uri.isLocal();

            System.out.println ("testIsLocal: " + u + " -> " + result);
            assertEquals(u, testurls.get(u), result);

        }
    }

    @Test
    public void testGetHost() throws MalformedURLException {
        String[][] testStrings = new String[][]{
            // teststring , expectedresult
            new String[]{"http://www.yacy.net", "www.yacy.net"},
            new String[]{"http://www.yacy.net:8090", "www.yacy.net"},
            new String[]{"http://www.yacy.net/test?query=test", "www.yacy.net"},
            new String[]{"http://www.yacy.net/?query=test", "www.yacy.net"},
            new String[]{"http://www.yacy.net?query=test", "www.yacy.net"},
            new String[]{"http://www.yacy.net:?query=test", "www.yacy.net"},
            new String[]{"//www.yacy.net:?query=test", "www.yacy.net"},
            
            new String[]{"http://www.yacy.net?data=1/2/3", "www.yacy.net"},
            new String[]{"http://www.yacy.net?url=http://test.com", "www.yacy.net"},
            /* Punycode encoded internationalized domain name : Algeria TLD */
            new String[]{"http://xn--ggbdmbaav3cjl1c9heugfv.xn--lgbbat1ad8j/", "xn--ggbdmbaav3cjl1c9heugfv.xn--lgbbat1ad8j"},
            /* Internationalized domain name : Algeria TLD */
            new String[]{"http://مركزأسماءالنطاقات.الجزائر/", "xn--ggbdmbaav3cjl1c9heugfv.xn--lgbbat1ad8j"},
            /* Internationalized domain name : Chinese Ministry of education */
            new String[]{"http://教育部.中国/", "xn--wcvs22dzol.xn--fiqs8s"},
            /*http://教育部.中国/ */
        };

        for (int i = 0; i < testStrings.length; i++) {
            // desired conversion result
            System.out.print("testGetHost: " + testStrings[i][0]);
            String shouldBe = testStrings[i][1];

            // conversion result
            String resolvedHost = new MultiProtocolURL(testStrings[i][0]).getHost();

            // test if equal
            assertEquals(shouldBe, resolvedHost);
            System.out.println(" -> " + resolvedHost);

        }
    }

    /**
     * Test getProtocol()
     */
    @Test
    public void testGetProtocol() throws MalformedURLException {
        Map<String, String> testurls = new HashMap<String, String>();
        // ( 1. parameter = urlstring to test, 2. parameter = expected protocol)
        testurls.put("http://host.com",  "http");
        testurls.put("HTTPS://host.com", "https");
        testurls.put("Ftp://host.com",   "ftp");
        testurls.put("SMB://host.com",   "smb");
        testurls.put("/file.com",        "file");
        testurls.put("file://host.com/file.com", "file");
        testurls.put("MailTo:Abc@host.com",      "mailto");

        for (String txt : testurls.keySet()) {
            MultiProtocolURL url = new MultiProtocolURL(txt);
            assertEquals("test " + txt, url.getProtocol(), testurls.get(txt));

        }
    }

    /**
     * Test of toNormalform method, of class MultiProtocolURL.
     */
    @Test
    public void testToNormalform() throws Exception {
        // some test url/uri with problems in the past
        String[][] testStrings = new String[][]{
            // teststring , expectedresult
            new String[]{"http://www.heise.de/newsticker/thema/%23saukontrovers", "http://www.heise.de/newsticker/thema/%23saukontrovers"}, // http://mantis.tokeek.de/view.php?id=519
            new String[]{"http://www.heise.de/newsticker/thema/#saukontrovers", "http://www.heise.de/newsticker/thema/"}, // anchor fragment
            new String[]{"http://www.liferay.com/community/wiki/-/wiki/Main/Wiki+Portlet", "http://www.liferay.com/community/wiki/-/wiki/Main/Wiki+Portlet"}, // http://mantis.tokeek.de/view.php?id=559
            new String[]{"http://de.wikipedia.org/wiki/Philippe_Ariès", "http://de.wikipedia.org/wiki/Philippe_Ari%C3%A8s"}, // UTF-8 2 byte char
            new String[] {"https://zh.wikipedia.org/wiki/Wikipedia:方針與指引", "https://zh.wikipedia.org/wiki/Wikipedia:%E6%96%B9%E9%87%9D%E8%88%87%E6%8C%87%E5%BC%95"}, // UTF-8 3 bytes chars
            new String[] {"http://教育部.中国/jyb_xwfb/", "http://xn--wcvs22dzol.xn--fiqs8s/jyb_xwfb/"} // Internationalized Domain Name
        };

        for (String[] testString : testStrings) {
            // desired conversion result
            System.out.print("toNormalform orig uri: " + testString[0]);
            String shouldBe = testString[1];
            // conversion result
            String resultUrl = new MultiProtocolURL(testString[0]).toNormalform(true);
            // test if equal
            assertEquals(shouldBe, resultUrl);
            System.out.println(" -> " + resultUrl);
        }
    }

    /**
     * Test of getAttribute method, of class MultiProtocolURL.
     */
    @Test
    public void testGetAttribute() throws Exception {
        // some test url/uri with problems in the past
        String[][] testStrings = new String[][]{
            // teststring , expectedresultkey, expectedresultvalue
            new String[]{"http://yacy.net?&test", "test", ""},
            /* Encoded UTF-8 2 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?search=encodedlatinchars%C3%A0%C3%A4%C3%A2%C3%A9%C3%A8%C3%AF%C3%AE%C3%B4%C3%B6%C3%B9", "search", "encodedlatincharsàäâéèïîôöù"},
            /* Non encoded UTF-8 2 bytes characters parameter value */
            new String[]{"http://yacy.net?query=unencodedlatincharsàäâéèïîôöù", "query", "unencodedlatincharsàäâéèïîôöù"},
            /* Encoded UTF-8 3 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?query=%E6%96%B9%E9%87%9D%E8%88%87%E6%8C%87%E5%BC%95", "query", "方針與指引"},
            /* Non encoded UTF-8 3 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?query=方針與指引", "query", "方針與指引"},
            /* Non encoded rfc3986 unreserved ascii chars parameter value */
            new String[]{"https://example.net?query=-.~_", "query", "-.~_"},
            /* Encoded rfc3986 reserved ascii chars parameter value */
            new String[]{"https://example.net?query=%3A%2F%3F%23%40%24%26%2B%2C%3B%3D", "query", ":/?#@$&+,;="},
            /* Non-Encoded rfc3986 reserved ascii chars parameter value 
             * (some reserved characters have a meaning here and can not be passed as non-encoded without breaking the parameter value : #, &, +) */
            new String[]{"https://example.net?query=:/?[]@!$'()*,;=", "query", ":/?[]@!$'()*,;="},
        };

        for (String[] testString : testStrings) {
            // desired conversion result
            System.out.print("test getAttribute: " + testString[0]);
            String shouldBe = testString[1];

            MultiProtocolURL resultUrl = new MultiProtocolURL(testString[0]);
            System.out.println(" -> " + resultUrl.toNormalform(false));
            Map<String, String> attr = resultUrl.getAttributes();

            assertEquals(testString[2], attr.get(shouldBe));
        }
    }

     /**
     * Test of getFileExtension method, of class MultiProtocolURL.
     */
    @Test
    public void testGetFileExtension() {
        Map<String, String> testurls = new HashMap<String, String>();
        //  key=testurl, value=result
        testurls.put("path/file.xml","xml"); // easiest
        testurls.put("path/file?h.pdf",""); // file w/o extension
        testurls.put("file.html?param=h.pdf","html"); // dot in query part
        testurls.put("url?param=h.pdf",""); // dot in query part
        testurls.put("file.html?param", "html");
        testurls.put("/path/","");
        for (String s : testurls.keySet()) {
            System.out.println("test getFileExtension: " + s + " -> " + testurls.get(s));
            String result = MultiProtocolURL.getFileExtension(s);
            assertEquals(testurls.get(s),result);
        }
    }

     /**
     * Test of toTokens static method, of class MultiProtocolURL.
     */
    @Test
    public void testStaticToTokens() {
        // test string pairs which should generate equal results
        String[][] testString = new String[][]{
            {"abc", "abc "},
            {" cde", "cde"},
            {"  efg", "efg "},
            {"hij hij", " hij "},
            {"klm          mno", "klm@mno"},
            {"abc/cde?fff", "abc\\cde-fff "} };
        String result1, result2;
        for (String[] s : testString) {
            result1 = MultiProtocolURL.toTokens(s[0]);
            result2 = MultiProtocolURL.toTokens(s[1]);
            assertEquals("input: "+s[0]+"="+s[1],result1, result2);
        }
    }
    
	/**
	 * Unit tests for {@link MultiProtocolURL#toTokens()}
	 * @throws MalformedURLException when 
	 */
	@Test
	public void testToTokens() throws MalformedURLException {
        String[][] testStrings = new String[][]{
            // test string , "expected tokens"
            new String[]{"http://yacy.net?&test", "yacy net test"},
            new String[]{"http://example.net/camelCased/subpath1/PATH_EXAMPLE", "example net camelCased subpath1 PATH EXAMPLE camel Cased subpath 1"},
            /* Encoded UTF-8 2 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?search=encodedlatinchars%C3%A0%C3%A4%C3%A2%C3%A9%C3%A8%C3%AF%C3%AE%C3%B4%C3%B6%C3%B9", "zh wikipedia org w index php search encodedlatincharsàäâéèïîôöù"},
            /* Non encoded UTF-8 2 bytes characters parameter value */
            new String[]{"http://yacy.net?query=unencodedlatincharsàäâéèïîôöù", "yacy net query unencodedlatincharsàäâéèïîôöù"},
            /* Encoded UTF-8 3 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?query=%E6%96%B9%E9%87%9D%E8%88%87%E6%8C%87%E5%BC%95", "zh wikipedia org w index php query 方針與指引"},
            /* Non encoded UTF-8 3 bytes characters parameter value */
            new String[]{"https://zh.wikipedia.org/w/index.php?query=方針與指引", "zh wikipedia org w index php query 方針與指引"},
            /* Non encoded rfc3986 unreserved ascii chars parameter value */
            new String[]{"https://example.net?query=-.~_", "example net query"},
            /* Encoded rfc3986 reserved ascii chars parameter value */
            new String[]{"https://example.net?query=%3A%2F%3F%23%40%24%26%2B%2C%3B%3D", "example net query"},
            /* Non-Encoded rfc3986 reserved ascii chars parameter value 
             * (some reserved characters have a meaning here and can not be passed as non-encoded without breaking the parameter value : #, &, +) */
            new String[]{"https://example.net?query=:/?[]@!$'()*,;=", "example net query"}
        };

        for (int i = 0; i < testStrings.length; i++) {
        	String[] testString = testStrings[i];
            MultiProtocolURL resultUrl = new MultiProtocolURL(testString[0]);
            String tokens = resultUrl.toTokens();
            assertEquals("Test toTokens : " + i, testString[1], tokens);
        }
	}
    
	/**
	 * Unit tests for {@link MultiProtocolURL#escape(String)}
	 */
	@Test
	public void testEscape() {
		String[] testStrings = { 
				"", 
				"asciiString", 
				"latin chars:àäâéèïîôöù", 
				"logograms:正體字/繁體字",
				"with spaces and\ttab", 
				"rfc3986 unreserved ascii chars:-.~_",
				"rfc3986 reserved ascii chars::/?#[]@!$&'()*+,;=", 
				"http://simpleurl.com/",
				"http://urlwithqueryandanchor.net/path?q=asciiquery&p1=param1&p2=pâräm2&p3=简化字#anchor" };
		for (String testString : testStrings) {
			String encoded = MultiProtocolURL.escape(testString).toString();
			assertTrue("Encoded string contains only ascii chars",
					StandardCharsets.US_ASCII.newEncoder().canEncode(encoded));
			assertEquals("escape/unescape consistency", testString,
					MultiProtocolURL.unescape(encoded));
		}
	}
	
	/**
	 * Unit tests for {@link MultiProtocolURL#unescape(String)}
	 */
	@Test
	public void testUnescape() {
		String[][] testStrings = new String[][] {
				// test string , "expected unencoded result"
				new String[] { "", "" }, new String[] { "asciiString", "asciiString" },
				new String[] { "encoded latinchars : %C3%A0%C3%A4%C3%A2%C3%A9%C3%A8%C3%AF%C3%AE%C3%B4%C3%B6%C3%B9",
						"encoded latinchars : àäâéèïîôöù" },
				new String[] { "unencoded latin chars : àäâéèïîôöù", "unencoded latin chars : àäâéèïîôöù" },
				new String[] { "encoded logograms : %E6%96%B9%E9%87%9D%E8%88%87%E6%8C%87%E5%BC%95",
						"encoded logograms : 方針與指引" },
				new String[] { "unencoded logograms : 方針與指引", "unencoded logograms : 方針與指引" },
				new String[] { "with spaces and\ttab", "with spaces and\ttab" },
				new String[] { "unencoded rfc3986 unreserved ascii chars:-.~_",
						"unencoded rfc3986 unreserved ascii chars:-.~_" },
				new String[] { "http://simpleurl.com/", "http://simpleurl.com/" },
				new String[] {
						"http://url-with-unencoded-query-and-anchor.net/path?q=asciiquery&p1=param1&p2=pâräm2&p3=简化字#anchor",
						"http://url-with-unencoded-query-and-anchor.net/path?q=asciiquery&p1=param1&p2=pâräm2&p3=简化字#anchor" }, };
		for (int i = 0; i < testStrings.length; i++) {
			String[] testString = testStrings[i];
			String unescaped = MultiProtocolURL.unescape(testString[0]);
			assertEquals(testString[1], unescaped);
		}
	}
	
	/**
	 * Unit tests for {@link MultiProtocolURL#MultiProtocolURL(java.io.File)}
	 * @throws MalformedURLException when an error occurred
	 * @throws URISyntaxException 
	 */
	@Test
	public void testFileConstructor() throws MalformedURLException, URISyntaxException {
		File[] files = new File[] {
			/* Simple file name */
			new File(File.separator + "textFile.txt"),
			/* File name with space */
			new File(File.separator + "text file.txt"),
			/* File name with non ASCII latin chars */
			new File(File.separator + "fileéàè.txt"),
		};
		for(int i = 0; i < files.length; i++) {
			MultiProtocolURL url = new MultiProtocolURL(files[i]);
			assertTrue(url.isFile());
			/* Check consistency when retrieving a File object with getFSFile() */
			assertEquals(files[i], url.getFSFile());
		}
	}
    
}



