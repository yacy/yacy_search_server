// HeaderFrameworkTest.java
// Copyright 2006-2018 by theli, f1ori, Michael Peter Christen; mc@yacy.net, reger; reger18@arcor.de, luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.cora.protocol;

import java.time.Instant;
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
		
		parsedDate = HeaderFramework.parseHTTPDate("Monday, 12-Nov-07 10:11:12 GMT");
		
		// returned date must not be null
		assertNotNull(parsedDate);
		
		// Print Result
		System.out.println("testParseHTTPDate: " + parsedDate.toString());
		
		parsedDate = HeaderFramework.parseHTTPDate("Mon Nov 12 10:11:12 2007");
		
		// returned date must not be null
		assertNotNull(parsedDate);
		
		// Print Result
		System.out.println("testParseHTTPDate: " + parsedDate.toString());
	}
    
    /**
     * Unit test for character encoding retrieval
     */
    @Test
    public void testGetCharacterEncoding() {
    	/* Examples from RFC 7231 - HTTP/1.1, section "Media Type" (https://tools.ietf.org/html/rfc7231#section-3.1.1.1)*/
    	assertEquals("utf-8", HeaderFramework.getCharacterEncoding("text/html;charset=utf-8"));
    	assertEquals("UTF-8", HeaderFramework.getCharacterEncoding("text/html;charset=UTF-8"));
    	assertEquals("utf-8", HeaderFramework.getCharacterEncoding("Text/HTML;Charset=\"utf-8\""));
    	assertEquals("utf-8", HeaderFramework.getCharacterEncoding("text/html; charset=\"utf-8\""));
    }
    
	/**
	 * Unit test for date formatting with RFC 1123 format
	 */
	@Test
	public void testFormatRfc1123() {
		assertEquals("", HeaderFramework.formatRFC1123(null));

		final Instant time = Instant.parse("2018-06-29T13:04:55.00Z");
		assertEquals("Fri, 29 Jun 2018 13:04:55 +0000", HeaderFramework.formatRFC1123(time.toEpochMilli()));
		assertEquals("Fri, 29 Jun 2018 13:04:55 +0000", HeaderFramework.formatRFC1123(Date.from(time)));
	}
}
