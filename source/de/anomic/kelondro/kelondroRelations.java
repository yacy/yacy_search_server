// kelondroRelations.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 3.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
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

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class kelondroRelations {

    private File baseDir;
    private HashMap<String, kelondroIndex> relations;
    
    public kelondroRelations(File location) {
        this.baseDir = location;
    }
    
    private static kelondroRow rowdef(String filename) {
        int p = filename.lastIndexOf('.');
        if (p >= 0) filename = filename.substring(0, p);
        p = filename.lastIndexOf('-');
        assert p >= 0;
        int payloadsize = Integer.parseInt(filename.substring(p + 1));
        filename = filename.substring(0, p);
        p = filename.lastIndexOf('-');
        assert p >= 0;
        int keysize = Integer.parseInt(filename.substring(p + 1));
        return rowdef(keysize, payloadsize);
    }
    
    private static kelondroRow rowdef(int keysize, int payloadsize) {
        return new kelondroRow(
                "byte[] key-" + keysize + ", " +
                "long time-8" + keysize + ", " +
                "int ttl-4" + keysize + ", " +
                "byte[] node-" + payloadsize,
                kelondroNaturalOrder.naturalOrder, 0);
    }
    
    private static String filename(String tablename, int keysize, int payloadsize) {
        return tablename + "-" + keysize + "-" + payloadsize + ".eco";
    }
    
    public void declareRelation(String name, int keysize, int payloadsize) {
        // try to get the relation from the relation-cache
        kelondroIndex relation = relations.get(name);
        if (relation != null) return;
        // try to find the relation as stored on file
        String[] list = baseDir.list();
        String targetfilename = filename(name, keysize, payloadsize);
        for (int i = 0; i < list.length; i++) {
            if (list[i].startsWith(name)) {
                if (!list[i].equals(targetfilename)) continue;
                kelondroRow row = rowdef(list[i]);
                if (row.primaryKeyLength != keysize || row.column(1).cellwidth != payloadsize) continue; // a wrong table
                kelondroIndex table = new kelondroEcoTable(new File(baseDir, list[i]), row, kelondroEcoTable.tailCacheUsageAuto, 1024*1024, 0);
                relations.put(name, table);
                return;
            }
        }
        // the relation does not exist, create it
        kelondroRow row = rowdef(keysize, payloadsize);
        kelondroIndex table = new kelondroEcoTable(new File(baseDir, targetfilename), row, kelondroEcoTable.tailCacheUsageAuto, 1024*1024, 0);
        relations.put(name, table);
    }
    
    public kelondroIndex getRelation(String name) {
        // try to get the relation from the relation-cache
        kelondroIndex relation = relations.get(name);
        if (relation != null) return relation;
        // try to find the relation as stored on file
        String[] list = baseDir.list();
        for (int i = 0; i < list.length; i++) {
            if (list[i].startsWith(name)) {
                kelondroRow row = rowdef(list[i]);
                kelondroIndex table = new kelondroEcoTable(new File(baseDir, list[i]), row, kelondroEcoTable.tailCacheUsageAuto, 1024*1024, 0);
                relations.put(name, table);
                return table;
            }
        }
        // the relation does not exist
        return null;
    }
    
    public String putRelation(String name, String key, String value) throws IOException {
        byte[] r = putRelation(name, key.getBytes(), value.getBytes());
        if (r == null) return null;
        return new String(r);
    }
    
    public byte[] putRelation(String name, byte[] key, byte[] value) throws IOException {
        kelondroIndex table = getRelation(name);
        if (table == null) return null;
        kelondroRow.Entry entry = table.row().newEntry();
        entry.setCol(0, key);
        entry.setCol(1, System.currentTimeMillis());
        entry.setCol(2, 1000000);
        entry.setCol(3, value);
        kelondroRow.Entry oldentry = table.put(entry);
        if (oldentry == null) return null;
        return oldentry.getColBytes(3);
    }
    
    public String getRelation(String name, String key) throws IOException {
        byte[] r = getRelation(name, key.getBytes());
        if (r == null) return null;
        return new String(r);
    }
    
    public byte[] getRelation(String name, byte[] key) throws IOException {
        kelondroIndex table = getRelation(name);
        if (table == null) return null;
        kelondroRow.Entry entry = table.get(key);
        if (entry == null) return null;
        return entry.getColBytes(3);
    }
    
    public boolean hasRelation(String name, byte[] key) throws IOException {
        kelondroIndex table = getRelation(name);
        if (table == null) return false;
        return table.has(key);
    }
    
    public byte[] removeRelation(String name, byte[] key) throws IOException {
        kelondroIndex table = getRelation(name);
        if (table == null) return null;
        kelondroRow.Entry entry = table.remove(key, false);
        if (entry == null) return null;
        return entry.getColBytes(3);
    }
    
    public static void main(String args[]) {
        kelondroRelations r = new kelondroRelations(new File("/Users/admin/"));
        try {
            String table1 = "test1";
            r.declareRelation(table1, 12, 30);
            r.putRelation(table1, "abcdefg", "eineintrag");
            r.putRelation(table1, "abcdefg", "eineintrag");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
