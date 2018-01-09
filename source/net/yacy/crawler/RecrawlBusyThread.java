/**
 * RecrawlBusyThread.java
 * Copyright 2015 by Burkhard Buelte
 * First released 15.05.2015 at http://yacy.net
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
import java.util.HashSet;
import java.util.Set;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.workflow.AbstractBusyThread;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

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

    /** The current query selecting documents to recrawl */
    private String currentQuery;
    
    /** flag if docs with httpstatus_i <> 200 shall be recrawled */
    private boolean includefailed;
    
    private int chunkstart = 0;
    private final int chunksize;
    private final Switchboard sb;
    
    /** buffer of urls to recrawl */
    private final Set<DigestURL> urlstack;
    
    /** The total number of candidate URLs found for recrawl */
    private long urlsToRecrawl = 0;
    
    private String solrSortBy;
    
    /** Set to true when more URLs are still to be processed */
    private boolean moreToRecrawl = true;

	/**
	 * @param xsb
	 *            the Switchboard instance holding server environment
	 * @param query
	 *            the Solr selection query
	 * @param includeFailed
	 *            set to true when documents with a https status different from 200
	 *            (success) must be included
	 */
    public RecrawlBusyThread(final Switchboard xsb, final String query, final boolean includeFailed) {
        super(3000, 1000); // set lower limits of cycle delay
        setName(THREAD_NAME);
        this.setIdleSleep(10*60000); // set actual cycle delays
        this.setBusySleep(2*60000);
        this.setPriority(Thread.MIN_PRIORITY);

        this.sb = xsb;
        this.currentQuery = query;
        this.includefailed = includeFailed;
        this.urlstack = new HashSet<DigestURL>();
        // workaround to prevent solr exception on existing index (not fully reindexed) since intro of schema with docvalues
        // org.apache.solr.core.SolrCore java.lang.IllegalStateException: unexpected docvalues type NONE for field 'load_date_dt' (expected=NUMERIC). Use UninvertingReader or index with docvalues.
        this.solrSortBy = null; // CollectionSchema.load_date_dt.getSolrFieldName() + " asc";
        this.chunksize = sb.getConfigInt(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 200);
    }

    /**
     * Set the query to select documents to recrawl
     * and resets the counter to start a fresh query loop
     * @param q select query
     * @param includefailedurls true=all http status docs are recrawled, false=httpstatus=200 docs are recrawled
     */
    public void setQuery(String q, boolean includefailedurls) {
        this.currentQuery = q;
        this.includefailed = includefailedurls;
        this.chunkstart = 0;
    }

    public String getQuery () {
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

    /**
     * feed urls to the local crawler
     * (Switchboard.addToCrawler() is not used here, as there existing urls are always skiped)
     *
     * @return true if urls were added/accepted to the crawler
     */
    private boolean feedToCrawler() {

        int added = 0;

        if (!this.urlstack.isEmpty()) {
            final CrawlProfile profile = sb.crawler.defaultTextSnippetGlobalProfile;

            for (DigestURL url : this.urlstack) {
                final Request request = sb.loader.request(url, true, true);
                String acceptedError = sb.crawlStacker.checkAcceptanceChangeable(url, profile, 0);
                if (!includefailed && acceptedError == null) { // skip check if failed docs to be included
                    acceptedError = sb.crawlStacker.checkAcceptanceInitially(url, profile);
                }
                if (acceptedError != null) {
                    ConcurrentLog.info(THREAD_NAME, "addToCrawler: cannot load " + url.toNormalform(true) + ": " + acceptedError);
                    continue;
                }
                final String s;
                s = sb.crawlQueues.noticeURL.push(NoticedURL.StackType.LOCAL, request, profile, sb.robots);

                if (s != null) {
                    ConcurrentLog.info(THREAD_NAME, "addToCrawler: failed to add " + url.toNormalform(true) + ": " + s);
                } else {
                    added++;
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
        if (sb.crawlQueues.coreCrawlJobSize() > this.chunksize) {
            return false;
        }

        boolean didSomething = false;
        if (this.urlstack.isEmpty()) {
        	if(!this.moreToRecrawl) {
        		/* We do not remove the thread from the Switchboard worker threads using serverSwitch.terminateThread(String,boolean),
        		 * because we want to be able to provide a report after its termination */
        		terminate(false);
        	} else {
        		this.moreToRecrawl = processSingleQuery();
        		/* Even if no more URLs are to recrawl, the job has done something by searching the Solr index */
        		didSomething = true;
        	}
        } else {
        	didSomething = feedToCrawler();
        }
        return didSomething;

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
        SolrConnector solrConnector = sb.index.fulltext().getDefaultConnector();
        if (solrConnector.isClosed()) {
        	this.urlsToRecrawl = 0;
        	return false;
        }
        
        try {
            // query all or only httpstatus=200 depending on includefailed flag
            docList = solrConnector.getDocumentListByQuery(RecrawlBusyThread.buildSelectionQuery(this.currentQuery, this.includefailed),
                this.solrSortBy, this.chunkstart, this.chunksize, CollectionSchema.sku.getSolrFieldName());
            this.urlsToRecrawl = docList.getNumFound();
        } catch (final Throwable e) {
        	this.urlsToRecrawl = 0;
        }

        if (docList != null) {
            for (final SolrDocument doc : docList) {
                try {
                    this.urlstack.add(new DigestURL((String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName())));
                } catch (MalformedURLException ex) {
                    try { // if index entry hasn't a valid url (useless), delete it
                        solrConnector.deleteById((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                        ConcurrentLog.severe(THREAD_NAME, "deleted index document with invalid url " + (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                    } catch (IOException ex1) {
                        ConcurrentLog.severe(THREAD_NAME, ex1.getMessage());
                    }
                }
            }
            this.chunkstart = this.chunkstart + this.chunksize;
        }
        
        if (docList == null || docList.size() < this.chunksize) {
            return false;
        }
        return true;
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

    @Override
    public void freemem() {
        this.urlstack.clear();
    }

}
