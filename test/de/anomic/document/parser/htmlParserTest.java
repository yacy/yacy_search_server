package de.anomic.document.parser;

import java.nio.charset.Charset;

import net.yacy.document.parser.htmlParser;

import junit.framework.TestCase;

public class htmlParserTest extends TestCase {
	
	public void testGetRealCharsetEncoding() {
		String[][] testStrings = new String[][] {
	       new String[]{null,null},
	       new String[]{"windows1250","windows-1250"},
	       new String[]{"windows_1250","windows-1250"},
	       new String[]{"ISO-8859-1","ISO-8859-1"},
	       new String[]{"ISO8859-1","ISO-8859-1"},
	       new String[]{"ISO-88591","ISO-8859-1"},
	       new String[]{"ISO88591","ISO-8859-1"},
	       new String[]{"iso_8859_1","ISO-8859-1"},
	       new String[]{"cp-1252","windows-1252"},
	       new String[]{"gb_2312","gb2312"},           // was: x-EUC-CN
	       new String[]{"gb_2312-80","gb2312"},           // was: x-EUC-CN
	       new String[]{"UTF-8;","UTF-8"}
		};
		
		for (int i=0; i < testStrings.length; i++) {
			// desired conversion result
			String shouldBe = testStrings[i][1];
			shouldBe = shouldBe!=null ? shouldBe.toLowerCase() : null;
			
			// conversion result
			String charset = htmlParser.patchCharsetEncoding(testStrings[i][0]);
			
			// test if equal
			assertEquals(shouldBe, charset!=null ? charset.toLowerCase() : null);
			System.out.println("testGetRealCharsetEncoding: " + (testStrings[i][0]!=null?testStrings[i][0]:"null") + " -> " + (charset!=null?charset:"null") + " | Supported: " + (charset!=null?Charset.isSupported(charset):false));
			
		}
		
	}

}
