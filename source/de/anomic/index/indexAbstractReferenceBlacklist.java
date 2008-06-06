// indexAbstractReference.java
// first published on http://www.yacy.net
// (C) 2007 by Bjoern Krombholz
// last major change: 12. August 2006 (theli) ?
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

package de.anomic.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.yacy.yacyURL;

public abstract class indexAbstractReferenceBlacklist implements indexReferenceBlacklist {

    protected static final HashSet<String> BLACKLIST_TYPES = new HashSet<String>(Arrays.asList(new String[]{
            indexReferenceBlacklist.BLACKLIST_CRAWLER,
            indexReferenceBlacklist.BLACKLIST_PROXY,
            indexReferenceBlacklist.BLACKLIST_DHT,
            indexReferenceBlacklist.BLACKLIST_SEARCH,
            indexReferenceBlacklist.BLACKLIST_SURFTIPS,
            indexReferenceBlacklist.BLACKLIST_NEWS
    }));
    public static final String BLACKLIST_TYPES_STRING="proxy,crawler,dht,search,surftips,news";
    
    protected File blacklistRootPath = null;
    protected HashMap<String, Set<String>> cachedUrlHashs = null; 
    //protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths_matchable = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths_notmatchable = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    
    public indexAbstractReferenceBlacklist(File rootPath) {
        this.setRootPath(rootPath);
        
        this.blacklistRootPath = rootPath;
        
        // prepare the data structure
        //this.hostpaths = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.hostpaths_matchable = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.hostpaths_notmatchable = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.cachedUrlHashs = new HashMap<String, Set<String>>();
        
        Iterator<String> iter = BLACKLIST_TYPES.iterator();
        while (iter.hasNext()) {
            String blacklistType = iter.next();
            //this.hostpaths.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.hostpaths_matchable.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.hostpaths_notmatchable.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.cachedUrlHashs.put(blacklistType, Collections.synchronizedSet(new HashSet<String>()));
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
    
    protected HashMap<String, ArrayList<String>> getBlacklistMap(String blacklistType,boolean matchable) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown blacklist type: "+blacklistType+".");        
        
        return (matchable)? this.hostpaths_matchable.get(blacklistType) : this.hostpaths_notmatchable.get(blacklistType);
    }
    
    protected Set<String> getCacheUrlHashsSet(String blacklistType) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        

        return this.cachedUrlHashs.get(blacklistType);
    }
    
    public void clear() {
        for(HashMap<String, ArrayList<String>> entry: this.hostpaths_matchable.values()) {
            entry.clear();
        }
        for(HashMap<String, ArrayList<String>> entry: this.hostpaths_notmatchable.values()) {
            entry.clear();
        }
        for(Set<String> entry: this.cachedUrlHashs.values()) {
            entry.clear();
        }
    }

