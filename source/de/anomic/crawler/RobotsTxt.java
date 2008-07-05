//plasmaCrawlRobotsTxt.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
// [MC] moved some methods from robotsParser file that had been created by Alexander Schier to this class
//last major change: $LastChangedDate$ by $LastChangedBy$
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.http.HttpClient;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class RobotsTxt {
    
    public static final String ROBOTS_DB_PATH_SEPARATOR = ";";    
    
    kelondroMapObjects robotsTable;
    private final File robotsTableFile;
    
    public RobotsTxt(File robotsTableFile) {
        this.robotsTableFile = robotsTableFile;
        robotsTableFile.getParentFile().mkdirs();
        robotsTable = new kelondroMapObjects(new kelondroBLOBTree(robotsTableFile, true, true, 256, 512, '_', kelondroNaturalOrder.naturalOrder, false, false, true), 100);
    }
    
    private void resetDatabase() {
        // deletes the robots.txt database and creates a new one
        if (robotsTable != null) robotsTable.close();
        if (!(robotsTableFile.delete())) throw new RuntimeException("cannot delete robots.txt database");
        robotsTableFile.getParentFile().mkdirs();
        robotsTable = new kelondroMapObjects(new kelondroBLOBTree(robotsTableFile, true, true, 256, 512, '_', kelondroNaturalOrder.naturalOrder, false, false, true), 100);
    }
    
    public void clear() throws IOException {
        this.robotsTable.clear();
    }
    
    public void close() {
        this.robotsTable.close();
    }
    
    public int size() {
        return this.robotsTable.size();
    }
    
    private Entry getEntry(String hostName) {
        try {
            HashMap<String, String> record = this.robotsTable.getMap(hostName);
            if (record == null) return null;
            return new Entry(hostName, record);
        } catch (kelondroException e) {
        	resetDatabase();
        	return null;
        }
    }
    
    public int crawlDelay(String hostname) {
        RobotsTxt.Entry robotsEntry = getEntry(hostname);
        Integer hostDelay = (robotsEntry == null) ? null : robotsEntry.getCrawlDelay();
        if (hostDelay == null) return 0; else return hostDelay.intValue();
    }
    
    private Entry addEntry(
    		String hostName, 
    		ArrayList<String> disallowPathList, 
    		Date loadedDate, 
    		Date modDate, 
    		String eTag, 
    		String sitemap,
    		Integer crawlDelay
    ) {
        Entry entry = new Entry(
                hostName, disallowPathList, loadedDate, modDate,
                eTag, sitemap, crawlDelay);
        addEntry(entry);
        return entry;
    }
    
    private String addEntry(Entry entry) {
        // writes a new page and returns key
        try {
            this.robotsTable.set(entry.hostName, entry.mem);
            return entry.hostName;
        } catch (IOException e) {
            return null;
        }
    }    
    
    public class Entry {
        public static final String DISALLOW_PATH_LIST = "disallow";
        public static final String LOADED_DATE = "date";
        public static final String MOD_DATE = "modDate";
        public static final String ETAG = "etag";
        public static final String SITEMAP = "sitemap";
        public static final String CRAWL_DELAY = "crawlDelay";
        
        // this is a simple record structure that hold all properties of a single crawl start
        HashMap<String, String> mem;
        private LinkedList<String> disallowPathList;
        String hostName;
        
        public Entry(String hostName, HashMap<String, String> mem) {
            this.hostName = hostName.toLowerCase();
            this.mem = mem; 
            
            if (this.mem.containsKey(DISALLOW_PATH_LIST)) {
                this.disallowPathList = new LinkedList<String>();
                String csPl = this.mem.get(DISALLOW_PATH_LIST);
                if (csPl.length() > 0){
                    String[] pathArray = csPl.split(ROBOTS_DB_PATH_SEPARATOR);
                    if ((pathArray != null)&&(pathArray.length > 0)) {
                        this.disallowPathList.addAll(Arrays.asList(pathArray));
                    }
                }
            } else {
                this.disallowPathList = new LinkedList<String>();
            }
        }  
        
        public Entry(
                String hostName, 
                ArrayList<String> disallowPathList, 
                Date loadedDate,
                Date modDate,
                String eTag,
                String sitemap,
                Integer crawlDelay
        ) {
            if ((hostName == null) || (hostName.length() == 0)) throw new IllegalArgumentException("The hostname is missing");
            
            this.hostName = hostName.trim().toLowerCase();
            this.disallowPathList = new LinkedList<String>();
            
            this.mem = new HashMap<String, String>(5);
            if (loadedDate != null) this.mem.put(LOADED_DATE,Long.toString(loadedDate.getTime()));
            if (modDate != null) this.mem.put(MOD_DATE,Long.toString(modDate.getTime()));
            if (eTag != null) this.mem.put(ETAG,eTag);
            if (sitemap != null) this.mem.put(SITEMAP,sitemap);
            if (crawlDelay != null) this.mem.put(CRAWL_DELAY,crawlDelay.toString());
            
            if ((disallowPathList != null)&&(disallowPathList.size()>0)) {
                this.disallowPathList.addAll(disallowPathList);
                
                StringBuffer pathListStr = new StringBuffer();
                for (int i=0; i<disallowPathList.size();i++) {
                    pathListStr.append(disallowPathList.get(i))
                               .append(ROBOTS_DB_PATH_SEPARATOR);
                }
                this.mem.put(DISALLOW_PATH_LIST,pathListStr.substring(0,pathListStr.length()-1));
            }
        }
        
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append((this.hostName==null)?"null":this.hostName)
               .append(": ");
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            } 
            
            return str.toString();
        }    
        
        public String getSitemap() {
            return this.mem.containsKey(SITEMAP)? (String)this.mem.get(SITEMAP): null;
        }
        
        public Date getLoadedDate() {
            if (this.mem.containsKey(LOADED_DATE)) {
                return new Date(Long.valueOf(this.mem.get(LOADED_DATE)).longValue());
            }
            return null;
        }
        
        public void setLoadedDate(Date newLoadedDate) {
            if (newLoadedDate != null) {
                this.mem.put(LOADED_DATE,Long.toString(newLoadedDate.getTime()));
            }
        }
        
        public Date getModDate() {
            if (this.mem.containsKey(MOD_DATE)) {
                return new Date(Long.valueOf(this.mem.get(MOD_DATE)).longValue());
            }
            return null;
        }        
        
        public String getETag() {
            if (this.mem.containsKey(ETAG)) {
                return this.mem.get(ETAG);
            }
            return null;
        }          
        
        public Integer getCrawlDelay() {
            if (this.mem.containsKey(CRAWL_DELAY)) {
                return Integer.valueOf(this.mem.get(CRAWL_DELAY));
            }
            return null;        	
        }
        
        public boolean isDisallowed(String path) {
            if ((this.mem == null) || (this.disallowPathList.size() == 0)) return false;   
            
            // if the path is null or empty we set it to /
            if ((path == null) || (path.length() == 0)) path = "/";            
            // escaping all occurences of ; because this char is used as special char in the Robots DB
            else  path = path.replaceAll(ROBOTS_DB_PATH_SEPARATOR,"%3B");
            
            
            Iterator<String> pathIter = this.disallowPathList.iterator();
            while (pathIter.hasNext()) {
                String nextPath = pathIter.next();
                // allow rule
                if (nextPath.startsWith("!") && nextPath.length() > 1 && path.startsWith(nextPath.substring(1))) {
                    return false;
                }
                    
                // disallow rule
                if (path.startsWith(nextPath)) {
                    return true;
                }
            }
            return false;
        }
    
    }
    
    // methods that had been in robotsParser.java:
    
    public static final int DOWNLOAD_ACCESS_RESTRICTED = 0;
    public static final int DOWNLOAD_ROBOTS_TXT = 1;
    public static final int DOWNLOAD_ETAG = 2;
    public static final int DOWNLOAD_MODDATE = 3;
    
    private static final String getHostPort(yacyURL theURL) {
        String urlHostPort = null;
        int port = getPort(theURL);
        urlHostPort = theURL.getHost() + ":" + port;
        urlHostPort = urlHostPort.toLowerCase().intern();    
        
        return urlHostPort;
    }
    
    private static final int getPort(yacyURL theURL) {
        int port = theURL.getPort();
        if (port == -1) {
            if (theURL.getProtocol().equalsIgnoreCase("http")) {
                port = 80;
            } else if (theURL.getProtocol().equalsIgnoreCase("https")) {
                port = 443;
            }
            
        }
        return port;
    }
   
    public yacyURL getSitemapURL(yacyURL theURL) {
        if (theURL == null) throw new IllegalArgumentException(); 
        yacyURL sitemapURL = null;
        
        // generating the hostname:poart string needed to do a DB lookup
        String urlHostPort = getHostPort(theURL);       
        
        RobotsTxt.Entry robotsTxt4Host = this.getEntry(urlHostPort);           
        if (robotsTxt4Host == null) return null;
                       
        try {
            String sitemapUrlStr = robotsTxt4Host.getSitemap();
            if (sitemapUrlStr != null) sitemapURL = new yacyURL(sitemapUrlStr, null);
        } catch (MalformedURLException e) {/* ignore this */}
        
        return sitemapURL;
    }
    
    public Integer getCrawlDelay(yacyURL theURL) {
        if (theURL == null) throw new IllegalArgumentException(); 
        Integer crawlDelay = null;
        
        // generating the hostname:poart string needed to do a DB lookup
        String urlHostPort = getHostPort(theURL);       
        
        RobotsTxt.Entry robotsTxt4Host = getEntry(urlHostPort);
        if (robotsTxt4Host == null) return null;
                       
        try {
            crawlDelay = robotsTxt4Host.getCrawlDelay();
        } catch (NumberFormatException e) {/* ignore this */}
        
        return crawlDelay;      
    }
    
    @SuppressWarnings("unchecked")
    public boolean isDisallowed(yacyURL nexturl) {
        if (nexturl == null) throw new IllegalArgumentException();               
        
        // generating the hostname:poart string needed to do a DB lookup
        String urlHostPort = getHostPort(nexturl);
        
        // do a DB lookup to determine if the robots data is already available
        RobotsTxt.Entry robotsTxt4Host = getEntry(urlHostPort);

        // if we have not found any data or the data is older than 7 days, we need to load it from the remote server
        if (
            (robotsTxt4Host == null) || 
            (robotsTxt4Host.getLoadedDate() == null) ||
            (System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000)
           ) {
            synchronized(this) {
                
                // generating the proper url to download the robots txt
                yacyURL robotsURL = null;
                try {                 
                    robotsURL = new yacyURL(nexturl.getProtocol(),nexturl.getHost(),getPort(nexturl),"/robots.txt");
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
                        addEntry(robotsTxt4Host);
                    }
                } catch (Exception e) {
                    serverLog.logSevere("ROBOTS","Unable to download the robots.txt file from URL '" + robotsURL + "'. " + e.getMessage());
                }
                
                if ((robotsTxt4Host==null)||((robotsTxt4Host!=null)&&(result!=null))) {
                    ArrayList<String> denyPath = null;
                    String sitemap = null;
                    Integer crawlDelay = null;
                    if (accessCompletelyRestricted) {
                        denyPath = new ArrayList<String>();
                        denyPath.add("/");
                    } else {
                        // parsing the robots.txt Data and converting it into an arraylist
                        try {
                            Object[] parserResult = robotsParser.parse(robotsTxt);
                            denyPath = (ArrayList<String>) parserResult[0];
                            sitemap = (String) parserResult[1];
                            crawlDelay = (Integer) parserResult[2];
                        } catch (IOException e) {
                            serverLog.logSevere("ROBOTS","Unable to parse the robots.txt file from URL '" + robotsURL + "'.");
                        }
                    } 
                    
                    // storing the data into the robots DB
                    robotsTxt4Host = addEntry(urlHostPort,denyPath,new Date(),modDate,eTag,sitemap,crawlDelay);
                }
            }
        }
        
        if (robotsTxt4Host != null && robotsTxt4Host.isDisallowed(nexturl.getFile())) {
            return true;        
        }        
        return false;
    }
    
    private static Object[] downloadRobotsTxt(yacyURL robotsURL, int redirectionCount, RobotsTxt.Entry entry) throws Exception {
        
        if (redirectionCount < 0) return new Object[]{Boolean.FALSE,null,null};
        redirectionCount--;
        
        boolean accessCompletelyRestricted = false;
        byte[] robotsTxt = null;
        long downloadStart, downloadEnd;
        String eTag=null, oldEtag = null;
        Date lastMod=null;
        downloadStart = System.currentTimeMillis();
        
        // if we previously have downloaded this robots.txt then we can set the if-modified-since header
        httpHeader reqHeaders = new httpHeader();
        
        // add yacybot user agent
        reqHeaders.put(httpHeader.USER_AGENT, HTTPLoader.crawlerUserAgent);
        
        // adding referer
        reqHeaders.put(httpHeader.REFERER, (yacyURL.newURL(robotsURL,"/")).toNormalform(true, true));
        
        if (entry != null) {
            oldEtag = entry.getETag();
            reqHeaders = new httpHeader();
            Date modDate = entry.getModDate();
            if (modDate != null) reqHeaders.put(httpHeader.IF_MODIFIED_SINCE,HttpClient.dateString(entry.getModDate()));
            
        }
        
        // setup http-client
        //TODO: adding Traffic statistic for robots download?
        JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(10000, reqHeaders, null);
        JakartaCommonsHttpResponse res = null;
        try {
            // sending the get request
            res = client.GET(robotsURL.toString());
            
            // check for interruption
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress.");
            
            // check the response status
            if (res.getStatusLine().startsWith("2")) {
                if (!res.getResponseHeader().mime().startsWith("text/plain")) {
                    robotsTxt = null;
                    serverLog.logFinest("ROBOTS","Robots.txt from URL '" + robotsURL + "' has wrong mimetype '" + res.getResponseHeader().mime() + "'.");                    
                } else {

                    // getting some metadata
                    eTag = res.getResponseHeader().containsKey(httpHeader.ETAG)?(res.getResponseHeader().get(httpHeader.ETAG)).trim():null;
                    lastMod = res.getResponseHeader().lastModified();                    
                    
                    // if the robots.txt file was not changed we break here
                    if ((eTag != null) && (oldEtag != null) && (eTag.equals(oldEtag))) {
                        serverLog.logFinest("ROBOTS","Robots.txt from URL '" + robotsURL + "' was not modified. Abort downloading of new version.");
                        return null;
                    }
                    
                    // downloading the content
                    serverByteBuffer sbb = new serverByteBuffer();
                    try {
                        serverFileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(sbb));
                    } finally {
                        res.closeStream();
                    }
                    robotsTxt = sbb.getBytes();
                    
                    downloadEnd = System.currentTimeMillis();                    
                    serverLog.logFinest("ROBOTS","Robots.txt successfully loaded from URL '" + robotsURL + "' in " + (downloadEnd-downloadStart) + " ms.");
                }
            } else if (res.getStatusCode() == 304) {
                return null;
            } else if (res.getStatusLine().startsWith("3")) {
                // getting redirection URL
                String redirectionUrlString = res.getResponseHeader().get(httpHeader.LOCATION);
                if (redirectionUrlString==null) {
                    serverLog.logFinest("ROBOTS","robots.txt could not be downloaded from URL '" + robotsURL + "' because of missing redirecton header. [" + res.getStatusLine() + "].");
                    robotsTxt = null;                    
                } else {
                
                    redirectionUrlString = redirectionUrlString.trim();
                    
                    // generating the new URL object
                    yacyURL redirectionUrl = yacyURL.newURL(robotsURL, redirectionUrlString);      
                    
                    // following the redirection
                    serverLog.logFinest("ROBOTS","Redirection detected for robots.txt with URL '" + robotsURL + "'." + 
                            "\nRedirecting request to: " + redirectionUrl);
                    return downloadRobotsTxt(redirectionUrl,redirectionCount,entry);
                }
            } else if (res.getStatusCode() == 401 || res.getStatusCode() == 403) {
                accessCompletelyRestricted = true;
                serverLog.logFinest("ROBOTS","Access to Robots.txt not allowed on URL '" + robotsURL + "'.");
            } else {
                serverLog.logFinest("ROBOTS","robots.txt could not be downloaded from URL '" + robotsURL + "'. [" + res.getStatusLine() + "].");
                robotsTxt = null;
            }        
        } catch (Exception e) {
            throw e;
        } finally {
            if(res != null) {
                // release connection
                res.closeStream();
            }
        }
        return new Object[]{new Boolean(accessCompletelyRestricted),robotsTxt,eTag,lastMod};
    }
}
