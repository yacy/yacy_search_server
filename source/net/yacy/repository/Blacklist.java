// Blacklist.java
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.document.id.Punycode;
import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.ListManager;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.SetTools;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

public class Blacklist {

    public enum BlacklistType {
        DHT, CRAWLER, PROXY, SEARCH, SURFTIPS, NEWS;

        @Override
        public final String toString () {
            return super.toString().toLowerCase();
        }
    }

    public static final String BLACKLIST_FILENAME_FILTER = "^.*\\.black$";

    public static enum BlacklistError {

        NO_ERROR(0),
        TWO_WILDCARDS_IN_HOST(1),
        SUBDOMAIN_XOR_WILDCARD(2),
        PATH_REGEX(3),
        WILDCARD_BEGIN_OR_END(4),
        HOST_WRONG_CHARS(5),
        DOUBLE_OCCURANCE(6),
        HOST_REGEX(7);
        final int errorCode;

        BlacklistError(final int errorCode) {
            this.errorCode = errorCode;
        }

        public int getInt() {
            return this.errorCode;
        }

        public long getLong() {
            return this.errorCode;
        }
    }

    private File blacklistRootPath = null;
    private Map<BlacklistType, String> blacklistFiles = new TreeMap<BlacklistType, String>();
    private final ConcurrentMap<BlacklistType, HandleSet> cachedUrlHashs;
    private final ConcurrentMap<BlacklistType, Map<String, Set<Pattern>>> hostpaths_matchable; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here
    private final ConcurrentMap<BlacklistType, Map<String, Set<Pattern>>> hostpaths_notmatchable; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here

    public Blacklist(final File rootPath) {

        setRootPath(rootPath);

        // prepare the data structure
        this.hostpaths_matchable = new ConcurrentHashMap<BlacklistType, Map<String, Set<Pattern>>>();
        this.hostpaths_notmatchable = new ConcurrentHashMap<BlacklistType, Map<String, Set<Pattern>>>();
        this.cachedUrlHashs = new ConcurrentHashMap<BlacklistType, HandleSet>();

        for (final BlacklistType blacklistType : BlacklistType.values()) {
            this.hostpaths_matchable.put(blacklistType, new ConcurrentHashMap<String, Set<Pattern>>());
            this.hostpaths_notmatchable.put(blacklistType, new ConcurrentHashMap<String, Set<Pattern>>());
            loadDHTCache(blacklistType);
        }
    }

    /**
     * Close (shutdown) this "sub-system", add more here for shutdown.
     */
    public final synchronized void close() {
        ConcurrentLog.fine("Blacklist", "Shutting down blacklists ...");

        // Save cache
        for (final BlacklistType blacklistType : BlacklistType.values()) {
            saveDHTCache(blacklistType);
        }

        ConcurrentLog.fine("Blacklist", "All blacklists has been shutdown.");
    }

    private final void setRootPath(final File rootPath) {
        if (rootPath == null) {
            throw new NullPointerException("The blacklist root path must not be null.");
        }
        if (!rootPath.isDirectory()) {
            throw new IllegalArgumentException("The blacklist root path is not a directory.");
        }
        if (!rootPath.canRead()) {
            throw new IllegalArgumentException("The blacklist root path is not readable.");
        }

        this.blacklistRootPath = rootPath;
    }

    protected final Map<String, Set<Pattern>> getBlacklistMap(final BlacklistType blacklistType, final boolean matchable) {
        return (matchable) ? this.hostpaths_matchable.get(blacklistType) : this.hostpaths_notmatchable.get(blacklistType);
    }

    protected final HandleSet getCacheUrlHashsSet(final BlacklistType blacklistType) {
        return this.cachedUrlHashs.get(blacklistType);
    }

    public final File getRootPath() {
    	return blacklistRootPath;
    }
    
