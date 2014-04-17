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
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.BEncodedHeap;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.repository.Blacklist.BlacklistType;

public class RobotsTxt {

    private final static ConcurrentLog log = new ConcurrentLog(RobotsTxt.class.getName());

    protected static final String ROBOTS_DB_PATH_SEPARATOR = ";";
    protected static final Pattern ROBOTS_DB_PATH_SEPARATOR_MATCHER = Pattern.compile(ROBOTS_DB_PATH_SEPARATOR);

    private final ConcurrentMap<String, DomSync> syncObjects;
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

    public RobotsTxtEntry getEntry(final MultiProtocolURL theURL, final ClientIdentification.Agent agent) {
        if (theURL == null) throw new IllegalArgumentException();
        if (!theURL.getProtocol().startsWith("http")) return null;
        return getEntry(getHostPort(theURL), agent, true);
    }

    public RobotsTxtEntry getEntry(final String urlHostPort, final ClientIdentification.Agent agent, final boolean fetchOnlineIfNotAvailableOrNotFresh) {
            // this method will always return a non-null value
        RobotsTxtEntry robotsTxt4Host = null;
        Map<String, byte[]> record;
        BEncodedHeap robotsTable = null;
        try {
            robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
        } catch (final IOException e1) {
            log.severe("tables not available", e1);
        }
        try {
            record = robotsTable.get(robotsTable.encodedKey(urlHostPort));
        } catch (final SpaceExceededException e) {
            log.warn("memory exhausted", e);
            record = null;
        } catch (final IOException e) {
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
                } catch (final IOException e) {
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
                DigestURL robotsURL = robotsURL(urlHostPort);

                Response response = null;
                if (robotsURL != null) {
                    if (log.isFine()) log.fine("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    Request request = new Request(robotsURL, null);
                    try {
                        response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE, null, agent);
                    } catch (final Throwable e) {
                        log.info("Trying to download the robots.txt file from URL '" + robotsURL + "' failed - " + e.getMessage());
                        response = null;
                    }
                }

                if (response == null) {
                    processOldEntry(robotsTxt4Host, robotsURL, robotsTable);
                } else {
                    processNewEntry(robotsURL, response, agent.robotIDs);
                }
            }
        }

