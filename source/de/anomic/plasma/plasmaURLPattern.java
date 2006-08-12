// plasmaURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 11.07.2005
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

package de.anomic.plasma;

import java.io.File;
import de.anomic.net.URL;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroMSetTools;

public class plasmaURLPattern {
    
    public static final String BLACKLIST_CRAWLER = "crawler";
    public static final String BLACKLIST_PROXY = "proxy";
    public static final String BLACKLIST_DHT = "dht";
    public static final String BLACKLIST_SEARCH = "search";
    
    public static final HashSet BLACKLIST_TYPES = new HashSet(Arrays.asList(new String[]{
            BLACKLIST_CRAWLER,
            BLACKLIST_PROXY,
            BLACKLIST_DHT,
            BLACKLIST_SEARCH
    }));
    

    private File blacklistRootPath = null;
    private HashMap cachedUrlHashs = null; 
    private HashMap hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here

    public plasmaURLPattern(File rootPath) {
        super();
        this.blacklistRootPath = rootPath;
        
        // prepare the data structure
        this.hostpaths = new HashMap();
        this.cachedUrlHashs = new HashMap();
        
        Iterator iter = BLACKLIST_TYPES.iterator();
        while (iter.hasNext()) {
            String blacklistType = (String) iter.next();
            this.hostpaths.put(blacklistType, new HashMap());
            this.cachedUrlHashs.put(blacklistType, Collections.synchronizedSet(new HashSet()));
        }        
    }

    public void clear() {
        Iterator iter = this.hostpaths.keySet().iterator();
        while (iter.hasNext()) {
            HashMap blacklistMap = (HashMap) this.hostpaths.get(iter.next());
            blacklistMap.clear();
        }
    }

    public int size() {
        int size = 0;
        Iterator iter = this.hostpaths.keySet().iterator();
        while (iter.hasNext()) {
            HashMap blacklistMap = (HashMap) this.hostpaths.get(iter.next());
            size += blacklistMap.size();
        }
        return size;
    }
    
    public void loadList(String blacklistType, String filenames, String sep) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");
        
        HashMap blacklistMap = (HashMap) this.hostpaths.get(blacklistType);
        String[] filenamesarray = filenames.split(",");

        if( filenamesarray.length > 0) {
            for (int i = 0; i < filenamesarray.length; i++) {
                blacklistMap.putAll(kelondroMSetTools.loadMap(new File(this.blacklistRootPath, filenamesarray[i]).toString(), sep));
            }
        }        
    }
    
    public void loadList(String[][] filenames, String sep) {        
        for (int j = 0; j < filenames.length; j++) {
            String[] nextFile = filenames[j];
            String blacklistType = nextFile[0];
            String fileName = nextFile[1];
            this.loadList(blacklistType, fileName, sep);
        }
    }

    public void remove(String blacklistType, String host) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");
        
        HashMap blacklistMap = (HashMap) this.hostpaths.get(blacklistType);
        blacklistMap.remove(host);
    }

    public void add(String blacklistType, String host, String path) {
        if (host == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        
        HashMap blacklistMap = (HashMap) this.hostpaths.get(blacklistType);
        blacklistMap.put(host.toLowerCase(), path);
    }

    public int blacklistCacheSize() {
        int size = 0;
        Iterator iter = this.cachedUrlHashs.keySet().iterator();
        while (iter.hasNext()) {
            Set blacklistMap = (Set) this.cachedUrlHashs.get(iter.next());
            size += blacklistMap.size();
        }
        return size;        
    }

    public boolean hashInBlacklistedCache(String blacklistType, String urlHash) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        
        
        Set urlHashCache = (Set) this.cachedUrlHashs.get(blacklistType);   
        return urlHashCache.contains(urlHash);
    }

    public boolean isListed(String blacklistType, String urlHash, URL url) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");         
        
        Set urlHashCache = (Set) this.cachedUrlHashs.get(blacklistType);        
        if (!urlHashCache.contains(urlHash)) {
            boolean temp = isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
            if (temp) {
                urlHashCache.add(urlHash);
            }
            return temp;   
        }        
        return true;  
    }
    
    public boolean isListed(String blacklistType, URL url) {
        return isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
    }
    
    public boolean isListed(String blacklistType, String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        
        
        // getting the proper blacklist
        HashMap blacklistMap = (HashMap) this.hostpaths.get(blacklistType);
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        String pp = ""; // path-pattern

        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while ((index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((pp = (String) blacklistMap.get(hostlow.substring(0, index + 1) + "*")) != null) {
                return ((pp.equals("*")) || (path.matches(pp)));
            }
        }
        index = hostlow.length();
        while ((index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((pp = (String) blacklistMap.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                return ((pp.equals("*")) || (path.matches(pp)));
            }
        }

        // try to match without wildcard in domain
        return (((pp = (String) blacklistMap.get(hostlow)) != null) &&
                ((pp.equals("*")) || (path.matches(pp))));
    }

}
