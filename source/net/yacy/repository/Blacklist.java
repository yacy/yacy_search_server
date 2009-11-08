// indexDefaultReference.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2005 on http://yacy.net
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

package net.yacy.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.SetTools;

public class Blacklist {


    public static final String BLACKLIST_DHT      = "dht";
    public static final String BLACKLIST_CRAWLER  = "crawler";
    public static final String BLACKLIST_PROXY    = "proxy";
    public static final String BLACKLIST_SEARCH   = "search";
    public static final String BLACKLIST_SURFTIPS = "surftips";
    public static final String BLACKLIST_NEWS     = "news";

    public final static String BLACKLIST_FILENAME_FILTER = "^.*\\.black$";
    
    public static final int ERR_TWO_WILDCARDS_IN_HOST = 1;
    public static final int ERR_SUBDOMAIN_XOR_WILDCARD = 2;
    public static final int ERR_PATH_REGEX = 3;
    public static final int ERR_WILDCARD_BEGIN_OR_END = 4;
    public static final int ERR_HOST_WRONG_CHARS = 5;
    public static final int ERR_DOUBLE_OCCURANCE = 6;
    public static final int ERR_HOST_REGEX = 7;

    protected static final HashSet<String> BLACKLIST_TYPES = new HashSet<String>(Arrays.asList(new String[]{
            Blacklist.BLACKLIST_CRAWLER,
            Blacklist.BLACKLIST_PROXY,
            Blacklist.BLACKLIST_DHT,
            Blacklist.BLACKLIST_SEARCH,
            Blacklist.BLACKLIST_SURFTIPS,
            Blacklist.BLACKLIST_NEWS
    }));
    public static final String BLACKLIST_TYPES_STRING="proxy,crawler,dht,search,surftips,news";
    
    protected File blacklistRootPath = null;
    protected HashMap<String, Set<String>> cachedUrlHashs = null; 
    //protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths_matchable = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    protected HashMap<String, HashMap<String, ArrayList<String>>> hostpaths_notmatchable = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    
    public Blacklist(final File rootPath) {
        this.setRootPath(rootPath);
        
        this.blacklistRootPath = rootPath;
        
        // prepare the data structure
        //this.hostpaths = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.hostpaths_matchable = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.hostpaths_notmatchable = new HashMap<String, HashMap<String, ArrayList<String>>>();
        this.cachedUrlHashs = new HashMap<String, Set<String>>();
        
        final Iterator<String> iter = BLACKLIST_TYPES.iterator();
        while (iter.hasNext()) {
            final String blacklistType = iter.next();
            //this.hostpaths.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.hostpaths_matchable.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.hostpaths_notmatchable.put(blacklistType, new HashMap<String, ArrayList<String>>());
            this.cachedUrlHashs.put(blacklistType, Collections.synchronizedSet(new HashSet<String>()));
        }            
    }

    public void setRootPath(final File rootPath) {
        if (rootPath == null) 
            throw new NullPointerException("The blacklist root path must not be null.");
        if (!rootPath.isDirectory()) 
            throw new IllegalArgumentException("The blacklist root path is not a directory.");
        if (!rootPath.canRead())
            throw new IllegalArgumentException("The blacklist root path is not readable.");
        
        this.blacklistRootPath = rootPath;
    }
    
    protected HashMap<String, ArrayList<String>> getBlacklistMap(final String blacklistType,final boolean matchable) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown blacklist type: "+blacklistType+".");        
        
