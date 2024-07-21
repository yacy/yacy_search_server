/**
 *  IndexTable
 *  Copyright 2014 by Michael Peter Christen
 *  First released 11.11.2014 at https://yacy.net
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

package net.yacy.kelondro.table;

import java.io.File;
import java.io.IOException;

import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.util.kelondroException;

/**
 * this is a stored index for primary keys. Each key is associated to a single long value
 */
public class IndexTable {

    private final Table table;

    /**
     * create an index with given (fixed) key and value length 
     * @param location
     * @param keysize
     * @param payloadsize
     * @param useTailCache
     * @param exceed134217727
     * @throws IOException
     */
    public IndexTable(
            final File location, int keysize, int payloadsize,
            final boolean useTailCache,
            final boolean exceed134217727) throws IOException {
        final Row row = new Row(
                "byte[] key-" + keysize + ", " +
                "long num-" + payloadsize + " {b256}",
                NaturalOrder.naturalOrder);
        Table t;
        try {
            t = new Table(location, row, 1024*1024, 0, useTailCache, exceed134217727, true);
        } catch (final SpaceExceededException e) {
            try {
                t = new Table(location, row, 0, 0, false, exceed134217727, true);
            } catch (kelondroException | SpaceExceededException e1) {
                throw new IOException(e);
            }
        }
        this.table = t;
    }

    /**
     * Write an index entry. All written values are persistent.
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    public Long put(final byte[] key, final long value) throws IOException {
        final Row.Entry entry = table.row().newEntry();
        entry.setCol(0, key);
        entry.setCol(1, value);
        Row.Entry oldentry;
        try {
            oldentry = table.replace(entry);
            if (oldentry == null) return null;
            return oldentry.getColLong(1);
        } catch (SpaceExceededException e) {
            throw new IOException(e);
        }
    }

    /**
     * get a value from the index
     * @param key
     * @return the value of the relation or -1 if that does not exist
     * @throws IOException
     */
    public long get(final byte[] key) throws IOException {
        final Row.Entry entry = table.get(key, false);
        if (entry == null) return -1;
        Long l = entry.getColLong(1);
        return l == null ? -1 : l.longValue();
    }
    
    /**
     * check if a given value exists in the index. The check is very efficient because all operations are done in the RAM.
     * @param key
     * @return true if the key already existed, false otherwise
     */
    public boolean has(final byte[] key) {
        return table.has(key);
    }

    /**
     * remove a given key from the index
     * @param key
     * @return the previous value of the relation
     * @throws IOException
     */
    public Long remove(final byte[] key) throws IOException {
        final Row.Entry entry = table.remove(key);
        if (entry == null) return null;
        return entry.getColLong(1);
    }

    /**
     * clear the index
     * @throws IOException
     */
    public void clear() throws IOException {
        this.table.clear();
    }
    
    /**
     * close the index. must be called to finally write all data to disc
     */
    public void close() {
        this.table.close();
    }
}
