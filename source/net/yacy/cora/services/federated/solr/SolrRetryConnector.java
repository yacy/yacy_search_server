/**
 *  SolrRetryConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 08.11.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
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
import java.util.List;

import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

public class SolrRetryConnector implements SolrConnector {

    private final SolrConnector solrConnector;
    private final long retryMaxTime;

    public SolrRetryConnector(final SolrConnector solrConnector, final long retryMaxTime) {
        this.solrConnector = solrConnector;
        this.retryMaxTime = retryMaxTime;
    }

    @Override
    public SolrScheme getScheme() {
        return this.solrConnector.getScheme();
    }

    @Override
    public void close() {
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
    public void add(final String id, final ResponseHeader header, final Document doc) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.add(id, header, doc);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
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
    public void err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            this.solrConnector.err(digestURI, failReason, httpstatus);
            return;
        } catch (final Throwable e) {
            ee = e;
            try {Thread.sleep(10);} catch (final InterruptedException e1) {}
            continue;
        }
        if (ee != null) throw (ee instanceof IOException) ? (IOException) ee : new IOException(ee.getMessage());
    }

    @Override
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException {
        final long t = System.currentTimeMillis() + this.retryMaxTime;
        Throwable ee = null;
        while (System.currentTimeMillis() < t) try {
            return this.solrConnector.get(querystring, offset, count);
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
            continue;
        }
        return 0;
    }

}
