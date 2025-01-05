/**
 *  LongLiteral
 *  Copyright 2014 by Michael Peter Christen
 *  First released 24.10.2014 at https://yacy.net
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

package net.yacy.cora.federate.solr.logic;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.federate.solr.SchemaDeclaration;

public class LongLiteral extends Literal implements Term {

    private SchemaDeclaration key;
    private long value;
    
    public LongLiteral(final SchemaDeclaration key, final long value) {
        super();
        this.key = key;
        this.value = value;
    }

    @Override
    public Object clone() {
        return new LongLiteral(this.key, this.value);
    }

    @Override
    public boolean equals(Object otherTerm) {
        if (!(otherTerm instanceof LongLiteral)) return false;
        LongLiteral o = (LongLiteral) otherTerm;
        return this.key.equals(o.key) && this.value == o.value;
    }
    
    @Override
    public int hashCode() {
        return this.key.hashCode() + (int) (this.value & Integer.MAX_VALUE);
    }
    
    /**
     * create a Solr query string from this literal
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.key.getSolrFieldName());
        sb.append(':').append('"').append(this.value).append('"'); // quotes must be used for negative values
        return sb.toString();
    }
    
    /**
     * check if the key/value pair of this literal occurs in the SolrDocument
     * @param doc the document to match to this literal
     * @return true, if the key of this literal is contained in the document and the
     *   value equals (does not equal) with the value if this literal (if the signature is false)
     */
    @Override
    public boolean matches(SolrDocument doc) {
        Object v = doc.getFieldValue(this.key.getSolrFieldName());
        if (v == null) return false;
        return v.toString().matches(Long.toString(this.value));
    }
    
}