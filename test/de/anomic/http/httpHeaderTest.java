package de.anomic.http;

import java.util.Date;

import junit.framework.TestCase;

public class httpHeaderTest extends TestCase {

	/**
	 * Test of httpHeader date parsing routine
	 */
	public void testParseHTTPDate() {
		Date parsedDate = httpHeader.parseHTTPDate("Tue, 08 Jul 2003 21:22:46 GMT");
		
		// returned date must not be null
		assertNotNull(parsedDate);
		
		// Print Result
		System.out.println("testParseHTTPDate: " + parsedDate.toString());
	}
}
