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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;

public class plasmaCrawlNURL extends plasmaURL {

    public static final int STACK_TYPE_NULL     =  0; // do not stack
    public static final int STACK_TYPE_CORE     =  1; // put on local stack
    public static final int STACK_TYPE_LIMIT    =  2; // put on global stack
    public static final int STACK_TYPE_OVERHANG =  3; // put on overhang stack; links that are known but not crawled
    public static final int STACK_TYPE_REMOTE   =  4; // put on remote-triggered stack
    public static final int STACK_TYPE_IMAGE    = 11; // put on image stack
    public static final int STACK_TYPE_MOVIE    = 12; // put on movie stack
    public static final int STACK_TYPE_MUSIC    = 13; // put on music stack
    
    private kelondroStack coreStack;      // links found by crawling to depth-1
    private kelondroStack limitStack;     // links found by crawling at target depth
    private kelondroStack overhangStack;  // links found by crawling at depth+1
    private kelondroStack remoteStack;    // links from remote crawl orders
    private kelondroStack imageStack;     // links pointing to image resources
    private kelondroStack movieStack;     // links pointing to movie resources
    private kelondroStack musicStack;     // links pointing to music resources
    
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
        
        File coreStackFile = new File(cacheStacksPath, "urlNoticeLocal0.stack");
        File limitStackFile = new File(cacheStacksPath, "urlNoticeLimit0.stack");
        File overhangStackFile = new File(cacheStacksPath, "urlNoticeOverhang0.stack");
        File remoteStackFile = new File(cacheStacksPath, "urlNoticeRemote0.stack");
        File imageStackFile = new File(cacheStacksPath, "urlNoticeImage0.stack");
        File movieStackFile = new File(cacheStacksPath, "urlNoticeMovie0.stack");
        File musicStackFile = new File(cacheStacksPath, "urlNoticeMusic0.stack");
        if (coreStackFile.exists()) coreStack = new kelondroStack(coreStackFile, 0); else coreStack = new kelondroStack(coreStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (limitStackFile.exists()) limitStack = new kelondroStack(limitStackFile, 0); else limitStack = new kelondroStack(limitStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (overhangStackFile.exists()) overhangStack = new kelondroStack(overhangStackFile, 0); else overhangStack = new kelondroStack(overhangStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (remoteStackFile.exists()) remoteStack = new kelondroStack(remoteStackFile, 0); else remoteStack = new kelondroStack(remoteStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (imageStackFile.exists()) imageStack = new kelondroStack(imageStackFile, 0); else imageStack = new kelondroStack(imageStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (movieStackFile.exists()) movieStack = new kelondroStack(movieStackFile, 0); else movieStack = new kelondroStack(movieStackFile, 0, new int[] {plasmaURL.urlHashLength});
        if (musicStackFile.exists()) musicStack = new kelondroStack(musicStackFile, 0); else musicStack = new kelondroStack(musicStackFile, 0, new int[] {plasmaURL.urlHashLength});

        // init stack Index
        stackIndex = new HashSet();
        new initStackIndex().start();
    }

    public class initStackIndex extends Thread {
        public void run() {
            Iterator i;
            try {
                i =     coreStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i =    limitStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i = overhangStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i =   remoteStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i =    imageStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i =    movieStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
                i =    musicStack.iterator(); while (i.hasNext()) stackIndex.add(new String(((kelondroRecords.Node) i.next()).getKey()));
            } catch (IOException e) {}
        }
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
        // this does not count the overhang stack size
        return coreStack.size()  + limitStack.size() + remoteStack.size();
    }
    
    public int stackSize(int stackType) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return coreStack.size();
            case STACK_TYPE_LIMIT:    return limitStack.size();
            case STACK_TYPE_OVERHANG: return overhangStack.size();
            case STACK_TYPE_REMOTE:   return remoteStack.size();
            case STACK_TYPE_IMAGE:    return imageStack.size();
            case STACK_TYPE_MOVIE:    return movieStack.size();
            case STACK_TYPE_MUSIC:    return musicStack.size();
            default: return -1;
        }
    }
    
    public boolean existsInStack(String urlhash) {
        return stackIndex.contains(urlhash);
    }
    
    public synchronized entry newEntry(String initiator, URL url, Date loaddate, String referrer, String name,
                String profile, int depth, int anchors, int forkfactor, int stackMode) {
	entry e = new entry(initiator, url, referrer, name, loaddate, profile,
                     depth, anchors, forkfactor);
        try {
            switch (stackMode) {
                case STACK_TYPE_CORE:     coreStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_LIMIT:    limitStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_OVERHANG: overhangStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_REMOTE:   remoteStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_IMAGE:    imageStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_MOVIE:    movieStack.push(new byte[][] {e.hash.getBytes()}); break;
                case STACK_TYPE_MUSIC:    musicStack.push(new byte[][] {e.hash.getBytes()}); break;
                default: break;
            }
            stackIndex.add(new String(e.hash.getBytes()));
        } catch (IOException er) {
        }
        return e;
    }

    public entry[] top(int stackType, int count) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return top(coreStack, count);
            case STACK_TYPE_LIMIT:    return top(limitStack, count);
            case STACK_TYPE_OVERHANG: return top(overhangStack, count);
            case STACK_TYPE_REMOTE:   return top(remoteStack, count);
            case STACK_TYPE_IMAGE:    return top(imageStack, count);
            case STACK_TYPE_MOVIE:    return top(movieStack, count);
            case STACK_TYPE_MUSIC:    return top(musicStack, count);
            default: return null;
        }
    }
    
    public entry pop(int stackType) {
        switch (stackType) {
            case STACK_TYPE_CORE:     return pop(coreStack);
            case STACK_TYPE_LIMIT:    return pop(limitStack);
            case STACK_TYPE_OVERHANG: return pop(overhangStack);
            case STACK_TYPE_REMOTE:   return pop(remoteStack);
            case STACK_TYPE_IMAGE:    return pop(imageStack);
            case STACK_TYPE_MOVIE:    return pop(movieStack);
            case STACK_TYPE_MUSIC:    return pop(musicStack);
            default: return null;
        }
    }
    
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

        private String   initiator;     // the initiator hash, is NULL or "" if it is the own proxy;
                                        // if this is generated by a crawl, the own peer hash in entered
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
		System.out.println("INTERNAL ERROR AT plasmaNURL:store:" + e.toString());
	    } catch (kelondroException e) {
                serverLog.logError("PLASMA", "plasmaCrawlNURL.store failed: " + e.getMessage());
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

    /*
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
    */
}
