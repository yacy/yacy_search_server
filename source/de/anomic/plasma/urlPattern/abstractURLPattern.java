package de.anomic.plasma.urlPattern;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.net.URL;

public abstract class abstractURLPattern implements plasmaURLPattern {

    protected static final HashSet BLACKLIST_TYPES = new HashSet(Arrays.asList(new String[]{
            plasmaURLPattern.BLACKLIST_CRAWLER,
            plasmaURLPattern.BLACKLIST_PROXY,
            plasmaURLPattern.BLACKLIST_DHT,
            plasmaURLPattern.BLACKLIST_SEARCH
    }));
    
    protected File blacklistRootPath = null;
    protected HashMap cachedUrlHashs = null; 
    protected HashMap hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    
    
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
    
    protected HashMap geBlacklistMap(String blacklistType) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        
        
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
            HashMap blacklistMap = (HashMap) this.hostpaths.get(iter.next());
            size += blacklistMap.size();
        }
        return size;
    }
    
    public void loadList(String[][] filenames, String sep) {        
        for (int j = 0; j < filenames.length; j++) {
            String[] nextFile = filenames[j];
            String blacklistType = nextFile[0];
            String fileName = nextFile[1];
            this.loadList(blacklistType, fileName, sep);
        }
    }
    
    public void loadList(String blacklistType, String filenames, String sep) {
        
        HashMap blacklistMap = geBlacklistMap(blacklistType);
        String[] filenamesarray = filenames.split(",");

        if( filenamesarray.length > 0) {
            for (int i = 0; i < filenamesarray.length; i++) {
                blacklistMap.putAll(kelondroMSetTools.loadMap(new File(this.blacklistRootPath, filenamesarray[i]).toString(), sep));
            }
        }        
    }
    
    public void remove(String blacklistType, String host) {
        
        HashMap blacklistMap = geBlacklistMap(blacklistType);
        blacklistMap.remove(host);
    }

    public void add(String blacklistType, String host, String path) {
        if (host == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        
        HashMap blacklistMap = geBlacklistMap(blacklistType);        
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
        Set urlHashCache = getCacheUrlHashsSet(blacklistType);   
        return urlHashCache.contains(urlHash);
    }

    public boolean isListed(String blacklistType, String urlHash, URL url) {

        Set urlHashCache = getCacheUrlHashsSet(blacklistType);        
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
    
}
