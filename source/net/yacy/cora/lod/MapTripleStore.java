/**
 *  TripleStore
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

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.lod.vocabulary.Rdf;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.storage.MapStore;

public class MapTripleStore {

    MapStore store;
    
    public MapTripleStore(MapStore store) {
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
        return new Node(Rdf.Description, n);
    }

    public boolean isEmpty() {
        return this.store.isEmpty();
    }

    public Node put(byte[] id, Node node) {
        Map<String, byte[]> n = this.store.put(id, node);
        if (n == null) return null;
        return new Node(Rdf.Description, n);
    }

    public void putAll(MapTripleStore entries) {
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
        return new Node(Rdf.Description, n);
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
                return new AbstractMap.SimpleImmutableEntry<byte[], Node>(key, MapTripleStore.this.get(key));
            }

            @Override
            public void remove() {
                id.remove();
            }
            
        };
    }

    public ByteOrder getOrdering() {
        return this.store.getOrdering();
    }

    public CloneableIterator<byte[]> idIterator() {
        return this.store.keyIterator();
    }

    public synchronized void close() {
        this.store.close();
    }

}
