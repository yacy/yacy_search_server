package net.yacy.peers.operation;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class yacyVersionTest extends TestCase {

	/**
	 * Test method for 'yacy.combinedVersionString2PrettyString(String)'
	 * @author Bost
	 */
        @Test
	public void testCombinedVersionString2PrettyString() {
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion(""));                 // not a number
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion(" "));                // not a number
        Assert.assertArrayEquals(new String[]{"dev","02417"},       yacyVersion.combined2prettyVersion("0.10002417"));
        Assert.assertArrayEquals(new String[]{"dev","0244"},        yacyVersion.combined2prettyVersion("0.1000244"));
        Assert.assertArrayEquals(new String[]{"dev","02417"},       yacyVersion.combined2prettyVersion("0.10002417"));
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("0.100024400"));      // input is too long
        Assert.assertArrayEquals(new String[]{"dev","0244"},        yacyVersion.combined2prettyVersion("0.1090244"));
        Assert.assertArrayEquals(new String[]{"0.110","0244"},      yacyVersion.combined2prettyVersion("0.1100244"));
        Assert.assertArrayEquals(new String[]{"0.111","0244"},      yacyVersion.combined2prettyVersion("0.1110244"));    
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("0.0"));       // input is valid - no warning generated
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("    0.11102440"));   // spaces are not allowed
        Assert.assertArrayEquals(new String[]{"0.111","0000"},      yacyVersion.combined2prettyVersion("0.111"));         // was (input is too short)
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("0.1112440\t\n"));    // \t and \n are not allowed
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("124353432xxxx4546399999"));  // not a number + too long 
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("123456789x"));       // not a number
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("9999999999"));       // missing decimal point
        Assert.assertArrayEquals(new String[]{"999.999","9990"},    yacyVersion.combined2prettyVersion("999.999999"));       // was (floating point part must have 3 and SVN-Version 4 digits)
        Assert.assertArrayEquals(new String[]{"0.999","99999"},     yacyVersion.combined2prettyVersion("0.99999999"));    
        Assert.assertArrayEquals(new String[]{"99999.004","56789"}, yacyVersion.combined2prettyVersion("99999.00456789"));    
        Assert.assertArrayEquals(new String[]{"dev","0000"},        yacyVersion.combined2prettyVersion("99999.003456789"));  // input is too long
    }

}
