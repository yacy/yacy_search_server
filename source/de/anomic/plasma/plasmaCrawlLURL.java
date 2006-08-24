// plasmaCrawlLURL.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

/*
   This class provides storage functions for the plasma search engine.
   - the url-specific properties, including condenser results
   - the text content of the url
   Both entities are accessed with a hash, which is based on the MD5
   algorithm. The MD5 is not encoded as a hex value, but a b64 value.
*/

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import de.anomic.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Properties;

import de.anomic.http.httpc;
import de.anomic.http.httpc.response;
import de.anomic.index.indexEntry;
import de.anomic.index.indexURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroTree;
import de.anomic.kelondro.kelondroRow;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverCodings;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class plasmaCrawlLURL extends indexURL {

    // result stacks;
    // these have all entries of form
    // strings: urlHash + initiatorHash + ExecutorHash
    private final LinkedList externResultStack; // 1 - remote index: retrieved by other peer
    private final LinkedList searchResultStack; // 2 - partly remote/local index: result of search queries
    private final LinkedList transfResultStack; // 3 - partly remote/local index: result of index transfer
    private final LinkedList proxyResultStack;  // 4 - local index: result of proxy fetch/prefetch
    private final LinkedList lcrawlResultStack; // 5 - local index: result of local crawling
    private final LinkedList gcrawlResultStack; // 6 - local index: triggered external

    //public static Set damagedURLS = Collections.synchronizedSet(new HashSet());
    
    public plasmaCrawlLURL(File cachePath, int bufferkb, long preloadTime) {
        super();
        kelondroRow rowdef = new kelondroRow(
            "String urlhash-"      + urlHashLength      + ", " +        // the url's hash
            "String urlstring-"    + urlStringLength    + ", " +        // the url as string
            "String urldescr-"     + urlDescrLength     + ", " +        // the description of the url
            "Cardinal moddate-"    + urlDateLength      + " {b64e}, " + // last-modified from the httpd
            "Cardinal loaddate-"   + urlDateLength      + " {b64e}, " + // time when the url was loaded
            "String refhash-"      + urlHashLength      + ", " +        // the url's referrer hash
            "Cardinal copycount-"  + urlCopyCountLength + " {b64e}, " + //
            "byte[] flags-"        + urlFlagLength      + ", " +        // flags
            "Cardinal quality-"    + urlQualityLength   + " {b64e}, " + // 
            "String language-"     + urlLanguageLength  + ", " +        //
            "byte[] doctype-"      + urlDoctypeLength   + ", " +        //
            "Cardinal size-"       + urlSizeLength      + " {b64e}, " + // size of file in bytes
            "Cardinal wc-"         + urlWordCountLength + " {b64e}");   // word count

        File cacheFile = new File(cachePath, "urlHash.db");
        
        cacheFile.getParentFile().mkdirs();
        try {
            urlHashCache = new kelondroTree(cacheFile, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent, rowdef);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // init result stacks
        externResultStack = new LinkedList();
        searchResultStack = new LinkedList();
        transfResultStack = new LinkedList();
        proxyResultStack  = new LinkedList();
        lcrawlResultStack = new LinkedList();
        gcrawlResultStack = new LinkedList();
    }
    
    public synchronized void stackEntry(Entry e, String initiatorHash, String executorHash, int stackType) {
        if (e == null) { return; }
        try {
            if (initiatorHash == null) { initiatorHash = dummyHash; }
            if (executorHash == null) { executorHash = dummyHash; }
            switch (stackType) {
                case 0: break;
                case 1: externResultStack.add(e.urlHash + initiatorHash + executorHash); break;
                case 2: searchResultStack.add(e.urlHash + initiatorHash + executorHash); break;
                case 3: transfResultStack.add(e.urlHash + initiatorHash + executorHash); break;
                case 4: proxyResultStack.add(e.urlHash + initiatorHash + executorHash); break;
                case 5: lcrawlResultStack.add(e.urlHash + initiatorHash + executorHash); break;
                case 6: gcrawlResultStack.add(e.urlHash + initiatorHash + executorHash); break;
            }
            return;
        } catch (Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
    }

    public void notifyGCrawl(String urlHash, String initiatorHash, String executorHash) {
        gcrawlResultStack.add(urlHash + initiatorHash + executorHash);
    }

    public Entry getEntry(String hash, indexEntry searchedWord) throws IOException {
        return new Entry(hash, searchedWord);
    }

    public synchronized Entry newEntry(Entry oldEntry) {
        if (oldEntry == null) return null;
        return new Entry(
                oldEntry.url(),
                oldEntry.descr(),
                oldEntry.moddate(),
                oldEntry.loaddate(),
                oldEntry.referrerHash(),
                oldEntry.copyCount(),
                oldEntry.local(),
                oldEntry.quality(),
                oldEntry.language(),
                oldEntry.doctype(),
                oldEntry.size(),
                oldEntry.wordCount());
    }
    
    public synchronized Entry newEntry(String propStr, boolean setGlobal) {
        if (propStr.startsWith("{") && propStr.endsWith("}")) {
            return new Entry(serverCodings.s2p(propStr.substring(1, propStr.length() - 1)), setGlobal);
        } else {
            return null;
        }
    }

    public synchronized Entry newEntry(URL url, String descr, Date moddate, Date loaddate,
            String referrerHash, int copyCount, boolean localNeed,
            int quality, String language, char doctype,
            int size, int wordCount) {
        Entry e = new Entry(url, descr, moddate, loaddate, referrerHash, copyCount, localNeed, quality, language, doctype, size, wordCount);
        return e;
    }
    
    public int getStackSize(int stack) {
        switch (stack) {
            case 1: return externResultStack.size();
            case 2: return searchResultStack.size();
            case 3: return transfResultStack.size();
            case 4: return proxyResultStack.size();
            case 5: return lcrawlResultStack.size();
            case 6: return gcrawlResultStack.size();
        }
        return -1;
    }

    public String getUrlHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(0, urlHashLength);
            case 2: return ((String) searchResultStack.get(pos)).substring(0, urlHashLength);
            case 3: return ((String) transfResultStack.get(pos)).substring(0, urlHashLength);
            case 4: return ((String) proxyResultStack.get(pos)).substring(0, urlHashLength);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(0, urlHashLength);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(0, urlHashLength);
        }
        return null;
    }

    public String getInitiatorHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
            case 2: return ((String) searchResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
            case 3: return ((String) transfResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
            case 4: return ((String) proxyResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(urlHashLength, urlHashLength * 2);
        }
        return null;
    }

    public String getExecutorHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
            case 2: return ((String) searchResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
            case 3: return ((String) transfResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
            case 4: return ((String) proxyResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(urlHashLength * 2, urlHashLength * 3);
        }
        return null;
    }

    public boolean removeStack(int stack, int pos) {
        Object prevElement = null;
        switch (stack) {
            case 1: prevElement = externResultStack.remove(pos); break;
            case 2: prevElement = searchResultStack.remove(pos); break;
            case 3: prevElement = transfResultStack.remove(pos); break;
            case 4: prevElement = proxyResultStack.remove(pos); break;
            case 5: prevElement = lcrawlResultStack.remove(pos); break;
            case 6: prevElement = gcrawlResultStack.remove(pos); break;
        }
        return prevElement != null;
    }

    public void clearStack(int stack) {
        switch (stack) {
            case 1: externResultStack.clear(); break;
            case 2: searchResultStack.clear(); break;
            case 3: transfResultStack.clear(); break;
            case 4: proxyResultStack.clear(); break;
            case 5: lcrawlResultStack.clear(); break;
            case 6: gcrawlResultStack.clear(); break;
        }
    }

    public boolean remove(String urlHash) {
        if (!super.remove(urlHash)) return false;
        for (int stack = 1; stack <= 6; stack++) {
            for (int i = getStackSize(stack) - 1; i >= 0; i--) {
                if (getUrlHash(stack,i).equals(urlHash)) {
                    removeStack(stack,i);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean exists(String urlHash) {
            try {
                if (urlHashCache.get(urlHash.getBytes()) != null) {
                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
    }
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) {
            return "";
        } else {
            return dayFormatter.format(date);
        }
    }

    public serverObjects genTableProps(int tabletype, int lines, boolean showInit, boolean showExec, String dfltInit, String dfltExec, String feedbackpage, boolean makeLink) {
/*      serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps tabletype=" + tabletype    +    " lines=" + lines    +
                                                                    " showInit=" + showInit     + " showExec=" + showExec +
                                                                    " dfltInit=" + dfltInit     + " dfltExec=" + dfltExec +
                                                                " feedbackpage=" + feedbackpage + " makeLink=" + makeLink); */
        final serverObjects prop = new serverObjects();
        if (getStackSize(tabletype) == 0) {
            prop.put("table", 0);
            return prop;
        }
        prop.put("table", 1);
        if (lines > getStackSize(tabletype)) lines = getStackSize(tabletype);
        if (lines == getStackSize(tabletype)) {
            prop.put("table_size", 0);
        } else {
            prop.put("table_size", 1);
            prop.put("table_size_count", lines);
        }
        prop.put("table_size_all", getStackSize(tabletype));
        prop.put("table_feedbackpage", feedbackpage);
        prop.put("table_tabletype", tabletype);
        prop.put("table_showInit", (showInit) ? 1 : 0);
        prop.put("table_showExec", (showExec) ? 1 : 0);

        boolean dark = true;
        String urlHash, initiatorHash, executorHash;
        String cachepath, urlstr, urltxt;
        yacySeed initiatorSeed, executorSeed;
        plasmaCrawlLURL.Entry urle;
        URL url;

        // needed for getCachePath(url)
        final plasmaSwitchboard switchboard = plasmaSwitchboard.getSwitchboard();
        final plasmaHTCache cacheManager = switchboard.getCacheManager();

        int cnt = 0;
        for (int i = getStackSize(tabletype) - 1; i >= (getStackSize(tabletype) - lines); i--) {
            initiatorHash = getInitiatorHash(tabletype, i);
            executorHash = getExecutorHash(tabletype, i);
//          serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps initiatorHash=" + initiatorHash + " executorHash=" + executorHash);
            urlHash = getUrlHash(tabletype, i);
//          serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urlHash=" + urlHash);
            try {
                urle = getEntry(urlHash, null);
//              serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urle=" + urle.toString());
                initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                executorSeed = yacyCore.seedDB.getConnected(executorHash);

                url = urle.url();
                urlstr = url.toString();
                urltxt = nxTools.cutUrlText(urlstr, 72); // shorten the string text like a URL
                cachepath = (url == null) ? "-not-cached-" : cacheManager.getCachePath(url).toString().replace('\\', '/').substring(cacheManager.cachePath.toString().length() + 1);

                prop.put("table_indexed_" + cnt + "_dark", (dark) ? 1 : 0);
                prop.put("table_indexed_" + cnt + "_feedbackpage", feedbackpage);
                prop.put("table_indexed_" + cnt + "_tabletype", tabletype);
                prop.put("table_indexed_" + cnt + "_urlhash", urlHash);
                prop.put("table_indexed_" + cnt + "_showInit", (showInit) ? 1 : 0);
                prop.put("table_indexed_" + cnt + "_showInit_initiatorSeed", (initiatorSeed == null) ? dfltInit : initiatorSeed.getName());
                prop.put("table_indexed_" + cnt + "_showExec", (showExec) ? 1 : 0);
                prop.put("table_indexed_" + cnt + "_showExec_executorSeed", (executorSeed == null) ? dfltExec : executorSeed.getName());
                prop.put("table_indexed_" + cnt + "_moddate", daydate(urle.moddate()));
                prop.put("table_indexed_" + cnt + "_wordcount", urle.wordCount());
                prop.put("table_indexed_" + cnt + "_urldescr", urle.descr());
                prop.put("table_indexed_" + cnt + "_url", (urle.url() == null) ? "-not-cached-" : ((makeLink) ? ("<a href=\"CacheAdmin_p.html?action=info&path=" + cachepath + "\" class=\"small\" title=\"" + urlstr + "\">" + urltxt + "</a>") : urlstr));
                dark = !dark;
                cnt++;
            } catch (Exception e) {
                serverLog.logSevere("PLASMA", "genTableProps", e);
            }
        }
        prop.put("table_indexed", cnt);
        return prop;
    }

    public class Entry {

        private URL url;

        private String descr;
        private Date moddate;
        private Date loaddate;
        private String urlHash;
        private String referrerHash;
        private int copyCount;
        private String flags;
        private int quality;
        private String language;
        private char doctype;
        private int size;
        private int wordCount;
        private String snippet;
        private indexEntry word; // this is only used if the url is transported via remote search requests
        private boolean stored;
        
        // more needed attributes:
        // - author / copyright owner
        // - keywords
        // - phrasecount, total number of phrases
        // - boolean: URL attributes (see Word-Entity definition)
        // - boolean: appearance of bold and/or italics
        // - ETag: for re-crawl decision upon HEAD request
        // - int: # of outlinks to same domain
        // - int: # of outlinks to outside domain
        // - int: # of keywords
        // - int: # der auf der Seite vorhandenen Links zu image, audio, video, applications
        
        public Entry(URL url, String descr, Date moddate, Date loaddate, String referrerHash, int copyCount, boolean localNeed, int quality, String language, char doctype, int size, int wordCount) {
            // create new entry and store it into database
            this.urlHash = urlHash(url);
            this.url = url;
            this.descr = (descr == null) ? this.url.toString() : descr;
            this.moddate = moddate;
            this.loaddate = loaddate;
            this.referrerHash = (referrerHash == null) ? dummyHash : referrerHash;
            this.copyCount = copyCount; // the number of remote (global) copies of this object without this one
            this.flags = (localNeed) ? "L " : "  ";
            this.quality = quality;
            this.language = (language == null) ? "uk" : language;
            this.doctype = doctype;
            this.size = size;
            this.wordCount = wordCount;
            this.snippet = null;
            this.word = null;
            this.stored = false;
        }

        public Entry(String urlHash, indexEntry searchedWord) throws IOException {
            // generates an plasmaLURLEntry using the url hash
            // to speed up the access, the url-hashes are buffered
            // in the hash cache.
            // we have two options to find the url:
            // - look into the hash cache
            // - look into the filed properties
            // if the url cannot be found, this returns null
            this.urlHash = urlHash;
            kelondroRow.Entry entry = plasmaCrawlLURL.this.urlHashCache.get(urlHash.getBytes());
            if (entry == null) throw new IOException("url hash " + urlHash + " not found in LURL");
            insertEntry(entry, searchedWord);
            this.stored = true;
        }
        
        public Entry(kelondroRow.Entry entry, indexEntry searchedWord) throws IOException {
            assert (entry != null);
            insertEntry(entry, word);
            this.stored = false;
        }
        
        private void insertEntry(kelondroRow.Entry entry, indexEntry searchedWord) throws IOException {
            try {
                this.urlHash = entry.getColString(0, null);
                this.url = new URL(entry.getColString(1, "UTF-8").trim());
                this.descr = (entry.empty(2)) ? this.url.toString() : entry.getColString(2, "UTF-8").trim();
                this.moddate = new Date(86400000 * entry.getColLong(3));
                this.loaddate = new Date(86400000 * entry.getColLong(4));
                this.referrerHash = (entry.empty(5)) ? dummyHash : entry.getColString(5, "UTF-8");
                this.copyCount = (int) entry.getColLong(6);
                this.flags = entry.getColString(7, "UTF-8");
                this.quality = (int) entry.getColLong(8);
                this.language = entry.getColString(9, "UTF-8");
                this.doctype = (char) entry.getColByte(10);
                this.size = (int) entry.getColLong(11);
                this.wordCount = (int) entry.getColLong(12);
                this.snippet = null;
                this.word = searchedWord;
                this.stored = false;
                return;
            } catch (Exception e) {
                serverLog.logSevere("PLASMA", "INTERNAL ERROR in plasmaLURL.entry/1: " + e.toString(), e);
                throw new IOException("plasmaLURL.entry/1: " + e.toString());
            }
        }

        public Entry(Properties prop, boolean setGlobal) {
            // generates an plasmaLURLEntry using the properties from the argument
            // the property names must correspond to the one from toString
            //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
            this.urlHash = prop.getProperty("hash", dummyHash);
            try {
                //byte[][] entry = urlHashCache.get(urlHash.getBytes());
                //if (entry == null) {
                this.referrerHash = prop.getProperty("referrer", dummyHash);
                this.moddate = shortDayFormatter.parse(prop.getProperty("mod", "20000101"));
                //System.out.println("DEBUG: moddate = " + moddate + ", prop=" + prop.getProperty("mod"));
                this.loaddate = shortDayFormatter.parse(prop.getProperty("load", "20000101"));
                this.copyCount = Integer.parseInt(prop.getProperty("cc", "0"));
                this.flags = ((prop.getProperty("local", "true").equals("true")) ? "L " : "  ");
                if (setGlobal) this.flags = "G ";
                this.url = new URL(crypt.simpleDecode(prop.getProperty("url", ""), null));
                this.descr = crypt.simpleDecode(prop.getProperty("descr", ""), null);
                        if (this.descr == null) this.descr = this.url.toString();
                this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(prop.getProperty("q", ""));
                this.language = prop.getProperty("lang", "uk");
                this.doctype = prop.getProperty("dt", "t").charAt(0);
                this.size = Integer.parseInt(prop.getProperty("size", "0"));
                this.wordCount = Integer.parseInt(prop.getProperty("wc", "0"));
                this.snippet = prop.getProperty("snippet", "");
                if (snippet.length() == 0) snippet = null; else snippet = crypt.simpleDecode(snippet, null);
                this.word = (prop.containsKey("word")) ? new indexURLEntry(kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("word",""))) : null;
                this.stored = false;
                //}
            } catch (Exception e) {
                serverLog.logSevere("PLASMA", "INTERNAL ERROR in plasmaLURL.entry/2: " + e.toString(), e);
            }
        }

        public void store() {
            // Check if there is a more recent Entry already in the DB
            if (this.stored) return;
                Entry oldEntry;
                try {
                    if (exists(urlHash)) {
                        oldEntry = new Entry(urlHash, null);
                    } else {
                        oldEntry = null;
                    }
                } catch (Exception e) {
                    oldEntry = null;
                }
                if ((oldEntry != null) && (isOlder(oldEntry))) {
                    // the fetched oldEntry is better, so return its properties instead of the new ones
                    // this.urlHash = oldEntry.urlHash; // unnecessary, should be the same
                    // this.url = oldEntry.url; // unnecessary, should be the same
                    this.descr = oldEntry.descr;
                    this.moddate = oldEntry.moddate;
                    this.loaddate = oldEntry.loaddate;
                    this.referrerHash = oldEntry.referrerHash;
                    this.copyCount = oldEntry.copyCount;
                    this.flags =  oldEntry.flags;
                    this.quality = oldEntry.quality;
                    this.language = oldEntry.language;
                    this.doctype = oldEntry.doctype;
                    this.size = oldEntry.size;
                    this.wordCount = oldEntry.wordCount;
                    // this.snippet // not read from db
                    // this.word // not read from db
                    return;
                }

                // stores the values from the object variables into the database
                final String moddatestr = kelondroBase64Order.enhancedCoder.encodeLong(moddate.getTime() / 86400000, urlDateLength);
                final String loaddatestr = kelondroBase64Order.enhancedCoder.encodeLong(loaddate.getTime() / 86400000, urlDateLength);

                // store the hash in the hash cache
                try {
                    // even if the entry exists, we simply overwrite it
                    final byte[][] entry = new byte[][] {
                        urlHash.getBytes(),
                        url.toString().getBytes(),
                        descr.getBytes(), // null?
                        moddatestr.getBytes(),
                        loaddatestr.getBytes(),
                        referrerHash.getBytes(),
                        kelondroBase64Order.enhancedCoder.encodeLong(copyCount, urlCopyCountLength).getBytes(),
                        flags.getBytes(),
                        kelondroBase64Order.enhancedCoder.encodeLong(quality, urlQualityLength).getBytes(),
                        language.getBytes(),
                        new byte[] {(byte) doctype},
                        kelondroBase64Order.enhancedCoder.encodeLong(size, urlSizeLength).getBytes(),
                        kelondroBase64Order.enhancedCoder.encodeLong(wordCount, urlWordCountLength).getBytes(),
                    };
                    urlHashCache.put(urlHashCache.row().newEntry(entry));
                    //serverLog.logFine("PLASMA","STORED new LURL " + url.toString());
                    this.stored = true;
                } catch (Exception e) {
                    serverLog.logSevere("PLASMA", "INTERNAL ERROR AT plasmaCrawlLURL:store:" + e.toString(), e);
                }
        }

        public String hash() {
            // return a url-hash, based on the md5 algorithm
            // the result is a String of 12 bytes within a 72-bit space
            // (each byte has an 6-bit range)
            // that should be enough for all web pages on the world
            return this.urlHash;
        }

        public URL url() {
            return url;
        }

        public String descr() {
            return descr;
        }

        public Date moddate() {
            return moddate;
        }

        public Date loaddate() {
            return loaddate;
        }

        public String referrerHash() {
            // return the creator's hash
            return referrerHash;
        }

        public char doctype() {
            return doctype;
        }

        public int copyCount() {
            // return number of copies of this object in the global index
            return copyCount;
        }

        public boolean local() {
            // returns true if the url was created locally and is needed for own word index
            if (flags == null) return false;
            return flags.charAt(0) == 'L';
        }

        public int quality() {
            return quality;
        }

        public String language() {
            return language;
        }

        public int size() {
            return size;
        }

        public int wordCount() {
            return wordCount;
        }

        public String snippet() {
            // the snippet may appear here if the url was transported in a remote search
            // it will not be saved anywhere, but can only be requested here
            return snippet;
        }

        public indexEntry word() {
            return word;
        }
    
        public boolean isOlder (Entry other) {
            if (other == null) return false;
            if (moddate.before(other.moddate())) return true;
            if (moddate.equals(other.moddate())) {
                if (loaddate.before(other.loaddate())) return true;
                if (loaddate.equals(other.loaddate())) {
                    if (quality < other.quality()) return true;
                }
            }
            return false;
        }

        private StringBuffer corePropList() {
            // generate a parseable string; this is a simple property-list
            final StringBuffer corePropStr = new StringBuffer(300);
            try {
                corePropStr
                .append("hash=")     .append(urlHash)
                .append(",referrer=").append(referrerHash)
                .append(",mod=")     .append(shortDayFormatter.format(moddate))
                .append(",load=")    .append(shortDayFormatter.format(loaddate))
                .append(",size=")    .append(size)
                .append(",wc=")      .append(wordCount)
                .append(",cc=")      .append(copyCount)
                .append(",local=")   .append(((local()) ? "true" : "false"))
                .append(",q=")       .append(kelondroBase64Order.enhancedCoder.encodeLong(quality, urlQualityLength))
                .append(",dt=")      .append(doctype)
                .append(",lang=")    .append(language)
                .append(",url=")     .append(crypt.simpleEncode(url.toString()))
                .append(",descr=")   .append(crypt.simpleEncode(descr));

                if (this.word != null) {
                    // append also word properties
                    corePropStr.append(",word=").append(kelondroBase64Order.enhancedCoder.encodeString(word.toPropertyForm()));
                }
                return corePropStr;

            } catch (Exception e) {
//          serverLog.logFailure("plasmaLURL.corePropList", e.getMessage());
//          if (moddate == null) serverLog.logFailure("plasmaLURL.corePropList", "moddate=null");
//          if (loaddate == null) serverLog.logFailure("plasmaLURL.corePropList", "loaddate=null");
//          e.printStackTrace();
                return null;
            }
        }

    /*
    public String toString(int posintext, int posinphrase, int posofphrase) {
        // add information needed for remote transport
        final StringBuffer core = corePropList();
        if (core == null) return null;

        core.ensureCapacity(core.length() + 200);
        core.insert(0,"{")
            .append(",posintext=").append(posintext)
            .append(",posinphrase=").append(posinphrase)
            .append(",posofphraseint=").append(posofphrase)
            .append("}");
        return core.toString();
    }        
    */
    
        public String toString(String snippet) {
            // add information needed for remote transport
            final StringBuffer core = corePropList();
            if (core == null) return null;

            core.ensureCapacity(core.length() + snippet.length()*2);
            core.insert(0,"{");
            core.append(",snippet=").append(crypt.simpleEncode(snippet));
            core.append("}");

            return core.toString();        
            //return "{" + core + ",snippet=" + crypt.simpleEncode(snippet) + "}";
        }

        /**
         * Returns this object as String.<br> 
         * This e.g. looks like this:
         * <pre>{hash=jmqfMk7Y3NKw,referrer=------------,mod=20050610,load=20051003,size=51666,wc=1392,cc=0,local=true,q=AEn,dt=h,lang=uk,url=b|aHR0cDovL3d3dy50cmFuc3BhcmVuY3kub3JnL3N1cnZleXMv,descr=b|S25vd2xlZGdlIENlbnRyZTogQ29ycnVwdGlvbiBTdXJ2ZXlzIGFuZCBJbmRpY2Vz}</pre>
         */
        public String toString() {
            final StringBuffer core = corePropList();
            if (core == null) return null;

            core.insert(0,"{");
            core.append("}");

            return core.toString();
            //return "{" + core + "}";
        }

        public void print() {
            System.out.println("URL           : " + url);
            System.out.println("Description   : " + descr);
            System.out.println("Modified      : " + httpc.dateString(moddate));
            System.out.println("Loaded        : " + httpc.dateString(loaddate));
            System.out.println("Size          : " + size + " bytes, " + wordCount + " words");
            System.out.println("Referrer Hash : " + referrerHash);
            System.out.println("Quality       : " + quality);
            System.out.println("Language      : " + language);
            System.out.println("DocType       : " + doctype);
            System.out.println();
        }
    } // class Entry

    public class kiter implements Iterator {
        // enumerates entry elements
        Iterator i;
        boolean error = false;
        
        public kiter(boolean up, boolean rotating, String firstHash) throws IOException {
            i = urlHashCache.rows(up, rotating, (firstHash == null) ? null : firstHash.getBytes());
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
                return new Entry(e, null);
            } catch (IOException ex) {
                throw new RuntimeException("error '" + ex.getMessage() + "' for hash " + e.getColString(0, null));
            }
        }
        
        public void remove() {
            i.remove();
        }
        
    }

    public Iterator entries(boolean up, boolean rotating, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, rotating, firstHash);
    }
    
    /**
     * Uses an Iteration over urlHash.db to detect malformed URL-Entries.
     * Damaged URL-Entries will be marked in a HashSet and removed at the end of the function.
     *
     * @param homePath Root-Path where all information is to be found.
     */
    public void urldbcleanup() {
        serverLog log = new serverLog("URLDBCLEANUP");
        HashSet damagedURLS = new HashSet();
        try {
            Iterator eiter = entries(true, false, null);
            int iteratorCount = 0;
            while (eiter.hasNext()) try {
                eiter.next();
                iteratorCount++;
            } catch (RuntimeException e) {
                if(e.getMessage() != null) {
                    String m = e.getMessage();
                    damagedURLS.add(m.substring(m.length() - 12));
                } else {
                    log.logSevere("RuntimeException:", e);
                }
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            log.logInfo("URLs vorher: " + size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            Iterator eiter2 = damagedURLS.iterator();
            String urlHash;
            while (eiter2.hasNext()) {
                urlHash = (String) eiter2.next();

                // trying to fix the invalid URL
                httpc theHttpc = null;
                String oldUrlStr = null;
                try {
                    // getting the url data as byte array
                    kelondroRow.Entry entry = urlHashCache.get(urlHash.getBytes());

                    // getting the wrong url string
                    oldUrlStr = entry.getColString(1, null).trim();

                    int pos = -1;
                    if ((pos = oldUrlStr.indexOf("://")) != -1) {
                        // trying to correct the url
                        String newUrlStr = "http://" + oldUrlStr.substring(pos + 3);
                        URL newUrl = new URL(newUrlStr);

                        // doing a http head request to test if the url is correct
                        theHttpc = httpc.getInstance(newUrl.getHost(), newUrl.getHost(), newUrl.getPort(), 30000, false);
                        response res = theHttpc.HEAD(newUrl.getPath(), null);

                        if (res.statusCode == 200) {
                            entry.setCol(1, newUrl.toString().getBytes());
                            urlHashCache.put(entry);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' corrected\n\tURL: " + oldUrlStr + " -> " + newUrlStr);
                        } else {
                            remove(urlHash);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tConnection Status: " + res.status);
                        }
                    }
                } catch (Exception e) {
                    remove(urlHash);
                    log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                } finally {
                    if (theHttpc != null) try {
                        theHttpc.close();
                        httpc.returnInstance(theHttpc);
                    } catch (Exception e) { }
                }
            }

            log.logInfo("URLs nachher: " + size() + " kaputte URLs: " + damagedURLS.size());
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
    }

    //  The Cleaner class was provided as "UrldbCleaner" by Hydrox
    //  see http://www.yacy-forum.de/viewtopic.php?p=18093#18093
    public Cleaner makeCleaner() {
        return new Cleaner();
    }
    
    public class Cleaner extends Thread {

        private boolean run = true;
        private boolean pause = false;    
        public int blacklistedUrls = 0;
        public int totalSearchedUrls = 1;
        public String lastBlacklistedUrl = "";
        public String lastBlacklistedHash = "";
        public String lastUrl = "";
        public String lastHash = "";
        
        public Cleaner() {
        }
        
        public void run() {
            try {
                serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread startet");
                Iterator eiter = entries(true, false, null);
                while (eiter.hasNext() && run) {
                    synchronized(this) {
                        if (this.pause) {
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                serverLog.logWarning("URLDBCLEANER", "InterruptedException", e);
                                this.run = false;
                                return;
                            }
                        }
                    }
                    
                    plasmaCrawlLURL.Entry entry = (plasmaCrawlLURL.Entry) eiter.next();
                    totalSearchedUrls++;
                    if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER,entry.url())==true || plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_DHT,entry.url())==true) {
                        lastBlacklistedUrl = entry.url().toString();
                        lastBlacklistedHash = entry.hash();                        
                        serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double)blacklistedUrls/totalSearchedUrls)*100 + "%): " + entry.hash() + " " + entry.url());
                        remove(entry.hash());
                        if (blacklistedUrls % 100 == 0) {
                            serverLog.logInfo("URLDBCLEANER", "Deleted " + blacklistedUrls + " URLs until now. Last deleted URL-Hash: " + lastBlacklistedUrl);
                        }
                    }
                    lastUrl = entry.url().toString();
                    lastHash = entry.hash();
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("not found in LURL") != -1) {
                    serverLog.logWarning("URLDBCLEANER", "urlHash not found in LURL", e);
                }
                else {
                    serverLog.logWarning("URLDBCLEANER", "RuntimeException", e);
                    run = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                run = false;
            }
            serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread stopped");
        }
        
        public void abort() {
            synchronized(this) {
                run = false;
                this.notifyAll();
            }
        }

        public void pause() {
            synchronized(this) {
                if(pause == false) {
                    pause = true;
                    serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized(this) {
                if (pause == true) {
                    pause = false;
                    this.notifyAll();
                    serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread resumed");
                }
            }
        }
    }
    
    
    public static void main(String[] args) {
        // test-generation of url hashes for debugging
        // one argument requires, will be treated as url
        // returns url-hash
        if (args[0].equals("-h")) try {
            // arg 1 is url
            System.out.println("HASH: " + urlHash(new URL(args[1])));
        } catch (MalformedURLException e) {}
        if (args[0].equals("-l")) try {
            // arg 1 is path to URLCache
            final plasmaCrawlLURL urls = new plasmaCrawlLURL(new File(args[1]), 1, 0);
            final Iterator enu = urls.entries(true, false, null);
            while (enu.hasNext()) {
                ((Entry) enu.next()).print();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
