//plasmaCrawlRobotsTxt.java
//-------------------------------------
//part of YACY
// SPDX-FileCopyrightText: 2004 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import net.yacy.kelondro.util.NamePrefixThreadFactory;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.LoaderDispatcher;

public class RobotsTxt {

    private final static ConcurrentLog log = new ConcurrentLog(RobotsTxt.class.getName());

    protected static final String ROBOTS_TXT_PATH = "/robots.txt";
    protected static final String ROBOTS_DB_PATH_SEPARATOR = ";";
    protected static final Pattern ROBOTS_DB_PATH_SEPARATOR_MATCHER = Pattern.compile(ROBOTS_DB_PATH_SEPARATOR);

    private final ConcurrentMap<String, DomSync> syncObjects;
    //private static final HashSet<String> loadedRobots = new HashSet<String>(); // only for debugging
    private final WorkTables tables;
    private final LoaderDispatcher loader;
    /** Thread pool used to launch concurrent tasks */
    private final ThreadPoolExecutor threadPool;

    private static class DomSync {
        private DomSync() {}
    }

    /**
     *
     * @param worktables
     * @param loader
     * @param maxActiveTheads maximum active threads this instance is allowed to run for its concurrent tasks
     */
    public RobotsTxt(final WorkTables worktables, LoaderDispatcher loader, final int maxActiveTheads) {
        this.threadPool = new ThreadPoolExecutor(maxActiveTheads, maxActiveTheads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new NamePrefixThreadFactory(RobotsTxt.class.getSimpleName()));
        this.syncObjects = new ConcurrentHashMap<>();
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

    public void close() {
        /* Shutdown all active robots.txt loading threads */
        if(this.threadPool != null) {
            this.threadPool.shutdownNow();
        }
    }

    public int size() throws IOException {
        return this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME).size();
    }

