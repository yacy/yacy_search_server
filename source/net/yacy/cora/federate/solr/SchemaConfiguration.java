/**
 *  SchemaConfiguration
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at http://yacy.net
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

package net.yacy.cora.federate.solr;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.index.Segment;
import net.yacy.search.index.Segment.ReferenceReport;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.search.schema.CollectionSchema;

public class SchemaConfiguration extends Configuration implements Serializable {

    private final static long serialVersionUID=-5961730809008841258L;
    private final static ConcurrentLog log = new ConcurrentLog(SchemaConfiguration.class.getName());
   

    public SchemaConfiguration() {
        super();
    }

    public SchemaConfiguration(final File file) throws IOException {
        super(file);
    }

    public void fill(final SchemaConfiguration other, final boolean defaultActivated) {
        final Iterator<Entry> i = other.entryIterator();
        Entry e, enew = null;
        while (i.hasNext()) {
            e = i.next();
            if (contains(e.key()) || containsDisabled(e.key())) continue;
            // add as new entry
            enew = new Entry(e.key(),e.getValue(),defaultActivated && e.enabled());
            enew.setComment(e.getComment());
            this.put(e.key(),enew);
        }
        if (enew != null) {
            try {
                commit();
            } catch (IOException ex) {
                log.warn(ex);
            }
        }
    }

    public boolean postprocessing_clickdepth(Segment segment, SolrDocument doc, SolrInputDocument sid, DigestURI url, SchemaDeclaration clickdepthfield) {
        if (!this.contains(clickdepthfield)) return false;
        // get new click depth and compare with old
        Integer oldclickdepth = (Integer) doc.getFieldValue(clickdepthfield.getSolrFieldName());
        if (oldclickdepth != null && oldclickdepth.intValue() != 999) return false; // we do not want to compute that again
        try {
            int clickdepth = segment.getClickDepth(url);
            if (oldclickdepth == null || oldclickdepth.intValue() != clickdepth) {
                sid.setField(clickdepthfield.getSolrFieldName(), clickdepth);
                return true;
            }
        } catch (IOException e) {
        }
        return false;
    }

    public boolean postprocessing_references(ReferenceReportCache rrCache, SolrDocument doc, SolrInputDocument sid, DigestURI url, Map<String, Long> hostExtentCount) {
        if (!(this.contains(CollectionSchema.references_i) ||
              this.contains(CollectionSchema.references_internal_i) ||
              this.contains(CollectionSchema.references_external_i) || this.contains(CollectionSchema.references_exthosts_i))) return false;
        Integer all_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_i.getSolrFieldName());
        Integer internal_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_internal_i.getSolrFieldName());
        Integer external_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_external_i.getSolrFieldName());
        Integer exthosts_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_exthosts_i.getSolrFieldName());
        Integer hostextc_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.host_extent_i.getSolrFieldName());
        try {
            ReferenceReport rr = rrCache.getReferenceReport(url.hash(), false);
            List<String> internalIDs = new ArrayList<String>();
            HandleSet iids = rr.getInternallIDs();
            for (byte[] b: iids) internalIDs.add(ASCII.String(b));
            
            boolean change = false;
            int all = rr.getExternalCount() + rr.getInternalCount();
            if (this.contains(CollectionSchema.references_i) &&
                (all_old == null || all_old.intValue() != all)) {
                sid.setField(CollectionSchema.references_i.getSolrFieldName(), all);
                change = true;
            }
            if (this.contains(CollectionSchema.references_internal_i) &&
                (internal_old == null || internal_old.intValue() != rr.getInternalCount())) {
                sid.setField(CollectionSchema.references_internal_i.getSolrFieldName(), rr.getInternalCount());
                change = true;
            }
            if (this.contains(CollectionSchema.references_external_i) &&
                (external_old == null || external_old.intValue() != rr.getExternalCount())) {
                sid.setField(CollectionSchema.references_external_i.getSolrFieldName(), rr.getExternalCount());
                change = true;
            }
            if (this.contains(CollectionSchema.references_exthosts_i) &&
                (exthosts_old == null || exthosts_old.intValue() != rr.getExternalHostIDs().size())) {
                sid.setField(CollectionSchema.references_exthosts_i.getSolrFieldName(), rr.getExternalHostIDs().size());
                change = true;
            }
            Long hostExtent = hostExtentCount == null ? Integer.MAX_VALUE : hostExtentCount.get(url.hosthash());
            if (this.contains(CollectionSchema.host_extent_i) &&
                (hostextc_old == null || hostextc_old.intValue() != hostExtent)) {
                sid.setField(CollectionSchema.host_extent_i.getSolrFieldName(), hostExtent.intValue());
                change = true;
            }
            return change;
        } catch (IOException e) {
        }
        return false;
    }
    
    public boolean contains(SchemaDeclaration field) {
        return this.contains(field.name());
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Date value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String[] value) {
        assert key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Integer[] value) {
        assert key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final List<?> values) {
        assert key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (values != null && !values.isEmpty()))) key.add(doc, values);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final int value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final long value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final boolean value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || value)) key.add(doc, value);
    }

    public static Date getDate(SolrInputDocument doc, final SchemaDeclaration key) {
        Date x = (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

}
