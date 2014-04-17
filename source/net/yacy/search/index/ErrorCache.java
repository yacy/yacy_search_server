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
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class ErrorCache {

    private static final ConcurrentLog log = new ConcurrentLog("REJECTED");
    private static final int maxStackSize = 1000;

    // the class object
    private final Map<String, CollectionConfiguration.FailDoc> cache;
    private final Fulltext fulltext;

    public ErrorCache(final Fulltext fulltext) {
        this.fulltext = fulltext;
        this.cache = new LinkedHashMap<String, CollectionConfiguration.FailDoc>();
        try {
            // fill stack with latest values
            final SolrQuery params = new SolrQuery();
            params.setParam("defType", "edismax");
            params.setStart(0);
            params.setRows(100);
            params.setFacet(false);
            params.setSort(new SortClause(CollectionSchema.last_modified.getSolrFieldName(), SolrQuery.ORDER.desc));
            params.setFields(CollectionSchema.id.getSolrFieldName());
            params.setQuery(CollectionSchema.failreason_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);
            params.set(CommonParams.DF, CollectionSchema.id.getSolrFieldName()); // DisMaxParams.QF or CommonParams.DF must be given
            SolrDocumentList docList =  fulltext.getDefaultConnector().getDocumentListByParams(params);
            if (docList != null) for (int i = docList.size() - 1; i >= 0; i--) {
                SolrDocument doc = docList.get(i);
                String hash = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                this.cache.put(hash, null);
            }
        } catch (final Throwable e) {
        }
    }

    public void clearCache() {
        if (this.cache != null) synchronized (this.cache) {this.cache.clear();}
    }

    public void clear() throws IOException {
        clearCache();
        this.fulltext.getDefaultConnector().deleteByQuery(CollectionSchema.failreason_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);
    }

    public void removeHosts(final Set<String> hosthashes) {
        if (hosthashes == null || hosthashes.size() == 0) return;
        this.fulltext.deleteDomainErrors(hosthashes);
        synchronized (this.cache) {
            Iterator<String> i = ErrorCache.this.cache.keySet().iterator();
            while (i.hasNext()) {
                String b = i.next();
                if (hosthashes.contains(b)) i.remove();
            }
        }
    }

    public void push(final DigestURL url, final int crawldepth, final CrawlProfile profile, final FailCategory failCategory, String anycause, final int httpcode) {
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
                httpcode, crawldepth);
        if (this.fulltext.getDefaultConnector() != null && failCategory.store) {
            // send the error to solr
            try {
                // do not overwrite error reports with error reports
                SolrDocument olddoc = this.fulltext.getDefaultConnector().getDocumentById(ASCII.String(failDoc.getDigestURL().hash()), CollectionSchema.httpstatus_i.getSolrFieldName());
                if (olddoc == null ||
                    olddoc.getFieldValue(CollectionSchema.httpstatus_i.getSolrFieldName()) == null ||
                    ((Integer) olddoc.getFieldValue(CollectionSchema.httpstatus_i.getSolrFieldName())) == 200) {
                    SolrInputDocument errorDoc = failDoc.toSolr(this.fulltext.getDefaultConfiguration());
                    this.fulltext.getDefaultConnector().add(errorDoc);
                }
            } catch (final IOException e) {
                ConcurrentLog.warn("SOLR", "failed to send error " + url.toNormalform(true) + " to solr: " + e.getMessage());
            }
            synchronized (this.cache) {
                this.cache.put(ASCII.String(url.hash()), null);
            }
        } else {
            synchronized (this.cache) {
                this.cache.put(ASCII.String(url.hash()), failDoc);
            }
        }
        checkStackSize();
    }
    
    private void checkStackSize() {
        synchronized (this.cache) {
            int dc = this.cache.size() - maxStackSize;
            if (dc > 0) {
                Collection<String> d = new ArrayList<String>();
                Iterator<String> i = this.cache.keySet().iterator();
                while (dc-- > 0 && i.hasNext()) d.add(i.next());
                for (String s: d) this.cache.remove(s);
            }
        }        
    }

    public ArrayList<CollectionConfiguration.FailDoc> list(int max) {
        final ArrayList<CollectionConfiguration.FailDoc> l = new ArrayList<CollectionConfiguration.FailDoc>();
        synchronized (this.cache) {
            Iterator<Map.Entry<String, CollectionConfiguration.FailDoc>> hi = this.cache.entrySet().iterator();
            for (int i = 0; i < this.cache.size() - max; i++) hi.next();
            while (hi.hasNext()) {
                try {
                    Map.Entry<String, CollectionConfiguration.FailDoc> entry = hi.next();
                    String hash = entry.getKey();
                    CollectionConfiguration.FailDoc failDoc = entry.getValue();
                    if (failDoc == null) {
                        SolrDocument doc = this.fulltext.getDefaultConnector().getDocumentById(hash);
                        if (doc != null) failDoc = new CollectionConfiguration.FailDoc(doc);
                    }
                    if (failDoc != null) l.add(failDoc);
                } catch (IOException e) {
                }
            }
        }
        return l;
    }
    
    public CollectionConfiguration.FailDoc get(final String urlhash) {
        CollectionConfiguration.FailDoc failDoc = null;
        synchronized (this.cache) {
            failDoc = this.cache.get(urlhash);
        }
        if (failDoc != null) return failDoc;
        try {
            final SolrDocumentList docs = this.fulltext.getDefaultConnector().getDocumentListByQuery(CollectionSchema.id + ":\"" + urlhash + "\" AND " + CollectionSchema.failtype_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM, null, 0, 1);
            if (docs == null || docs.isEmpty()) return null;
            SolrDocument doc = docs.get(0);
            if (doc == null) return null;
            return new CollectionConfiguration.FailDoc(doc);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    public boolean exists(final byte[] urlHash) {
        String urlHashString = ASCII.String(urlHash);
        try {
            // first try to check if the document exists at all.
            long loaddate = this.fulltext.getLoadTime(urlHashString);
            if (loaddate < 0) return false;
            // then load the fail reason, if exists
            final SolrDocument doc = this.fulltext.getDefaultConnector().getDocumentById(urlHashString, CollectionSchema.failreason_s.getSolrFieldName());
            if (doc == null) return false;
            // check if the document contains a value in the field CollectionSchema.failreason_s
            Object failreason = doc.getFieldValue(CollectionSchema.failreason_s.getSolrFieldName());
            return failreason != null && failreason.toString().length() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public void clearStack() {
        synchronized (this.cache) {
            this.cache.clear();
        }
    }

    public int stackSize() {
        synchronized (this.cache) {
            return this.cache.size();
        }
    }

}


