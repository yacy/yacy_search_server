/**
 *  CatchallLiteral
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
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;

public class CatchallLiteral extends Literal implements Term {

    private SchemaDeclaration key;
    
    public CatchallLiteral(final SchemaDeclaration key) {
        super();
        this.key = key;
    }

    @Override
    public Object clone() {
        return new CatchallLiteral(this.key);
    }

    @Override
    public boolean equals(Object otherTerm) {
        if (!(otherTerm instanceof CatchallLiteral)) return false;
        CatchallLiteral o = (CatchallLiteral) otherTerm;
        return this.key.equals(o.key);
    }
    
    @Override
    public int hashCode() {
        return this.key.hashCode();
    }
    
    /**
     * create a Solr query string from this literal
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.key.getSolrFieldName());
        sb.append(AbstractSolrConnector.CATCHALL_DTERM);
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
        if (v == null) return false; // this does not match if the field is missing
        return true;
    }
    
}