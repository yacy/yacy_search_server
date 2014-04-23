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
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.Configuration;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
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
            } catch (final IOException ex) {
                log.warn(ex);
            }
        }
    }
    
    /**
     * Convert a SolrDocument to a SolrInputDocument.
     * This is useful if a document from the search index shall be modified and indexed again.
     * This shall be used as replacement of ClientUtils.toSolrInputDocument because we remove some fields
     * which are created automatically during the indexing process.
     * @param doc the solr document
     * @return a solr input document
     */
    public SolrInputDocument toSolrInputDocument(final SolrDocument doc, Set<String> omitFields) {
        SolrInputDocument sid = new SolrInputDocument();
        for (String name: doc.getFieldNames()) {
            if (this.contains(name) && (omitFields == null || !omitFields.contains(name))) { // check each field if enabled in local Solr schema
                sid.addField(name, doc.getFieldValue(name), 1.0f);
            }
        }
        return sid;
    }
    
    public SolrDocument toSolrDocument(final SolrInputDocument doc, Set<String> omitFields) {
        SolrDocument sd = new SolrDocument();
        for (SolrInputField field: doc) {
            if (this.contains(field.getName()) && (omitFields == null || !omitFields.contains(field.getName()))) { // check each field if enabled in local Solr schema
                sd.setField(field.getName(), field.getValue());
            }
        }
        return sd;
    }
    
    public boolean postprocessing_doublecontent(Segment segment, Set<String> uniqueURLs, SolrInputDocument sid, DigestURL url) {
        boolean changed = false;
        // FIND OUT IF THIS IS A DOUBLE DOCUMENT
        String hostid = url.hosthash();
        for (CollectionSchema[] checkfields: new CollectionSchema[][]{
                {CollectionSchema.exact_signature_l, CollectionSchema.exact_signature_unique_b, CollectionSchema.exact_signature_copycount_i},
                {CollectionSchema.fuzzy_signature_l, CollectionSchema.fuzzy_signature_unique_b, CollectionSchema.fuzzy_signature_copycount_i}}) {
            CollectionSchema checkfield = checkfields[0];
            CollectionSchema uniquefield = checkfields[1];
            CollectionSchema countfield = checkfields[2];
            if (this.contains(checkfield) && this.contains(uniquefield)) {
                // lookup the document with the same signature
                long signature = ((Long) sid.getField(checkfield.getSolrFieldName()).getValue()).longValue();
                try {
                    long count = segment.fulltext().getDefaultConnector().getCountByQuery(CollectionSchema.host_id_s + ":\"" + hostid + "\" AND " + checkfield.getSolrFieldName() + ":\"" + Long.toString(signature) + "\"");
                    if (count > 1) {
                        String urlhash = ASCII.String(url.hash());
                        if (uniqueURLs.contains(urlhash)) {
                            // this is not the first appearance, therefore this is a non-unique document
                            sid.setField(uniquefield.getSolrFieldName(), false);
                        } else {
                            // this is the first appearance, therefore this shall be treated as unique document
                            sid.setField(uniquefield.getSolrFieldName(), true);
                            uniqueURLs.add(urlhash);
                        }
                        sid.setField(countfield.getSolrFieldName(), count);
                        changed = true;
                    }
                } catch (final IOException e) {}
            }
        }
        
        // CHECK IF TITLE AND DESCRIPTION IS UNIQUE (this is by default not switched on)
        if (segment.fulltext().getDefaultConfiguration().contains(CollectionSchema.host_id_s)) {
            uniquecheck: for (CollectionSchema[] checkfields: new CollectionSchema[][]{
                    {CollectionSchema.title, CollectionSchema.title_exact_signature_l, CollectionSchema.title_unique_b},
                    {CollectionSchema.description_txt, CollectionSchema.description_exact_signature_l, CollectionSchema.description_unique_b}}) {
                CollectionSchema checkfield = checkfields[0];
                CollectionSchema signaturefield = checkfields[1];
                CollectionSchema uniquefield = checkfields[2];
                if (this.contains(checkfield) && this.contains(signaturefield) && this.contains(uniquefield)) {
                    // lookup in the index within the same hosts for the same title or description
                    //String checkstring = checkfield == CollectionSchema.title ? document.dc_title() : document.dc_description();
                    Long checkhash = (Long) sid.getFieldValue(signaturefield.getSolrFieldName());
                    if (checkhash == null) {
                        sid.setField(uniquefield.getSolrFieldName(), false);
                        changed = true;
                        continue uniquecheck;
                    }
                    try {
                        final SolrDocumentList docs = segment.fulltext().getDefaultConnector().getDocumentListByQuery(CollectionSchema.host_id_s + ":\"" + hostid + "\" AND " + signaturefield.getSolrFieldName() + ":\"" + checkhash.toString() + "\"", null, 0, 1);
                        if (docs != null && !docs.isEmpty()) {
                            SolrDocument doc = docs.get(0);
                            // switch unique attribute in new document
                            sid.setField(uniquefield.getSolrFieldName(), false);
                            // switch attribute in existing document
                            SolrInputDocument sidContext = segment.fulltext().getDefaultConfiguration().toSolrInputDocument(doc);
                            sidContext.setField(uniquefield.getSolrFieldName(), false);
                            segment.putDocument(sidContext);
                            changed = true;
                        } else {
                            sid.setField(uniquefield.getSolrFieldName(), true);
                        }
                    } catch (final IOException e) {}
                }
            }
        }
        return changed;
    }

    public boolean postprocessing_references(final ReferenceReportCache rrCache, final SolrInputDocument sid, final DigestURL url, final Map<String, Long> hostExtentCount) {
        if (!(this.contains(CollectionSchema.references_i) ||
              this.contains(CollectionSchema.references_internal_i) ||
              this.contains(CollectionSchema.references_external_i) || this.contains(CollectionSchema.references_exthosts_i))) return false;
        Integer all_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_i.getSolrFieldName());
        Integer internal_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_internal_i.getSolrFieldName());
        Integer external_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_external_i.getSolrFieldName());
        Integer exthosts_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.references_exthosts_i.getSolrFieldName());
        Integer hostextc_old = sid == null ? null : (Integer) sid.getFieldValue(CollectionSchema.host_extent_i.getSolrFieldName());
        try {
            ReferenceReport rr = rrCache.getReferenceReport(ASCII.String(url.hash()), false);
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
        } catch (final IOException e) {
        }
        return false;
    }
    
    public boolean contains(SchemaDeclaration field) {
        return this.contains(field.getSolrFieldName());
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
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final long value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final boolean value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if (isEmpty() || contains(key)) key.add(doc, value);
    }

    public static Date getDate(SolrInputDocument doc, final SchemaDeclaration key) {
        Date x = (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

}
