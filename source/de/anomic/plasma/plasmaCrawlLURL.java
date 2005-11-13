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
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Properties;
import de.anomic.http.httpc;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverCodings;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class plasmaCrawlLURL extends plasmaURL {

    // result stacks;
    // these have all entries of form
    // strings: urlHash + initiatorHash + ExecutorHash
    private final LinkedList externResultStack; // 1 - remote index: retrieved by other peer
    private final LinkedList searchResultStack; // 2 - partly remote/local index: result of search queries
    private final LinkedList transfResultStack; // 3 - partly remote/local index: result of index transfer
    private final LinkedList proxyResultStack;  // 4 - local index: result of proxy fetch/prefetch
    private final LinkedList lcrawlResultStack; // 5 - local index: result of local crawling
    private final LinkedList gcrawlResultStack; // 6 - local index: triggered external

    public plasmaCrawlLURL(File cachePath, int bufferkb) throws IOException {
        super();
        int[] ce = {
            urlHashLength,
            urlStringLength,
            urlDescrLength,
            urlDateLength,
            urlDateLength,
            urlHashLength,
            urlCopyCountLength,
            urlFlagLength,
            urlQualityLength,
            urlLanguageLength,
            urlDoctypeLength,
            urlSizeLength,
            urlWordCountLength
        };
        int segmentsize = 0;
        for (int i = 0; i < ce.length; i++) { segmentsize += ce[i]; }
        if (cachePath.exists()) {
            // open existing cache
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400);
        } else {
            // create new cache
            cachePath.getParentFile().mkdirs();
           urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, ce);
        }

        // init result stacks
        externResultStack = new LinkedList();
        searchResultStack = new LinkedList();
        transfResultStack = new LinkedList();
        proxyResultStack  = new LinkedList();
        lcrawlResultStack = new LinkedList();
        gcrawlResultStack = new LinkedList();
    }

    public synchronized Entry addEntry(URL url, String descr, Date moddate, Date loaddate,
                                       String initiatorHash, String executorHash,
                                       String referrerHash, int copyCount, boolean localNeed,
                                       int quality, String language, char doctype,
                                       long size, int wordCount, int stackType) {
        Entry e = new Entry(url, descr, moddate, loaddate, referrerHash, copyCount, localNeed, quality, language, doctype, size, wordCount);
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
        return e;
    }

    public synchronized Entry addEntry(Entry e, String initiatorHash, String executorHash, int stackType) {
        if (e == null) { return null; }
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
            return e;
        } catch (Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return null;
        }
    }

    public void notifyGCrawl(String urlHash, String initiatorHash, String executorHash) {
        gcrawlResultStack.add(urlHash + initiatorHash + executorHash);
    }

    public synchronized Entry getEntry(String hash) {
        return new Entry(hash);
    }

    public synchronized Entry newEntry(Entry oldEntry) {
        if (oldEntry == null) return null;
        /*
         * de.anomic.plasma.plasmaCrawlLURL.Entry.Entry(URL url, String descr,
         * Date moddate, Date loaddate,
         * String referrerHash,
         * int copyCount,
         * boolean localNeed,
         * int quality,
         * String language,
         * char doctype,
         * long size,
         * int wordCount)
         */
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
        boolean exists1 = super.remove(urlHash);
        for (int stack = 1; stack <= 6; stack++) {
            for (int i = getStackSize(stack) - 1; i >= 0; i--) {
                if (getUrlHash(stack,i).equals(urlHash)) {
                    boolean exits2 = removeStack(stack,i);
                    exists1 = exists1 || exits2;
                    return exists1;
                }
            }
        }
        return exists1;
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
        serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps tabletype=" + tabletype    +    " lines=" + lines    +
                                                                    " showInit=" + showInit     + " showExec=" + showExec +
                                                                    " dfltInit=" + dfltInit     + " dfltExec=" + dfltExec +
                                                                " feedbackpage=" + feedbackpage + " makeLink=" + makeLink);
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
        plasmaCrawlLURL.Entry urle;
        yacySeed initiatorSeed, executorSeed;
        String cachepath,url,txt;
        int p, c = 0;

        for (int i = getStackSize(tabletype) - 1; i >= (getStackSize(tabletype) - lines); i--) {
            initiatorHash = getInitiatorHash(tabletype, i);
            executorHash = getExecutorHash(tabletype, i);
//          serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps initiatorHash=" + initiatorHash + " executorHash=" + executorHash);
            urlHash = getUrlHash(tabletype, i);
//          serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urlHash=" + urlHash);
            urle = getEntry(urlHash);
//          serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps urle=" + urle.toString());
            if (urle != null) try {
                initiatorSeed = yacyCore.seedDB.getConnected(initiatorHash);
                executorSeed = yacyCore.seedDB.getConnected(executorHash);

                url = urle.url().toString();
                txt = urle.url().toString();

                // Kosmetik, die wirklich benutzte URL behaelt die ':80'
                if (txt.endsWith(":80")) txt = txt.substring(0, txt.length() - 3);
                if ((p = txt.indexOf(":80/")) != -1) {
                    txt = txt.substring(0, p).concat(txt.substring(p + 3)); // den '/' erstmal nicht abschneiden
                    serverLog.logFinest("PLASMA", "plasmaCrawlLURL/genTableProps Remove ':80' URL=" + txt);
                }

                txt = nxTools.cutUrlText(txt, 72); // shorten the string text like a URL

                cachepath = (urle.url() == null) ? "-not-cached-" : url.substring(url.indexOf("://") + 3);
                if (cachepath.endsWith("/")) cachepath = cachepath + "ndx";

                prop.put("table_indexed_" + c + "_dark", (dark) ? 1 : 0);
                prop.put("table_indexed_" + c + "_feedbackpage", feedbackpage);
                prop.put("table_indexed_" + c + "_tabletype", tabletype);
                prop.put("table_indexed_" + c + "_urlhash", urlHash);
                prop.put("table_indexed_" + c + "_showInit", (showInit) ? 1 : 0);
                prop.put("table_indexed_" + c + "_showInit_initiatorSeed", (initiatorSeed == null) ? dfltInit : initiatorSeed.getName());
                prop.put("table_indexed_" + c + "_showExec", (showExec) ? 1 : 0);
                prop.put("table_indexed_" + c + "_showExec_executorSeed", (executorSeed == null) ? dfltExec : executorSeed.getName());
                prop.put("table_indexed_" + c + "_moddate", daydate(urle.moddate()));
                prop.put("table_indexed_" + c + "_wordcount", urle.wordCount());
                prop.put("table_indexed_" + c + "_urldescr", urle.descr());
                prop.put("table_indexed_" + c + "_url", (urle.url() == null) ? "-not-cached-" : ((makeLink) ? ("<a href=\"CacheAdmin_p.html?action=info&path=" + cachepath + "\" class=\"small\" title=\"" + url + "\">" + txt + "</a>") : url));
                dark = !dark;
                c++;
            } catch (Exception e) {
                serverLog.logSevere("PLASMA", "genTableProps", e);
            }
        }
        prop.put("table_indexed", c);
        return prop;
    }

    public class Entry {

    private URL    url;
    private String descr;
    private Date   moddate;
    private Date   loaddate;
    private String urlHash;
    private String referrerHash;
    private int    copyCount;
    private String flags;
    private int    quality;
    private String language;
    private char   doctype;
    private long   size;
    private int    wordCount;
        private String snippet;

    public Entry(URL url, String descr, Date moddate, Date loaddate,
             String referrerHash, int copyCount, boolean localNeed,
             int quality, String language, char doctype, long size, int wordCount) {
        // create new entry and store it into database
        this.urlHash = urlHash(url);
        this.url = url;
        this.descr = (descr==null)?this.url.toString():descr;
        this.moddate = moddate;
        this.loaddate = loaddate;
        this.referrerHash = (referrerHash == null) ? dummyHash : referrerHash;
        this.copyCount = copyCount; // the number of remote (global) copies of this object without this one
        this.flags = (localNeed) ? "L " : "  ";
        this.quality = quality;
        this.language = (language==null)?"uk":language;
        this.doctype = doctype;
        this.size = size;
        this.wordCount = wordCount;
        this.snippet = null;
        store();
    }

    public Entry(String urlHash) {
        // generates an plasmaLURLEntry using the url hash
        // to speed up the access, the url-hashes are buffered
        // in the hash cache.
        // we have two options to find the url:
        // - look into the hash cache
        // - look into the filed properties
        // if the url cannot be found, this returns null
        this.urlHash = urlHash;
        try {
            byte[][] entry = plasmaCrawlLURL.this.urlHashCache.get(urlHash.getBytes());
            if (entry != null) {
                this.url = new URL(new String(entry[1]).trim());
                this.descr = (entry[2] == null) ? this.url.toString() : new String(entry[2]).trim();
                this.moddate = new Date(86400000 * serverCodings.enhancedCoder.decodeBase64Long(new String(entry[3])));
                this.loaddate = new Date(86400000 * serverCodings.enhancedCoder.decodeBase64Long(new String(entry[4])));
                this.referrerHash = (entry[5]==null)?dummyHash:new String(entry[5]);
                this.copyCount = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[6]));
                this.flags = new String(entry[7]);
                this.quality = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[8]));
                this.language = new String(entry[9]);
                this.doctype = (char) entry[10][0];
                this.size = serverCodings.enhancedCoder.decodeBase64Long(new String(entry[11]));
                this.wordCount = (int) serverCodings.enhancedCoder.decodeBase64Long(new String(entry[12]));
                this.snippet = null;
                return;
            }
        } catch (Exception e) {
            serverLog.logSevere("PLASMA", "INTERNAL ERROR in plasmaLURL.entry/1: " + e.toString(), e);
        }
    }

    public Entry(Properties prop, boolean setGlobal) {
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        this.urlHash = prop.getProperty("hash", dummyHash);
        try {
        byte[][] entry = urlHashCache.get(urlHash.getBytes());
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
            this.quality = (int) serverCodings.enhancedCoder.decodeBase64Long(prop.getProperty("q", ""));
            this.language = prop.getProperty("lang", "uk");
            this.doctype = prop.getProperty("dt", "t").charAt(0);
            this.size = Long.parseLong(prop.getProperty("size", "0"));
            this.wordCount = Integer.parseInt(prop.getProperty("wc", "0"));
                    this.snippet = prop.getProperty("snippet", "");
                    if (snippet.length() == 0) snippet = null; else snippet = crypt.simpleDecode(snippet, null);
            store();
            //}
        } catch (Exception e) {
        serverLog.logSevere("PLASMA", "INTERNAL ERROR in plasmaLURL.entry/2: " + e.toString(), e);
        }
    }

    private void store() {
        // stores the values from the object variables into the database
        final String moddatestr = serverCodings.enhancedCoder.encodeBase64Long(moddate.getTime() / 86400000, urlDateLength);
        final String loaddatestr = serverCodings.enhancedCoder.encodeBase64Long(loaddate.getTime() / 86400000, urlDateLength);

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
            serverCodings.enhancedCoder.encodeBase64Long(copyCount, urlCopyCountLength).getBytes(),
            flags.getBytes(),
            serverCodings.enhancedCoder.encodeBase64Long(quality, urlQualityLength).getBytes(),
            language.getBytes(),
            new byte[] {(byte) doctype},
            serverCodings.enhancedCoder.encodeBase64Long(size, urlSizeLength).getBytes(),
            serverCodings.enhancedCoder.encodeBase64Long(wordCount, urlWordCountLength).getBytes(),
        };
        urlHashCache.put(entry);
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

    public long size() {
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
            .append(",q=")       .append(serverCodings.enhancedCoder.encodeBase64Long(quality, urlQualityLength))
            .append(",dt=")      .append(doctype)
            .append(",lang=")    .append(language)
            .append(",url=")     .append(crypt.simpleEncode(url.toString()))
            .append(",descr=")   .append(crypt.simpleEncode(descr));                
            return corePropStr;

//          return
//          "hash=" + urlHash +
//          ",referrer=" + referrerHash +
//          ",mod=" + shortDayFormatter.format(moddate) +
//          ",load=" + shortDayFormatter.format(loaddate) +
//          ",size=" + size +
//          ",wc=" + wordCount +
//          ",cc=" + copyCount +
//          ",local=" + ((local()) ? "true" : "false") +
//          ",q=" + serverCodings.enhancedCoder.encodeBase64Long(quality, urlQualityLength) +
//          ",dt=" + doctype +
//          ",lang=" + language +
//          ",url=" + crypt.simpleEncode(url.toString()) +
//          ",descr=" + crypt.simpleEncode(descr);
        } catch (Exception e) {
//          serverLog.logFailure("plasmaLURL.corePropList", e.getMessage());
//          if (moddate == null) serverLog.logFailure("plasmaLURL.corePropList", "moddate=null");
//          if (loaddate == null) serverLog.logFailure("plasmaLURL.corePropList", "loaddate=null");
//          e.printStackTrace();
            return null;
        }
    }

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

//      return
//      "{" + core +
//      ",posintext=" + posintext +
//      ",posinphrase=" + posinphrase +
//      ",posofphraseint=" + posofphrase +
//      "}";
    }        

    public String toString(String snippet) {
        // add information needed for remote transport
        final StringBuffer core = corePropList();
        if (core == null) return null;

        core.ensureCapacity(core.length() + snippet.length()*2);
        core.insert(0,"{");
        core.append(",snippet=").append(crypt.simpleEncode(snippet)).append("}");

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
            return new Entry(new String(((byte[][])i.next())[0]));
        }
    }

    public Enumeration elements(boolean up, boolean rotating) throws IOException {
        // enumerates entry elements
        return new kenum(up, rotating);
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
            final plasmaCrawlLURL urls = new plasmaCrawlLURL(new File(args[1]), 1);
            final Enumeration enu = urls.elements(true, false);
            while (enu.hasMoreElements()) {
                ((Entry) enu.nextElement()).print();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}