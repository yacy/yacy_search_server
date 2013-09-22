// kRelations.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 3.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.table;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;


public class Relations {

    private final File baseDir;
    private HashMap<String, Index> relations;
    private final boolean useTailCache;
    private final boolean exceed134217727;

    public Relations(
    		final File location,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.baseDir = location;
        this.useTailCache = useTailCache;
        this.exceed134217727 = exceed134217727;
    }

    private static Row rowdef(String filename) {
        int p = filename.lastIndexOf('.');
        if (p >= 0) filename = filename.substring(0, p);
        p = filename.lastIndexOf('-');
        assert p >= 0;
        final int payloadsize = NumberTools.parseIntDecSubstring(filename, p + 1);
        filename = filename.substring(0, p);
        p = filename.lastIndexOf('-');
        assert p >= 0;
        final int keysize = NumberTools.parseIntDecSubstring(filename, p + 1);
        return rowdef(keysize, payloadsize);
    }

    private static Row rowdef(final int keysize, final int payloadsize) {
        return new Row(
                "byte[] key-" + keysize + ", " +
                "long time-8" + keysize + ", " +
                "int ttl-4" + keysize + ", " +
                "byte[] node-" + payloadsize,
                NaturalOrder.naturalOrder);
    }

    private static String filename(final String tablename, final int keysize, final int payloadsize) {
        return tablename + "-" + keysize + "-" + payloadsize + ".eco";
    }

    public void declareRelation(final String name, final int keysize, final int payloadsize) throws SpaceExceededException {
        // try to get the relation from the relation-cache
        final Index relation = this.relations.get(name);
        if (relation != null) return;
        // try to find the relation as stored on file
        final String[] list = this.baseDir.list();
        final String targetfilename = filename(name, keysize, payloadsize);
        for (int i = 0; i < list.length; i++) {
            if (list[i].startsWith(name)) {
                if (!list[i].equals(targetfilename)) continue;
                final Row row = rowdef(list[i]);
                if (row.primaryKeyLength != keysize || row.column(1).cellwidth != payloadsize) continue; // a wrong table
                Index table;
                try {
                    table = new Table(new File(this.baseDir, list[i]), row, 1024*1024, 0, this.useTailCache, this.exceed134217727, true);
                } catch (final SpaceExceededException e) {
                    table = new Table(new File(this.baseDir, list[i]), row, 0, 0, false, this.exceed134217727, true);
                }
                this.relations.put(name, table);
                return;
            }
        }
        // the relation does not exist, create it
        final Row row = rowdef(keysize, payloadsize);
        Index table;
        try {
            table = new Table(new File(this.baseDir, targetfilename), row, 1024*1024, 0, this.useTailCache, this.exceed134217727, true);
        } catch (final SpaceExceededException e) {
            table = new Table(new File(this.baseDir, targetfilename), row, 0, 0, false, this.exceed134217727, true);
        }
        this.relations.put(name, table);
    }

    public Index getRelation(final String name) throws SpaceExceededException {
        // try to get the relation from the relation-cache
        final Index relation = this.relations.get(name);
        if (relation != null) return relation;
        // try to find the relation as stored on file
        final String[] list = this.baseDir.list();
        for (final String element : list) {
            if (element.startsWith(name)) {
                final Row row = rowdef(element);
                Index table;
                try {
                    table = new Table(new File(this.baseDir, element), row, 1024*1024, 0, this.useTailCache, this.exceed134217727, true);
                } catch (final SpaceExceededException e) {
                    table = new Table(new File(this.baseDir, element), row, 0, 0, false, this.exceed134217727, true);
                }
                this.relations.put(name, table);
                return table;
            }
        }
        // the relation does not exist
        return null;
    }

    public String putRelation(final String name, final String key, final String value) throws IOException, SpaceExceededException {
        final byte[] r = putRelation(name, UTF8.getBytes(key), UTF8.getBytes(value));
        if (r == null) return null;
        return UTF8.String(r);
    }

    public byte[] putRelation(final String name, final byte[] key, final byte[] value) throws IOException, SpaceExceededException {
        final Index table = getRelation(name);
        if (table == null) return null;
        final Row.Entry entry = table.row().newEntry();
        entry.setCol(0, key);
        entry.setCol(1, System.currentTimeMillis());
        entry.setCol(2, 1000000);
        entry.setCol(3, value);
        final Row.Entry oldentry = table.replace(entry);
        if (oldentry == null) return null;
        return oldentry.getColBytes(3, true);
    }

    public String getRelation(final String name, final String key) throws IOException, SpaceExceededException {
        final byte[] r = getRelation(name, UTF8.getBytes(key));
        if (r == null) return null;
        return UTF8.String(r);
    }

    public byte[] getRelation(final String name, final byte[] key) throws IOException, SpaceExceededException {
        final Index table = getRelation(name);
        if (table == null) return null;
        final Row.Entry entry = table.get(key, false);
        if (entry == null) return null;
        return entry.getColBytes(3, true);
    }

    public boolean hasRelation(final String name, final byte[] key) throws SpaceExceededException {
        final Index table = getRelation(name);
        if (table == null) return false;
        return table.has(key);
    }

    public byte[] removeRelation(final String name, final byte[] key) throws IOException, SpaceExceededException {
        final Index table = getRelation(name);
        if (table == null) return null;
        final Row.Entry entry = table.remove(key);
        if (entry == null) return null;
        return entry.getColBytes(3, true);
    }

    public static void main(final String args[]) {
        final Relations r = new Relations(new File("/Users/admin/"), true, true);
        try {
            final String table1 = "test1";
            r.declareRelation(table1, 12, 30);
            r.putRelation(table1, "abcdefg", "eineintrag");
            r.putRelation(table1, "abcdefg", "eineintrag");
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
    }

}