        return robotsTxt4Host;
    }
    
    public void delete(final MultiProtocolURL theURL) {
        final String urlHostPort = getHostPort(theURL);
        if (urlHostPort == null) return;
        final BEncodedHeap robotsTable;
        try {
            robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
        } catch (final IOException e1) {
            log.severe("tables not available", e1);
            return;
        }
        if (robotsTable == null) return;
        try {
            robotsTable.delete(robotsTable.encodedKey(urlHostPort));
        } catch (IOException e) {
        }
    }
    
    public void ensureExist(final MultiProtocolURL theURL, final ClientIdentification.Agent agent, boolean concurrent) {
        if (theURL.isLocal()) return;
        final String urlHostPort = getHostPort(theURL);
        if (urlHostPort == null) return;
        final BEncodedHeap robotsTable;
        try {
            robotsTable = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
        } catch (final IOException e1) {
            log.severe("tables not available", e1);
            return;
        }
        if (robotsTable != null && robotsTable.containsKey(robotsTable.encodedKey(urlHostPort))) return;
        Thread t = new Thread() {
            @Override
            public void run(){
                this.setName("Robots.txt:ensureExist(" + theURL.toNormalform(true) + ")");
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
                    DigestURL robotsURL = robotsURL(urlHostPort);
                    
                    Response response = null;
                    if (robotsURL != null) {
                        if (log.isFine()) log.fine("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                        Request request = new Request(robotsURL, null);
                        try {
                            response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE, null, agent);
                        } catch (final IOException e) {
                            response = null;
                        }
                    }

                    if (response == null) {
                        processOldEntry(null, robotsURL, robotsTable);
                    } else {
                        processNewEntry(robotsURL, response, agent.robotIDs);
                    }
                }
            }
        };
        if (concurrent) t.start(); else t.run();
    }

    private void processOldEntry(RobotsTxtEntry robotsTxt4Host, DigestURL robotsURL, BEncodedHeap robotsTable) {
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
            log.severe("new entry in robots.txt table failed, resetting database");
            try {clear();} catch (final IOException e) {}
            addEntry(robotsTxt4Host);
        }
    }
    
    private void processNewEntry(DigestURL robotsURL, Response response, final String[] thisAgents) {
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
        boolean isBrowserAgent = thisAgents.length == 1 && thisAgents[0].equals("Mozilla");
        if (isBrowserAgent) denyPath.clear();
        final RobotsTxtEntry robotsTxt4Host = new RobotsTxtEntry(
                    robotsURL,
                    parserResult.allowList(),
                    denyPath,
                    new Date(),
                    response.getResponseHeader().lastModified(),
                    etag,
                    parserResult.sitemap(),
                    parserResult.crawlDelayMillis(),
                    parserResult.agentName());
        addEntry(robotsTxt4Host);
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

    public static final String getHostPort(final MultiProtocolURL theURL) {
        int port = theURL.getPort();
        if (port == -1) {
            if (theURL.getProtocol().equalsIgnoreCase("http")) {
                port = 80;
            } else if (theURL.getProtocol().equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }
        String host = theURL.getHost();
        if (host == null) return null;
        StringBuilder sb = new StringBuilder(host.length() + 6);
        sb.append(host).append(':').append(Integer.toString(port));
        return sb.toString();
    }
    
    public static DigestURL robotsURL(final String urlHostPort) {
        DigestURL robotsURL = null;
        try {
            robotsURL = new DigestURL((urlHostPort.endsWith(":443") ? "https://" : "http://") + urlHostPort + "/robots.txt");
        } catch (final MalformedURLException e) {
            log.severe("Unable to generate robots.txt URL for host:port '" + urlHostPort + "'.", e);
            robotsURL = null;
        }
        return robotsURL;
    }
    
    public static class CheckEntry {
        public final DigestURL digestURL;
        public final RobotsTxtEntry robotsTxtEntry;
        public final Response response;
        public final String error;
        public CheckEntry(DigestURL digestURL, RobotsTxtEntry robotsTxtEntry, Response response, String error) {
            this.digestURL = digestURL;
            this.robotsTxtEntry = robotsTxtEntry;
            this.response = response;
            this.error = error;
        }
    }
    
    public Collection<CheckEntry> massCrawlCheck(final Collection<DigestURL> rootURLs, final ClientIdentification.Agent userAgent, final int concurrency) {
        // put the rootURLs into a blocking queue as input for concurrent computation
        final BlockingQueue<DigestURL> in = new LinkedBlockingQueue<DigestURL>();
        try {
            for (DigestURL u: rootURLs) in.put(u);
            for (int i = 0; i < concurrency; i++) in.put(DigestURL.POISON);
        } catch (InterruptedException e) {}
        final BlockingQueue<CheckEntry> out = new LinkedBlockingQueue<CheckEntry>();
        final Thread[] threads = new Thread[concurrency];
        for (int i = 0; i < concurrency; i++) {
            threads[i] = new Thread() {
                @Override
                public void run() {
                    DigestURL u;
                    try {
                        while ((u = in.take()) != DigestURL.POISON) {
                            // try to load the robots
                            RobotsTxtEntry robotsEntry = getEntry(u, userAgent);
                            boolean robotsAllowed = robotsEntry == null ? true : !robotsEntry.isDisallowed(u);
                            if (robotsAllowed) try {
                                Request request = loader.request(u, true, false);
                                Response response = loader.load(request, CacheStrategy.NOCACHE, BlacklistType.CRAWLER, userAgent);
                                out.put(new CheckEntry(u, robotsEntry, response, null));
                            } catch (final IOException e) {
                                out.put(new CheckEntry(u, robotsEntry, null, "error response: " + e.getMessage()));
                            }
                        }
                    } catch (InterruptedException e) {}
                }
            };
            threads[i].start();
        }
        // wait for termiation
        try {for (Thread t: threads) t.join();} catch (InterruptedException e1) {}
        return out;
    }
}
