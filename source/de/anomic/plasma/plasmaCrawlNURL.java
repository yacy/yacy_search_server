// plasmaNURL.java 
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

// NURL - noticed (known but not loaded) URL's

package de.anomic.plasma;

import java.io.*;
import java.net.*;
import java.util.*;
import de.anomic.kelondro.*;
import de.anomic.server.*;
import de.anomic.tools.*;
import de.anomic.http.*;
import de.anomic.yacy.*;

public class plasmaCrawlNURL extends plasmaURL {

    
    private kelondroStack localStack;     // links found by crawling to depth-1
    private kelondroStack limitStack;     // links found by crawling at target depth
    private kelondroStack overhangStack;  // links found by crawling at depth+1
    private kelondroStack remoteStack;    // links from remote crawl orders
    
    private HashSet stackIndex;           // to find out if a specific link is already on any stack
    
    public plasmaCrawlNURL(File cacheStacksPath, int bufferkb) throws IOException {
        super();
        int[] ce = {
            urlHashLength,               // the url hash
            urlHashLength,               // initiator
            urlStringLength,             // the url as string
            urlHashLength,               // the url's referrer hash
            urlNameLength,               // the name of the url, from anchor tag <a>name</a>
            urlDateLength,               // the time when the url was first time appeared
            urlCrawlProfileHandleLength, // the name of the prefetch profile handle
            urlCrawlDepthLength,         // the prefetch depth so far, starts at 0
            urlParentBranchesLength,     // number of anchors of the parent
            urlForkFactorLength,         // sum of anchors of all ancestors
            urlFlagLength,               // extra space
            urlHandleLength              // extra handle
        };
        
	// create a stack for newly entered entries
        if (!(cacheStacksPath.exists())) cacheStacksPath.mkdir(); // make the path
        
        File cacheFile = new File(cacheStacksPath, "urlNotice1.db");
        if (cacheFile.exists()) {
	    // open existing cache
	    urlHashCache = new kelondroTree(cacheFile, bufferkb * 0x400);
	} else {
	    // create new cache
	    cacheFile.getParentFile().mkdirs();
	    urlHashCache = new kelondroTree(cacheFile, bufferkb * 0x400, ce);
	}
        
	File localCrawlStack = new File(cacheStacksPath, "urlNoticeLocal0.stack");
        if (localCrawlStack.exists()) {
	    localStack = new kelondroStack(localCrawlStack, 0);
	} else {
	    localStack = new kelondroStack(localCrawlStack, 0, new int[] {plasmaURL.urlHashLength});
	}
        File globalCrawlStack = new File(cacheStacksPath, "urlNoticeRemote0.stack");
        if (globalCrawlStack.exists()) {
	    remoteStack = new kelondroStack(globalCrawlStack, 0);
	} else {
	    remoteStack = new kelondroStack(globalCrawlStack, 0, new int[] {plasmaURL.urlHashLength});
	}
        
        // init stack Index
        stackIndex = new HashSet();
        Iterator i = localStack.iterator();
        while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
        i = remoteStack.iterator();
        while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
    }

    private static String normalizeHost(String host) {
	if (host.length() > urlHostLength) host = host.substring(0, urlHostLength);
	host = host.toLowerCase();
	while (host.length() < urlHostLength) host = host + " ";
	return host;
    }

    private static String normalizeHandle(int h) {
	String d = Integer.toHexString(h);
	while (d.length() < urlHandleLength) d = "0" + d;
	return d;
    }
    
    public int stackSize() {
        return localStack.size() + remoteStack.size();
    }
    public int localStackSize() {
        return localStack.size();
    }
    public int remoteStackSize() {
        return remoteStack.size();
    }
    
    public boolean existsInStack(String urlhash) {
        return stackIndex.contains(urlhash);
    }
    
    public synchronized entry newEntry(String initiator, URL url, Date loaddate, String referrer, String name,
                String profile, int depth, int anchors, int forkfactor, int stackMode) {
	entry e = new entry(initiator, url, referrer, name, loaddate, profile,
                     depth, anchors, forkfactor);
        
        // stackMode can have 3 cases:
        // 0 = do not stack
        // 1 = on local stack
        // 2 = on global stack
        // 3 = on overhang stack
        // 4 = on remote stack
        try {
            if (stackMode == 1) {
                localStack.push(new byte[][] {e.hash.getBytes()});
                stackIndex.add(new String(e.hash.getBytes()));
            }
            if (stackMode == 4) {
                remoteStack.push(new byte[][] {e.hash.getBytes()});
                stackIndex.add(new String(e.hash.getBytes()));
            }
        } catch (IOException er) {
        }
        return e;
    }

    public entry localPop() { return pop(localStack); }
    public entry[] localTop(int count) { return top(localStack, count); }
    
    public entry remotePop() { return pop(remoteStack); }
    public entry[] remoteTop(int count) { return top(remoteStack, count); }
    
    private entry pop(kelondroStack stack) {
	// this is a filo - pop
	try {
	    if (stack.size() > 0) {
                entry e = new entry(new String(stack.pop()[0]));
                stackIndex.remove(e.hash);
                return e;
	    } else {
		return null;
	    }
	} catch (IOException e) {
	    return null;
	}
    }

