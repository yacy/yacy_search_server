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

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.LookAheadIterator;
import net.yacy.search.index.YaCySchema;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;

public abstract class AbstractSolrConnector implements SolrConnector {

    public final SolrDocument POISON_DOCUMENT = new SolrDocument();
    public final static String POISON_ID = "POISON_ID";
    public final static SolrQuery catchallQuery = new SolrQuery();
    static {
        catchallQuery.setQuery("*:*");
        catchallQuery.setFields(YaCySchema.id.name());
        catchallQuery.setRows(1);
        catchallQuery.setStart(0);
    }
    private final static int pagesize = 10;
    
    @Override
    public boolean exists(final String id) throws IOException {
        try {
            final SolrDocument doc = get(id);
            return doc != null;
        } catch (final Throwable e) {
            Log.logException(e);
            return false;
        }
    }

    @Override
    public BlockingQueue<SolrDocument> concurrentQuery(final String querystring, final int offset, final int maxcount, final long maxtime) {
        final BlockingQueue<SolrDocument> queue = new LinkedBlockingQueue<SolrDocument>();
        final long endtime = System.currentTimeMillis() + maxtime;
        final Thread t = new Thread() {
            @Override
            public void run() {
                int o = offset;
                while (System.currentTimeMillis() < endtime) {
                    try {
                        SolrDocumentList sdl = query(querystring, o, pagesize);
                        for (SolrDocument d: sdl) {
                            try {queue.put(d);} catch (InterruptedException e) {break;}
                        }
                        if (sdl.size() < pagesize) break;
                    } catch (SolrException e) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
                try {queue.put(AbstractSolrConnector.this.POISON_DOCUMENT);} catch (InterruptedException e1) {}
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
                        SolrDocumentList sdl = query(querystring, o, pagesize);
                        for (SolrDocument d: sdl) {
                            try {queue.put((String) d.getFieldValue(YaCySchema.id.name()));} catch (InterruptedException e) {break;}
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
