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
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;

public class SchemaConfiguration extends Configuration implements Serializable {

    private final static long serialVersionUID=-5961730809008841258L;
    private final static Logger log = Logger.getLogger(SchemaConfiguration.class);
   

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

    public boolean postprocessing_references(Segment segment, SolrDocument doc, SolrInputDocument sid, DigestURI url) {
        if (!(this.contains(CollectionSchema.references_i) || this.contains(CollectionSchema.references_internal_i) ||
              this.contains(CollectionSchema.references_external_i) || this.contains(CollectionSchema.references_exthosts_i))) return false;
        Integer all_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_i.getSolrFieldName());
        Integer internal_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_internal_i.getSolrFieldName());
        Integer external_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_external_i.getSolrFieldName());
        Integer exthosts_old = doc == null ? null : (Integer) doc.getFieldValue(CollectionSchema.references_exthosts_i.getSolrFieldName());
        ReferenceContainer<CitationReference> references;
        try {
            int all = 0, internal = 0, external = 0;
            references = segment.urlCitation().get(url.hash(), null);
            if (references == null) return false; // no references at all
            //int references = segment.urlCitation().count(url.hash());
            byte[] uh0 = url.hash();
            Iterator<CitationReference> ri = references.entries();
            HandleSet exthosts = new RowHandleSet(6, Base64Order.enhancedCoder, 0);
            while (ri.hasNext()) {
                CitationReference ref = ri.next();
                byte[] hh = ref.hosthash();
                exthosts.put(hh);
                all++;
                if (ByteBuffer.equals(hh, 0, uh0, 6, 6)) internal++; else external++;
            }
            
            boolean change = false;
            if (all_old == null || all_old.intValue() != all) {
                sid.setField(CollectionSchema.references_i.getSolrFieldName(), all);
                change = true;
            }
            if (internal_old == null || internal_old.intValue() != internal) {
                sid.setField(CollectionSchema.references_internal_i.getSolrFieldName(), internal);
                change = true;
            }
            if (external_old == null || external_old.intValue() != external) {
                sid.setField(CollectionSchema.references_external_i.getSolrFieldName(), external);
                change = true;
            }
            if (exthosts_old == null || exthosts_old.intValue() != exthosts.size()) {
                sid.setField(CollectionSchema.references_exthosts_i.getSolrFieldName(), exthosts.size());
                change = true;
            }
            return change;
        } catch (IOException e) {
        } catch (SpaceExceededException e) {
        }
        return false;
    }
    
    public boolean contains(SchemaDeclaration field) {
        return this.contains(field.name());
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Date value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Integer[] value) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final List<?> values) {
        assert key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || (values != null && !values.isEmpty()))) key.add(doc, values);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final int value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final long value) {
        assert !key.isMultiValued();
        if ((isEmpty() || contains(key)) && (!this.lazy || value != 0)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final boolean value) {
        assert !key.isMultiValued();
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public static Date getDate(SolrInputDocument doc, final SchemaDeclaration key) {
        Date x = (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

}