    private entry[] top(kelondroStack stack, int count) {
	// this is a filo - top
        if (count > stack.size()) count = stack.size();
        entry[] list = new entry[count];
	try {
            for (int i = 0; i < count; i++) {
		list[i] = new entry(new String(stack.top(i)[0]));
	    }
            return list;
        } catch (IOException e) {
	    return null;
	}
    }
    
    public synchronized entry getEntry(String hash) {
	return new entry(hash);
    }

    public synchronized void remove(String hash) {
        try {
            urlHashCache.remove(hash.getBytes());
        } catch (IOException e) {}
    }
    
    public class entry {

        private String   initiator;     // the initiator hash, is NULL or "" if it is the own proxy
	private String   hash;          // the url's hash
        private String   referrer;      // the url's referrer hash
        private URL      url;           // the url as string
        private String   name;          // the name of the url, from anchor tag <a>name</a>     
        private Date     loaddate;      // the time when the url was first time appeared
	private String   profileHandle; // the name of the prefetch profile
        private int      depth;         // the prefetch depth so far, starts at 0
        private int      anchors;       // number of anchors of the parent
        private int      forkfactor;    // sum of anchors of all ancestors
        private bitfield flags;
        private int      handle;
        
	public entry(String initiator, URL url, String referrer, String name, Date loaddate, String profileHandle,
                     int depth, int anchors, int forkfactor) {
	    // create new entry and store it into database
	    this.hash          = urlHash(url);
            this.initiator     = initiator;
	    this.url           = url;
            this.referrer      = (referrer == null) ? "------------" : referrer;
            this.name          = name;
            this.loaddate      = loaddate;
            this.profileHandle = profileHandle;
            this.depth         = depth;
            this.anchors       = anchors;
            this.forkfactor    = forkfactor;
            this.flags         = new bitfield(urlFlagLength);
            this.handle        = 0;
	    store();
	}

	public entry(String hash) {
	    // generates an plasmaNURLEntry using the url hash
	    // to speed up the access, the url-hashes are buffered
	    // in the hash cache.
	    // we have two options to find the url:
	    // - look into the hash cache
	    // - look into the filed properties
	    // if the url cannot be found, this returns null
	    this.hash = hash;
	    try {
		byte[][] entry = urlHashCache.get(hash.getBytes());
		if (entry != null) {
                    this.initiator     = new String(entry[1]);
		    this.url           = new URL(new String(entry[2]).trim());
                    this.referrer      = new String(entry[3]);
                    this.name          = new String(entry[4]).trim();
                    this.loaddate      = new Date(86400000 * serverCodings.enhancedCoder.decodeBase64Long(new String(entry[5])));
                    this.profileHandle = new String(entry[6]).trim();
                    this.depth         = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[7]));
                    this.anchors       = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[8]));
                    this.forkfactor    = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[9]));
                    this.flags         = new bitfield(entry[10]);
                    this.handle        = Integer.parseInt(new String(entry[11]));
		    return;
		}
	    } catch (Exception e) {
	    }
	}

	private void store() {
	    // stores the values from the object variables into the database
            String loaddatestr = serverCodings.enhancedCoder.encodeBase64Long(loaddate.getTime() / 86400000, urlDateLength);

	    // store the hash in the hash cache
	    try {
		// even if the entry exists, we simply overwrite it
		byte[][] entry = new byte[][] {
                    this.hash.getBytes(),
                    (initiator == null) ? "".getBytes() : this.initiator.getBytes(),
		    this.url.toString().getBytes(),
                    this.referrer.getBytes(),
                    this.name.getBytes(),
                    loaddatestr.getBytes(),
                    this.profileHandle.getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.depth, urlCrawlDepthLength).getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.anchors, urlParentBranchesLength).getBytes(),
                    serverCodings.enhancedCoder.encodeBase64Long(this.forkfactor, urlForkFactorLength).getBytes(),
                    this.flags.getBytes(),
                    normalizeHandle(this.handle).getBytes()
		};
		urlHashCache.put(entry);
	    } catch (IOException e) {
		System.out.println("INTERNAL ERROR AT plasmaNURL:url2hash:" + e.toString());
	    }
	}
        
	public String hash() {
	    // return a url-hash, based on the md5 algorithm
	    // the result is a String of 12 bytes within a 72-bit space
	    // (each byte has an 6-bit range)
	    // that should be enough for all web pages on the world
	    return this.hash;
	}
        public String initiator() {
            if (initiator == null) return null;
            if (initiator.length() == 0) return null; 
            return initiator;
        }
        public boolean proxy() {
            return (initiator() == null);
        }
        public String referrerHash() {
	    return this.referrer;
	}
	public URL url() {
	    return url;
	}
	public Date loaddate() {
	    return loaddate;
	}
        public String name() {
	    // return the creator's hash
	    return name;
	}
        public int depth() {
            return depth;
        }
        public String profileHandle() {
            return profileHandle;
        }
    }

    public class kenum implements Enumeration {
	// enumerates entry elements
	kelondroTree.rowIterator i;
	public kenum(boolean up, boolean rotating) throws IOException {
            i = urlHashCache.rows(up, rotating);
        }
	public boolean hasMoreElements() {
            return i.hasNext();
        }
	public Object nextElement() {
            return new entry(new String(((byte[][]) i.next())[0]));
        }
    }
    
    public Enumeration elements(boolean up, boolean rotating) throws IOException {
	// enumerates entry elements
	return new kenum(up, rotating);
    }
    
}