    public final void clear() {
        for (final Map<String, Set<Pattern>> entry : this.hostpaths_matchable.values()) {
            entry.clear();
        }
        for (final Map<String, Set<Pattern>> entry : this.hostpaths_notmatchable.values()) {
            entry.clear();
        }
        for (final HandleSet entry : this.cachedUrlHashs.values()) {
            entry.clear();
        }
        blacklistFiles.clear();
    }

    public final int size() {
        int size = 0;
        for (final BlacklistType entry : this.hostpaths_matchable.keySet()) {
            for (final Set<Pattern> ientry : this.hostpaths_matchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        for (final BlacklistType entry : this.hostpaths_notmatchable.keySet()) {
            for (final Set<Pattern> ientry : this.hostpaths_notmatchable.get(entry).values()) {
                size += ientry.size();
            }
        }
        return size;
    }

    public final void loadList(final BlacklistFile[] blFiles, final String sep) {
        for (final BlacklistFile blf : blFiles) {
            loadList(blf, sep);
        }
    }

    /**
     * create a blacklist from file, entries separated by 'sep'
     * duplicate entries are removed
     * @param blFile
     * @param sep
     */
    private void loadList(final BlacklistFile blFile, final String sep) {
    	if (!blacklistFiles.containsKey(blFile.getType())) {
    		blacklistFiles.put(blFile.getType(), blFile.getFileName());
    	}
    	
        final Map<String, Set<Pattern>> blacklistMapMatch = getBlacklistMap(blFile.getType(), true);
        final Map<String, Set<Pattern>> blacklistMapNotMatch = getBlacklistMap(blFile.getType(), false);
        Set<Map.Entry<String, List<String>>> loadedBlacklist;
        Map.Entry<String, List<String>> loadedEntry;
        Set<Pattern> paths;
        List<String> loadedPaths;
        Set<Pattern> loadedPathsPattern;

        final Set<String> fileNames = blFile.getFileNamesUnified();
        for (final String fileName : fileNames) {
            // make sure all requested blacklist files exist
            final File file = new File(this.blacklistRootPath, fileName);
            try {
                file.createNewFile();
            } catch (final IOException e) { /* */ }

            // join all blacklists from files into one internal blacklist map
            loadedBlacklist = SetTools.loadMapMultiValsPerKey(file.toString(), sep).entrySet();
            for (final Iterator<Map.Entry<String, List<String>>> mi = loadedBlacklist.iterator(); mi.hasNext();) {
                loadedEntry = mi.next();
                loadedPaths = loadedEntry.getValue();
                loadedPathsPattern = new HashSet<Pattern>();
                for (String a: loadedPaths) {
                    if (a.equals("*")) {
                        loadedPathsPattern.add(Pattern.compile(".*", Pattern.CASE_INSENSITIVE));
                        continue;
                    }
                    if (a.indexOf("?*", 0) > 0) {
                        // prevent "Dangling meta character '*'" exception
                        ConcurrentLog.warn("Blacklist", "ignored blacklist path to prevent 'Dangling meta character' exception: " + a);
                        continue;
                    }
                    loadedPathsPattern.add(Pattern.compile(a, Pattern.CASE_INSENSITIVE)); // add case insesitive regex
                }

                // create new entry if host mask unknown, otherwise merge
                // existing one with path patterns from blacklist file
                paths = (isMatchable(loadedEntry.getKey())) ? blacklistMapMatch.get(loadedEntry.getKey()) : blacklistMapNotMatch.get(loadedEntry.getKey());
                if (paths == null) {
                    if (isMatchable(loadedEntry.getKey())) {
                        blacklistMapMatch.put(loadedEntry.getKey(), loadedPathsPattern);
                    } else {
                        blacklistMapNotMatch.put(loadedEntry.getKey(), loadedPathsPattern);
                    }
                } else {
                    paths.addAll(new HashSet<Pattern>(loadedPathsPattern));
                }
            }
        }
    }

    public final void loadList(final BlacklistType blacklistType, final String fileNames, final String sep) {
        // method for not breaking older plasmaURLPattern interface
        final BlacklistFile blFile = new BlacklistFile(fileNames, blacklistType);
        loadList(blFile, sep);
    }

    public final void removeAll(final BlacklistType blacklistType, final String host) {
        getBlacklistMap(blacklistType, true).remove(host);
        getBlacklistMap(blacklistType, false).remove(host);
    }

    public final void remove(final BlacklistType blacklistType, final String blacklistToUse, final String host, final String path) {

        final Map<String, Set<Pattern>> blacklistMap = getBlacklistMap(blacklistType, true);
        Set<Pattern> hostList = blacklistMap.get(host);
        if (hostList != null) {
            hostList.remove(path);
            if (hostList.isEmpty()) {
                blacklistMap.remove(host);
            }
        }

        final Map<String, Set<Pattern>> blacklistMapNotMatch = getBlacklistMap(blacklistType, false);
        hostList = blacklistMapNotMatch.get(host);
        if (hostList != null) {
            hostList.remove(path);
            if (hostList.isEmpty()) {
                blacklistMapNotMatch.remove(host);
            }
        }

        // load blacklist data from file
        final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, blacklistToUse));
        
        // delete the old entry from file
        if (list != null) {
            for (final String e : list) {
                if (e.equals(host + "/" + path)) {
                    list.remove(e);
                    break;
                }
            }
            FileUtils.writeList(new File(ListManager.listsPath, blacklistToUse), list.toArray(new String[list.size()]));
        }
    }
    
