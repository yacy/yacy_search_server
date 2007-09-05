// abstractURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 12. August 2006 (theli) ?
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.plasma.urlPattern;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.yacy.yacyURL;

public abstract class abstractURLPattern implements plasmaURLPattern {

    protected static final HashSet BLACKLIST_TYPES = new HashSet(Arrays.asList(new String[]{
            plasmaURLPattern.BLACKLIST_CRAWLER,
            plasmaURLPattern.BLACKLIST_PROXY,
            plasmaURLPattern.BLACKLIST_DHT,
            plasmaURLPattern.BLACKLIST_SEARCH,
            plasmaURLPattern.BLACKLIST_SURFTIPS,
            plasmaURLPattern.BLACKLIST_NEWS
    }));
    public static final String BLACKLIST_TYPES_STRING="proxy,crawler,dht,search,surftips,news";
    
    protected File blacklistRootPath = null;
    protected HashMap cachedUrlHashs = null; 
    protected HashMap /* <blacklistType,HashMap<host,ArrayList<path>>> */ hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    
    public abstractURLPattern(File rootPath) {
        this.setRootPath(rootPath);
        
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
    
    public void setRootPath(File rootPath) {
        if (rootPath == null) 
            throw new NullPointerException("The blacklist root path must not be null.");
        if (!rootPath.isDirectory()) 
            throw new IllegalArgumentException("The blacklist root path is not a directory.");
        if (!rootPath.canRead())
            throw new IllegalArgumentException("The blacklist root path is not readable.");
        
        this.blacklistRootPath = rootPath;
    }
    
    protected HashMap getBlacklistMap(String blacklistType) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown blacklist type: "+blacklistType+".");        
        
        return (HashMap) this.hostpaths.get(blacklistType);
    }
    
    protected Set getCacheUrlHashsSet(String blacklistType) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        

        return (Set) this.cachedUrlHashs.get(blacklistType);
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
            Iterator blIter = ((HashMap)this.hostpaths.get(iter.next())).values().iterator();
            while (blIter.hasNext())
                size += ((ArrayList)blIter.next()).size();
        }
        return size;
    }
    
    public void loadList(blacklistFile[] blFiles, String sep) {        
        for (int j = 0; j < blFiles.length; j++) {
            blacklistFile blf = blFiles[j];
            loadList(blf.getType(), blf.getFileName(), sep);
        }
    }
    
    public void loadList(String blacklistType, String filenames, String sep) {
        HashMap blacklistMap = getBlacklistMap(blacklistType);
        String[] filenamesarray = filenames.split(",");

        if (filenamesarray.length > 0) {
            for (int i = 0; i < filenamesarray.length; i++) {
                blacklistMap.putAll(kelondroMSetTools.loadMapMultiValsPerKey(new File(this.blacklistRootPath, filenamesarray[i]).toString(), sep));
            }
        }
    }
    
    public void removeAll(String blacklistType, String host) {
        HashMap blacklistMap = getBlacklistMap(blacklistType);
        blacklistMap.remove(host);
    }
    
    public void remove(String blacklistType, String host, String path) {
        HashMap blacklistMap = getBlacklistMap(blacklistType);
        ArrayList hostList = (ArrayList)blacklistMap.get(host);
        hostList.remove(path);
        if (hostList.size() == 0)
            blacklistMap.remove(host);
    }

    public void add(String blacklistType, String host, String path) {
        if (host == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        
        HashMap blacklistMap = getBlacklistMap(blacklistType);
        ArrayList hostList = (ArrayList)blacklistMap.get(host.toLowerCase());
        if (hostList == null)
            blacklistMap.put(host.toLowerCase(), (hostList = new ArrayList()));
        hostList.add(path);
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
        Set urlHashCache = getCacheUrlHashsSet(blacklistType);   
        return urlHashCache.contains(urlHash);
    }

    public boolean isListed(String blacklistType, yacyURL url) {

        Set urlHashCache = getCacheUrlHashsSet(blacklistType);        
        if (!urlHashCache.contains(url.hash())) {
            boolean temp = isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
            if (temp) {
                urlHashCache.add(url.hash());
            }
            return temp;   
        }        
        return true;  
    }

}
