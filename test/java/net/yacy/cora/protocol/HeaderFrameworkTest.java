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
}