    public RobotsTxtEntry getEntry(final MultiProtocolURL theURL, final ClientIdentification.Agent agent) {
        if (theURL == null) throw new IllegalArgumentException();
        if (!theURL.getProtocol().startsWith("http")) return null;
        return this.getEntry(getHostPort(theURL), agent, true);
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
             robotsTxt4Host == null /*||
             robotsTxt4Host.getLoadedDate() == null ||
             System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() > 7*24*60*60*1000 */
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
                if (robotsTxt4Host != null /*&&
                    robotsTxt4Host.getLoadedDate() != null &&
                    System.currentTimeMillis() - robotsTxt4Host.getLoadedDate().getTime() <= 1*24*60*60*1000 */) {
                    return robotsTxt4Host;
                }

                // generating the proper url to download the robots txt
                final DigestURL robotsURL = robotsURL(urlHostPort);

                Response response = null;
                if (robotsURL != null) {
                    if (log.isFine()) log.fine("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                    final Request request = new Request(robotsURL, null);
                    try {
                        response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE, null, agent);
                    } catch (final Throwable e) {
                        log.info("Trying to download the robots.txt file from URL '" + robotsURL.toNormalform(false) + "' failed - " + e.getMessage());
                        response = null;
                    }
                }

                if (response == null) {
                    this.processOldEntry(robotsTxt4Host, robotsURL, robotsTable);
                } else {
                    robotsTxt4Host = this.processNewEntry(robotsURL, response, agent.robotIDs);
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
        } catch (final IOException e) {
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
        final Thread t = new Thread("Robots.txt:ensureExist(" + theURL.toNormalform(true) + ")") {
            @Override
            public void run(){
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
                    final DigestURL robotsURL = robotsURL(urlHostPort);

                    Response response = null;
                    if (robotsURL != null) {
                        if (log.isFine()) log.fine("Trying to download the robots.txt file from URL '" + robotsURL + "'.");
                        final Request request = new Request(robotsURL, null);
                        try {
                            response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE, null, agent);
                        } catch (final IOException e) {
                            response = null;
                        }
                    }

                    if (response == null) {
                        RobotsTxt.this.processOldEntry(null, robotsURL, robotsTable);
                    } else {
                        RobotsTxt.this.processNewEntry(robotsURL, response, agent.robotIDs);
                    }
                }
            }
        };
        if (concurrent) {
            this.threadPool.execute(t);
        } else {
            t.run();
        }
    }

    /**
     * @return the approximate number of threads that are actively
     * executing robots.txt loading tasks
     */
    public int getActiveThreads() {
        return this.threadPool != null ? this.threadPool.getActiveCount() : 0;
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
        this.addEntry(robotsTxt4Host);
        if (robotsTable.size() <= sz) {
            log.severe("new entry in robots.txt table failed, resetting database");
            try {this.clear();} catch (final IOException e) {}
            this.addEntry(robotsTxt4Host);
        }
    }

    /**
     * Process a response to a robots.txt request, create a new robots entry, add it to the robots table then return it.
     * @param robotsURL the initial robots.txt URL (before any eventual redirection). Must not be null.
     * @param response the response to the requested robots.txt URL. Must not be null.
     * @param thisAgents the agent identifier(s) used to request the robots.txt URL
     * @return the new robots entry
     */
    private RobotsTxtEntry processNewEntry(final DigestURL robotsURL, final Response response, final String[] thisAgents) {
        final byte[] robotsTxt = response.getContent();
        //Log.logInfo("RobotsTxt", "robots of " + robotsURL.toNormalform(true, true) + ":\n" + ((robotsTxt == null) ? "null" : UTF8.String(robotsTxt))); // debug TODO remove
        RobotsTxtParser parserResult;
        ArrayList<String> denyPath;
        if (response.getResponseHeader().getStatusCode() == 401 || response.getResponseHeader().getStatusCode() == 403) {
            parserResult = new RobotsTxtParser(thisAgents);
            // create virtual deny path
            denyPath = new ArrayList<>();
            denyPath.add("/");
        } else {
            parserResult = new RobotsTxtParser(thisAgents, robotsTxt);
            denyPath = parserResult.denyList();
        }

        // store the data into the robots DB
        final String etag = response.getResponseHeader().containsKey(HeaderFramework.ETAG) ? (response.getResponseHeader().get(HeaderFramework.ETAG)).trim() : null;
        final boolean isBrowserAgent = thisAgents.length == 1 && thisAgents[0].equals("Mozilla");
        if (isBrowserAgent) {
            denyPath.clear();
        }
        /* The robotsURL may eventually be redirected (from http to https is common),
         * but we store here the url before any redirection. If would not process this way, the unredirected URL would later
         * never found in the robots table thus needing each time a http load.*/
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
        this.addEntry(robotsTxt4Host);
        return robotsTxt4Host;
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
        final String host = theURL.getHost();
        if (host == null) return null;
        final StringBuilder sb = new StringBuilder(host.length() + 6);
        if (host.indexOf(':') >= 0) {sb.append('[').append(host).append(']');} else sb.append(host);
        sb.append(':').append(Integer.toString(port));
        return sb.toString();
    }

    public static boolean isRobotsURL(MultiProtocolURL url) {
        return url.getPath().equals(ROBOTS_TXT_PATH);
    }

    /**
     * generate a robots.txt url.
     * @param urlHostPort a string of the form <host>':'<port> or just <host>
     * @return the full robots.txt url
     */
    public static DigestURL robotsURL(String urlHostPort) {
        if (urlHostPort.endsWith(":80")) urlHostPort = urlHostPort.substring(0, urlHostPort.length() - 3);
        DigestURL robotsURL = null;
        try {
            robotsURL = new DigestURL((urlHostPort.endsWith(":443") ? "https://" : "http://") + urlHostPort + ROBOTS_TXT_PATH);
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

    /**
     * A unit task to load a robots.txt entry
     */
    private class CrawlCheckTask implements Callable<CheckEntry> {

        private final DigestURL url;
        private final ClientIdentification.Agent userAgent;

        public CrawlCheckTask(final DigestURL url, final ClientIdentification.Agent userAgent) {
            this.url = url;
            this.userAgent = userAgent;
        }

        @Override
        public CheckEntry call() throws Exception {
            // try to load the robots
            final RobotsTxtEntry robotsEntry = RobotsTxt.this.getEntry(this.url, this.userAgent);
            final boolean robotsAllowed = robotsEntry == null ? true : !robotsEntry.isDisallowed(this.url);
            if (robotsAllowed) {
                try {
                    final Request request = RobotsTxt.this.loader.request(this.url, true, false);
                    final Response response = RobotsTxt.this.loader.load(request, CacheStrategy.NOCACHE,
                            BlacklistType.CRAWLER, this.userAgent);
                    return new CheckEntry(this.url, robotsEntry, response, null);
                } catch (final IOException e) {
                    return new CheckEntry(this.url, robotsEntry, null, "error response: " + e.getMessage());
                }
            }
            return new CheckEntry(this.url, robotsEntry, null, null);
        }


    }

    public Collection<CheckEntry> massCrawlCheck(final Collection<DigestURL> rootURLs, final ClientIdentification.Agent userAgent) {
        final List<Future<CheckEntry>> futures = new ArrayList<>();
            for (final DigestURL u: rootURLs) {
                futures.add(this.threadPool.submit(new CrawlCheckTask(u, userAgent)));
            }
        final Collection<CheckEntry> results = new ArrayList<>();
        /* Now collect the results concurrently loaded */
        for(final Future<CheckEntry> future: futures) {
            try {
                results.add(future.get());
            } catch (final InterruptedException e) {
                log.warn("massCrawlCheck was interrupted before retrieving all results.");
                break;
            } catch (final ExecutionException e) {
                /* A robots.txt loading failed : let's continue and try to get the next result
                 * (most of time this should not happen, as Exceptions are caught inside the concurrent task) */
                continue;
            }
        }
        return results;
    }
}
