/**
 *  AbstractMapStore
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

package net.yacy.cora.storage;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.encoding.UTF8;

public abstract class AbstractMapStore implements MapStore {

    @Override
    public boolean containsValue(Object arg0) {
        throw new UnsupportedOperationException("ContainsValue() not appropriate, use outer indexing");
    }

    @Override
    public Set<java.util.Map.Entry<byte[], Map<String, byte[]>>> entrySet() {
        throw new UnsupportedOperationException("entrySet() not appropriate, use an iterator");
    }

    @Override
    public Set<byte[]> keySet() {
        throw new UnsupportedOperationException("keySet() not appropriate, use an iterator");
    }

    @Override
    public void putAll(Map<? extends byte[], ? extends Map<String, byte[]>> entries) {
        if (entries instanceof MapStore) {
            Iterator<Map.Entry<byte[], Map<String, byte[]>>> i = ((MapStore) entries).iterator();
            Map.Entry<? extends byte[], ? extends Map<String, byte[]>> entry;
            while (i.hasNext()) {
                entry = i.next();
                this.put(entry.getKey(), entry.getValue());
            }
        } else {
            for (Map.Entry<? extends byte[], ? extends Map<String, byte[]>> e: entries.entrySet()) {
                this.put(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public Collection<Map<String, byte[]>> values() {
        throw new UnsupportedOperationException("values() not appropriate, use an iterator");
    }

    @Override
    public Iterator<Map.Entry<byte[], Map<String, byte[]>>> iterator() {
        final Iterator<byte[]> k = this.keyIterator();
        return new Iterator<Map.Entry<byte[], Map<String, byte[]>>>(){

            @Override
            public boolean hasNext() {
                return k.hasNext();
            }

            @Override
            public Map.Entry<byte[], Map<String, byte[]>> next() {
                byte[] key = k.next();
                if (key == null) return null;
                return new AbstractMap.SimpleImmutableEntry<byte[], Map<String, byte[]>>(key, AbstractMapStore.this.get(key));
            }

            @Override
            public void remove() {
                k.remove();
            }
            
        };
    }
    
    public static String map2String(Map<String, byte[]> map) {
        StringBuilder sb = new StringBuilder(map.size() * 50);
        sb.append("<map>\n");
        for (Map.Entry<String, byte[]> entry: map.entrySet()) {
            sb.append('<').append(entry.getKey()).append('>');
            sb.append(UTF8.String(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">\n");
        }
        sb.append("</map>\n");
        return sb.toString();
    }
    
}