    /**
     * 
     * @param blacklistType
     * @param blacklistToUse
     * @param host
     * @param path
     * @throws PunycodeException
     */
    public final void add(final BlacklistType blacklistType, final String blacklistToUse, final String host, final String path) throws PunycodeException {
    	
        final String safeHost = Punycode.isBasic(host) ? host : MultiProtocolURL.toPunycode(host);
        
        if (contains(blacklistType, safeHost, path)) {
    		return;
    	}
        if (safeHost == null) {
            throw new IllegalArgumentException("host may not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path may not be null");
        }

        String p = (!path.isEmpty() && path.charAt(0) == '/') ? path.substring(1) : path;
        final Map<String, Set<Pattern>> blacklistMap = getBlacklistMap(blacklistType, isMatchable(host));

        // avoid PatternSyntaxException e
        final String h = ((!isMatchable(safeHost) && !safeHost.isEmpty() && safeHost.charAt(0) == '*') ? "." + safeHost : safeHost).toLowerCase();
        if (!p.isEmpty() && p.charAt(0) == '*') {
            p = "." + p;
        }

        Set<Pattern> hostList;
        if (!(blacklistMap.containsKey(h) && ((hostList = blacklistMap.get(h)) != null))) {
            blacklistMap.put(h, (hostList = new HashSet<Pattern>()));
        }
        
        Pattern pattern = Pattern.compile(p, Pattern.CASE_INSENSITIVE); 
        
        hostList.add(pattern); 

        // Append the line to the file.
        PrintWriter pw = null;
        try {
        	final String newEntry = h + "/" + pattern;
        	if (!blacklistFileContains(blacklistRootPath, 
        			blacklistToUse, newEntry)) {
	            pw = new PrintWriter(new FileWriter(new File(blacklistRootPath, 
	            		blacklistToUse), true));
	            pw.println(newEntry);
	            pw.close();
        	}
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
        	if (pw != null) {
                try {
                		pw.close();
                } catch (final Exception e) {
                    ConcurrentLog.warn("Blacklist", "could not close stream to " + 
                    		blacklistToUse + "! " + e.getMessage());
                }
        	}
        }
    }

