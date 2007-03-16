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
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.net.URL;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeedDB;

public class plasmaCrawlZURL {
    
    public final static kelondroRow rowdef = new kelondroRow(
            "String urlhash-"   + yacySeedDB.commonHashLength + ", " + // the url's hash
            "String executor-"  + yacySeedDB.commonHashLength + ", " + // the crawling executor
            "Cardinal workdate-8 {b256}, " +                           // the time when the url was last time tried to load
            "Cardinal workcount-4 {b256}, " +                          // number of load retries
            "String anycause-80, " +                                   // string describing load failure
            "byte[] entry-" + plasmaCrawlEntry.rowdef.objectsize(),                                          // extra space
            kelondroBase64Order.enhancedCoder,
            0);

    // the class object
    private kelondroIndex urlIndexFile = null;
    private LinkedList rejectedStack = new LinkedList(); // strings: url
    
    public plasmaCrawlZURL(File cachePath, String tablename) {
        cachePath.mkdirs();
        try {
            urlIndexFile = new kelondroFlexTable(cachePath, tablename, -1, rowdef);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    public int size() {
        try {
           return urlIndexFile.size() ;
       } catch (IOException e) {
           return 0;
       }
    }
    
    public void close() {
        if (urlIndexFile != null) {
            urlIndexFile.close();
            urlIndexFile = null;
        }
    }

    public synchronized Entry newEntry(
            plasmaCrawlEntry bentry, String executor, Date workdate,
            int workcount, String anycause) {
        if ((executor == null) || (executor.length() < yacySeedDB.commonHashLength)) executor = plasmaURL.dummyHash;
        if (anycause == null) anycause = "unknown";
        return new Entry(bentry, executor, workdate, workcount, anycause);
    }

    public synchronized Entry newEntry(URL url, String anycause) {
        return new Entry(url, anycause);
    }

    public boolean remove(String hash) {
        if (hash == null) return false;
        try {
            urlIndexFile.remove(hash.getBytes());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    public synchronized void stackPushEntry(Entry e) {
        rejectedStack.add(e.hash());
    }
    
    public Entry stackPopEntry(int pos) throws IOException {
        String urlhash = (String) rejectedStack.get(pos);
        if (urlhash == null) return null;
        return new Entry(urlhash);
    }
   
    public synchronized Entry getEntry(String hash) throws IOException {
        return new Entry(hash);
    }
    
    public boolean getUseNewDB() {
        return (urlIndexFile instanceof kelondroFlexTable);
    }

    public boolean exists(String urlHash) {
        try {
            return urlIndexFile.has(urlHash.getBytes());
        } catch (IOException e) {
            return false;
        }
    }
    
    public void clearStack() {
        rejectedStack.clear();
    }
    
    public int stackSize() {
        return rejectedStack.size();
    }
    
    public class Entry {

        plasmaCrawlEntry bentry;    // the balancer entry
        private String           executor;  // the crawling initiator
        private Date             workdate;  // the time when the url was last time tried to load
        private int              workcount; // number of tryings
        private String           anycause;  // string describing reason for load fail
        private boolean          stored;

        public Entry(URL url, String reason) {
            this(new plasmaCrawlEntry(url), null, new Date(), 0, reason);
        }
        
        public Entry(
                plasmaCrawlEntry bentry, String executor, Date workdate,
                int workcount, String anycause) {
            // create new entry
            this.bentry = bentry;
            this.executor = (executor == null) ? yacyCore.seedDB.mySeed.hash : executor;
            this.workdate = (workdate == null) ? new Date() : workdate;
            this.workcount = workcount;
            this.anycause = (anycause == null) ? "" : anycause;
            stored = false;
        }

        public Entry(String hash) throws IOException {
            kelondroRow.Entry entry = urlIndexFile.get(hash.getBytes());
            if (entry != null) {
                insertEntry(entry);
            }
            this.stored = true;
        }

        public Entry(kelondroRow.Entry entry) throws IOException {
            insertEntry(entry);
            this.stored = false;
        }
        
        private void insertEntry(kelondroRow.Entry entry) throws IOException {
            assert (entry != null);
            this.executor = entry.getColString(1, "UTF-8");
            this.workdate = new Date(entry.getColLong(2));
            this.workcount = (int) entry.getColLong(3);
            this.anycause = entry.getColString(4, "UTF-8");
            this.bentry = new plasmaCrawlEntry(plasmaCrawlEntry.rowdef.newEntry(entry.getColBytes(5)));
            assert ((new String(entry.getColBytes(0))).equals(bentry.urlhash()));
            return;
        }
        
        public void store() {
            // stores the values from the object variables into the database
            if (this.stored) return;
            if (this.bentry == null) return;
            kelondroRow.Entry newrow = rowdef.newEntry();
            newrow.setCol(0, this.bentry.urlhash().getBytes());
            newrow.setCol(1, this.executor.getBytes());
            newrow.setCol(2, this.workdate.getTime());
            newrow.setCol(3, this.workcount);
            newrow.setCol(4, this.anycause.getBytes());
            newrow.setCol(5, this.bentry.toRow().bytes());
            try {
                urlIndexFile.put(newrow);
                this.stored = true;
            } catch (IOException e) {
                System.out.println("INTERNAL ERROR AT plasmaEURL:url2hash:" + e.toString());
            }
        }

        public URL url() {
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
            return this.bentry.urlhash();
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

    public class kiter implements Iterator {
        // enumerates entry elements
        Iterator i;
        boolean error = false;
        
        public kiter(boolean up, String firstHash) throws IOException {
            i = urlIndexFile.rows(up, (firstHash == null) ? null : firstHash.getBytes());
            error = false;
        }

        public boolean hasNext() {
            if (error) return false;
            return i.hasNext();
        }

        public Object next() throws RuntimeException {
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

    public Iterator entries(boolean up, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }
}
