package net.yacy.cora.lod;

import java.util.Map;
import java.util.HashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.Vocabulary;

public class Node extends HashMap<String, byte[]> implements Map<String, byte[]> {

    private static final long serialVersionUID = -6715118942251224832L;
    
    public static final String SUBJECT = "rdf:about";

    public Node() {
        super();
    }
    
    public Node(byte[] subject) {
        super();
        this.put(SUBJECT, subject);
    }
    
    /**
     * initialize the triples.
     * one of the properties must be the resource SUBJECT
     * for a blank node the SUBJECT can be omitted
     * @param set
     */
    public Node(Map<String, byte[]> set) {
        super();
        this.putAll(set);
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
        sb.append("<rdf:Description");
        byte[] subject = this.get(SUBJECT);
        if (subject != null) sb.append(" rdf:about=\"").append(UTF8.String(subject)).append('\"');
        sb.append(">\n");
        for (Map.Entry<String, byte[]> entry: this.entrySet()) {
            if (entry.getKey().equals(SUBJECT)) continue;
            sb.append('<').append(entry.getKey()).append('>');
            sb.append(UTF8.String(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">\n");
        }
        sb.append("</rdf:Description>\n");
        return UTF8.getBytes(sb);
    }
    
}
