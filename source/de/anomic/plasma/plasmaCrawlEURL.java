// plasmaEURL.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.08.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// EURL - noticed (known but not loaded) URL's

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;

import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.tools.bitfield;

public class plasmaCrawlEURL extends indexURL {

    private LinkedList rejectedStack = new LinkedList(); // strings: url
    
    public plasmaCrawlEURL(File cachePath, int bufferkb, long preloadTime) {
        super();
        int[] ce = {
            urlHashLength,           // the url's hash
            urlHashLength,           // the url's referrer hash
            urlHashLength,           // the crawling initiator
            urlHashLength,           // the crawling executor
            urlStringLength,         // the url as string
            urlNameLength,           // the name of the url, from anchor tag <a>name</a>
            urlDateLength,           // the time when the url was first time appeared
            urlDateLength,           // the time when the url was last time tried to load
            urlRetryLength,          // number of load retries
            urlErrorLength,          // string describing load failure
            urlFlagLength            // extra space
        };
        if (cachePath.exists()) try {
            // open existing cache
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent);
        } catch (IOException e) {
            cachePath.delete();
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent, new kelondroRow(ce), true);
        } else {
            // create new cache
            cachePath.getParentFile().mkdirs();
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent, new kelondroRow(ce), true);
        }
    }

    public synchronized Entry newEntry(URL url, String referrer, String initiator, String executor,
				       String name, String failreason, bitfield flags, boolean retry) {
        if ((referrer == null) || (referrer.length() < urlHashLength)) referrer = dummyHash;
        if ((initiator == null) || (initiator.length() < urlHashLength)) initiator = dummyHash;
        if ((executor == null) || (executor.length() < urlHashLength)) executor = dummyHash;
        if (failreason == null) failreason = "unknown";

        // create a stack entry
        HashMap map = new HashMap();
        map.put("url", url);
        map.put("referrer", referrer);
        map.put("initiator", initiator);
        map.put("executor", executor);
        map.put("name", name);
        map.put("failreason", failreason);
        map.put("flags", flags);
        rejectedStack.add(map);
        Entry e = new Entry(url, referrer, initiator, executor, name, failreason, flags);
        
        // put in table
        if (retry) e.store();
        return e;
    }

    public synchronized Entry getEntry(String hash) throws IOException {
	return new Entry(hash);
    }

    public boolean exists(String urlHash) {
        try {
            return (urlHashCache.get(urlHash.getBytes()) != null);
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
    
    public Entry getStack(int pos) {
        HashMap m = (HashMap) rejectedStack.get(pos);
        return new Entry((URL) m.get("url"), (String) m.get("referrer"), (String) m.get("initiator"), (String) m.get("executor"),
			 (String) m.get("name"), (String) m.get("failreason"), (bitfield) m.get("flags"));
    }
    
    public class Entry {

        private String   hash;       // the url's hash
        private String   referrer;   // the url's referrer hash
        private String   initiator;  // the crawling initiator
        private String   executor;  // the crawling initiator
        private URL      url;        // the url as string
        private String   name;       // the name of the url, from anchor tag <a>name</a>     
        private Date     initdate;   // the time when the url was first time appeared
        private Date     trydate;    // the time when the url was last time tried to load
        private int      trycount;   // number of tryings
        private String   failreason; // string describing reason for load fail
        private bitfield flags;      // extra space

	public Entry(URL url, String referrer, String initiator, String executor, String name, String failreason, bitfield flags) {
	    // create new entry and store it into database
	    this.hash       = urlHash(url);
	    this.referrer   = (referrer == null) ? dummyHash : referrer;
            this.initiator  = initiator;
            this.executor   = executor;
            this.url        = url;
	    this.name       = name;
            this.initdate   = new Date();
            this.trydate    = new Date();
	    this.trycount   = 0;
	    this.failreason = failreason;
            this.flags      = flags;
	    
	}

	    public Entry(String hash) throws IOException {
            // generates an plasmaEURLEntry using the url hash
            // to speed up the access, the url-hashes are buffered
            // in the hash cache.
            // we have two options to find the url:
            // - look into the hash cache
            // - look into the filed properties
            // if the url cannot be found, this returns null
            this.hash = hash;
            kelondroRow.Entry entry = urlHashCache.get(hash.getBytes());
            if (entry != null) {
                this.referrer = entry.getColString(1, "UTF-8");
                this.initiator = entry.getColString(2, "UTF-8");
                this.executor = entry.getColString(3, "UTF-8");
                this.url = new URL(entry.getColString(4, "UTF-8").trim());
                this.name = entry.getColString(5, "UTF-8").trim();
                this.initdate = new Date(86400000 * entry.getColLongB64E(6));
                this.trydate = new Date(86400000 * entry.getColLongB64E(7));
                this.trycount = (int) entry.getColLongB64E(8);
                this.failreason = entry.getColString(9, "UTF-8");
                this.flags = new bitfield(entry.getColBytes(10));
                return;
            }
        }
        
	private void store() {
	    // stores the values from the object variables into the database
            String initdatestr = kelondroBase64Order.enhancedCoder.encodeLong(initdate.getTime() / 86400000, urlDateLength);
            String trydatestr = kelondroBase64Order.enhancedCoder.encodeLong(trydate.getTime() / 86400000, urlDateLength);

	    // store the hash in the hash cache
	    try {
		// even if the entry exists, we simply overwrite it
		byte[][] entry = new byte[][] {
		    this.hash.getBytes(),
                    this.referrer.getBytes(),
                    this.initiator.getBytes(),
                    this.executor.getBytes(),
                    this.url.toString().getBytes(),
                    this.name.getBytes(),
                    initdatestr.getBytes(),
                    trydatestr.getBytes(),
                    kelondroBase64Order.enhancedCoder.encodeLong(this.trycount, urlRetryLength).getBytes(),
                    this.failreason.getBytes(),
                    this.flags.getBytes()
		    };
            urlHashCache.put(urlHashCache.row().newEntry(entry));
	    } catch (IOException e) {
		System.out.println("INTERNAL ERROR AT plasmaEURL:url2hash:" + e.toString());
	    }
	}

	public String hash() {
	    // return a url-hash, based on the md5 algorithm
	    // the result is a String of 12 bytes within a 72-bit space
	    // (each byte has an 6-bit range)
	    // that should be enough for all web pages on the world
	    return this.hash;
	}

        public String referrer() {
	    return this.referrer;
	}
        
	public URL url() {
	    return url;
	}

	public Date initdate() {
	    return trydate;
	}

	public Date trydate() {
	    return trydate;
	}

	public String initiator() {
	    // return the creator's hash
	    return initiator;
	}
        
        public String executor() {
	    // return the creator's hash
	    return executor;
	}
        
        public String name() {
	    // return the creator's hash
	    return name;
	}
        
        public String failreason() {
            return failreason;
        }

    }

    public class kenum implements Enumeration {
        // enumerates entry elements
        Iterator i;
        public kenum(boolean up, boolean rotating) throws IOException {
            i = urlHashCache.rows(up, rotating, null);
        }
        public boolean hasMoreElements() {
            return i.hasNext();
        }
	    public Object nextElement() {
            try {
                return new Entry(new String(((byte[][]) i.next())[0]));
            } catch (IOException e) {
                return null;
            }
        }
    }
    
    public Enumeration elements(boolean up, boolean rotating) throws IOException {
	// enumerates entry elements
	return new kenum(up, rotating);
    }
    
}
