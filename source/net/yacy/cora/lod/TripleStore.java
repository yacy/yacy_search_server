package net.yacy.cora.lod;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;

import net.yacy.kelondro.blob.MapStore;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;

public class TripleStore {

    MapStore store;
    
    public TripleStore(MapStore store) {
        this.store = store;
    }

    public void clear() {
        this.store.clear();
    }

    public boolean contains(byte[] id) {
        return this.store.containsKey(id);
    }

    public Node get(byte[] id) {
        Map<String, byte[]> n = this.store.get(id);
        if (n == null) return null;
        return new Node(n);
    }

    public boolean isEmpty() {
        return this.store.isEmpty();
    }

    public Node put(byte[] id, Node node) {
        Map<String, byte[]> n = this.store.put(id, node);
        if (n == null) return null;
        return new Node(n);
    }

    public void putAll(TripleStore entries) {
        Iterator<Map.Entry<byte[], Node>> i = entries.iterator();
        Map.Entry<? extends byte[], ? extends Node> entry;
        while (i.hasNext()) {
            entry = i.next();
            this.put(entry.getKey(), entry.getValue());
        }
    }

    public Node remove(byte[] id) {
        Map<String, byte[]> n = this.store.remove(id);
        if (n == null) return null;
        return new Node(n);
    }

    public int size() {
        return this.store.size();
    }

    public Iterator<java.util.Map.Entry<byte[], Node>> iterator() {
        final Iterator<byte[]> id = this.idIterator();
        return new Iterator<Map.Entry<byte[], Node>>(){

            @Override
            public boolean hasNext() {
                return id.hasNext();
            }

            @Override
            public Map.Entry<byte[], Node> next() {
                byte[] key = id.next();
                if (key == null) return null;
                return new AbstractMap.SimpleImmutableEntry<byte[], Node>(key, TripleStore.this.get(key));
            }

            @Override
            public void remove() {
                id.remove();
            }
            
        };
    }

    public ByteOrder getOrdering() {
        return store.getOrdering();
    }

    public CloneableIterator<byte[]> idIterator() {
        return this.store.keyIterator();
    }

    public void close() {
        this.store.close();
    }

}