    public int size() {
        int size = 0;
        for(String entry: this.hostpaths_matchable.keySet()) {
            for(ArrayList<String> ientry: this.hostpaths_matchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        for(String entry: this.hostpaths_notmatchable.keySet()) {
            for(ArrayList<String> ientry: this.hostpaths_notmatchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        return size;
    }
    
    public void loadList(blacklistFile[] blFiles, String sep) {        
        for (int j = 0; j < blFiles.length; j++) {
            blacklistFile blf = blFiles[j];
            loadList(blf.getType(), blf.getFileName(), sep);
        }
    }
    
    public void loadList(blacklistFile blFile, String sep) {
        HashMap<String, ArrayList<String>> blacklistMapMatch = getBlacklistMap(blFile.getType(),true);
        HashMap<String, ArrayList<String>> blacklistMapNotMatch = getBlacklistMap(blFile.getType(),false);
        Set<Map.Entry<String, ArrayList<String>>> loadedBlacklist;
        Map.Entry<String, ArrayList<String>> loadedEntry;
        ArrayList<String> paths;
        ArrayList<String> loadedPaths;

        String[] fileNames = blFile.getFileNamesUnified();
        if (fileNames.length > 0) {
            for (int i = 0; i < fileNames.length; i++) {
                // make sure all requested blacklist files exist
                File file = new File(this.blacklistRootPath, fileNames[i]);
                try {
                    file.createNewFile();
                } catch (IOException e) { /* */ }
                
                // join all blacklists from files into one internal blacklist map
                loadedBlacklist = kelondroMSetTools.loadMapMultiValsPerKey(file.toString(), sep).entrySet();
                for (Iterator<Map.Entry<String, ArrayList<String>>> mi = loadedBlacklist.iterator(); mi.hasNext(); ) {
                    loadedEntry = mi.next();
                    loadedPaths = loadedEntry.getValue();
                    
                    // create new entry if host mask unknown, otherwise merge
                    // existing one with path patterns from blacklist file 
                    paths = (isMatchable(loadedEntry.getKey())) ? blacklistMapMatch.get(loadedEntry.getKey()) : blacklistMapNotMatch.get(loadedEntry.getKey());
                    if (paths == null) {
                        if(isMatchable(loadedEntry.getKey()))
                            blacklistMapMatch.put(loadedEntry.getKey(), loadedPaths);
                        else
                            blacklistMapNotMatch.put(loadedEntry.getKey(), loadedPaths);
                    } else {
                        // TODO check for duplicates? (refactor List -> Set)
                        paths.addAll(loadedPaths);
                    }
                }
            }
        }
    }
    
    public void loadList(String blacklistType, String fileNames, String sep) {
        // method for not breaking older plasmaURLPattern interface
        blacklistFile blFile = new blacklistFile(fileNames, blacklistType);
        
        loadList(blFile, sep);
    }
    
    public void removeAll(String blacklistType, String host) {
        getBlacklistMap(blacklistType,true).remove(host);
        getBlacklistMap(blacklistType,false).remove(host);
    }
    
    public void remove(String blacklistType, String host, String path) {
        
        HashMap<String, ArrayList<String>> blacklistMap = getBlacklistMap(blacklistType,true);
        ArrayList<String> hostList = blacklistMap.get(host);
        if(hostList != null) {
            hostList.remove(path);
            if (hostList.size() == 0)
                blacklistMap.remove(host);
        }
        HashMap<String, ArrayList<String>> blacklistMapNotMatch = getBlacklistMap(blacklistType,false);
        hostList = blacklistMapNotMatch.get(host);
        if(hostList != null) {
            hostList.remove(path);
            if (hostList.size() == 0)
                blacklistMapNotMatch.remove(host);
        }
}   

    public void add(String blacklistType, String host, String path) {
        if (host == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        
        HashMap<String, ArrayList<String>> blacklistMap;
        blacklistMap = (isMatchable(host)) ? getBlacklistMap(blacklistType,true) : getBlacklistMap(blacklistType,false);
        
        // avoid PatternSyntaxException e
        if(!isMatchable(host) && host.startsWith("*"))
            host = "." + host;
        
        ArrayList<String> hostList = blacklistMap.get(host.toLowerCase());
        if (hostList == null) blacklistMap.put(host.toLowerCase(), (hostList = new ArrayList<String>()));
        hostList.add(path);
    }

    public int blacklistCacheSize() {
        int size = 0;
        Iterator<String> iter = this.cachedUrlHashs.keySet().iterator();
        while (iter.hasNext()) {
            Set<String> blacklistMap = this.cachedUrlHashs.get(iter.next());
            size += blacklistMap.size();
        }
        return size;        
    }

    public boolean hashInBlacklistedCache(String blacklistType, String urlHash) {
        Set<String> urlHashCache = getCacheUrlHashsSet(blacklistType);   
        return urlHashCache.contains(urlHash);
    }

    public boolean isListed(String blacklistType, yacyURL url) {

        Set<String> urlHashCache = getCacheUrlHashsSet(blacklistType);        
        if (!urlHashCache.contains(url.hash())) {
            boolean temp = isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
            if (temp) {
                urlHashCache.add(url.hash());
            }
            return temp;   
        }        
        return true;  
    }
    public static boolean isMatchable (String host) {
        try {
            if(Pattern.matches("^[a-z0-9.-]*$", host)) // simple Domain (yacy.net or www.yacy.net)
                return true;
            if(Pattern.matches("^\\*\\.[a-z0-9-.]*$", host)) // start with *. (not .* and * must follow a dot)
                return true;
            if(Pattern.matches("^[a-z0-9-.]*\\.\\*$", host)) // ends with .* (not *. and befor * must be a dot)
                return true;
        } catch (PatternSyntaxException e) {
            //System.out.println(e.toString());
            return false;
        }
       return false;
    }

}
