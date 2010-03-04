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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.blob.BEncodedHeap;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.http.client.Client;
import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseContainer;

public class RobotsTxt {
    
    public static final String ROBOTS_DB_PATH_SEPARATOR = ";";    
    private static final Log log = new Log("ROBOTS");
    
    BEncodedHeap robotsTable;
    private final ConcurrentHashMap<String, DomSync> syncObjects;
    //private static final HashSet<String> loadedRobots = new HashSet<String>(); // only for debugging
    
    private static class DomSync {
    	public DomSync() {}
    }
    
    public RobotsTxt(final BEncodedHeap robotsTable) {
        this.robotsTable = robotsTable;
        syncObjects = new ConcurrentHashMap<String, DomSync>();
    }
    
    public void clear() {
        try {
            this.robotsTable.clear();
        } catch (IOException e) {
        }
        syncObjects.clear();
    }
    
    public int size() {
        return this.robotsTable.size();
    }
    
    private RobotsEntry getEntry(final DigestURI theURL, final boolean fetchOnlineIfNotAvailableOrNotFresh) throws IOException {
        // this method will always return a non-null value
        String urlHostPort = getHostPort(theURL);
        RobotsEntry robotsTxt4Host = null;
        Map<String, byte[]> record = this.robotsTable.get(this.robotsTable.encodedKey(urlHostPort));
        if (record != null) robotsTxt4Host = new RobotsEntry(urlHostPort, record);
        
        if (fetchOnlineIfNotAvailableOrNotFresh && (
             robotsTxt4Host == null || 
             robotsTxt4Host.getLoadedDate() == null ||
             System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000
           )) {
            
            // make or get a synchronization object
        	DomSync syncObj = this.syncObjects.get(urlHostPort);
            if (syncObj == null) {
                syncObj = new DomSync();
                this.syncObjects.put(urlHostPort, syncObj);
            }
            
            // we can now synchronize for each host separately
            synchronized (syncObj) {
        
                // if we have not found any data or the data is older than 7 days, we need to load it from the remote server
                
                // check the robots table again for all threads that come here because they waited for another one
                // to complete a download
                record = this.robotsTable.get(this.robotsTable.encodedKey(urlHostPort));
                if (record != null) robotsTxt4Host = new RobotsEntry(urlHostPort, record);
                if (robotsTxt4Host != null &&
                    robotsTxt4Host.getLoadedDate() != null &&
                    System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() <= 1*24*60*60*1000) {
                    return robotsTxt4Host;
                }
                
                // generating the proper url to download the robots txt
                DigestURI robotsURL = null;
                try {                 
                    robotsURL = new DigestURI("http://" + urlHostPort + "/robots.txt", null);
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
                        robotsTxt4Host = new RobotsEntry(
                                robotsURL, 
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
                    int sz = this.robotsTable.size();
                    addEntry(robotsTxt4Host);
                    if (this.robotsTable.size() <= sz) {
                    	Log.logSevere("RobotsTxt", "new entry in robots.txt table failed, resetting database");
                    	this.clear();
                    	addEntry(robotsTxt4Host);
                    }
                } else {
                    final robotsParser parserResult = new robotsParser((byte[]) result[DOWNLOAD_ROBOTS_TXT]);
                    ArrayList<String> denyPath = parserResult.denyList();
                    if (((Boolean) result[DOWNLOAD_ACCESS_RESTRICTED]).booleanValue()) {
                        denyPath = new ArrayList<String>();
                        denyPath.add("/");
                    }
                    
                    // store the data into the robots DB
                    robotsTxt4Host = addEntry(
                            robotsURL,
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
    
    private RobotsEntry addEntry(
    		final DigestURI theURL, 
    		final ArrayList<String> allowPathList, 
    		final ArrayList<String> denyPathList, 
            final Date loadedDate, 
    		final Date modDate, 
    		final String eTag, 
    		final String sitemap,
    		final long crawlDelayMillis
    ) {
        final RobotsEntry entry = new RobotsEntry(
                                theURL, allowPathList, denyPathList,
                                loadedDate, modDate,
                                eTag, sitemap, crawlDelayMillis);
        addEntry(entry);
        return entry;
    }
    
    private String addEntry(final RobotsEntry entry) {
        // writes a new page and returns key
        try {
            this.robotsTable.put(this.robotsTable.encodedKey(entry.hostName), entry.getMem());
            return entry.hostName;
        } catch (final Exception e) {
            Log.logException(e);
            return null;
        }
    }    
    
    // methods that had been in robotsParser.java:
    
    public static final int DOWNLOAD_ACCESS_RESTRICTED = 0;
    public static final int DOWNLOAD_ROBOTS_TXT = 1;
    public static final int DOWNLOAD_ETAG = 2;
    public static final int DOWNLOAD_MODDATE = 3;
    
    static final String getHostPort(final DigestURI theURL) {
        String urlHostPort = null;
        final int port = getPort(theURL);
        urlHostPort = theURL.getHost() + ":" + port;
        urlHostPort = urlHostPort.toLowerCase().intern();    
        
        return urlHostPort;
    }
    
    private static final int getPort(final DigestURI theURL) {
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
   
    public DigestURI getSitemapURL(final DigestURI theURL) {
        if (theURL == null) throw new IllegalArgumentException(); 
        DigestURI sitemapURL = null;
        
        // generating the hostname:poart string needed to do a DB lookup
        RobotsEntry robotsTxt4Host;
        try {
            robotsTxt4Host = this.getEntry(theURL, true);
        } catch (IOException e1) {
            return null;
        }
                       
        try {
            final String sitemapUrlStr = robotsTxt4Host.getSitemap();
            if (sitemapUrlStr != null) sitemapURL = new DigestURI(sitemapUrlStr, null);
        } catch (final MalformedURLException e) {/* ignore this */}
        
        return sitemapURL;
    }
    
    public Long getCrawlDelayMillis(final DigestURI theURL) {
        if (theURL == null) throw new IllegalArgumentException();
        RobotsEntry robotsEntry;
        try {
            robotsEntry = getEntry(theURL, true);
        } catch (IOException e) {
            Log.logException(e);
            return new Long(0);
        }
        return robotsEntry.getCrawlDelayMillis();
    }
    
    public boolean isDisallowed(final DigestURI nexturl) {
        if (nexturl == null) throw new IllegalArgumentException();               
        
        // generating the hostname:port string needed to do a DB lookup
        RobotsEntry robotsTxt4Host = null;
        try {
            robotsTxt4Host = getEntry(nexturl, true);
        } catch (IOException e) {
            Log.logException(e);
            return false;
        }
        return robotsTxt4Host.isDisallowed(nexturl.getFile());
    }
    
    private static Object[] downloadRobotsTxt(final DigestURI robotsURL, int redirectionCount, final RobotsEntry entry) throws Exception {
        
        if (redirectionCount < 0) return new Object[]{Boolean.FALSE,null,null};
        redirectionCount--;
        
        boolean accessCompletelyRestricted = false;
        byte[] robotsTxt = null;
        long downloadStart, downloadEnd;
        String eTag=null, oldEtag = null;
        Date lastMod=null;
        downloadStart = System.currentTimeMillis();
        
        // if we previously have downloaded this robots.txt then we can set the if-modified-since header
        RequestHeader reqHeaders = new RequestHeader();
        
        // add yacybot user agent
        reqHeaders.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
        
        // adding referer
        reqHeaders.put(RequestHeader.REFERER, (DigestURI.newURL(robotsURL,"/")).toNormalform(true, true));
        
        if (entry != null) {
            oldEtag = entry.getETag();
            reqHeaders = new RequestHeader();
            final Date modDate = entry.getModDate();
            if (modDate != null) reqHeaders.put(RequestHeader.IF_MODIFIED_SINCE, DateFormatter.formatRFC1123(entry.getModDate()));
            
        }
        
        // setup http-client
        //TODO: adding Traffic statistic for robots download?
        final Client client = new Client(10000, reqHeaders);
        ResponseContainer res = null;
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
                    eTag = res.getResponseHeader().containsKey(HeaderFramework.ETAG)?(res.getResponseHeader().get(HeaderFramework.ETAG)).trim():null;
                    lastMod = res.getResponseHeader().lastModified();                    
                    
                    // if the robots.txt file was not changed we break here
                    if ((eTag != null) && (oldEtag != null) && (eTag.equals(oldEtag))) {
                        if (log.isFinest()) log.logFinest("Robots.txt from URL '" + robotsURL + "' was not modified. Abort downloading of new version.");
                        return null;
                    }
                    
                    // downloading the content
                    final ByteBuffer sbb = new ByteBuffer();
                    try {
                        FileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(sbb));
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
                String redirectionUrlString = res.getResponseHeader().get(HeaderFramework.LOCATION);
                if (redirectionUrlString==null) {
                    if (log.isFinest()) log.logFinest("robots.txt could not be downloaded from URL '" + robotsURL + "' because of missing redirecton header. [" + res.getStatusLine() + "].");
                    robotsTxt = null;                    
                } else {
                
                    redirectionUrlString = redirectionUrlString.trim();
                    
                    // generating the new URL object
                    final DigestURI redirectionUrl = DigestURI.newURL(robotsURL, redirectionUrlString);      
                    
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
