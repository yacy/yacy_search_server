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

package net.yacy.crawler.robots;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.BEncodedHeap;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.repository.LoaderDispatcher;

import org.apache.log4j.Logger;


public class RobotsTxt {

    private static Logger log = Logger.getLogger(RobotsTxt.class);

    protected static final String ROBOTS_DB_PATH_SEPARATOR = ";";
    protected static final Pattern ROBOTS_DB_PATH_SEPARATOR_MATCHER = Pattern.compile(ROBOTS_DB_PATH_SEPARATOR);

    private final ConcurrentHashMap<String, DomSync> syncObjects;
    //private static final HashSet<String> loadedRobots = new HashSet<String>(); // only for debugging
    private final WorkTables tables;
    private final LoaderDispatcher loader;

    private static class DomSync {
    	private DomSync() {}
    }

    public RobotsTxt(final WorkTables worktables, LoaderDispatcher loader) {
        this.syncObjects = new ConcurrentHashMap<String, DomSync>();
        this.tables = worktables;
        this.loader = loader;
        try {
            this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
            //log.info("initiated robots table: " + this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME).getFile());
        } catch (final IOException e) {
            try {
                this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME).clear();
            } catch (final IOException e1) {
            }
        }
    }

    public void clear() throws IOException {
        log.info("clearing robots table");
        this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME).clear();
        this.syncObjects.clear();
    }

    public int size() throws IOException {
        return this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME).size();
    }

    public RobotsTxtEntry getEntry(final MultiProtocolURI theURL, final Set<String> thisAgents) {
        if (theURL == null) throw new IllegalArgumentException();
        if (!theURL.getProtocol().startsWith("http")) return null;
        return getEntry(theURL, thisAgents, true);
    }

    private RobotsTxtEntry getEntry(final MultiProtocolURI theURL, final Set<String> thisAgents, final boolean fetchOnlineIfNotAvailableOrNotFresh) {
            // this method will always return a non-null value
        final String urlHostPort = getHostPort(theURL);
        RobotsTxtEntry robotsTxt4Host = null;
        Map<String, byte[]> record;
        BEncodedHeap robotsTable = null;
        try {
            robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
        } catch (IOException e1) {
            log.fatal("tables not available", e1);
        }
        try {
            record = robotsTable.get(robotsTable.encodedKey(urlHostPort));
        } catch (final SpaceExceededException e) {
            log.warn("memory exhausted", e);
            record = null;
        } catch (IOException e) {
            log.warn("cannot get robotstxt from table", e);
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
                    record = robotsTable.get(robotsTable.encodedKey(urlHostPort));
                } catch (final SpaceExceededException e) {
                    log.warn("memory exhausted", e);
                    record = null;
                } catch (IOException e) {
                    log.warn("cannot get robotstxt from table", e);
                    record = null;
                }
                if (record != null) robotsTxt4Host = new RobotsTxtEntry(urlHostPort, record);
                if (robotsTxt4Host != null &&
                    robotsTxt4Host.getLoadedDate() != null &&
                    System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() <= 1*24*60*60*1000) {
                    return robotsTxt4Host;
                }

                // generating the proper url to download the robots txt
                DigestURI robotsURL = null;
                try {
                    robotsURL = new DigestURI("http://" + urlHostPort + "/robots.txt");
                } catch (final MalformedURLException e) {
                    log.fatal("Unable to generate robots.txt URL for host:port '" + urlHostPort + "'.", e);
                    robotsURL = null;
                }

                Response response = null;
                if (robotsURL != null) {
                    if (log.isDebugEnabled()) log.debug("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    Request request = new Request(robotsURL, null);
                    try {
                        response = this.loader.load(request, CacheStrategy.NOCACHE, null, 0);
                    } catch (IOException e) {
                        response = null;
                    }
                }

                if (response == null) {
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
                    final int sz = robotsTable.size();
                    addEntry(robotsTxt4Host);
                    if (robotsTable.size() <= sz) {
                    	log.fatal("new entry in robots.txt table failed, resetting database");
                    	try {clear();} catch (IOException e) {}
                    	addEntry(robotsTxt4Host);
                    }
                } else {
                    final byte[] robotsTxt = response.getContent();
                    //Log.logInfo("RobotsTxt", "robots of " + robotsURL.toNormalform(true, true) + ":\n" + ((robotsTxt == null) ? "null" : UTF8.String(robotsTxt))); // debug TODO remove
                    RobotsTxtParser parserResult;
                    ArrayList<String> denyPath;
                    if (response.getResponseHeader().getStatusCode() == 401 || response.getResponseHeader().getStatusCode() == 403) {
                        parserResult = new RobotsTxtParser(thisAgents);
                        // create virtual deny path
                        denyPath = new ArrayList<String>();
                        denyPath.add("/");
                    } else {
                        parserResult = new RobotsTxtParser(thisAgents, robotsTxt);
                        denyPath = parserResult.denyList();
                    }

                    // store the data into the robots DB
                    String etag = response.getResponseHeader().containsKey(HeaderFramework.ETAG) ? (response.getResponseHeader().get(HeaderFramework.ETAG)).trim() : null;
                    robotsTxt4Host = addEntry(
                            robotsURL,
                            parserResult.allowList(),
                            denyPath,
                            new Date(),
                            response.getResponseHeader().lastModified(),
                            etag,
                            parserResult.sitemap(),
                            parserResult.crawlDelayMillis(),
                            parserResult.agentName());
                }
            }
        }

        return robotsTxt4Host;
    }

    public void ensureExist(final MultiProtocolURI theURL, final Set<String> thisAgents, boolean concurrent) {
        final String urlHostPort = getHostPort(theURL);
        final BEncodedHeap robotsTable;
        try {
            robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
        } catch (IOException e1) {
            log.fatal("tables not available", e1);
            return;
        }
        if (robotsTable == null || robotsTable.containsKey(robotsTable.encodedKey(urlHostPort))) return;

        if (concurrent)
            new Thread() {public void run(){ensureExist(urlHostPort, robotsTable, thisAgents);}}.start();
        else
            ensureExist(urlHostPort, robotsTable, thisAgents);
    }
    
    private void ensureExist(final String urlHostPort, BEncodedHeap robotsTable, final Set<String> thisAgents) {

        // make or get a synchronization object
        DomSync syncObj = RobotsTxt.this.syncObjects.get(urlHostPort);
        if (syncObj == null) {
            syncObj = new DomSync();
            RobotsTxt.this.syncObjects.put(urlHostPort, syncObj);
        }
        // we can now synchronize for each host separately
        synchronized (syncObj) {
            if (robotsTable.containsKey(robotsTable.encodedKey(urlHostPort))) return;

            // generating the proper url to download the robots txt
            DigestURI robotsURL = null;
            try {
                robotsURL = new DigestURI("http://" + urlHostPort + "/robots.txt");
            } catch (final MalformedURLException e) {
                log.fatal("Unable to generate robots.txt URL for host:port '" + urlHostPort + "'.", e);
                robotsURL = null;
            }

            Response response = null;
            if (robotsURL != null) {
                if (log.isDebugEnabled()) log.debug("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                Request request = new Request(robotsURL, null);
                try {
                    response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE, null, 0);
                } catch (IOException e) {
                    response = null;
                }
            }

            RobotsTxtEntry robotsTxt4Host = null;
            if (response == null) {
                // no robots.txt available, make an entry to prevent that the robots loading is done twice
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

                // store the data into the robots DB
                final int sz = robotsTable.size();
                addEntry(robotsTxt4Host);
                if (robotsTable.size() <= sz) {
                    log.fatal("new entry in robots.txt table failed, resetting database");
                    try {clear();} catch (IOException e) {}
                    addEntry(robotsTxt4Host);
                }
            } else {
                final byte[] robotsTxt = response.getContent();
                //Log.logInfo("RobotsTxt", "robots of " + robotsURL.toNormalform(true, true) + ":\n" + ((robotsTxt == null) ? "null" : UTF8.String(robotsTxt))); // debug TODO remove
                RobotsTxtParser parserResult;
                ArrayList<String> denyPath;
                if (response.getResponseHeader().getStatusCode() == 401 || response.getResponseHeader().getStatusCode() == 403) {
                    parserResult = new RobotsTxtParser(thisAgents);
                    // create virtual deny path
                    denyPath = new ArrayList<String>();
                    denyPath.add("/");
                } else {
                    parserResult = new RobotsTxtParser(thisAgents, robotsTxt);
                    denyPath = parserResult.denyList();
                }

                // store the data into the robots DB
                String etag = response.getResponseHeader().containsKey(HeaderFramework.ETAG) ? (response.getResponseHeader().get(HeaderFramework.ETAG)).trim() : null;
                robotsTxt4Host = addEntry(
                        robotsURL,
                        parserResult.allowList(),
                        denyPath,
                        new Date(),
                        response.getResponseHeader().lastModified(),
                        etag,
                        parserResult.sitemap(),
                        parserResult.crawlDelayMillis(),
                        parserResult.agentName());
            }
        }
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
            final BEncodedHeap robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
            robotsTable.insert(robotsTable.encodedKey(entry.getHostName()), entry.getMem());
            return entry.getHostName();
        } catch (final Exception e) {
            log.warn("cannot write robots.txt entry", e);
            return null;
        }
    }

    static final String getHostPort(final MultiProtocolURI theURL) {
        final int port = getPort(theURL);
        String host = theURL.getHost();
        StringBuilder sb = new StringBuilder(host.length() + 6);
        sb.append(host).append(':').append(Integer.toString(port));
        return sb.toString();
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

}
