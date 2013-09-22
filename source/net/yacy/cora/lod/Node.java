/**
 *  AbstractScoreMap
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.lod;

import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.lod.vocabulary.Rdf;

/**
 * class for a RDF node element. For a short primer see
 * http://www.w3.org/TR/REC-rdf-syntax/
 */
public class Node extends HashMap<String, byte[]> implements Map<String, byte[]> {

    private static final long serialVersionUID = -6715118942251224832L;
    
    public static final String SUBJECT = "rdf:about";

    private final Rdf type;
    
    public Node(Rdf type) {
        super();
        this.type = type;
    }
    
    public Node(Rdf type, byte[] subject) {
        this(type);
        this.put(SUBJECT, subject);
    }
    
    /**
     * initialize the triples.
     * one of the properties must be the resource SUBJECT
     * for a blank node the SUBJECT can be omitted
     * @param set
     */
    public Node(Rdf type, Map<String, byte[]> set) {
        this(type);
        this.putAll(set);
    }
    
    public Rdf getType() {
        return this.type;
    }
    
    public boolean isBlank() {
        return !this.containsKey(SUBJECT);
    }
    
    public byte[] getSubject() {
        return this.get(SUBJECT);
    }
    
    public void setSubject(byte[] subject) {
        this.put(SUBJECT, subject);
    }
    
    public byte[] getObject(Vocabulary predicate) {
        return this.get(predicate.getPredicate());
    }
    
    public byte[] setObject(Vocabulary predicate, byte[] object) {
        return this.put(predicate.getPredicate(), object);
    }
    
    public byte[] removePredicate(Vocabulary predicate) {
        return this.remove(predicate.getPredicate());
    }

    public byte[] toObject() {
        StringBuilder sb = new StringBuilder(this.size() * 50);
        sb.append("<");
        sb.append(this.type.getPredicate());
        byte[] subject = this.get(SUBJECT);
        if (subject != null) sb.append(" rdf:about=\"").append(UTF8.String(subject)).append('\"');
        sb.append(">\n");
        for (Map.Entry<String, byte[]> entry: this.entrySet()) {
            if (entry.getKey().equals(SUBJECT)) continue;
            sb.append('<').append(entry.getKey()).append('>');
            sb.append(UTF8.String(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">\n");
        }
        sb.append("</");
        sb.append(this.type.getPredicate());
        sb.append(">\n");
        return UTF8.getBytes(sb);
    }
    
}
