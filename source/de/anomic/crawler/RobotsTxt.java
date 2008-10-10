//plasmaCrawlRobotsTxt.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.http.HttpClient;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.kelondroBLOB;
import de.anomic.kelondro.kelondroBLOBHeap;
import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class RobotsTxt {
    
    public static final String ROBOTS_DB_PATH_SEPARATOR = ";";    
    private static final serverLog log = new serverLog("ROBOTS");
    
    kelondroMap robotsTable;
    private final File robotsTableFile;
    private final ConcurrentHashMap<String, Long> syncObjects;
    //private static final HashSet<String> loadedRobots = new HashSet<String>(); // only for debugging
    
    public RobotsTxt(final File robotsTableFile) {
        this.robotsTableFile = robotsTableFile;
        robotsTableFile.getParentFile().mkdirs();
        kelondroBLOB blob = null;
        if (robotsTableFile.getName().endsWith(".heap")) {
            try {
                blob = new kelondroBLOBHeap(robotsTableFile, 64, kelondroNaturalOrder.naturalOrder);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        } else {
            blob = new kelondroBLOBTree(robotsTableFile, true, true, 256, 512, '_', kelondroNaturalOrder.naturalOrder, false, false, true);
        }
        robotsTable = new kelondroMap(blob, 100);
        syncObjects = new ConcurrentHashMap<String, Long>();
    }
    
    private void resetDatabase() {
        // deletes the robots.txt database and creates a new one
        if (robotsTable != null) robotsTable.close();
        if (!(robotsTableFile.delete())) throw new RuntimeException("cannot delete robots.txt database");
        robotsTableFile.getParentFile().mkdirs();
        robotsTable = new kelondroMap(new kelondroBLOBTree(robotsTableFile, true, true, 256, 512, '_', kelondroNaturalOrder.naturalOrder, false, false, true), 100);
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
    
    private Entry getEntry(final String urlHostPort, final boolean fetchOnlineIfNotAvailableOrNotFresh) {
        // this method will always return a non-null value
        Entry robotsTxt4Host = null;
        try {
            final Map<String, String> record = this.robotsTable.get(urlHostPort);
            if (record != null) robotsTxt4Host = new Entry(urlHostPort, record);
        } catch (final kelondroException e) {
        	resetDatabase();
        } catch (final IOException e) {
            resetDatabase();
        }
        
        if (fetchOnlineIfNotAvailableOrNotFresh && (
             robotsTxt4Host == null || 
             robotsTxt4Host.getLoadedDate() == null ||
             System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000
           )) {
            
            // make or get a synchronization object
            Long syncObj = this.syncObjects.get(urlHostPort);
            if (syncObj == null) {
                syncObj = new Long(System.currentTimeMillis());
                this.syncObjects.put(urlHostPort, syncObj);
            }
            
            // we can now synchronize for each host separately
            synchronized (syncObj) {
        
                // if we have not found any data or the data is older than 7 days, we need to load it from the remote server
                
                // check the robots table again for all threads that come here because they waited for another one
                // to complete a download
                try {
                    final Map<String, String> record = this.robotsTable.get(urlHostPort);
                    if (record != null) robotsTxt4Host = new Entry(urlHostPort, record);
                } catch (final kelondroException e) {
                    resetDatabase();
                } catch (final IOException e) {
                    resetDatabase();
                }
                if (robotsTxt4Host != null &&
                    robotsTxt4Host.getLoadedDate() != null &&
                    System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() <= 7*24*60*60*1000) {
                    return robotsTxt4Host;
                }
                
                // generating the proper url to download the robots txt
                yacyURL robotsURL = null;
                try {                 
                    robotsURL = new yacyURL("http://" + urlHostPort + "/robots.txt", null);
                } catch (final MalformedURLException e) {
                    log.logSevere("Unable to generate robots.txt URL for host:port '" + urlHostPort + "'.");
                    robotsURL = null;
                }
                
                Object[] result = null;
                if (robotsURL != null) {
                    if (log.isFine()) log.logFine("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    try {
                        result = downloadRobotsTxt(robotsURL, 5, robotsTxt4Host);
                    } catch (final Exception e) {
                        result = null;
                    }
                }
                /*
                assert !loadedRobots.contains(robotsURL.toNormalform(false, false)) :
                    "robots-url=" + robotsURL.toString() +
                    ", robots=" + ((result == null || result[DOWNLOAD_ROBOTS_TXT] == null) ? "NULL" : new String((byte[]) result[DOWNLOAD_ROBOTS_TXT])) +
                    ", robotsTxt4Host=" + ((robotsTxt4Host == null) ? "NULL" : robotsTxt4Host.getLoadedDate().toString());
                loadedRobots.add(robotsURL.toNormalform(false, false));
                */
                
                if (result == null) {
                    // no robots.txt available, make an entry to prevent that the robots loading is done twice
                    if (robotsTxt4Host == null) {
                        // generate artificial entry
                        robotsTxt4Host = new Entry(
                                urlHostPort, 
                                new ArrayList<String>(), 
                                new ArrayList<String>(), 
                                new Date(),
                                new Date(),
                                null,
                                null,
                                Integer.valueOf(0));
                    } else {
                        robotsTxt4Host.setLoadedDate(new Date());
                    }
                    
                    // store the data into the robots DB
                    addEntry(robotsTxt4Host);
                } else {
                    final robotsParser parserResult = new robotsParser((byte[]) result[DOWNLOAD_ROBOTS_TXT]);
                    ArrayList<String> denyPath = parserResult.denyList();
                    if (((Boolean) result[DOWNLOAD_ACCESS_RESTRICTED]).booleanValue()) {
                        denyPath = new ArrayList<String>();
                        denyPath.add("/");
                    }
                    
                    // store the data into the robots DB
                    robotsTxt4Host = addEntry(
                            urlHostPort,
                            parserResult.allowList(),
                            denyPath,
                            new Date(),
                            (Date) result[DOWNLOAD_MODDATE],
                            (String) result[DOWNLOAD_ETAG],
                            parserResult.sitemap(),
                            parserResult.crawlDelayMillis());
                }
            }
        }

        return robotsTxt4Host;
    }
    
    public long crawlDelayMillis(final yacyURL theURL) {
        final String urlHostPort = getHostPort(theURL);
        final RobotsTxt.Entry robotsEntry = getEntry(urlHostPort, true);
        return robotsEntry.getCrawlDelayMillis();
    }
    
    private Entry addEntry(
    		final String hostName, 
    		final ArrayList<String> allowPathList, 
    		final ArrayList<String> denyPathList, 
            final Date loadedDate, 
    		final Date modDate, 
    		final String eTag, 
    		final String sitemap,
    		final long crawlDelayMillis
    ) {
        final Entry entry = new Entry(
                hostName, allowPathList, denyPathList, loadedDate, modDate,
                eTag, sitemap, crawlDelayMillis);
        addEntry(entry);
        return entry;
    }
    
    private String addEntry(final Entry entry) {
        // writes a new page and returns key
        try {
            this.robotsTable.put(entry.hostName, entry.mem);
            return entry.hostName;
        } catch (final IOException e) {
            return null;
        }
    }    
    
    public static class Entry {
        public static final String ALLOW_PATH_LIST    = "allow";
        public static final String DISALLOW_PATH_LIST = "disallow";
        public static final String LOADED_DATE        = "date";
        public static final String MOD_DATE           = "modDate";
        public static final String ETAG               = "etag";
        public static final String SITEMAP            = "sitemap";
        public static final String CRAWL_DELAY        = "crawlDelay";
        public static final String CRAWL_DELAY_MILLIS = "crawlDelayMillis";
        
        // this is a simple record structure that holds all properties of a single crawl start
        Map<String, String> mem;
        private LinkedList<String> allowPathList, denyPathList;
        String hostName;
        
        public Entry(final String hostName, final Map<String, String> mem) {
            this.hostName = hostName.toLowerCase();
            this.mem = mem; 
            
            if (this.mem.containsKey(DISALLOW_PATH_LIST)) {
                this.denyPathList = new LinkedList<String>();
                final String csPl = this.mem.get(DISALLOW_PATH_LIST);
                if (csPl.length() > 0){
                    final String[] pathArray = csPl.split(ROBOTS_DB_PATH_SEPARATOR);
                    if ((pathArray != null)&&(pathArray.length > 0)) {
                        this.denyPathList.addAll(Arrays.asList(pathArray));
                    }
                }
            } else {
                this.denyPathList = new LinkedList<String>();
            }
            if (this.mem.containsKey(ALLOW_PATH_LIST)) {
                this.allowPathList = new LinkedList<String>();
                final String csPl = this.mem.get(ALLOW_PATH_LIST);
                if (csPl.length() > 0){
                    final String[] pathArray = csPl.split(ROBOTS_DB_PATH_SEPARATOR);
                    if ((pathArray != null)&&(pathArray.length > 0)) {
                        this.allowPathList.addAll(Arrays.asList(pathArray));
                    }
                }
            } else {
                this.allowPathList = new LinkedList<String>();
            }
        }  
        
        public Entry(
                final String hostName, 
                final ArrayList<String> allowPathList, 
                final ArrayList<String> disallowPathList, 
                final Date loadedDate,
                final Date modDate,
                final String eTag,
                final String sitemap,
                final long crawlDelayMillis
        ) {
            if ((hostName == null) || (hostName.length() == 0)) throw new IllegalArgumentException("The hostname is missing");
            
            this.hostName = hostName.trim().toLowerCase();
            this.allowPathList = new LinkedList<String>();
            this.denyPathList = new LinkedList<String>();
            
            this.mem = new HashMap<String, String>(5);
            if (loadedDate != null) this.mem.put(LOADED_DATE,Long.toString(loadedDate.getTime()));
            if (modDate != null) this.mem.put(MOD_DATE,Long.toString(modDate.getTime()));
            if (eTag != null) this.mem.put(ETAG,eTag);
            if (sitemap != null) this.mem.put(SITEMAP,sitemap);
            if (crawlDelayMillis > 0) this.mem.put(CRAWL_DELAY_MILLIS, Long.toString(crawlDelayMillis));
            
            if ((allowPathList != null)&&(allowPathList.size()>0)) {
                this.allowPathList.addAll(allowPathList);
                
                final StringBuffer pathListStr = new StringBuffer();
                for (int i=0; i<allowPathList.size();i++) {
                    pathListStr.append(allowPathList.get(i))
                               .append(ROBOTS_DB_PATH_SEPARATOR);
                }
                this.mem.put(ALLOW_PATH_LIST,pathListStr.substring(0,pathListStr.length()-1));
            }
            
            if ((disallowPathList != null)&&(disallowPathList.size()>0)) {
                this.denyPathList.addAll(disallowPathList);
                
                final StringBuffer pathListStr = new StringBuffer();
                for (int i=0; i<disallowPathList.size();i++) {
                    pathListStr.append(disallowPathList.get(i))
                               .append(ROBOTS_DB_PATH_SEPARATOR);
                }
                this.mem.put(DISALLOW_PATH_LIST,pathListStr.substring(0,pathListStr.length()-1));
            }
        }
        
        public String toString() {
            final StringBuffer str = new StringBuffer();
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
        
        public void setLoadedDate(final Date newLoadedDate) {
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
        
        public long getCrawlDelayMillis() {
            if (this.mem.containsKey(CRAWL_DELAY_MILLIS)) try {
                return Long.parseLong(this.mem.get(CRAWL_DELAY_MILLIS));
            } catch (final NumberFormatException e) {
                return 0;
            }
            if (this.mem.containsKey(CRAWL_DELAY)) try {
                return 1000 * Integer.parseInt(this.mem.get(CRAWL_DELAY));
            } catch (final NumberFormatException e) {
                return 0;
            }
            return 0;        	
        }
        
        public boolean isDisallowed(String path) {
            if ((this.mem == null) || (this.denyPathList.size() == 0)) return false;   
            
            // if the path is null or empty we set it to /
            if ((path == null) || (path.length() == 0)) path = "/";            
            // escaping all occurences of ; because this char is used as special char in the Robots DB
            else  path = path.replaceAll(ROBOTS_DB_PATH_SEPARATOR,"%3B");
            
            final Iterator<String> pathIter = this.denyPathList.iterator();
            while (pathIter.hasNext()) {
                final String nextPath = pathIter.next();
                    
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
    
    private static final String getHostPort(final yacyURL theURL) {
        String urlHostPort = null;
        final int port = getPort(theURL);
        urlHostPort = theURL.getHost() + ":" + port;
        urlHostPort = urlHostPort.toLowerCase().intern();    
        
        return urlHostPort;
    }
    
    private static final int getPort(final yacyURL theURL) {
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
   
    public yacyURL getSitemapURL(final yacyURL theURL) {
        if (theURL == null) throw new IllegalArgumentException(); 
        yacyURL sitemapURL = null;
        
        // generating the hostname:poart string needed to do a DB lookup
        final String urlHostPort = getHostPort(theURL);
        final RobotsTxt.Entry robotsTxt4Host = this.getEntry(urlHostPort, true);
                       
        try {
            final String sitemapUrlStr = robotsTxt4Host.getSitemap();
            if (sitemapUrlStr != null) sitemapURL = new yacyURL(sitemapUrlStr, null);
        } catch (final MalformedURLException e) {/* ignore this */}
        
        return sitemapURL;
    }
    
    public Long getCrawlDelayMillis(final yacyURL theURL) {
        if (theURL == null) throw new IllegalArgumentException(); 
        Long crawlDelay = null;
        
        // generating the hostname:poart string needed to do a DB lookup
        final String urlHostPort = getHostPort(theURL);
        final RobotsTxt.Entry robotsTxt4Host = getEntry(urlHostPort, true);
                       
        try {
            crawlDelay = robotsTxt4Host.getCrawlDelayMillis();
        } catch (final NumberFormatException e) {/* ignore this */}
        
        return crawlDelay;
    }
    
    public boolean isDisallowed(final yacyURL nexturl) {
        if (nexturl == null) throw new IllegalArgumentException();               
        
        // generating the hostname:port string needed to do a DB lookup
        final String urlHostPort = getHostPort(nexturl);
        RobotsTxt.Entry robotsTxt4Host = null;
        robotsTxt4Host = getEntry(urlHostPort, true);
        return robotsTxt4Host.isDisallowed(nexturl.getFile());
    }
    
    private static Object[] downloadRobotsTxt(final yacyURL robotsURL, int redirectionCount, final RobotsTxt.Entry entry) throws Exception {
        
        if (redirectionCount < 0) return new Object[]{Boolean.FALSE,null,null};
        redirectionCount--;
        
        boolean accessCompletelyRestricted = false;
        byte[] robotsTxt = null;
        long downloadStart, downloadEnd;
        String eTag=null, oldEtag = null;
        Date lastMod=null;
        downloadStart = System.currentTimeMillis();
        
        // if we previously have downloaded this robots.txt then we can set the if-modified-since header
        httpRequestHeader reqHeaders = new httpRequestHeader();
        
        // add yacybot user agent
        reqHeaders.put(httpRequestHeader.USER_AGENT, HTTPLoader.crawlerUserAgent);
        
        // adding referer
        reqHeaders.put(httpRequestHeader.REFERER, (yacyURL.newURL(robotsURL,"/")).toNormalform(true, true));
        
        if (entry != null) {
            oldEtag = entry.getETag();
            reqHeaders = new httpRequestHeader();
            final Date modDate = entry.getModDate();
            if (modDate != null) reqHeaders.put(httpRequestHeader.IF_MODIFIED_SINCE,HttpClient.dateString(entry.getModDate()));
            
        }
        
        // setup http-client
        //TODO: adding Traffic statistic for robots download?
        final JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(10000, reqHeaders);
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
                    if (log.isFinest()) log.logFinest("Robots.txt from URL '" + robotsURL + "' has wrong mimetype '" + res.getResponseHeader().mime() + "'.");                    
                } else {

                    // getting some metadata
                    eTag = res.getResponseHeader().containsKey(httpRequestHeader.ETAG)?(res.getResponseHeader().get(httpRequestHeader.ETAG)).trim():null;
                    lastMod = res.getResponseHeader().lastModified();                    
                    
                    // if the robots.txt file was not changed we break here
                    if ((eTag != null) && (oldEtag != null) && (eTag.equals(oldEtag))) {
                        if (log.isFinest()) log.logFinest("Robots.txt from URL '" + robotsURL + "' was not modified. Abort downloading of new version.");
                        return null;
                    }
                    
                    // downloading the content
                    final serverByteBuffer sbb = new serverByteBuffer();
                    try {
                        serverFileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(sbb));
                    } finally {
                        res.closeStream();
                    }
                    robotsTxt = sbb.getBytes();
                    
                    downloadEnd = System.currentTimeMillis();                    
                    if (log.isFinest()) log.logFinest("Robots.txt successfully loaded from URL '" + robotsURL + "' in " + (downloadEnd-downloadStart) + " ms.");
                }
            } else if (res.getStatusCode() == 304) {
                return null;
            } else if (res.getStatusLine().startsWith("3")) {
                // getting redirection URL
                String redirectionUrlString = res.getResponseHeader().get(httpRequestHeader.LOCATION);
                if (redirectionUrlString==null) {
                    if (log.isFinest()) log.logFinest("robots.txt could not be downloaded from URL '" + robotsURL + "' because of missing redirecton header. [" + res.getStatusLine() + "].");
                    robotsTxt = null;                    
                } else {
                
                    redirectionUrlString = redirectionUrlString.trim();
                    
                    // generating the new URL object
                    final yacyURL redirectionUrl = yacyURL.newURL(robotsURL, redirectionUrlString);      
                    
                    // following the redirection
                    if (log.isFinest()) log.logFinest("Redirection detected for robots.txt with URL '" + robotsURL + "'." + 
                            "\nRedirecting request to: " + redirectionUrl);
                    return downloadRobotsTxt(redirectionUrl,redirectionCount,entry);
                }
            } else if (res.getStatusCode() == 401 || res.getStatusCode() == 403) {
                accessCompletelyRestricted = true;
                if (log.isFinest()) log.logFinest("Access to Robots.txt not allowed on URL '" + robotsURL + "'.");
            } else {
                if (log.isFinest()) log.logFinest("robots.txt could not be downloaded from URL '" + robotsURL + "'. [" + res.getStatusLine() + "].");
                robotsTxt = null;
            }        
        } catch (final Exception e) {
            throw e;
        } finally {
            if(res != null) {
                // release connection
                res.closeStream();
            }
        }
        return new Object[]{Boolean.valueOf(accessCompletelyRestricted),robotsTxt,eTag,lastMod};
    }
}
