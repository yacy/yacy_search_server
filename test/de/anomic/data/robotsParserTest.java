package de.anomic.data;

import junit.framework.TestCase;
import de.anomic.net.URL;

public class robotsParserTest extends TestCase {
	public void testDownload() throws Exception {
        URL robotsURL = new URL("http://www.bigfoot2002.de.vu/robots.txt");
        Object[] result = robotsParser.downloadRobotsTxt(robotsURL,5,null);
        
        if (result != null) {
        	System.out.println("Access restricted: " + result[robotsParser.DOWNLOAD_ACCESS_RESTRICTED]);
            System.out.println("ETag: " + result[robotsParser.DOWNLOAD_ETAG]);
            System.out.println("Mod-Date: " + result[robotsParser.DOWNLOAD_MODDATE]);
            System.out.println("-------------------------------- Robots.txt START: -------------------------------");
            System.out.println(new String((byte[])result[robotsParser.DOWNLOAD_ROBOTS_TXT]));
            System.out.println("-------------------------------- Robots.txt END: ---------------------------------");
        }
	}
}
