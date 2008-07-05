//robotsParser.java 
//-------------------------------------
//part of YACY
//
//(C) 2005, 2006 by Alexander Schier
//                  Martin Thelian
//
//last change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.crawler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;

/*
 * A class for Parsing robots.txt files.
 * It only parses the Deny Part, yet.
 *  
 * Robots RFC
 * http://www.robotstxt.org/wc/norobots-rfc.html
 * 
 * TODO:
 *      - On the request attempt resulted in temporary failure a robot
 *      should defer visits to the site until such time as the resource
 *      can be retrieved.
 *      
 *      - Extended Standard for Robot Exclusion 
 *        See: http://www.conman.org/people/spc/robots2.html
 *        
 *      - Robot Exclusion Standard Revisited 
 *        See: http://www.kollar.com/robots.html
 */
public final class robotsParser{
	public static final String ROBOTS_USER_AGENT = "User-agent:".toUpperCase();
    public static final String ROBOTS_DISALLOW = "Disallow:".toUpperCase();
    public static final String ROBOTS_ALLOW = "Allow:".toUpperCase();
    public static final String ROBOTS_COMMENT = "#";
    public static final String ROBOTS_SITEMAP = "Sitemap:".toUpperCase();
    public static final String ROBOTS_CRAWL_DELAY = "Crawl-Delay:".toUpperCase();
    
    /*public robotsParser(URL robotsUrl){
     }*/
    /*
     * this parses the robots.txt.
     * at the Moment it only creates a list of Deny Paths
     */
    
    public static Object[] parse(File robotsFile) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(robotsFile));
            return parse(reader);
        } finally {
            if (reader != null) try{reader.close();}catch(Exception e){/* ignore this */}
        }
    }
    
    @SuppressWarnings("unchecked")
    public static Object[] parse(byte[] robotsTxt) throws IOException {
        if ((robotsTxt == null)||(robotsTxt.length == 0)) return new Object[]{new ArrayList(0),null,null};
        ByteArrayInputStream bin = new ByteArrayInputStream(robotsTxt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
        return parse(reader);
    }
    
    public static Object[] parse(BufferedReader reader) throws IOException{
        ArrayList<String> deny4AllAgents = new ArrayList<String>();
        ArrayList<String> deny4YaCyAgent = new ArrayList<String>();
        
        int pos;
        String line = null, lineUpper = null, sitemap = null;
        Integer crawlDelay = null;
        boolean isRuleBlock4AllAgents = false,
                isRuleBlock4YaCyAgent = false,
                rule4YaCyFound = false,
                inBlock = false;        
        
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            lineUpper = line.toUpperCase();
            
            if (line.length() == 0) {
                // OLD: we have reached the end of the rule block
                // rule4Yacy = false; inBlock = false;
                
                // NEW: just ignore it
            } else if (line.startsWith(ROBOTS_COMMENT)) {
                // we can ignore this. Just a comment line
            } else if (lineUpper.startsWith(ROBOTS_SITEMAP)) {
                pos = line.indexOf(" ");
                if (pos != -1) {
                    sitemap = line.substring(pos).trim();
                }
            } else if (lineUpper.startsWith(ROBOTS_USER_AGENT)) {
                
                if (inBlock) {
                    // we have detected the start of a new block
                    inBlock = false;
                    isRuleBlock4AllAgents = false;
                    isRuleBlock4YaCyAgent = false;
                    crawlDelay = null; // each block has a separate delay
                }
                
                // cutting off comments at the line end
                pos = line.indexOf(ROBOTS_COMMENT);
                if (pos != -1) line = line.substring(0,pos).trim();
                
                // replacing all tabs with spaces
                line = line.replaceAll("\t"," ");
                
                // getting out the robots name
                pos = line.indexOf(" ");
                if (pos != -1) {
                    String userAgent = line.substring(pos).trim();
                    isRuleBlock4AllAgents |= userAgent.equals("*");
                    isRuleBlock4YaCyAgent |= userAgent.toLowerCase().indexOf("yacy") >=0;
                    if (isRuleBlock4YaCyAgent) rule4YaCyFound = true;
                }
            } else if (lineUpper.startsWith(ROBOTS_CRAWL_DELAY)) {
                pos = line.indexOf(" ");
                if (pos != -1) {
                	try {
                		crawlDelay = Integer.valueOf(line.substring(pos).trim());
                	} catch (NumberFormatException e) {
                		// invalid crawling delay
                	}
                } 
            } else if (lineUpper.startsWith(ROBOTS_DISALLOW) || 
                       lineUpper.startsWith(ROBOTS_ALLOW)) {
                inBlock = true;
                boolean isDisallowRule = lineUpper.startsWith(ROBOTS_DISALLOW);
                
                if (isRuleBlock4YaCyAgent || isRuleBlock4AllAgents) {
                    // cutting off comments at the line end
                    pos = line.indexOf(ROBOTS_COMMENT);
                    if (pos != -1) line = line.substring(0,pos).trim();
                                       
                    // cutting of tailing *
                    if (line.endsWith("*")) line = line.substring(0,line.length()-1);
                    
                    // replacing all tabs with spaces
                    line = line.replaceAll("\t"," ");
                    
                    // getting the path
                    pos = line.indexOf(" ");
                    if (pos != -1) {
                        // getting the path
                        String path = line.substring(pos).trim();
                        
                        // unencoding all special charsx
                        try {
                            path = URLDecoder.decode(path,"UTF-8");
                        } catch (Exception e) {
                            /* 
                             * url decoding failed. E.g. because of
                             * "Incomplete trailing escape (%) pattern"
                             */
                        }
                        
                        // escaping all occurences of ; because this char is used as special char in the Robots DB
                        path = path.replaceAll(RobotsTxt.ROBOTS_DB_PATH_SEPARATOR,"%3B");                    
                        
                        // adding it to the pathlist
                        if (!isDisallowRule) path = "!" + path;
                        if (isRuleBlock4AllAgents) deny4AllAgents.add(path);
                        if (isRuleBlock4YaCyAgent) deny4YaCyAgent.add(path);
                    }
                }
            }
        }
        
        ArrayList<String> denyList = (rule4YaCyFound) ? deny4YaCyAgent : deny4AllAgents;
        return new Object[]{denyList,sitemap,crawlDelay};
    }
    
}
