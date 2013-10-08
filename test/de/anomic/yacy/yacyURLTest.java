package de.anomic.yacy;

import java.net.MalformedURLException;


import junit.framework.TestCase;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;

public class yacyURLTest extends TestCase {

	public void testResolveBackpath() {
		String[][] testStrings = new String[][] {
				new String[]{"/..home","/..home"},
				new String[]{"/test/..home/test.html","/test/..home/test.html"},
				new String[]{"/../","/../"},
				new String[]{"/..","/.."},
				new String[]{"/test/..","/"},
				new String[]{"/test/../","/"},
				new String[]{"/test/test2/..","/test"},
				new String[]{"/test/test2/../","/test/"},
				new String[]{"/test/test2/../hallo","/test/hallo"},
				new String[]{"/test/test2/../hallo/","/test/hallo/"},
				new String[]{"/home/..test/../hallo/../","/home/"}
		};		
		String testhost = "http://localhost";
		for (int i=0; i < testStrings.length; i++) {
			// desired conversion result
			System.out.print("testResolveBackpath: " + testStrings[i][0]);
			String shouldBe = testhost + testStrings[i][1];
			
			// conversion result
			String resolvedURL="";
                        try {
                            resolvedURL = (new MultiProtocolURL(testhost + testStrings[i][0])).toString();
                        } catch (final MalformedURLException ex) {
                            fail ("malformed URL");
                        }
			// test if equal
			assertEquals(shouldBe,resolvedURL);
			System.out.println(" -> " + resolvedURL);
			
		}		
	}
	
	public void testIdentPort() throws MalformedURLException {
		String[][] testStrings = new String[][] {
				new String[]{"http://www.yacy.net:","http://www.yacy.net/"},
				new String[]{"http://www.yacy.net:-1","http://www.yacy.net/"},
				new String[]{"http://www.yacy.net:/","http://www.yacy.net/"},
				new String[]{"http://www.yacy.net: /","http://www.yacy.net/"}
		};
		
		for (int i=0; i < testStrings.length; i++) {
			// desired conversion result
			System.out.print("testIdentPort: " + testStrings[i][0]);
			String shouldBe = testStrings[i][1];
			
			// conversion result
			String resolvedURL = (new DigestURL(testStrings[i][0])).toString();
			
			// test if equal
			assertEquals(shouldBe,resolvedURL);
			System.out.println(" -> " + resolvedURL);
			
		}			
	}
	
}
