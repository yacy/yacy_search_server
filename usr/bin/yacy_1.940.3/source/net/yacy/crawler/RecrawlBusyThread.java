/**
 * RecrawlBusyThread.java
 * SPDX-FileCopyrightText: 2015 by Burkhard Buelte
 * SPDX-License-Identifier: GPL-2.0-or-later
 * First released 15.05.2015 at https://yacy.net
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
package net.yacy.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.retrieval.Request;
import net.yacy.document.parser.html.TagValency;
import net.yacy.kelondro.workflow.AbstractBusyThread;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

/**
 * Selects documents by a query from the local index
 * and feeds the found urls to the crawler to recrawl the documents.
 * This is intended to keep the index up-to-date
 * Currently the doucments are selected by expired fresh_date_dt field
 * an added to the crawler in smaller chunks (see chunksize) as long as no other crawl is running.
 */
public class RecrawlBusyThread extends AbstractBusyThread {

    /** The thread name */
    public final static String THREAD_NAME = "recrawlindex";

    /** The default selection query */
    public static final String DEFAULT_QUERY = CollectionSchema.fresh_date_dt.getSolrFieldName()+":[* TO NOW/DAY-1DAY]";

    /** Default value for inclusion or not of documents with a https status different from 200 (success) */
    public static final boolean DEFAULT_INCLUDE_FAILED = false;

    /** The default value whether to delete on Recrawl */
    public static final boolean DEFAULT_DELETE_ON_RECRAWL = false;

    /** The current query selecting documents to recrawl */
    private String currentQuery;

    /** flag if docs with httpstatus_i <> 200 shall be recrawled */
    private boolean includefailed;

    /** flag whether to delete on Recrawl */
    private boolean deleteOnRecrawl;

    private int chunkstart = 0;
    private final int chunksize = 100;
    private final Switchboard sb;

    /** buffer of urls to recrawl */
    private final Set<DigestURL> urlstack;

    /** The total number of candidate URLs found for recrawl */
    private long urlsToRecrawl = 0;

    /** Total number of URLs added to the crawler queue for recrawl */
    private long recrawledUrlsCount = 0;

    /** Total number of URLs rejected for some reason by the crawl stacker or the crawler queue */
    private long rejectedUrlsCount = 0;

    /** Total number of malformed URLs found */
    private long malformedUrlsCount = 0;

    /** Total number of malformed URLs deleted from index */
    private long malformedUrlsDeletedCount = 0;

    private final String solrSortBy;

    /** Set to true when more URLs are still to be processed */
    private boolean moreToRecrawl = true;

    /** True when the job terminated early because an error occurred when requesting the Solr index, or the Solr index was closed */
    private boolean terminatedBySolrFailure = false;

    /** The recrawl job start time */
    private LocalDateTime startTime;

    /** The recrawl job end time */
    private LocalDateTime endTime;

    /**
     * @param xsb
     *            the Switchboard instance holding server environment
     * @param query
     *            the Solr selection query
     * @param includeFailed
     *            set to true when documents with a https status different from 200
     *            (success) must be included
     */
    public RecrawlBusyThread(final Switchboard xsb, final String query, final boolean includeFailed, final boolean deleteOnRecrawl) {
        super(3000, 1000); // set lower limits of cycle delay
        this.setName(THREAD_NAME);
        this.setIdleSleep(10*60000); // set actual cycle delays
        this.setBusySleep(2*60000);
        this.setPriority(Thread.MIN_PRIORITY);
        this.setLoadPreReqisite(1);
        this.sb = xsb;
        this.currentQuery = query;
        this.includefailed = includeFailed;
        this.deleteOnRecrawl = deleteOnRecrawl;
        this.urlstack = new HashSet<>();
        // workaround to prevent solr exception on existing index (not fully reindexed) since intro of schema with docvalues
        // org.apache.solr.core.SolrCore java.lang.IllegalStateException: unexpected docvalues type NONE for field 'load_date_dt' (expected=NUMERIC). Use UninvertingReader or index with docvalues.
        this.solrSortBy = CollectionSchema.load_date_dt.getSolrFieldName() + " asc";

        final SolrConnector solrConnector = this.sb.index.fulltext().getDefaultConnector();
        if (solrConnector != null && !solrConnector.isClosed()) {
            /* Ensure indexed data is up-to-date before running the main job */
            solrConnector.commit(true);
        }
    }

    /**
     * Set the query to select documents to recrawl
     * and resets the counter to start a fresh query loop
     * @param q select query
     * @param includefailedurls true=all http status docs are recrawled, false=httpstatus=200 docs are recrawled
     * @param deleteOnRecrawl
     */
    public void setQuery(String q, boolean includefailedurls, final boolean deleteOnRecrawl) {
        this.currentQuery = q;
        this.includefailed = includefailedurls;
        this.deleteOnRecrawl = deleteOnRecrawl;
        this.chunkstart = 0;
    }

