package net.yacy.cora.document;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.util.TreeSet;
import net.yacy.cora.document.id.MultiProtocolURL;

import org.junit.Test;


public class MultiProtocolURITest {

	@Test public void testSessionIdRemoval() throws MalformedURLException {
		String[][] testURIs = new String[][] {
			// meaning:  original uri, stripped version
			new String[]{"http://test.de/bla.php?phpsessionid=asdf", "http://test.de/bla.php"},
			new String[]{"http://test.de/bla?phpsessionid=asdf&fdsa=asdf", "http://test.de/bla?fdsa=asdf"},
			new String[]{"http://test.de/bla?asdf=fdsa&phpsessionid=asdf", "http://test.de/bla?asdf=fdsa"},
			new String[]{"http://test.de/bla?asdf=fdsa&phpsessionid=asdf&fdsa=asdf", "http://test.de/bla?asdf=fdsa&fdsa=asdf"},
		};
		TreeSet<String> idNames = new TreeSet<String>();
		idNames.add("phpsessionid");

		MultiProtocolURL.initSessionIDNames(idNames);

		for (int i=0; i<testURIs.length; i++) {
			MultiProtocolURL uri = new MultiProtocolURL(testURIs[i][0]);
    
   			assertEquals(uri.toNormalform(true, true), testURIs[i][1]);
		}
	}
}

