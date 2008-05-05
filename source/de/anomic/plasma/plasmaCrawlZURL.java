// plasmaCrawlZURL.java
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 15.03.2007 on http://www.anomic.de
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

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroEcoTable;
import de.anomic.kelondro.kelondroFlexWidthArray;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public class plasmaCrawlZURL {
    
    private static final int EcoFSBufferSize = 200;
    
    public final static kelondroRow rowdef = new kelondroRow(
            "String urlhash-"   + yacySeedDB.commonHashLength + ", " + // the url's hash
            "String executor-"  + yacySeedDB.commonHashLength + ", " + // the crawling executor
            "Cardinal workdate-8 {b256}, " +                           // the time when the url was last time tried to load
            "Cardinal workcount-4 {b256}, " +                          // number of load retries
            "String anycause-80, " +                                   // string describing load failure
            "byte[] entry-" + plasmaCrawlEntry.rowdef.objectsize,                                          // extra space
            kelondroBase64Order.enhancedCoder,
            0);

    // the class object
    kelondroIndex urlIndex = null;
    private LinkedList<String> stack = new LinkedList<String>(); // strings: url
    
    public plasmaCrawlZURL(File cachePath, String tablename, boolean startWithEmptyFile) {
    	// creates a new ZURL in a file
        cachePath.mkdirs();
        File f = new File(cachePath, tablename);
        if (startWithEmptyFile) {
            if (f.exists()) {
                if (f.isDirectory()) kelondroFlexWidthArray.delete(cachePath, tablename); else f.delete();
            }
        }
        urlIndex = new kelondroEcoTable(f, rowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, 0);
        //urlIndex = new kelondroFlexTable(cachePath, tablename, -1, rowdef, 0, true);
    }
    
    public plasmaCrawlZURL() {
    	// creates a new ZUR in RAM
        urlIndex = new kelondroRowSet(rowdef, 0);
    }
    
    public int size() {
        return urlIndex.size() ;
    }
    
    public void close() {
        if (urlIndex != null) {
            urlIndex.close();
            urlIndex = null;
        }
    }

    public synchronized Entry newEntry(
            plasmaCrawlEntry bentry,
            String executor,
            Date workdate,
            int workcount,
            String anycause) {
        assert executor != null;
        assert executor.length() > 0;
        if (anycause == null) anycause = "unknown";
        return new Entry(bentry, executor, workdate, workcount, anycause);
    }

    public boolean remove(String hash) {
        if (hash == null) return false;
        try {
            urlIndex.remove(hash.getBytes(), false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    public synchronized void push(Entry e) {
        stack.add(e.hash());
    }
    
    public Entry top(int pos) {
        String urlhash;
        synchronized (stack) {
            if (pos >= stack.size()) return null;
            urlhash = (String) stack.get(pos);
        }
        if (urlhash == null) return null;
        return getEntry(urlhash);
    }
   
    public synchronized Entry getEntry(String urlhash) {
        try {
            kelondroRow.Entry entry = urlIndex.get(urlhash.getBytes());
            if (entry == null) return null;
            return new Entry(entry);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean exists(String urlHash) {
        try {
            return urlIndex.has(urlHash.getBytes());
        } catch (IOException e) {
            return false;
        }
    }
    
    public void clearStack() {
        stack.clear();
    }
    
    public int stackSize() {
        return stack.size();
    }
    
    public class Entry {

        plasmaCrawlEntry bentry;    // the balancer entry
        private String   executor;  // the crawling executor
        private Date     workdate;  // the time when the url was last time tried to load
        private int      workcount; // number of tryings
        private String   anycause;  // string describing reason for load fail
        private boolean  stored;

        public Entry(
                plasmaCrawlEntry bentry,
                String executor,
                Date workdate,
                int workcount,
                String anycause) {
            // create new entry
            assert bentry != null;
            assert executor != null;
            this.bentry = bentry;
            this.executor = executor;
            this.workdate = (workdate == null) ? new Date() : workdate;
            this.workcount = workcount;
            this.anycause = (anycause == null) ? "" : anycause;
            stored = false;
        }

        public Entry(kelondroRow.Entry entry) throws IOException {
            assert (entry != null);
            this.executor = entry.getColString(1, "UTF-8");
            this.workdate = new Date(entry.getColLong(2));
            this.workcount = (int) entry.getColLong(3);
            this.anycause = entry.getColString(4, "UTF-8");
            this.bentry = new plasmaCrawlEntry(plasmaCrawlEntry.rowdef.newEntry(entry.getColBytes(5)));
            assert ((new String(entry.getColBytes(0))).equals(bentry.url().hash()));
            this.stored = true;
            return;
        }
        
        public void store() {
            // stores the values from the object variables into the database
            if (this.stored) return;
            if (this.bentry == null) return;
            kelondroRow.Entry newrow = rowdef.newEntry();
            newrow.setCol(0, this.bentry.url().hash().getBytes());
            newrow.setCol(1, this.executor.getBytes());
            newrow.setCol(2, this.workdate.getTime());
            newrow.setCol(3, this.workcount);
            newrow.setCol(4, this.anycause.getBytes());
            newrow.setCol(5, this.bentry.toRow().bytes());
            try {
                if (urlIndex != null) urlIndex.put(newrow);
                this.stored = true;
            } catch (IOException e) {
                System.out.println("INTERNAL ERROR AT plasmaEURL:url2hash:" + e.toString());
            }
        }

        public yacyURL url() {
            return this.bentry.url();
        }
        
        public String initiator() {
            return this.bentry.initiator();
        }
        
        public String hash() {
            // return a url-hash, based on the md5 algorithm
            // the result is a String of 12 bytes within a 72-bit space
            // (each byte has an 6-bit range)
            // that should be enough for all web pages on the world
            return this.bentry.url().hash();
        }

        public Date workdate() {
            return workdate;
        }
        
        public String executor() {
            // return the creator's hash
            return executor;
        }
        
        public String anycause() {
            return anycause;
        }

    }

    public class kiter implements Iterator<Entry> {
        // enumerates entry elements
        Iterator<kelondroRow.Entry> i;
        boolean error = false;
        
        public kiter(boolean up, String firstHash) throws IOException {
            i = urlIndex.rows(up, (firstHash == null) ? null : firstHash.getBytes());
            error = false;
        }

        public boolean hasNext() {
            if (error) return false;
            return i.hasNext();
        }

        public Entry next() throws RuntimeException {
            kelondroRow.Entry e = (kelondroRow.Entry) i.next();
            if (e == null) return null;
            try {
                return new Entry(e);
            } catch (IOException ex) {
                throw new RuntimeException("error '" + ex.getMessage() + "' for hash " + e.getColString(0, null));
            }
        }
        
        public void remove() {
            i.remove();
        }
        
    }

    public Iterator<Entry> entries(boolean up, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }
}
