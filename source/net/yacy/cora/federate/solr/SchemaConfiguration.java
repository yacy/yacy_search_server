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
import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.storage.Configuration;

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
