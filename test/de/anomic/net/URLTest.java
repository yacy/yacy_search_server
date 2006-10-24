package de.anomic.net;

import java.net.MalformedURLException;

import junit.framework.TestCase;

public class URLTest extends TestCase {

	public void testResolveBackpath() throws MalformedURLException {
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
		
		URL urlObj = new URL("http://yacy.net");
		for (int i=0; i < testStrings.length; i++) {
			// desired conversion result
			System.out.print("testResolveBackpath: " + testStrings[i][0]);
			String shouldBe = testStrings[i][1];
			
			// conversion result
			String resolvedURL = urlObj.resolveBackpath(testStrings[i][0]);
			
			// test if equal
			assertEquals(shouldBe,resolvedURL);
			System.out.println(" -> " + resolvedURL);
			
		}		
	}
	
}
