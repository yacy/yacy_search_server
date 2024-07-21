/**
 *  StringLiteral
 *  Copyright 2014 by Michael Peter Christen
 *  First released 03.08.2014 at https://yacy.net
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
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;

public class StringLiteral extends Literal implements Term {

    private SchemaDeclaration key;
    private String value;
    
    public StringLiteral(final SchemaDeclaration key, final String value) {
        super();
        this.key = key;
        this.value = value;
    }

    @Override
    public Object clone() {
        return new StringLiteral(this.key, this.value);
    }

    @Override
    public boolean equals(Object otherTerm) {
        if (!(otherTerm instanceof StringLiteral)) return false;
        StringLiteral o = (StringLiteral) otherTerm;
        return this.key.equals(o.key) && this.value.equals(o.value);
    }
    
    @Override
    public int hashCode() {
        return key.hashCode() + value.hashCode();
    }
    
    /**
     * create a Solr query string from this literal
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.key.getSolrFieldName());
        sb.append(':').append('"').append(this.value).append('"');
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
        return this.value.equals(AbstractSolrConnector.CATCHALL_TERM) || v.toString().matches(this.value);
    }
    
}