    /**
     * appends aN entry to the backlist source file.
     * 
     * @param blacklistSourcefile name of the blacklist file (LISTS/*.black)
     * @param host host or host pattern
     * @param path path or path pattern
     * @throws PunycodeException 
     */
    public final void add (final String blacklistSourcefile, final String host, final String path) throws PunycodeException {
        // TODO: check sourcefile synced with cache.ser files ?
        if (host == null) {
            throw new IllegalArgumentException("host may not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path may not be null");
        }
        
        String p = (!path.isEmpty() && path.charAt(0) == '/') ? path.substring(1) : path;

        // avoid PatternSyntaxException e
        String h = ((!isMatchable(host) && !host.isEmpty() && host.charAt(0) == '*') ? "." + host : host).toLowerCase();
        
        h = Punycode.isBasic(h) ? h : MultiProtocolURL.toPunycode(h);
        
        if (!p.isEmpty() && p.charAt(0) == '*') {
            p = "." + p;
        }
        Pattern pattern = Pattern.compile(p, Pattern.CASE_INSENSITIVE); 
        // Append the line to the file.
        PrintWriter pw = null;
        try {
            final String newEntry = h + "/" + pattern;
            if (!blacklistFileContains(blacklistRootPath, blacklistSourcefile, newEntry)) {
                pw = new PrintWriter(new FileWriter(new File(blacklistRootPath, blacklistSourcefile), true));
                pw.println(newEntry);
                pw.close();
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
            if (pw != null) {
                try {
                    pw.close();
                } catch (final Exception e) {
                    ConcurrentLog.warn("Blacklist", "could not close stream to "
                            + blacklistSourcefile + "! " + e.getMessage());
                }
            }
        }
    }
    
    public final int blacklistCacheSize() {
        int size = 0;
        final Iterator<BlacklistType> iter = this.cachedUrlHashs.keySet().iterator();
        while (iter.hasNext()) {
            size += this.cachedUrlHashs.get(iter.next()).size();
        }
        return size;
    }

    public final void clearblacklistCache() {
        final Iterator<BlacklistType> iter = this.cachedUrlHashs.keySet().iterator();
        while (iter.hasNext()) {
            this.cachedUrlHashs.get(iter.next()).clear();
        }
    }

    public final boolean hashInBlacklistedCache(final BlacklistType blacklistType, final byte[] urlHash) {
        HandleSet s = getCacheUrlHashsSet(blacklistType);
        return s != null && s.has(urlHash);
    }

    public final boolean contains(final BlacklistType blacklistType, final String host, final String path) {
        boolean ret = false;

        if (blacklistType != null && host != null && path != null) {
            final Map<String, Set<Pattern>> blacklistMap = getBlacklistMap(blacklistType, isMatchable(host));

            // avoid PatternSyntaxException e
            final String h = ((!isMatchable(host) && !host.isEmpty() && host.charAt(0) == '*') ? "." + host : host).toLowerCase();

            final Set<Pattern> hostList = blacklistMap.get(h);
            if (hostList != null) {
                ret = hostList.contains(path);
            }
        }
        return ret;
    }

    /**
     * Checks whether the given entry is listed in given blacklist type.
     * @param blacklistType The used blacklist
     * @param url Entry to be checked
     * @return  Whether the given entry is blacklisted
     */
    public final boolean isListed(final BlacklistType blacklistType, final DigestURL url) {
        if (url == null) {
            throw new IllegalArgumentException("url may not be null");
        }

        if (url.getHost() == null) {
            return false;
        }
        HandleSet urlHashCache = getCacheUrlHashsSet(blacklistType);
        if (urlHashCache == null) {
            urlHashCache = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0);
            if (isListed(blacklistType, url.getHost().toLowerCase(), url.getFile())) {
                try {
                    urlHashCache.put(url.hash());
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
                this.cachedUrlHashs.put(blacklistType, urlHashCache);
            }
        }
        if (!urlHashCache.has(url.hash())) {
            final boolean temp = isListed(blacklistType, url.getHost().toLowerCase(), url.getFile());
            if (temp) {
                try {
                    urlHashCache.put(url.hash());
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.logException(e);
                }
            }
            return temp;
        }
        return true;
    }

    private static final Pattern m1 = Pattern.compile("^[a-z0-9.-]*$");       // simple Domain (yacy.net or www.yacy.net)
    private static final Pattern m2 = Pattern.compile("^\\*\\.[a-z0-9-.]*$"); // start with *. (not .* and * must follow a dot)
    private static final Pattern m3 = Pattern.compile("^[a-z0-9-.]*\\.\\*$"); // ends with .* (not *. and before * must be a dot)
    public static boolean isMatchable(final String host) {
        return (m1.matcher(host).matches() || m2.matcher(host).matches() || m3.matcher(host).matches());
    }

    public static String getEngineInfo() {
        return "Default YaCy Blacklist Engine";
    }

    public final boolean isListed(final BlacklistType blacklistType, final String hostlow, final String path) {
        if (hostlow == null) {
            throw new IllegalArgumentException("hostlow may not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path may not be null");
        }

        // getting the proper blacklist
        final Map<String, Set<Pattern>> blacklistMapMatched = getBlacklistMap(blacklistType, true);

        final String p = (!path.isEmpty() && path.charAt(0) == '/') ? path.substring(1) : path;

        Pattern[] app;
        boolean matched = false;
        Pattern pp; // path-pattern

        // try to match complete domain
        if (!matched && blacklistMapMatched.get(hostlow) != null) {
            app = blacklistMapMatched.get(hostlow).toArray(new Pattern[0]);
            for (int i = app.length - 1; !matched && i > -1; i--) {
                pp = app[i];
                matched |= pp.matcher(p).matches();
            }
        }
        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while (!matched && (index = hostlow.indexOf('.', index + 1)) != -1) {
            if (blacklistMapMatched.get(hostlow.substring(0, index + 1) + "*") != null) {
                app = blacklistMapMatched.get(hostlow.substring(0, index + 1) + "*").toArray(new Pattern[0]);
                for (int i = app.length - 1; !matched && i > -1; i--) {
                    pp = app[i];
                    matched |= pp.matcher(p).matches();
                }
            }
            if (blacklistMapMatched.get(hostlow.substring(0, index)) != null) {
                app = blacklistMapMatched.get(hostlow.substring(0, index)).toArray(new Pattern[0]);
                for (int i = app.length - 1; !matched && i > -1; i--) {
                    pp = app[i];
                    matched |= pp.matcher(p).matches();
                }
            }
        }
        index = hostlow.length();
        while (!matched && (index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if (blacklistMapMatched.get("*" + hostlow.substring(index, hostlow.length())) != null) {
                app = blacklistMapMatched.get("*" + hostlow.substring(index, hostlow.length())).toArray(new Pattern[0]);
                for (int i = app.length - 1; !matched && i > -1; i--) {
                    pp = app[i];
                    matched |= pp.matcher(p).matches();
                }
            }
            if (blacklistMapMatched.get(hostlow.substring(index + 1, hostlow.length())) != null) {
                app = blacklistMapMatched.get(hostlow.substring(index + 1, hostlow.length())).toArray(new Pattern[0]);
                for (int i = app.length - 1; !matched && i > -1; i--) {
                    pp = app[i];
                    matched |= pp.matcher(p).matches();
                }
            }
        }


        // loop over all Regex-entries
        if (!matched) {
            final Map<String, Set<Pattern>> blacklistMapNotMatched = getBlacklistMap(blacklistType, false);
            String key;
            for (final Entry<String, Set<Pattern>> entry : blacklistMapNotMatched.entrySet()) {
                key = entry.getKey();
                try {
                    if (Pattern.matches(key, hostlow)) {
                        app = entry.getValue().toArray(new Pattern[0]);
                        for (final Pattern ap : app) {
                            if (ap.matcher(p).matches()) {
                                return true;
                            }
                        }
                    }
                } catch (final PatternSyntaxException e) {
                    //System.out.println(e.toString());
                }
            }
        }
        return matched;
    }

    public static BlacklistError checkError(final String element, final Map<String, String> properties) {

        final boolean allowRegex = (properties != null) && properties.get("allowRegex").equalsIgnoreCase("true");
        int slashPos;
        final String host, path;

        if ((slashPos = element.indexOf('/')) == -1) {
            host = element;
            path = ".*";
        } else {
            host = element.substring(0, slashPos);
            path = element.substring(slashPos + 1);
        }

        if (!allowRegex || !RegexHelper.isValidRegex(host)) {
            final int i = host.indexOf('*');

            // check whether host begins illegally
            if (!host.matches("([A-Za-z0-9_-]+|\\*)(\\.([A-Za-z0-9_-]+|\\*))*")) {
                if (i == 0 && host.length() > 1 && host.charAt(1) != '.') {
                    return BlacklistError.SUBDOMAIN_XOR_WILDCARD;
                }
                return BlacklistError.HOST_WRONG_CHARS;
            }

            // in host-part only full sub-domains may be wildcards
            if (!host.isEmpty() && i > -1) {
                if (!(i == 0 || i == host.length() - 1)) {
                    return BlacklistError.WILDCARD_BEGIN_OR_END;
                }

                if (i == host.length() - 1 && host.length() > 1 && host.charAt(i - 1) != '.') {
                    return BlacklistError.SUBDOMAIN_XOR_WILDCARD;
                }
            }

            // check for double-occurrences of "*" in host
            if (host.indexOf("*", i + 1) > -1) {
                return BlacklistError.TWO_WILDCARDS_IN_HOST;
            }
        } else if (allowRegex && !RegexHelper.isValidRegex(host)) {
            return BlacklistError.HOST_REGEX;
        }

        // check for errors on regex-compiling path
        if (!RegexHelper.isValidRegex(path) && !"*".equals(path)) {
            return BlacklistError.PATH_REGEX;
        }

        return BlacklistError.NO_ERROR;
    }

    public static String defaultBlacklist(final File listsPath) {
        final List<String> dirlist = FileUtils.getDirListing(listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        if (dirlist.isEmpty()) {
            return null;
        }
        return dirlist.get(0);
    }

    /**
     * Checks if a blacklist file contains a certain entry.
     * @param blacklistToUse The blacklist.
     * @param newEntry The Entry.
     * @return True if file contains entry, else false.
     */
    public static boolean blacklistFileContains(final File listsPath, final String blacklistToUse, final String newEntry) {
        final Set<String> blacklist = new HashSet<String>(FileUtils.getListArray(new File(listsPath, blacklistToUse)));
        return blacklist != null && blacklist.contains(newEntry);
    }

    private static File DHTCacheFile(final BlacklistType type) {
        final String BLACKLIST_DHT_CACHEFILE_NAME = SwitchboardConstants.LISTS_PATH_DEFAULT + "/blacklist_" + type.name() + "_Cache.ser";
        return new File(Switchboard.getSwitchboard().dataPath, BLACKLIST_DHT_CACHEFILE_NAME);
    }

    private final void saveDHTCache(final BlacklistType type) {
        try {
            final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DHTCacheFile(type)));
            HandleSet s = getCacheUrlHashsSet(type);
            if (s != null) {
                out.writeObject(getCacheUrlHashsSet(type));
                out.close();
            }

        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

    private final void loadDHTCache(final BlacklistType type) {
        File cachefile = DHTCacheFile(type);
        if (cachefile.exists()) {
            try {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(cachefile));
                RowHandleSet rhs = (RowHandleSet) in.readObject();
                this.cachedUrlHashs.put(type, rhs == null ? new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0) : rhs);
                in.close();
                return;
            } catch (final Throwable e) {
                ConcurrentLog.logException(e);
            }
        }
        this.cachedUrlHashs.put(type, new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0));
    }
}
