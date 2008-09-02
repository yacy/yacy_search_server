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

// extended to return structured objects instead of a Object[] and
// extended to return a Allow-List by Michael Christen, 21.07.2008

package de.anomic.crawler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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

public final class robotsParser {
    
	public static final String ROBOTS_USER_AGENT = "User-agent:".toUpperCase();
    public static final String ROBOTS_DISALLOW = "Disallow:".toUpperCase();
    public static final String ROBOTS_ALLOW = "Allow:".toUpperCase();
    public static final String ROBOTS_COMMENT = "#";
    public static final String ROBOTS_SITEMAP = "Sitemap:".toUpperCase();
    public static final String ROBOTS_CRAWL_DELAY = "Crawl-Delay:".toUpperCase();
    
    private ArrayList<String> allowList;
    private ArrayList<String> denyList;
    private String sitemap;
    private long crawlDelayMillis;
    
    public robotsParser(final byte[] robotsTxt) {
        if ((robotsTxt == null)||(robotsTxt.length == 0)) {
            allowList = new ArrayList<String>(0);
            denyList = new ArrayList<String>(0);
            sitemap = "";
            crawlDelayMillis = 0;
        } else {
            final ByteArrayInputStream bin = new ByteArrayInputStream(robotsTxt);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
            parse(reader);
        }
    }
    
    public robotsParser(final BufferedReader reader) {
        if (reader == null) {
            allowList = new ArrayList<String>(0);
            denyList = new ArrayList<String>(0);
            sitemap = "";
            crawlDelayMillis = 0;
        } else {
            parse(reader);
        }
    }
    
    private void parse(final BufferedReader reader) {
        final ArrayList<String> deny4AllAgents = new ArrayList<String>();
        final ArrayList<String> deny4YaCyAgent = new ArrayList<String>();
        final ArrayList<String> allow4AllAgents = new ArrayList<String>();
        final ArrayList<String> allow4YaCyAgent = new ArrayList<String>();
        
        int pos;
        String line = null, lineUpper = null;
        sitemap = null;
        crawlDelayMillis = 0;
        boolean isRule4AllAgents = false,
                isRule4YaCyAgent = false,
                rule4YaCyFound = false,
                inBlock = false;        
        
        try {
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
                        isRule4AllAgents = false;
                        isRule4YaCyAgent = false;
                        crawlDelayMillis = 0; // each block has a separate delay
                    }
                    
                    // cutting off comments at the line end
                    pos = line.indexOf(ROBOTS_COMMENT);
                    if (pos != -1) line = line.substring(0,pos).trim();
                    
                    // replacing all tabs with spaces
                    line = line.replaceAll("\t"," ").replaceAll(":"," ");
                    
                    // getting out the robots name
                    pos = line.indexOf(" ");
                    if (pos != -1) {
                        final String userAgent = line.substring(pos).trim();
                        isRule4AllAgents |= userAgent.equals("*");
                        isRule4YaCyAgent |= userAgent.toLowerCase().indexOf("yacy") >=0;
                        if (isRule4YaCyAgent) rule4YaCyFound = true;
                    }
                } else if (lineUpper.startsWith(ROBOTS_CRAWL_DELAY)) {
                    // replacing all tabs with spaces
                    line = line.replaceAll("\t"," ").replaceAll(":"," ");
                    
                    pos = line.indexOf(" ");
                    if (pos != -1) {
                    	try {
                    	    // the crawl delay can be a float number and means number of seconds
                    	    crawlDelayMillis = (long) (1000.0 * Float.parseFloat(line.substring(pos).trim()));
                    	} catch (final NumberFormatException e) {
                    		// invalid crawling delay
                    	}
                    } 
                } else if (lineUpper.startsWith(ROBOTS_DISALLOW) || 
                           lineUpper.startsWith(ROBOTS_ALLOW)) {
                    inBlock = true;
                    final boolean isDisallowRule = lineUpper.startsWith(ROBOTS_DISALLOW);
                    
                    if (isRule4YaCyAgent || isRule4AllAgents) {
                        // cutting off comments at the line end
                        pos = line.indexOf(ROBOTS_COMMENT);
                        if (pos != -1) line = line.substring(0,pos).trim();
                                           
                        // cutting of tailing *
                        if (line.endsWith("*")) line = line.substring(0,line.length()-1);
                        
                        // replacing all tabs with spaces
                        line = line.replaceAll("\t"," ").replaceAll(":"," ");
                        
                        // getting the path
                        pos = line.indexOf(" ");
                        if (pos != -1) {
                            // getting the path
                            String path = line.substring(pos).trim();
                            
                            // unencoding all special charsx
                            try {
                                path = URLDecoder.decode(path,"UTF-8");
                            } catch (final Exception e) {
                                /* 
                                 * url decoding failed. E.g. because of
                                 * "Incomplete trailing escape (%) pattern"
                                 */
                            }
                            
                            // escaping all occurences of ; because this char is used as special char in the Robots DB
                            path = path.replaceAll(RobotsTxt.ROBOTS_DB_PATH_SEPARATOR,"%3B");                    
                            
                            // adding it to the pathlist
                            if (isDisallowRule) {
                                if (isRule4AllAgents) deny4AllAgents.add(path);
                                if (isRule4YaCyAgent) deny4YaCyAgent.add(path);
                            } else {
                                if (isRule4AllAgents) allow4AllAgents.add(path);
                                if (isRule4YaCyAgent) allow4YaCyAgent.add(path);
                            }
                        }
                    }
                }
            }
        } catch (final IOException e) {}
        
        allowList = (rule4YaCyFound) ? allow4YaCyAgent : allow4AllAgents;
        denyList = (rule4YaCyFound) ? deny4YaCyAgent : deny4AllAgents;
    }
    
    public long crawlDelayMillis() {
        return this.crawlDelayMillis;
    }
    
    public String sitemap() {
        return this.sitemap;
    }
    
    public ArrayList<String> allowList() {
        return this.allowList;
    }
    
    public ArrayList<String> denyList() {
        return this.denyList;
    }
}
