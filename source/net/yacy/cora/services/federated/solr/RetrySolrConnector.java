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

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

public class RetrySolrConnector implements SolrConnector {

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
    public void deleteByQuery(final String querystring) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.deleteByQuery(querystring);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public boolean exists(final String id) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.exists(id);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return false;
    }

	@Override
	public SolrDocument get(String id) throws IOException {
		final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.get(id);
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
    public SolrDocumentList query(final String querystring, final int offset, final int count) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.query(querystring, offset, count);
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
        return null;
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
