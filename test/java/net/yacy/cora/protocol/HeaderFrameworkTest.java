package net.yacy.cora.protocol;

import java.util.Date;
import junit.framework.TestCase;
import org.junit.Test;

public class HeaderFrameworkTest extends TestCase {

	/**
	 * Test of httpHeader date parsing routine
	 */
        @Test
	public void testParseHTTPDate() {
		Date parsedDate = HeaderFramework.parseHTTPDate("Tue, 08 Jul 2003 21:22:46 GMT");
		
		// returned date must not be null
		assertNotNull(parsedDate);
		
		// Print Result
		System.out.println("testParseHTTPDate: " + parsedDate.toString());
	}
}
