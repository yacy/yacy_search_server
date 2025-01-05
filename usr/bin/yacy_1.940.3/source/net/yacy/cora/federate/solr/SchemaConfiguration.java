/**
 *  SchemaConfiguration
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at https://yacy.net
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
import java.util.Set;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.params.MultiMapSolrParams;

import net.yacy.cora.storage.Configuration;
import net.yacy.cora.util.ConcurrentLog;

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
                sid.addField(name, doc.getFieldValue(name));
            }
        }
        return sid;
    }
    
    public SolrInputDocument toSolrInputDocument(final MultiMapSolrParams params) {
        SolrInputDocument sid = new SolrInputDocument();
        for (String name: params.getMap().keySet()) {
            if (this.contains(name)) { // check each field if enabled in local Solr schema
                sid.addField(name, params.getParams(name));
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
    
    public boolean contains(SchemaDeclaration field) {
        return this.contains(field.getSolrFieldName());
    }

    public void remove(final SolrInputDocument doc, final SchemaDeclaration key) {
        key.remove(doc);
    }
    
    public void remove(final SolrInputDocument doc, final String key) {
        doc.removeField(key);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final String value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && !value.isEmpty()))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Date value) {
        assert !key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.getTime() > 0))) key.add(doc, value);
    }

    public void add(final SolrInputDocument doc, final SchemaDeclaration key, final Date[] value) {
        assert key.isMultiValued() : "key = " + key.getSolrFieldName();
        if ((isEmpty() || contains(key)) && (!this.lazy || (value != null && value.length > 0))) key.add(doc, value);
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

}
