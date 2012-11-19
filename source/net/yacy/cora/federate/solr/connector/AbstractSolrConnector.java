/**
 *  AbstractSolrConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 27.06.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.federate.solr.connector;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.util.LookAheadIterator;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;

public abstract class AbstractSolrConnector implements SolrConnector {

    private final static Logger log = Logger.getLogger(AbstractSolrConnector.class);
    
    public final static SolrDocument POISON_DOCUMENT = new SolrDocument();
    public final static String POISON_ID = "POISON_ID";
    public final static SolrQuery catchallQuery = new SolrQuery();
    static {
        catchallQuery.setQuery("*:*");
        catchallQuery.setFields(YaCySchema.id.getSolrFieldName());
        catchallQuery.setRows(1);
        catchallQuery.setStart(0);
    }
    public final static SolrQuery catchSuccessQuery = new SolrQuery();
    static {
        catchSuccessQuery.setQuery("-" + YaCySchema.failreason_t.name() + ":[* TO *]");
        catchSuccessQuery.setFields(YaCySchema.id.getSolrFieldName());
        catchSuccessQuery.setRows(1);
        catchSuccessQuery.setStart(0);
    }
    private final static int pagesize = 100;
    
    @Override
    public boolean exists(final String id) throws IOException {
        try {
            final SolrDocument doc = get(id, YaCySchema.id.getSolrFieldName());
            return doc != null;
        } catch (final Throwable e) {
            log.warn(e);
            return false;
        }
    }
    
    /**
     * Get a query result from solr as a stream of documents.
     * The result queue is considered as terminated if AbstractSolrConnector.POISON_DOCUMENT is returned.
     * The method returns immediately and feeds the search results into the queue
     * @param querystring the solr query string
     * @param offset first result offset
     * @param maxcount the maximum number of results
     * @param maxtime the maximum time in milliseconds
     * @param buffersize the size of an ArrayBlockingQueue; if <= 0 then a LinkedBlockingQueue is used
     * @return a blocking queue which is terminated  with AbstractSolrConnector.POISON_DOCUMENT as last element
     */
    @Override
    public BlockingQueue<SolrDocument> concurrentQuery(final String querystring, final int offset, final int maxcount, final long maxtime, final int buffersize, final String ... fields) {
        final BlockingQueue<SolrDocument> queue = buffersize <= 0 ? new LinkedBlockingQueue<SolrDocument>() : new ArrayBlockingQueue<SolrDocument>(buffersize);
        final long endtime = System.currentTimeMillis() + maxtime;
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = query(querystring, o, pagesize, fields);
                        for (SolrDocument d: sdl) {
                            try {queue.put(d);} catch (InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                        o += pagesize;
                    } catch (SolrException e) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_DOCUMENT);} catch (InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public BlockingQueue<String> concurrentIDs(final String querystring, final int offset, final int maxcount, final long maxtime) {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
        final long endtime = System.currentTimeMillis() + maxtime;
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = query(querystring, o, pagesize, YaCySchema.id.getSolrFieldName());
                        for (SolrDocument d: sdl) {
                            try {queue.put((String) d.getFieldValue(YaCySchema.id.getSolrFieldName()));} catch (InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                        o += pagesize;
                    } catch (SolrException e) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.POISON_ID);} catch (InterruptedException e1) {}
            }
        };
        t.start();
        return queue;
    }

    @Override
    public Iterator<String> iterator() {
        final BlockingQueue<String> queue = concurrentIDs("*:*", 0, Integer.MAX_VALUE, 60000);
        return new LookAheadIterator<String>() {
            @Override
            protected String next0() {
                try {
                    String s = queue.poll(60000, TimeUnit.MILLISECONDS);
                    if (s == AbstractSolrConnector.POISON_ID) return null;
                    return s;
                } catch (InterruptedException e) {
                    return null;
                }
            }

        };
    }
}
