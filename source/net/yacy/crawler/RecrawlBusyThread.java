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
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * Selects documents by a query from the local index
 * and feeds the found urls to the crawler to recrawl the documents.
 * This is intended to keep the index up-to-date
 * Currently the doucments are selected by expired fresh_date_dt field
 * an added to the crawler in smaller chunks (see chunksize) as long as no other crawl is runnin.
 */
public class RecrawlBusyThread extends AbstractBusyThread {

    public final static String THREAD_NAME = "recrawlindex";

    public String currentQuery = CollectionSchema.fresh_date_dt.getSolrFieldName()+":[* TO NOW/DAY-1DAY]"; // current query
    private int chunkstart = 0;
    private int chunksize = 200;
    final Switchboard sb;
    private Set<DigestURL> urlstack; // buffer of urls to recrawl
    public long urlsfound = 0;

    public RecrawlBusyThread(Switchboard xsb) {
        super(3000, 1000); // set lower limits of cycle delay
        this.setIdleSleep(10*60000); // set actual cycle delays
        this.setBusySleep(2*60000);
        this.setPriority(Thread.MIN_PRIORITY);

        this.sb = xsb;
        urlstack = new HashSet<DigestURL>();
    }

    /**
     * feed urls to the local crawler
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
                if (acceptedError == null) {
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
        // other crawls are running, do nothing
        if (sb.crawlQueues.coreCrawlJobSize() > 0) {
            return false;
        }

        if (this.urlstack.isEmpty()) {
            return processSingleQuery();
        } else {
            return feedToCrawler();
        }

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
        if (!solrConnector.isClosed()) {
            try {
                docList = solrConnector.getDocumentListByQuery(currentQuery + " AND (" + CollectionSchema.httpstatus_i.name() + ":200)",
                        CollectionSchema.fresh_date_dt.getSolrFieldName() + " asc", this.chunkstart, this.chunksize, CollectionSchema.sku.getSolrFieldName());
                this.urlsfound = docList.getNumFound();
            } catch (Throwable e) {
                this.urlsfound = 0;
            }
        } else {
            this.urlsfound =0;
        }

        if (docList != null) {
            for (SolrDocument doc : docList) {
                try {
                    this.urlstack.add(new DigestURL((String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName())));
                } catch (MalformedURLException ex) {
                }
            }
            this.chunkstart = this.chunkstart + this.chunksize;
        }
        
        if (this.urlsfound <= this.chunkstart) {
            this.chunkstart = 0;
            return false;
            // TODO: add a stop condition
        }
        return true;
    }

    @Override
    public int getJobCount() {
        return this.urlstack.size();
    }

    @Override
    public void freemem() {
        this.urlstack.clear();
    }

}
