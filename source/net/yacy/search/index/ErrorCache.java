/**
 *  ErrorCache
 *  Copyright 2013 by Michael Peter Christen
 *  First released 17.10.2013 at http://yacy.net
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

package net.yacy.search.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class ErrorCache {

    private static final ConcurrentLog log = new ConcurrentLog("REJECTED");
    private static final int maxStackSize = 1000;

    // the class object
    private final Map<String, CollectionConfiguration.FailDoc> stack;
    private final Fulltext fulltext;

    public ErrorCache(final Fulltext fulltext) {
        this.fulltext = fulltext;
        this.stack = new LinkedHashMap<String, CollectionConfiguration.FailDoc>();
        try {
            // fill stack with latest values
            final SolrQuery params = new SolrQuery();
            params.setParam("defType", "edismax");
            params.setStart(0);
            params.setRows(100);
            params.setFacet(false);
            params.setSort(new SortClause(CollectionSchema.last_modified.getSolrFieldName(), SolrQuery.ORDER.desc));
            params.setFacet(false);
            params.setQuery(CollectionSchema.failreason_s.getSolrFieldName() + ":[* TO *]");
            QueryResponse rsp = fulltext.getDefaultConnector().getResponseByParams(params);
            SolrDocumentList docList = rsp == null ? null : rsp.getResults();
            if (docList != null) for (int i = docList.size() - 1; i >= 0; i--) {
                CollectionConfiguration.FailDoc failDoc = new CollectionConfiguration.FailDoc(docList.get(i));
                this.stack.put(ASCII.String(failDoc.getDigestURL().hash()), failDoc);
            }
        } catch (final Throwable e) {
        }
    }

    public void clear() throws IOException {
        if (this.stack != null) synchronized (this.stack) {this.stack.clear();}
        this.fulltext.getDefaultConnector().deleteByQuery(CollectionSchema.failreason_s.getSolrFieldName() + ":[* TO *]");
    }

    public void removeHosts(final Set<String> hosthashes) {
        if (hosthashes == null || hosthashes.size() == 0) return;
        this.fulltext.deleteDomainErrors(hosthashes);
        synchronized (this.stack) {
            Iterator<String> i = ErrorCache.this.stack.keySet().iterator();
            while (i.hasNext()) {
                String b = i.next();
                if (hosthashes.contains(b)) i.remove();
            }
        }
    }

    public void push(final DigestURL url, final CrawlProfile profile, final FailCategory failCategory, String anycause, final int httpcode) {
        // assert executor != null; // null == proxy !
        assert failCategory.store || httpcode == -1 : "failCategory=" + failCategory.name();
        if (exists(url.hash()))
            return; // don't insert double causes
        if (anycause == null) anycause = "unknown";
        final String reason = anycause + ((httpcode >= 0) ? " (http return code = " + httpcode + ")" : "");
        if (!reason.startsWith("double")) log.info(url.toNormalform(true) + " - " + reason);
        CollectionConfiguration.FailDoc failDoc = new CollectionConfiguration.FailDoc(
                url, profile == null ? null : profile.collections(),
                failCategory.name() + " " + reason, failCategory.failType,
                httpcode);
        synchronized (this.stack) {
            this.stack.put(ASCII.String(url.hash()), failDoc);
        }
        if (this.fulltext.getDefaultConnector() != null && failCategory.store) {
            // send the error to solr
            try {
                SolrInputDocument errorDoc = failDoc.toSolr(this.fulltext.getDefaultConfiguration());
                this.fulltext.getDefaultConnector().add(errorDoc);
            } catch (final IOException e) {
                ConcurrentLog.warn("SOLR", "failed to send error " + url.toNormalform(true) + " to solr: " + e.getMessage());
            }
        }
        checkStackSize();
    }
    
    private void checkStackSize() {
        synchronized (this.stack) {
            int dc = this.stack.size() - maxStackSize;
            if (dc > 0) {
                Collection<String> d = new ArrayList<String>();
                Iterator<String> i = this.stack.keySet().iterator();
                while (dc-- > 0 && i.hasNext()) d.add(i.next());
                for (String s: d) this.stack.remove(s);
            }
        }        
    }

    public ArrayList<CollectionConfiguration.FailDoc> list(int max) {
        final ArrayList<CollectionConfiguration.FailDoc> l = new ArrayList<CollectionConfiguration.FailDoc>();
        synchronized (this.stack) {
            Iterator<CollectionConfiguration.FailDoc> fdi = this.stack.values().iterator();
            for (int i = 0; i < this.stack.size() - max; i++) fdi.next();
            while (fdi.hasNext()) l.add(fdi.next());
        }
        return l;
    }

    public CollectionConfiguration.FailDoc get(final String urlhash) {
        CollectionConfiguration.FailDoc fd;
        synchronized (this.stack) {
            fd = this.stack.get(urlhash);
        }
        if (fd != null) return fd;
        try {
            SolrDocument doc = this.fulltext.getDefaultConnector().getDocumentById(urlhash);
            if (doc == null) return null;
            return new CollectionConfiguration.FailDoc(doc);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    public boolean exists(final byte[] urlHash) {
        try {
            return this.fulltext.getDefaultConnector().existsByQuery(CollectionSchema.id.getSolrFieldName() + ":\"" + ASCII.String(urlHash) + "\" AND " + CollectionSchema.failreason_s.getSolrFieldName() + ":[* TO *]");
        } catch (IOException e) {
            return false;
        }
    }

    public void clearStack() {
        synchronized (this.stack) {
            this.stack.clear();
        }
    }

    public int stackSize() {
        synchronized (this.stack) {
            return this.stack.size();
        }
    }

}

