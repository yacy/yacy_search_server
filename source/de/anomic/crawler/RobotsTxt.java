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
//it under the terms of the GNU General public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General public License for more details.
//
//You should have received a copy of the GNU General public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.blob.BEncodedHeap;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.io.ByteCount;

public class RobotsTxt {
    
    private static Logger log = Logger.getLogger(RobotsTxt.class);

    protected static final String ROBOTS_DB_PATH_SEPARATOR = ";";
    protected static final Pattern ROBOTS_DB_PATH_SEPARATOR_MATCHER = Pattern.compile(ROBOTS_DB_PATH_SEPARATOR);
    
    BEncodedHeap robotsTable;
    private final ConcurrentHashMap<String, DomSync> syncObjects;
    //private static final HashSet<String> loadedRobots = new HashSet<String>(); // only for debugging
    
    private static class DomSync {
    	private DomSync() {}
    }
    
    public RobotsTxt(final BEncodedHeap robotsTable) {
        this.robotsTable = robotsTable;
        syncObjects = new ConcurrentHashMap<String, DomSync>();
        log.info("initiated robots table: " + robotsTable.getFile());
    }
    
    public void clear() {
        log.info("clearing robots table");
        this.robotsTable.clear();
        syncObjects.clear();
    }
    
    public int size() {
        return this.robotsTable.size();
    }
    
    public RobotsTxtEntry getEntry(final MultiProtocolURI theURL, final Set<String> thisAgents) throws IOException {
        if (theURL == null) throw new IllegalArgumentException();
        if (!theURL.getProtocol().startsWith("http")) return null;
        return getEntry(theURL, thisAgents, true);
    }
    
