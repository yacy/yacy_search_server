// kelondroFlexSplitTable.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 12.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class kelondroFlexSplitTable implements kelondroIndex {

    // this is a set of kelondroFlex tables
    // the set is divided into FlexTables with different entry date
    
    private HashMap tables; // a map from a date string to a kelondroIndex object
    private kelondroRow rowdef;
    private File path;
    private String tablename;
    
    public kelondroFlexSplitTable(File path, String tablename, long preloadTime, kelondroRow rowdef, boolean resetOnFail) throws IOException {
        this.path = path;
        this.tablename = tablename;
        this.rowdef = rowdef;
        init(preloadTime, resetOnFail);
    }
    
    public void init(long preloadTime, boolean resetOnFail) throws IOException {
        
        // initialized tables map
        this.tables = new HashMap();
        if (!(path.exists())) path.mkdirs();
        String[] dir = path.list();
        String date;
        
        // first pass: find tables
        HashMap t = new HashMap(); // file/Integer(size) relation
        long ram, sum = 0;
        for (int i = 0; i < dir.length; i++) {
            if ((dir[i].startsWith(tablename)) &&
                (dir[i].charAt(tablename.length()) == '.') &&
                (dir[i].length() == tablename.length() + 7)) {
                ram = kelondroFlexTable.staticRAMIndexNeed(path, dir[i], rowdef);
                if (ram > 0) {
                    t.put(dir[i], new Long(ram));
                    sum += ram;
                }
            }
        }
        
        // second pass: open tables
        Iterator i;
        Map.Entry entry;
        String f, maxf;
        long maxram;
        kelondroIndex table;
        while (t.size() > 0) {
            // find maximum table
            maxram = 0;
            maxf = null;
            i = t.entrySet().iterator();
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                f = (String) entry.getKey();
                ram = ((Long) entry.getValue()).longValue();
                if (ram > maxram) {
                    maxf = f;
                    maxram = ram;
                }
            }
            
            // open next biggest table
            t.remove(maxf);
            date = maxf.substring(tablename.length() + 1);
            table = new kelondroCache(new kelondroFlexTable(path, maxf, preloadTime, rowdef, resetOnFail), true, false);
            tables.put(date, table);
        }
    }
    
    public void reset() throws IOException {
    	this.close();
    	String[] l = path.list();
    	for (int i = 0; i < l.length; i++) {
    		if (l[i].startsWith(tablename)) {
    			kelondroFlexTable.delete(path, l[i]);
    		}
    	}
    	init(-1, true);
    }
    
    public String filename() {
        return new File(path, tablename).toString();
    }
    
    private static final Calendar thisCalendar = Calendar.getInstance();
    public static final String dateSuffix(Date date) {
        int month, year;
        StringBuffer suffix = new StringBuffer(6);
        synchronized (thisCalendar) {
            thisCalendar.setTime(date);
            month = thisCalendar.get(Calendar.MONTH) + 1;
            year = thisCalendar.get(Calendar.YEAR);
        }
        if ((year < 1970) && (year >= 70)) suffix.append("19").append(Integer.toString(year));
        else if (year < 1970) suffix.append("20").append(Integer.toString(year));
        else if (year > 3000) return null;
        else suffix.append(Integer.toString(year));
        if (month < 10) suffix.append("0").append(Integer.toString(month)); else suffix.append(Integer.toString(month));
        return new String(suffix);    
    }
    
    public int size() throws IOException {
        Iterator i = tables.values().iterator();
        int s = 0;
        while (i.hasNext()) {
            s += ((kelondroIndex) i.next()).size();
        }
        return s;
    }
    
    public synchronized kelondroProfile profile() {
        kelondroProfile[] profiles = new kelondroProfile[tables.size()];
        Iterator i = tables.values().iterator();
        int c = 0;
        while (i.hasNext()) profiles[c++] = ((kelondroIndex) i.next()).profile();
        return kelondroProfile.consolidate(profiles);
    }
    
    public int writeBufferSize() {
        Iterator i = tables.values().iterator();
        int s = 0;
        kelondroIndex ki;
        while (i.hasNext()) {
            ki = ((kelondroIndex) i.next());
            if (ki instanceof kelondroCache) s += ((kelondroCache) ki).writeBufferSize();
        }
        return s;
    }
    
    public void flushSome() {
        Iterator i = tables.values().iterator();
        kelondroIndex ki;
        while (i.hasNext()) {
            ki = ((kelondroIndex) i.next());
            if (ki instanceof kelondroCache)
                try {((kelondroCache) ki).flushSome();} catch (IOException e) {}
        }
    }
    
    public kelondroRow row() {
        return this.rowdef;
    }
    
    public boolean has(byte[] key) throws IOException {
        Iterator i = tables.values().iterator();
        kelondroIndex table;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            if (table.has(key)) return true;
        }
        return false;
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        Object[] keeper = keeperOf(key);
        if (keeper == null) return null;
        return (kelondroRow.Entry) keeper[1];
    }
    
    public synchronized void putMultiple(List rows, Date entryDate) throws IOException {
        throw new UnsupportedOperationException("not yet implemented");
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        return put(row, null); // entry for current date
    }
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        Object[] keeper = keeperOf(row.getColBytes(0));
        if (keeper != null) return ((kelondroIndex) keeper[0]).put(row);
        if ((entryDate == null) || (entryDate.after(new Date()))) entryDate = new Date(); // fix date
        String suffix = dateSuffix(entryDate);
        if (suffix == null) return null;
        kelondroIndex table = (kelondroIndex) tables.get(suffix);
        if (table == null) {
            // make new table
            table = new kelondroFlexTable(path, tablename + "." + suffix, -1, rowdef, true);
            tables.put(suffix, table);
        }
        table.put(row);
        return null;
    }
    
    public synchronized Object[] keeperOf(byte[] key) throws IOException {
        Iterator i = tables.values().iterator();
        kelondroIndex table;
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            entry = table.get(key);
            if (entry != null) return new Object[]{table, entry};
        }
        return null;
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        addUnique(row, null);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        assert row.objectsize() <= this.rowdef.objectsize;
        if ((entryDate == null) || (entryDate.after(new Date()))) entryDate = new Date(); // fix date
        String suffix = dateSuffix(entryDate);
        if (suffix == null) return;
        kelondroIndex table = (kelondroIndex) tables.get(suffix);
        if (table == null) {
            // make new table
            table = new kelondroFlexTable(path, tablename + "." + suffix, -1, rowdef, true);
            tables.put(suffix, table);
        }
        table.addUnique(row, entryDate);
    }
    
    public synchronized void addUniqueMultiple(List rows, Date entryDate) throws IOException {
        Iterator i = rows.iterator();
        while (i.hasNext()) addUnique((kelondroRow.Entry) i.next(), entryDate);
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        Iterator i = tables.values().iterator();
        kelondroIndex table;
        kelondroRow.Entry entry;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            entry = table.remove(key);
            if (entry != null) return entry;
        }
        return null;
    }
    
    public synchronized kelondroRow.Entry removeOne() throws IOException {
        Iterator i = tables.values().iterator();
        kelondroIndex table, maxtable = null;
        int maxcount = -1;
        while (i.hasNext()) {
            table = (kelondroIndex) i.next();
            if (table.size() > maxcount) {
                maxtable = table;
                maxcount = table.size();
            }
        }
        if (maxtable == null) {
            return null;
        } else {
            return maxtable.removeOne();
        }
    }
    
    public synchronized kelondroCloneableIterator rows(boolean up, byte[] firstKey) throws IOException {
        HashSet set = new HashSet();
        Iterator i = tables.values().iterator();
        while (i.hasNext()) {
            set.add(((kelondroIndex) i.next()).rows(up, firstKey));
        }
        return kelondroMergeIterator.cascade(set, rowdef.objectOrder, kelondroMergeIterator.simpleMerge, up);
    }

    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }
    
    public synchronized void close() {
        if (tables == null) return;
        Iterator i = tables.values().iterator();
        while (i.hasNext()) {
            ((kelondroIndex) i.next()).close();
        }
        tables = null;
    }
    
    public static void main(String[] args) {
        System.out.println(dateSuffix(new Date()));
    }
    
}