    public String getQuery() {
        return this.currentQuery;
    }

    /**
     *
     * @param queryBase
     *            the base query
     * @param includeFailed
     *            set to true when documents with a https status different from 200
     *            (success) must be included
     * @return the Solr selection query for candidate URLs to recrawl
     */
    public static final String buildSelectionQuery(final String queryBase, final boolean includeFailed) {
        return includeFailed ? queryBase : queryBase + " AND (" + CollectionSchema.httpstatus_i.name() + ":200)";
    }

    /**
     * Flag to include failed urls (httpstatus_i <> 200)
     * if true -> currentQuery is used as is,
     * if false -> the term " AND (httpstatus_i:200)" is appended to currentQuery
     * @param includefailedurls
     */
    public void setIncludeFailed(boolean includefailedurls) {
        this.includefailed = includefailedurls;
    }

    public boolean getIncludeFailed () {
        return this.includefailed;
    }

    public void setDeleteOnRecrawl(final boolean deleteOnRecrawl) {
        this.deleteOnRecrawl = deleteOnRecrawl;
    }

    public boolean getDeleteOnRecrawl() {
        return this.deleteOnRecrawl;
    }

    /**
     * feed urls to the local crawler
     * (Switchboard.addToCrawler() is not used here, as there existing urls are always skipped)
     *
     * @return true if urls were added/accepted to the crawler
     */
    private boolean feedToCrawler() {

        int added = 0;

        if (!this.urlstack.isEmpty()) {
            final CrawlProfile profile = this.sb.crawler.defaultRecrawlJobProfile;

            for (final DigestURL url : this.urlstack) {
                final Request request = new Request(ASCII.getBytes(this.sb.peers.mySeed().hash), url, null, "",
                        new Date(), profile.handle(), 0, profile.timezoneOffset());
                String acceptedError = this.sb.crawlStacker.checkAcceptanceChangeable(url, profile, 0);
                if (!this.includefailed && acceptedError == null) { // skip check if failed docs to be included
                    acceptedError = this.sb.crawlStacker.checkAcceptanceInitially(url, profile);
                }
                if (acceptedError != null) {
                    this.rejectedUrlsCount++;
                    ConcurrentLog.info(THREAD_NAME, "addToCrawler: cannot load " + url.toNormalform(true) + ": " + acceptedError);
                    continue;
                }
                final String s;
                s = this.sb.crawlQueues.noticeURL.push(NoticedURL.StackType.LOCAL, request, profile, this.sb.robots);

                if (s != null) {
                    this.rejectedUrlsCount++;
                    ConcurrentLog.info(THREAD_NAME, "addToCrawler: failed to add " + url.toNormalform(true) + ": " + s);
                } else {
                    added++;
                    this.recrawledUrlsCount++;
                }
            }
            this.urlstack.clear();
        }
        return (added > 0);
    }

    /**
     * Process query and hand over urls to the crawler
     *
     * @return true if something processed
     */
    @Override
    public boolean job() {
        // more than chunksize crawls are running, do nothing
        if (this.sb.crawlQueues.coreCrawlJobSize() > this.chunksize) {
            return false;
        }

        boolean didSomething = false;
        if (this.urlstack.isEmpty()) {
            if(!this.moreToRecrawl) {
                /* We do not remove the thread from the Switchboard worker threads using serverSwitch.terminateThread(String,boolean),
                 * because we want to be able to provide a report after its termination */
                this.terminate(false);
            } else {
                this.moreToRecrawl = this.processSingleQuery();
                /* Even if no more URLs are to recrawl, the job has done something by searching the Solr index */
                didSomething = true;
            }
        } else {
            didSomething = this.feedToCrawler();
        }
        return didSomething;
    }

    @Override
    public synchronized void start() {
        this.startTime = LocalDateTime.now();
        super.start();
    }

    @Override
    public void terminate(boolean waitFor) {
        super.terminate(waitFor);
        this.endTime = LocalDateTime.now();
    }

