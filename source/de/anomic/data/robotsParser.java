// robotsParser.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.data;

import java.lang.String;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaCrawlRobotsTxt;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.yacy.yacyCore;

/*
 * A class for Parsing robots.txt files.
 * It only parses the Deny Part, yet.
 * TODO: Allow, Do not deny if User-Agent: != yacy
 *
 * http://www.robotstxt.org/wc/norobots-rfc.html
 *
 * Use:
 * robotsParser rp=new Robotsparser(robotsfile);
 * if(rp.isAllowedRobots("/test")){
 *   System.out.println("/test is allowed");
 * }
 */
public final class robotsParser{
    
	/*public robotsParser(URL robotsUrl){
	}*/
	/*
	 * this parses the robots.txt.
	 * at the Moment it only creates a list of Deny Paths
	 */

    public static ArrayList parse(File robotsFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(robotsFile));
        return parse(reader);
    }
    
    public static ArrayList parse(byte[] robotsTxt) throws IOException {
        if ((robotsTxt == null)||(robotsTxt.length == 0)) return new ArrayList(0);
        ByteArrayInputStream bin = new ByteArrayInputStream(robotsTxt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
        return parse(reader);
    }
    
	public static ArrayList parse(BufferedReader reader) throws IOException{
        ArrayList deny = new ArrayList();
        
        int pos;
        String line = null;
        boolean rule4Yacy = false;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                // we have reached the end of the rule block
                rule4Yacy = false;
            } else if (line.startsWith("#")) {
                // we can ignore this. Just a comment line
            } else if ((!rule4Yacy) && (line.startsWith("User-agent:"))) {
                pos = line.indexOf(" ");
                if (pos != -1) {
                    String userAgent = line.substring(pos).trim();
                    rule4Yacy = (userAgent.equals("*") || (userAgent.toLowerCase().indexOf("yacy") >=0));
                }
            } else if (line.startsWith("Disallow:") && rule4Yacy) {
                pos = line.indexOf(" ");
                if (pos != -1) {
                    // getting the path
                    String path = line.substring(pos).trim();
                    
                    // escaping all occurences of ; because this char is used as special char in the Robots DB
                    path = path.replaceAll(";","%3B");
                    
                    // adding it to the pathlist
                    deny.add(path);
                }
            }
        }
        
        return deny;
	}
	
    public static boolean isDisallowed(URL nexturl) {
        if (nexturl == null) throw new IllegalArgumentException();               
        
        // generating the hostname:poart string needed to do a DB lookup
        String urlHostPort = nexturl.getHost() + ":" + ((nexturl.getPort()==-1)?80:nexturl.getPort());
        
        // doing a DB lookup to determine if the robots data is already available
        plasmaCrawlRobotsTxt.Entry robotsTxt4Host = plasmaSwitchboard.robots.getEntry(urlHostPort);
        
        // if we have not found any data or the data is older than 7 days, we need to load it from the remote server
        if ((robotsTxt4Host == null) || 
            (System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000)) {
            URL robotsURL = null;
            // generating the proper url to download the robots txt
            try {                 
                robotsURL = new URL(nexturl.getProtocol(),nexturl.getHost(),(nexturl.getPort()==-1)?80:nexturl.getPort(),"/robots.txt");
            } catch (MalformedURLException e) {
                serverLog.logSevere("ROBOTS","Unable to generate robots.txt URL for URL '" + nexturl.toString() + "'.");
                return false;
            }
            
            boolean accessCompletelyRestricted = false;
            byte[] robotsTxt = null;
            try { 
                Object[] result = downloadRobotsTxt(robotsURL,5);
                accessCompletelyRestricted = ((Boolean)result[0]).booleanValue();
                robotsTxt = (byte[])result[1];
                
            } catch (Exception e) {
                serverLog.logSevere("ROBOTS","Unable to download the robots.txt file from URL '" + robotsURL + "'. " + e.getMessage());
            }
            
            ArrayList denyPath = null;
            if (accessCompletelyRestricted) {
                denyPath = new ArrayList();
                denyPath.add("/");
            } else {
                // parsing the robots.txt Data and converting it into an arraylist
                try {
                    denyPath = robotsParser.parse(robotsTxt);
                } catch (IOException e) {
                    serverLog.logSevere("ROBOTS","Unable to parse the robots.txt file from URL '" + robotsURL + "'.");
                }
            } 
            
            // storing the data into the robots DB
            robotsTxt4Host = plasmaSwitchboard.robots.addEntry(urlHostPort,denyPath,new Date());
        }        
        
        if (robotsTxt4Host.isDisallowed(nexturl.getPath())) {
            return true;        
        }        
        return false;
    }
    
    private static Object[] downloadRobotsTxt(URL robotsURL, int redirectionCount) throws Exception {
        
        if (redirectionCount < 0) return new Object[]{Boolean.FALSE,null};
        redirectionCount--;
        
        boolean accessCompletelyRestricted = false;
        byte[] robotsTxt = null;
        httpc con = null;
        try {
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            if (!sb.remoteProxyUse) {
                con = httpc.getInstance(robotsURL.getHost(), robotsURL.getPort(), 10000, false);
            } else {
                con = httpc.getInstance(robotsURL.getHost(), robotsURL.getPort(), 10000, false, sb.remoteProxyHost, sb.remoteProxyPort);
            }
            
            httpc.response res = con.GET(robotsURL.getPath(), null);
            if (res.status.startsWith("2")) {
                if (!res.responseHeader.mime().startsWith("text/plain")) {
                    robotsTxt = null;
                    serverLog.logFinest("ROBOTS","Robots.txt from URL '" + robotsURL + "' has wrong mimetype '" + res.responseHeader.mime() + "'.");                    
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    res.writeContent(bos, null);
                    con.close();
                    robotsTxt = bos.toByteArray();
                    serverLog.logFinest("ROBOTS","Robots.txt successfully loaded from URL '" + robotsURL + "'.");
                }
            } else if (res.status.startsWith("3")) {
                // getting redirection URL
                String redirectionUrlString = (String) res.responseHeader.get(httpHeader.LOCATION);
                redirectionUrlString = redirectionUrlString.trim();

                // generating the new URL object
                URL redirectionUrl = new URL(robotsURL, redirectionUrlString);

                // returning the used httpc
                httpc.returnInstance(con); 
                con = null;            
                
                // following the redirection
                serverLog.logFinest("ROBOTS","Redirection detected for Robots.txt with URL '" + robotsURL + "'." + 
                                    "\nRedirecting request to: " + redirectionUrl);
                return downloadRobotsTxt(redirectionUrl,redirectionCount);
                
            } else if (res.status.startsWith("401") || res.status.startsWith("403")) {
                accessCompletelyRestricted = true;
                serverLog.logFinest("ROBOTS","Access to Robots.txt not allowed on URL '" + robotsURL + "'.");
            } else {
                serverLog.logFinest("ROBOTS","Robots.txt could not be downloaded from URL '" + robotsURL + "'. [" + res.status + "].");
                robotsTxt = null;
            }        
        } catch (Exception e) {
            throw e;
        } finally {
            if (con != null) httpc.returnInstance(con);            
        }            
        return new Object[]{new Boolean(accessCompletelyRestricted),robotsTxt};
    }

//	/*
//	 * Test the class with a file robots.txt in the workdir and a testpath as argument.
//	 */
//	public static void main(String[] args){
//		robotsParser rp=new robotsParser(new File("robots.txt"));
//		for(int i=0;i<args.length;i++){
//			if(rp.isAllowedRobots(args[i])){
//				System.out.println(args[i]+" is allowed.");
//			}else{
//				System.out.println(args[i]+" is NOT allowed.");
//			}
//		}
//	}
}
