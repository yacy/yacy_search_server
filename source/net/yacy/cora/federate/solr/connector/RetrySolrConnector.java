/**
 *  SolrRetryConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 08.11.2011 at http://yacy.net
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yacy.cora.sorting.ReversibleScoreMap;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

public class RetrySolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final SolrConnector solrConnector;
    private final long retryMaxTime;

    public RetrySolrConnector(final SolrConnector solrConnector, final long retryMaxTime) {
        this.solrConnector = solrConnector;
        this.retryMaxTime = retryMaxTime;
    }

    @Override
    public int getCommitWithinMs() {
        return this.solrConnector.getCommitWithinMs();
    }

    /**
     * set the solr autocommit delay
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    @Override
    public void setCommitWithinMs(int c) {
        this.solrConnector.setCommitWithinMs(c);
    }

    @Override
    public void commit() {
        this.solrConnector.commit();
    }

    @Override
    public synchronized void close() {
        this.solrConnector.close();
    }

    @Override
    public void clear() throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.clear();
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public void delete(final String id) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.delete(id);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public void delete(final List<String> ids) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.delete(ids);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public int deleteByQuery(final String querystring) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.deleteByQuery(querystring);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return 0;
    }

    @Override
    public boolean exists(final String fieldName, final String key) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.exists(fieldName, key);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return false;
    }

    @Override
    public SolrDocument getById(final String key, final String ... fields) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.getById(key, fields);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return null;
    }

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.add(solrdoc);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        for (SolrInputDocument d: solrdocs) add(d);
    }

    @Override
    public SolrDocumentList query(final String querystring, final int offset, final int count, final String ... fields) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.query(querystring, offset, count, fields);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return null;
    }

    @Override
    public QueryResponse query(final ModifiableSolrParams query) throws IOException, SolrException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.query(query);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return null;
    }
    
    @Override
    public long getQueryCount(final String querystring) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.getQueryCount(querystring);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return 0;
    }

    @Override
    public Map<String, ReversibleScoreMap<String>> getFacets(final String query, final int maxresults, final String ... fields) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.getFacets(query, maxresults, fields);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return new HashMap<String, ReversibleScoreMap<String>>();
    }
    
    @Override
    public long getSize() {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.getSize();
        } catch (final Throwable e) {
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        return 0;
    }

}
