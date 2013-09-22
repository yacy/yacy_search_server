/**
 *  MapColumnIndex
 *  Copyright 2012 by Michael Christen
 *  First released 01.02.2012 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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


package net.yacy.kelondro.blob;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.NaturalOrder;

/**
 * a mapping from a column name to maps with the value of the columns to the primary keys where the entry exist in the table
 */
public class MapColumnIndex implements Serializable {

    private static final long serialVersionUID=-424741536889467566L;

    private final Map<String, Map<String, Collection<byte[]>>> index;

    public MapColumnIndex() {
        this.index = new HashMap<String, Map<String, Collection<byte[]>>>();
    }

    public synchronized Collection<byte[]> getIndex(final String whereKey, final String isValue) throws UnsupportedOperationException {
        Map<String, Collection<byte[]>> references = this.index.get(whereKey);
        if (references == null) throw new UnsupportedOperationException();
        Collection<byte[]> indexes = references.get(isValue);
        if (indexes == null) return new ArrayList<byte[]>(0); // empty collection
        return indexes;
    }

    public synchronized void clear() {
        this.index.clear();
    }

    /**
     * create a full index for the whereKey
     * @param whereKey
     * @param isValue
     * @param table
     */
    public synchronized void init(final String whereKey, final String isValue, final Iterator<Map.Entry<byte[], Map<String, String>>> table) {
        Map<String, Collection<byte[]>> valueIdxMap = new HashMap<String, Collection<byte[]>>();
        this.index.put(whereKey, valueIdxMap);
        Map.Entry<byte[], Map<String, String>> line;
        while (table.hasNext()) {
            line = table.next();
            String value = line.getValue().get(whereKey);
            if (value == null) continue; // we don't need to remember that
            indexupdate(line.getKey(), valueIdxMap, value.toLowerCase()); // add the entry lowercase (needed for seedDB.lookupByName)
        }
    }

    /**
     * update an index entry
     * @param primarykey the primary key for the row that is updated
     * @param row the row that was updated (a mapping from column names to values)
     */
    public synchronized void update(final byte[] primarykey, final Map<String, String> row) {
        for (Map.Entry<String, Map<String, Collection<byte[]>>> entry: this.index.entrySet()) {
            // create an index for all columns that we track
            String value = row.get(entry.getKey());
            if (value == null) continue; // we don't need to remember that
            indexupdate(primarykey, entry.getValue(), value);
        }
    }

    private static void indexupdate(final byte[] primarykey, final Map<String, Collection<byte[]>> valueIdxMap, final String value) {
        Collection<byte[]> indexes = valueIdxMap.get(value);
        if (indexes == null) {
            // create a new index entry
            indexes = new ArrayList<byte[]>(1);
            indexes.add(primarykey);
            valueIdxMap.put(value, indexes);
        } else {
            // update the existing index entry
            // check if value already exist
            if (!net.yacy.cora.util.ByteBuffer.contains(indexes, primarykey)) {
                indexes.add(primarykey);
            }
        }
    }

    /**
     * delete all references to the primary key
     * @param primarykey
     */
    public synchronized void delete(final byte[] primarykey) {
        for (Map.Entry<String, Map<String, Collection<byte[]>>> entry: this.index.entrySet()) {
            // we must check all index reference maps: iterate over entries
            indexdelete(primarykey, entry.getValue());
        }
    }

    private static void indexdelete(final byte[] index, final Map<String, Collection<byte[]>> valueIdxMap) {
        Iterator<Map.Entry<String, Collection<byte[]>>> i = valueIdxMap.entrySet().iterator();
        Map.Entry<String, Collection<byte[]>> ref;
        while (i.hasNext()) {
            ref = i.next();
            net.yacy.cora.util.ByteBuffer.remove(ref.getValue(), index);
            if (ref.getValue().isEmpty()) {
                i.remove();
            }
        }
    }

    private static Collection<byte[]> getIndexWithExceptionHandler(final MapColumnIndex idx, final String whereKey, final String isValue, Map<byte[], Map<String, String>> table) {
        try {
            return idx.getIndex(whereKey, isValue);
        } catch (final UnsupportedOperationException e) {
            idx.init(whereKey, isValue, table.entrySet().iterator());
            try {
                return idx.getIndex(whereKey, isValue);
            } catch (final UnsupportedOperationException ee) {
                throw ee;
            }
        }
    }

    private static void printIndex(Collection<byte[]> index) {
        System.out.print("idx{");
        int c = 0;
        for (byte[] a: index) {
            if (c++ != 0) System.out.print(", ");
            System.out.print(ASCII.String(a));
        }
        System.out.print("}");
    }

    public static void main(String[] args) {
        Map<byte[], Map<String, String>> table = new TreeMap<byte[], Map<String, String>>(NaturalOrder.naturalOrder);
        Map<String, String> row;
        row = new HashMap<String, String>(); row.put("a", "1"); row.put("b", "2"); row.put("c", "2"); table.put("line1".getBytes(), row);
        row = new HashMap<String, String>(); row.put("a", "3"); row.put("b", "2"); row.put("c", "4"); table.put("line2".getBytes(), row);
        row = new HashMap<String, String>(); row.put("a", "5"); row.put("b", "2"); row.put("c", "4"); table.put("line3".getBytes(), row);
        row = new HashMap<String, String>(); row.put("a", "6"); row.put("b", "7"); row.put("c", "8"); table.put("line4".getBytes(), row);
        MapColumnIndex idx = new MapColumnIndex();
        System.out.print("colum b, value 2: "); printIndex(getIndexWithExceptionHandler(idx, "b", "2", table)); System.out.println();
        System.out.print("colum c, value 4: "); printIndex(getIndexWithExceptionHandler(idx, "c", "4", table)); System.out.println();
        System.out.print("colum b, value 2: "); printIndex(getIndexWithExceptionHandler(idx, "b", "7", table)); System.out.println();
        System.out.print("colum d, value 0: "); printIndex(getIndexWithExceptionHandler(idx, "d", "0", table)); System.out.println();
        row = new HashMap<String, String>(); row.put("a", "9"); row.put("b", "9"); row.put("c", "4"); table.put("line5".getBytes(), row);
        idx.update("line5".getBytes(), row);
        System.out.print("colum c, value 4: "); printIndex(getIndexWithExceptionHandler(idx, "c", "4", table)); System.out.println();
    }

}