    private RobotsTxtEntry getEntry(final MultiProtocolURI theURL, final Set<String> thisAgents, final boolean fetchOnlineIfNotAvailableOrNotFresh) throws IOException {
            // this method will always return a non-null value
        String urlHostPort = getHostPort(theURL);
        RobotsTxtEntry robotsTxt4Host = null;
        Map<String, byte[]> record;
        try {
            record = this.robotsTable.get(this.robotsTable.encodedKey(urlHostPort));
        } catch (RowSpaceExceededException e) {
            log.warn("memory exhausted", e);
            record = null;
        }
        if (record != null) robotsTxt4Host = new RobotsTxtEntry(urlHostPort, record);
        
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
                try {
                    record = this.robotsTable.get(this.robotsTable.encodedKey(urlHostPort));
                } catch (RowSpaceExceededException e) {
                    log.warn("memory exhausted", e);
                    record = null;
                }
                if (record != null) robotsTxt4Host = new RobotsTxtEntry(urlHostPort, record);
                if (robotsTxt4Host != null &&
                    robotsTxt4Host.getLoadedDate() != null &&
                    System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() <= 1*24*60*60*1000) {
                    return robotsTxt4Host;
                }
                
                // generating the proper url to download the robots txt
                MultiProtocolURI robotsURL = null;
                try {                 
                    robotsURL = new MultiProtocolURI("http://" + urlHostPort + "/robots.txt");
                } catch (final MalformedURLException e) {
                    log.fatal("Unable to generate robots.txt URL for host:port '" + urlHostPort + "'.", e);
                    robotsURL = null;
                }
                
                Object[] result = null;
                if (robotsURL != null) {
                    if (log.isDebugEnabled()) log.debug("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    try {
                        result = downloadRobotsTxt(robotsURL, 5, robotsTxt4Host);
                    } catch (final Exception e) {
                        result = null;
                    }
                }
                /*
                assert !loadedRobots.contains(robotsURL.toNormalform(false, false)) :
                    "robots-url=" + robotsURL.toString() +
                    ", robots=" + ((result == null || result[DOWNLOAD_ROBOTS_TXT] == null) ? "NULL" : UTF8.String((byte[]) result[DOWNLOAD_ROBOTS_TXT])) +
                    ", robotsTxt4Host=" + ((robotsTxt4Host == null) ? "NULL" : robotsTxt4Host.getLoadedDate().toString());
                loadedRobots.add(robotsURL.toNormalform(false, false));
                */
                
                if (result == null) {
                    // no robots.txt available, make an entry to prevent that the robots loading is done twice
                    if (robotsTxt4Host == null) {
                        // generate artificial entry
                        robotsTxt4Host = new RobotsTxtEntry(
                                robotsURL, 
                                new ArrayList<String>(), 
                                new ArrayList<String>(), 
                                new Date(),
                                new Date(),
                                null,
                                null,
                                Integer.valueOf(0),
                                null);
                    } else {
                        robotsTxt4Host.setLoadedDate(new Date());
                    }
                    
                    // store the data into the robots DB
                    int sz = this.robotsTable.size();
                    addEntry(robotsTxt4Host);
                    if (this.robotsTable.size() <= sz) {
                    	log.fatal("new entry in robots.txt table failed, resetting database");
                    	this.clear();
                    	addEntry(robotsTxt4Host);
                    }
                } else {
                    final RobotsTxtParser parserResult = new RobotsTxtParser((byte[]) result[DOWNLOAD_ROBOTS_TXT], thisAgents);
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
                            parserResult.crawlDelayMillis(),
                            parserResult.agentName());
                }
            }
        }

        return robotsTxt4Host;
    }
    
    private RobotsTxtEntry addEntry(
    		final MultiProtocolURI theURL, 
    		final ArrayList<String> allowPathList, 
    		final ArrayList<String> denyPathList, 
            final Date loadedDate, 
    		final Date modDate, 
    		final String eTag, 
    		final String sitemap,
    		final long crawlDelayMillis,
    		final String agentName
    ) {
        final RobotsTxtEntry entry = new RobotsTxtEntry(
                                theURL, allowPathList, denyPathList,
                                loadedDate, modDate,
                                eTag, sitemap, crawlDelayMillis, agentName);
        addEntry(entry);
        return entry;
    }
    
    private String addEntry(final RobotsTxtEntry entry) {
        // writes a new page and returns key
        try {
            this.robotsTable.insert(this.robotsTable.encodedKey(entry.getHostName()), entry.getMem());
            return entry.getHostName();
        } catch (final Exception e) {
            log.warn("cannot write robots.txt entry", e);
            return null;
        }
    }    
    
    // methods that had been in robotsParser.java:
    
    private static final int DOWNLOAD_ACCESS_RESTRICTED = 0;
    private static final int DOWNLOAD_ROBOTS_TXT = 1;
    private static final int DOWNLOAD_ETAG = 2;
    private static final int DOWNLOAD_MODDATE = 3;
    
    static final String getHostPort(final MultiProtocolURI theURL) {
        String urlHostPort = null;
        final int port = getPort(theURL);
        urlHostPort = theURL.getHost() + ":" + port;
        urlHostPort = urlHostPort.toLowerCase().intern();    
        
        return urlHostPort;
    }
    
    private static final int getPort(final MultiProtocolURI theURL) {
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

    private static Object[] downloadRobotsTxt(final MultiProtocolURI robotsURL, int redirectionCount, final RobotsTxtEntry entry) throws Exception {
        if (robotsURL == null || !robotsURL.getProtocol().startsWith("http")) return null;
        
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
        reqHeaders.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
        
        // adding referer
        reqHeaders.put(RequestHeader.REFERER, (MultiProtocolURI.newURL(robotsURL,"/")).toNormalform(true, true));
        
        if (entry != null) {
            oldEtag = entry.getETag();
            reqHeaders = new RequestHeader();
            final Date modDate = entry.getModDate();
            if (modDate != null) reqHeaders.put(RequestHeader.IF_MODIFIED_SINCE, HeaderFramework.formatRFC1123(entry.getModDate()));
            
        }
        
        // setup http-client
        //TODO: adding Traffic statistic for robots download?
        final HTTPClient client = new HTTPClient();
        client.setHeader(reqHeaders.entrySet());
        try {
            // check for interruption
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress.");
            
            // sending the get request
            robotsTxt = client.GETbytes(robotsURL);
            // statistics:
            if (robotsTxt != null) {
            	ByteCount.addAccountCount(ByteCount.CRAWLER, robotsTxt.length);
            }
            final int code = client.getHttpResponse().getStatusLine().getStatusCode();
            final ResponseHeader header = new ResponseHeader(client.getHttpResponse().getAllHeaders());
            
            // check the response status
            if (code > 199 && code < 300) {
            	if (!header.mime().startsWith("text/plain")) {
                    robotsTxt = null;
                    log.info("Robots.txt from URL '" + robotsURL + "' has wrong mimetype '" + header.mime() + "'.");
                } else {

                    // getting some metadata
                	eTag = header.containsKey(HeaderFramework.ETAG)?(header.get(HeaderFramework.ETAG)).trim():null;
                    lastMod = header.lastModified();
                    
                    // if the robots.txt file was not changed we break here
                    if ((eTag != null) && (oldEtag != null) && (eTag.equals(oldEtag))) {
                        if (log.isDebugEnabled()) log.debug("Robots.txt from URL '" + robotsURL + "' was not modified. Abort downloading of new version.");
                        return null;
                    }
                    
                    
                    downloadEnd = System.currentTimeMillis();                    
                    if (log.isDebugEnabled()) log.debug("Robots.txt successfully loaded from URL '" + robotsURL + "' in " + (downloadEnd-downloadStart) + " ms.");
                }
            } else if (code == 304) {
                return null;
            } else if (code > 299 && code < 400) {
                // getting redirection URL
            	String redirectionUrlString = header.get(HeaderFramework.LOCATION);
                if (redirectionUrlString==null) {
                    if (log.isDebugEnabled())
                		log.debug("robots.txt could not be downloaded from URL '" + robotsURL + "' because of missing redirecton header. [" + client.getHttpResponse().getStatusLine() + "].");
                    robotsTxt = null;                    
                } else {
                
                    redirectionUrlString = redirectionUrlString.trim();
                    
                    // generating the new URL object
                    final MultiProtocolURI redirectionUrl = MultiProtocolURI.newURL(robotsURL, redirectionUrlString);      
                    
                    // following the redirection
                    if (log.isDebugEnabled()) log.debug("Redirection detected for robots.txt with URL '" + robotsURL + "'." + 
                            "\nRedirecting request to: " + redirectionUrl);
                    return downloadRobotsTxt(redirectionUrl,redirectionCount,entry);
                }
            } else if (code == 401 || code == 403) {
                accessCompletelyRestricted = true;
                if (log.isDebugEnabled()) log.debug("Access to Robots.txt not allowed on URL '" + robotsURL + "'.");
            } else {
            	if (log.isDebugEnabled())
            		log.debug("robots.txt could not be downloaded from URL '" + robotsURL + "'. [" + client.getHttpResponse().getStatusLine() + "].");
                robotsTxt = null;
            }        
        } catch (final Exception e) {
            throw e;
        }
        return new Object[]{Boolean.valueOf(accessCompletelyRestricted),robotsTxt,eTag,lastMod};
    }
}
