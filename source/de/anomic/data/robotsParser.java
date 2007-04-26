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

package de.anomic.data;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlRobotsTxt;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

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
	public static final int DOWNLOAD_ACCESS_RESTRICTED = 0;
	public static final int DOWNLOAD_ROBOTS_TXT = 1;
	public static final int DOWNLOAD_ETAG = 2;
	public static final int DOWNLOAD_MODDATE = 3;
	
    public static final String ROBOTS_USER_AGENT = "User-agent:".toUpperCase();
    public static final String ROBOTS_DISALLOW = "Disallow:".toUpperCase();
    public static final String ROBOTS_ALLOW = "Allow:".toUpperCase();
    public static final String ROBOTS_COMMENT = "#";
    public static final String ROBOTS_SITEMAP = "Sitemap:".toUpperCase();
    
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
    
    public static Object[] parse(byte[] robotsTxt) throws IOException {
        if ((robotsTxt == null)||(robotsTxt.length == 0)) return new Object[]{new ArrayList(0),null};
        ByteArrayInputStream bin = new ByteArrayInputStream(robotsTxt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
        return parse(reader);
    }
    
    public static Object[] parse(BufferedReader reader) throws IOException{
        ArrayList deny4AllAgents = new ArrayList();
        ArrayList deny4YaCyAgent = new ArrayList();
        
        int pos;
        String line = null, lineUpper = null, sitemap = null;
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
            } else if (line.startsWith(ROBOTS_SITEMAP)) {
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
                        path = path.replaceAll(plasmaCrawlRobotsTxt.ROBOTS_DB_PATH_SEPARATOR,"%3B");                    
                        
                        // adding it to the pathlist
                        if (!isDisallowRule) path = "!" + path;
                        if (isRuleBlock4AllAgents) deny4AllAgents.add(path);
                        if (isRuleBlock4YaCyAgent) deny4YaCyAgent.add(path);
                    }
                }
            }
        }
        
        ArrayList denyList = (rule4YaCyFound)?deny4YaCyAgent:deny4AllAgents;
        return new Object[]{denyList,sitemap};
    }        
    
    public static boolean isDisallowed(URL nexturl) {
        if (nexturl == null) throw new IllegalArgumentException();               
        
        // generating the hostname:poart string needed to do a DB lookup
        String urlHostPort = null;
        int port = nexturl.getPort();
        if (port == -1) {
            if (nexturl.getProtocol().equalsIgnoreCase("http")) {
                port = 80;
            } else if (nexturl.getProtocol().equalsIgnoreCase("https")) {
                port = 443;
            }
            
        }
        urlHostPort = nexturl.getHost() + ":" + port;
        urlHostPort = urlHostPort.toLowerCase().intern();
        
        plasmaCrawlRobotsTxt.Entry robotsTxt4Host = null;
        synchronized(urlHostPort) {
            // doing a DB lookup to determine if the robots data is already available
            robotsTxt4Host = plasmaSwitchboard.robots.getEntry(urlHostPort);
            
            // if we have not found any data or the data is older than 7 days, we need to load it from the remote server
            if (
                    (robotsTxt4Host == null) || 
                    (robotsTxt4Host.getLoadedDate() == null) ||
                    (System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000)
            ) {
                URL robotsURL = null;
                // generating the proper url to download the robots txt
                try {                 
                    robotsURL = new URL(nexturl.getProtocol(),nexturl.getHost(),port,"/robots.txt");
                } catch (MalformedURLException e) {
                    serverLog.logSevere("ROBOTS","Unable to generate robots.txt URL for URL '" + nexturl.toString() + "'.");
                    return false;
                }
                
                Object[] result = null;
                boolean accessCompletelyRestricted = false;
                byte[] robotsTxt = null;
                String eTag = null;
                Date modDate = null;
                try { 
                    serverLog.logFine("ROBOTS","Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    result = downloadRobotsTxt(robotsURL,5,robotsTxt4Host);
                    
                    if (result != null) {
                        accessCompletelyRestricted = ((Boolean)result[DOWNLOAD_ACCESS_RESTRICTED]).booleanValue();
                        robotsTxt = (byte[])result[DOWNLOAD_ROBOTS_TXT];
                        eTag = (String) result[DOWNLOAD_ETAG];
                        modDate = (Date) result[DOWNLOAD_MODDATE];
                    } else if (robotsTxt4Host != null) {
                        robotsTxt4Host.setLoadedDate(new Date());
                        plasmaSwitchboard.robots.addEntry(robotsTxt4Host);
                    }
                } catch (Exception e) {
                    serverLog.logSevere("ROBOTS","Unable to download the robots.txt file from URL '" + robotsURL + "'. " + e.getMessage());
                }
                
                if ((robotsTxt4Host==null)||((robotsTxt4Host!=null)&&(result!=null))) {
                    ArrayList denyPath = null;
                    String sitemap = null;
                    if (accessCompletelyRestricted) {
                        denyPath = new ArrayList();
                        denyPath.add("/");
                    } else {
                        // parsing the robots.txt Data and converting it into an arraylist
                        try {
                            Object[] parserResult = robotsParser.parse(robotsTxt);
                            denyPath = (ArrayList) parserResult[0];
                            sitemap = (String) parserResult[1];
                        } catch (IOException e) {
                            serverLog.logSevere("ROBOTS","Unable to parse the robots.txt file from URL '" + robotsURL + "'.");
                        }
                    } 
                    
                    // storing the data into the robots DB
                    robotsTxt4Host = plasmaSwitchboard.robots.addEntry(urlHostPort,denyPath,new Date(),modDate,eTag,sitemap);
                } 
            }
        }
        
        if (robotsTxt4Host.isDisallowed(nexturl.getFile())) {
            return true;        
        }        
        return false;
    }
    
    static Object[] downloadRobotsTxt(URL robotsURL, int redirectionCount, plasmaCrawlRobotsTxt.Entry entry) throws Exception {
        
        if (redirectionCount < 0) return new Object[]{Boolean.FALSE,null,null};
        redirectionCount--;
        
        boolean accessCompletelyRestricted = false;
        byte[] robotsTxt = null;
        httpc con = null;
        long downloadStart, downloadEnd;
        String eTag=null, oldEtag = null;
        Date lastMod=null;
        try {
            downloadStart = System.currentTimeMillis();
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            //TODO: adding Traffic statistic for robots download?
            if (
                    (sb == null) || 
                    (sb.remoteProxyConfig == null) || 
                    (!sb.remoteProxyConfig.useProxy())
            ) {
                con = httpc.getInstance(robotsURL.getHost(), robotsURL.getHost(), robotsURL.getPort(), 10000, robotsURL.getProtocol().equalsIgnoreCase("https"));
            } else {
                con = httpc.getInstance(robotsURL.getHost(), robotsURL.getHost(), robotsURL.getPort(), 10000, robotsURL.getProtocol().equalsIgnoreCase("https"), sb.remoteProxyConfig);
            }
            
            // if we previously have downloaded this robots.txt then we can set the if-modified-since header
            httpHeader reqHeaders = new httpHeader();
            
            // adding referer
            reqHeaders.put(httpHeader.REFERER, (new URL(robotsURL,"/")).toString());
            
            if (entry != null) {
                oldEtag = entry.getETag();
                reqHeaders = new httpHeader();
                Date modDate = entry.getModDate();
                if (modDate != null) reqHeaders.put(httpHeader.IF_MODIFIED_SINCE,httpc.dateString(entry.getModDate()));
                
            }
            
            // sending the get request
            httpc.response res = con.GET(robotsURL.getFile(), reqHeaders);
            
            // check for interruption
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress.");
            
            // check the response status
            if (res.status.startsWith("2")) {
                if (!res.responseHeader.mime().startsWith("text/plain")) {
                    robotsTxt = null;
                    serverLog.logFinest("ROBOTS","Robots.txt from URL '" + robotsURL + "' has wrong mimetype '" + res.responseHeader.mime() + "'.");                    
                } else {

                    // getting some metadata
                    eTag = res.responseHeader.containsKey(httpHeader.ETAG)?((String)res.responseHeader.get(httpHeader.ETAG)).trim():null;
                    lastMod = res.responseHeader.lastModified();                    
                    
                    // if the robots.txt file was not changed we break here
                    if ((eTag != null) && (oldEtag != null) && (eTag.equals(oldEtag))) {
                        serverLog.logFinest("ROBOTS","Robots.txt from URL '" + robotsURL + "' was not modified. Abort downloading of new version.");
                        return null;
                    }
                    
                    // downloading the content
                    robotsTxt = res.writeContent();
                    con.close();
                    
                    downloadEnd = System.currentTimeMillis();                    
                    serverLog.logFinest("ROBOTS","Robots.txt successfully loaded from URL '" + robotsURL + "' in " + (downloadEnd-downloadStart) + " ms.");
                }
            } else if (res.status.startsWith("304")) {
                return null;
            } else if (res.status.startsWith("3")) {
                // getting redirection URL
                String redirectionUrlString = (String) res.responseHeader.get(httpHeader.LOCATION);
                if (redirectionUrlString==null) {
                    serverLog.logFinest("ROBOTS","robots.txt could not be downloaded from URL '" + robotsURL + "' because of missing redirecton header. [" + res.status + "].");
                    robotsTxt = null;                    
                }
                
                redirectionUrlString = redirectionUrlString.trim();
                
                // generating the new URL object
                URL redirectionUrl = new URL(robotsURL, redirectionUrlString);
                
                // returning the used httpc
                httpc.returnInstance(con); 
                con = null;            
                
                // following the redirection
                serverLog.logFinest("ROBOTS","Redirection detected for robots.txt with URL '" + robotsURL + "'." + 
                        "\nRedirecting request to: " + redirectionUrl);
                return downloadRobotsTxt(redirectionUrl,redirectionCount,entry);
                
            } else if (res.status.startsWith("401") || res.status.startsWith("403")) {
                accessCompletelyRestricted = true;
                serverLog.logFinest("ROBOTS","Access to Robots.txt not allowed on URL '" + robotsURL + "'.");
            } else {
                serverLog.logFinest("ROBOTS","robots.txt could not be downloaded from URL '" + robotsURL + "'. [" + res.status + "].");
                robotsTxt = null;
            }        
        } catch (Exception e) {
            throw e;
        } finally {
            if (con != null) httpc.returnInstance(con);            
        }            
        return new Object[]{new Boolean(accessCompletelyRestricted),robotsTxt,eTag,lastMod};
    }
}