        return (matchable)? this.hostpaths_matchable.get(blacklistType) : this.hostpaths_notmatchable.get(blacklistType);
    }
    
    protected Set<String> getCacheUrlHashsSet(final String blacklistType) {
        if (blacklistType == null) throw new IllegalArgumentException();
        if (!BLACKLIST_TYPES.contains(blacklistType)) throw new IllegalArgumentException("Unknown backlist type.");        

        return this.cachedUrlHashs.get(blacklistType);
    }
    
    public void clear() {
        for(final HashMap<String, ArrayList<String>> entry: this.hostpaths_matchable.values()) {
            entry.clear();
        }
        for(final HashMap<String, ArrayList<String>> entry: this.hostpaths_notmatchable.values()) {
            entry.clear();
        }
        for(final Set<String> entry: this.cachedUrlHashs.values()) {
            entry.clear();
        }
    }

    public int size() {
        int size = 0;
        for(final String entry: this.hostpaths_matchable.keySet()) {
            for(final ArrayList<String> ientry: this.hostpaths_matchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        for(final String entry: this.hostpaths_notmatchable.keySet()) {
            for(final ArrayList<String> ientry: this.hostpaths_notmatchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        return size;
    }
    
    public void loadList(final BlacklistFile[] blFiles, final String sep) {        
        for (int j = 0; j < blFiles.length; j++) {
            final BlacklistFile blf = blFiles[j];
            loadList(blf.getType(), blf.getFileName(), sep);
        }
    }
    
    public void loadList(final BlacklistFile blFile, final String sep) {
        final HashMap<String, ArrayList<String>> blacklistMapMatch = getBlacklistMap(blFile.getType(),true);
        final HashMap<String, ArrayList<String>> blacklistMapNotMatch = getBlacklistMap(blFile.getType(),false);
        Set<Map.Entry<String, ArrayList<String>>> loadedBlacklist;
        Map.Entry<String, ArrayList<String>> loadedEntry;
        ArrayList<String> paths;
        ArrayList<String> loadedPaths;

        final String[] fileNames = blFile.getFileNamesUnified();
        if (fileNames.length > 0) {
            for (int i = 0; i < fileNames.length; i++) {
                // make sure all requested blacklist files exist
                final File file = new File(this.blacklistRootPath, fileNames[i]);
                try {
                    file.createNewFile();
                } catch (final IOException e) { /* */ }
                
                // join all blacklists from files into one internal blacklist map
                loadedBlacklist = SetTools.loadMapMultiValsPerKey(file.toString(), sep).entrySet();
                for (final Iterator<Map.Entry<String, ArrayList<String>>> mi = loadedBlacklist.iterator(); mi.hasNext(); ) {
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
    
    public void loadList(final String blacklistType, final String fileNames, final String sep) {
        // method for not breaking older plasmaURLPattern interface
        final BlacklistFile blFile = new BlacklistFile(fileNames, blacklistType);
        
        loadList(blFile, sep);
    }
    
    public void removeAll(final String blacklistType, final String host) {
        getBlacklistMap(blacklistType,true).remove(host);
        getBlacklistMap(blacklistType,false).remove(host);
    }
    
    public void remove(final String blacklistType, final String host, final String path) {
        
        final HashMap<String, ArrayList<String>> blacklistMap = getBlacklistMap(blacklistType,true);
        ArrayList<String> hostList = blacklistMap.get(host);
        if(hostList != null) {
            hostList.remove(path);
            if (hostList.size() == 0)
                blacklistMap.remove(host);
        }
        final HashMap<String, ArrayList<String>> blacklistMapNotMatch = getBlacklistMap(blacklistType,false);
        hostList = blacklistMapNotMatch.get(host);
        if (hostList != null) {
            hostList.remove(path);
            if (hostList.size() == 0)
                blacklistMapNotMatch.remove(host);
        }
    }

    public void add(final String blacklistType, String host, String path) {
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
        final Iterator<String> iter = this.cachedUrlHashs.keySet().iterator();
        while (iter.hasNext()) {
            final Set<String> blacklistMap = this.cachedUrlHashs.get(iter.next());
            size += blacklistMap.size();
        }
        return size;        
    }

    public boolean hashInBlacklistedCache(final String blacklistType, final String urlHash) {
        final Set<String> urlHashCache = getCacheUrlHashsSet(blacklistType);   
        return urlHashCache.contains(urlHash);
    }

    public boolean contains(final String blacklistType, String host, String path) {
        boolean ret = false;

        if (blacklistType != null && host != null && path != null) {
            HashMap<String, ArrayList<String>> blacklistMap;
            blacklistMap = (isMatchable(host)) ? getBlacklistMap(blacklistType,true) : getBlacklistMap(blacklistType,false);

            // avoid PatternSyntaxException e
            if(!isMatchable(host) && host.startsWith("*"))
                host = "." + host;

            ArrayList<String> hostList = blacklistMap.get(host.toLowerCase());
            if (hostList != null) ret = hostList.contains(path);
        }
        return ret;
    }

    public boolean isListed(final String blacklistType, final DigestURI url) {

        final Set<String> urlHashCache = getCacheUrlHashsSet(blacklistType);        
        if (!urlHashCache.contains(url.hash())) {
            final boolean temp = isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
            if (temp) {
                urlHashCache.add(url.hash());
            }
            return temp;   
        }        
        return true;  
    }
    
    public static boolean isMatchable (final String host) {
        try {
            if(Pattern.matches("^[a-z0-9.-]*$", host)) // simple Domain (yacy.net or www.yacy.net)
                return true;
            if(Pattern.matches("^\\*\\.[a-z0-9-.]*$", host)) // start with *. (not .* and * must follow a dot)
                return true;
            if(Pattern.matches("^[a-z0-9-.]*\\.\\*$", host)) // ends with .* (not *. and befor * must be a dot)
                return true;
        } catch (final PatternSyntaxException e) {
            //System.out.println(e.toString());
            return false;
        }
       return false;
    }

    public String getEngineInfo() {
        return "Default YaCy Blacklist Engine";
    }

    public boolean isListed(final String blacklistType, final String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();

        // getting the proper blacklist
        final HashMap<String, ArrayList<String>> blacklistMapMatched = getBlacklistMap(blacklistType,true);

        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        ArrayList<String> app;
        boolean matched = false;
        String pp = ""; // path-pattern

        // try to match complete domain
        if (!matched && (app = blacklistMapMatched.get(hostlow)) != null) {
            for (int i=app.size()-1; !matched && i>-1; i--) {
                pp = app.get(i);
                matched |= ((pp.equals("*")) || (path.matches(pp)));
            }
        }
        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while (!matched && (index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((app = blacklistMapMatched.get(hostlow.substring(0, index + 1) + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
            if ((app = blacklistMapMatched.get(hostlow.substring(0, index))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }
        index = hostlow.length();
        while (!matched && (index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((app = blacklistMapMatched.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
            if ((app = blacklistMapMatched.get(hostlow.substring(index +1, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }


        // loop over all Regexentrys
        if(!matched) {
            final HashMap<String, ArrayList<String>> blacklistMapNotMatched = getBlacklistMap(blacklistType,false);
            String key;
            for(final Entry<String, ArrayList<String>> entry: blacklistMapNotMatched.entrySet()) {
                key = entry.getKey();
                try {
                    if(Pattern.matches(key, hostlow)) {
                        app = entry.getValue();
                        for (int i=0; i<app.size(); i++) {
                            if(Pattern.matches(app.get(i), path)) 
                                return true;
                        }
                    }
                } catch (final PatternSyntaxException e) {
                    //System.out.println(e.toString());
                }
            }
        }
        return matched;
    }

    public int checkError(String element, Map<String, String> properties) {

        boolean allowRegex = true;
        int slashPos;
        String host, path;

        if (properties != null) {
            allowRegex = properties.get("allowRegex").equalsIgnoreCase("true") ? true : false;
        }

        if ((slashPos = element.indexOf("/")) == -1) {
            host = element;
            path = ".*";
        } else {
            host = element.substring(0, slashPos);
            path = element.substring(slashPos + 1);
        }

        if (!allowRegex || !isValidRegex(host)) {
            final int i = host.indexOf("*");

            // check whether host begins illegally
            if (!host.matches("([A-Za-z0-9_-]+|\\*)(\\.([A-Za-z0-9_-]+|\\*))*")) {
                if (i == 0 && host.length() > 1 && host.charAt(1) != '.') {
                    return ERR_SUBDOMAIN_XOR_WILDCARD;
                }
                return ERR_HOST_WRONG_CHARS;
            }

            // in host-part only full sub-domains may be wildcards
            if (host.length() > 0 && i > -1) {
                if (!(i == 0 || i == host.length() - 1)) {
                    return  ERR_WILDCARD_BEGIN_OR_END;
                }

                if (i == host.length() - 1 && host.length() > 1 && host.charAt(i - 1) != '.') {
                    return ERR_SUBDOMAIN_XOR_WILDCARD;
                }
            }

            // check for double-occurences of "*" in host
            if (host.indexOf("*", i + 1) > -1) {
                return ERR_TWO_WILDCARDS_IN_HOST;
            }
        } else if (allowRegex && !isValidRegex(host)) {
            return ERR_HOST_REGEX;
        }

        // check for errors on regex-compiling path
        if (!isValidRegex(path) && !path.equals("*")) {
            return ERR_PATH_REGEX;
        }

        return 0;
    }

    /**
     * Checks if a given expression is a valid regular expression.
     * @param expression The expression to be checked.
     * @return True if the expression is a valid regular expression, else false.
     */
    private static boolean isValidRegex(String expression) {
        boolean ret = true;
        try {
            Pattern.compile(expression);
        } catch (final PatternSyntaxException e) {

            ret = false;
        }
        return ret;
    }
    
    public static final String defaultBlacklist(final File listsPath) {
        List<String> dirlist = FileUtils.getDirListing(listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        if (dirlist.size() == 0) return null;
        return dirlist.get(0);
    }

    /**
     * Checks if a blacklist file contains a certain entry.
     * @param blacklistToUse The blacklist.
     * @param newEntry The Entry.
     * @return True if file contains entry, else false.
     */
    public static boolean blacklistFileContains(final File listsPath, final String blacklistToUse, String newEntry) {
        boolean ret = false;
        final HashSet<String> Blacklist = new HashSet<String>(FileUtils.getListArray(new File(listsPath, blacklistToUse)));
        if (Blacklist != null) {
            ret = Blacklist.contains(newEntry);
        }
        return ret;
    }
    
}