    /**
     * Selects documents to recrawl the urls
     * @return true if query has more results
     */
    private boolean processSingleQuery() {
        if (!this.urlstack.isEmpty()) {
            return true;
        }
        SolrDocumentList docList = null;
        final SolrConnector solrConnector = this.sb.index.fulltext().getDefaultConnector();
        if (solrConnector == null || solrConnector.isClosed()) {
            this.urlsToRecrawl = 0;
            this.terminatedBySolrFailure = true;
            return false;
        }

        try {
            // query all or only httpstatus=200 depending on includefailed flag
            docList = solrConnector.getDocumentListByQuery(RecrawlBusyThread.buildSelectionQuery(this.currentQuery, this.includefailed),
                this.solrSortBy, this.chunkstart, this.chunksize, CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName());
            this.urlsToRecrawl = docList.getNumFound();
        } catch (final Throwable e) {
            this.urlsToRecrawl = 0;
            this.terminatedBySolrFailure = true;
        }

        if (docList != null) {
            final Set<String> tobedeletedIDs = new HashSet<>();
            for (final SolrDocument doc : docList) {
                try {
                    this.urlstack.add(new DigestURL((String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName())));
                    if (this.deleteOnRecrawl) tobedeletedIDs.add((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                } catch (final MalformedURLException ex) {
                    this.malformedUrlsCount++;
                    // if index entry hasn't a valid url (useless), delete it
                    tobedeletedIDs.add((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                    this.malformedUrlsDeletedCount++;
                    ConcurrentLog.severe(THREAD_NAME, "deleted index document with invalid url " + (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                }
            }

            if (!tobedeletedIDs.isEmpty()) try {
                solrConnector.deleteByIds(tobedeletedIDs);
                solrConnector.commit(false);
            } catch (final IOException e) {
                ConcurrentLog.severe(THREAD_NAME, "error deleting IDs ", e);
            }

            this.chunkstart = this.deleteOnRecrawl? 0 : this.chunkstart + this.chunksize;
        }

        if (docList == null || docList.size() < this.chunksize) {
            return false;
        }
        return true;
    }

    /**
     * @return a new default CrawlProfile instance to be used for recrawl jobs.
     */
    public static CrawlProfile buildDefaultCrawlProfile() {
        final CrawlProfile profile = new CrawlProfile(CrawlSwitchboard.CRAWL_PROFILE_RECRAWL_JOB, CrawlProfile.MATCH_ALL_STRING, // crawlerUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, // crawlerUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING, // crawlerIpMustMatch
                CrawlProfile.MATCH_NEVER_STRING, // crawlerIpMustNotMatch
                CrawlProfile.MATCH_NEVER_STRING, // crawlerCountryMustMatch
                CrawlProfile.MATCH_NEVER_STRING, // crawlerNoDepthLimitMatch
                CrawlProfile.MATCH_ALL_STRING, // indexUrlMustMatch
                CrawlProfile.MATCH_NEVER_STRING, // indexUrlMustNotMatch
                CrawlProfile.MATCH_ALL_STRING, // indexContentMustMatch
                CrawlProfile.MATCH_NEVER_STRING, // indexContentMustNotMatch
                true, //noindexWhenCanonicalUnequalURL
                0, false, CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_RECRAWL_JOB_RECRAWL_CYCLE), -1,
                true, true, true, false, // crawlingQ, followFrames, obeyHtmlRobotsNoindex, obeyHtmlRobotsNofollow,
                true, true, true, false, -1, false, true, CrawlProfile.MATCH_NEVER_STRING, CacheStrategy.IFFRESH,
                "robot_" + CrawlSwitchboard.CRAWL_PROFILE_RECRAWL_JOB,
                ClientIdentification.yacyInternetCrawlerAgentName, 
                TagValency.EVAL, null, null, 0);
        return profile;
    }

    @Override
    public int getJobCount() {
        return this.urlstack.size();
    }

    /**
     * @return The total number of candidate URLs found for recrawl
     */
    public long getUrlsToRecrawl() {
        return this.urlsToRecrawl;
    }

    /**
     * @return The total number of URLs added to the crawler queue for recrawl
     */
    public long getRecrawledUrlsCount() {
        return this.recrawledUrlsCount;
    }

    /**
     * @return The total number of URLs rejected for some reason by the crawl
     *         stacker or the crawler queue
     */
    public long getRejectedUrlsCount() {
        return this.rejectedUrlsCount;
    }

    /**
     * @return The total number of malformed URLs found
     */
    public long getMalformedUrlsCount() {
        return this.malformedUrlsCount;
    }

    /**
     * @return The total number of malformed URLs deleted from index
     */
    public long getMalformedUrlsDeletedCount() {
        return this.malformedUrlsDeletedCount;
    }

    /**
     * @return true when the job terminated early because an error occurred when
     *         requesting the Solr index, or the Solr index was closed
     */
    public boolean isTerminatedBySolrFailure() {
        return this.terminatedBySolrFailure;
    }

    /** @return The recrawl job start time */
    public LocalDateTime getStartTime() {
        return this.startTime;
    }

    /** @return The recrawl job end time */
    public LocalDateTime getEndTime() {
        return this.endTime;
    }

    @Override
    public void freemem() {
        this.urlstack.clear();
    }

}
