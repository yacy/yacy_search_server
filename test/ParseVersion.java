import junit.framework.TestCase;

public class ParseVersion extends TestCase {

	/**
	 * Test method for 'yacy.combinedVersionString2PrettyString(String)'
	 * @author Bost
	 */
	public void testCombinedVersionString2PrettyString() {
        assertEquals("dev/00000",       yacy.combined2prettyVersion(""));                 // not a number
        assertEquals("dev/00000",       yacy.combined2prettyVersion(" "));                // not a number
        assertEquals("dev/02417",       yacy.combined2prettyVersion("0.10002417"));
        assertEquals("dev/02440",       yacy.combined2prettyVersion("0.1000244"));
        assertEquals("dev/02417",       yacy.combined2prettyVersion("0.10002417"));
        assertEquals("dev/00000",       yacy.combined2prettyVersion("0.100024400"));      // input is too long
        assertEquals("dev/02440",       yacy.combined2prettyVersion("0.1090244"));
        assertEquals("0.110/02440",     yacy.combined2prettyVersion("0.1100244"));
        assertEquals("0.111/02440",     yacy.combined2prettyVersion("0.1110244"));    
        assertEquals("dev/00000",       yacy.combined2prettyVersion("0.0"));       // input is valid - no warning generated
        assertEquals("dev/00000",       yacy.combined2prettyVersion("    0.11102440"));   // spaces are not allowed
        assertEquals("dev/00000",       yacy.combined2prettyVersion("0.111"));         // input is too short
        assertEquals("dev/00000",       yacy.combined2prettyVersion("0.1112440\t\n"));    // \t and \n are not allowed
        assertEquals("dev/00000",       yacy.combined2prettyVersion("124353432xxxx4546399999"));  // not a number + too long 
        assertEquals("dev/00000",       yacy.combined2prettyVersion("123456789x"));       // not a number
        assertEquals("dev/00000",       yacy.combined2prettyVersion("9999999999"));       // missing decimal point
        assertEquals("dev/00000",       yacy.combined2prettyVersion("999.999999"));       // floating point part must have 3 and SVN-Version 5 digits
        assertEquals("0.999/99999",     yacy.combined2prettyVersion("0.99999999"));    
        assertEquals("99999.004/56789", yacy.combined2prettyVersion("99999.00456789"));    
        assertEquals("dev/00000",       yacy.combined2prettyVersion("99999.003456789"));  // input is too long
    }

}
