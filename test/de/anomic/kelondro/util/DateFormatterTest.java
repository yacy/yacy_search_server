package de.anomic.kelondro.util;

import java.util.Date;

import net.yacy.cora.protocol.HeaderFramework;


import junit.framework.TestCase;

public class DateFormatterTest extends TestCase {

	/**
	 * Test of httpHeader date parsing routine
	 */
	public void testParseHTTPDate() {
		Date parsedDate = HeaderFramework.parseHTTPDate("Tue, 08 Jul 2003 21:22:46 GMT");
		
		// returned date must not be null
		assertNotNull(parsedDate);
		
		// Print Result
		System.out.println("testParseHTTPDate: " + parsedDate.toString());
	}
}
