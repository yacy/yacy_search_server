// plasmaCrawlLURLOldEntry.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 13.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.plasma;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import de.anomic.http.httpc;
import de.anomic.index.indexEntry;
import de.anomic.index.indexURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.net.URL;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.bitfield;
import de.anomic.tools.crypt;

public class plasmaCrawlLURLOldEntry implements plasmaCrawlLURLEntry {

    public static final kelondroRow rowdef = new kelondroRow(
            "String urlhash-" + indexURL.urlHashLength + ", " + // the url's hash
            "String urlstring-" + indexURL.urlStringLength + ", " + // the url as string
            "String urldescr-" + indexURL.urlDescrLength + ", " + // the description of the url
            "Cardinal moddate-" + indexURL.urlDateLength + " {b64e}, " + // last-modified from the httpd
            "Cardinal loaddate-" + indexURL.urlDateLength + " {b64e}, " + // time when the url was loaded
            "String refhash-" + indexURL.urlHashLength + ", " + // the url's referrer hash
            "Cardinal copycount-" + indexURL.urlCopyCountLength + " {b64e}, " + //
            "byte[] flags-" + indexURL.urlFlagLength + ", " + // flags
            "Cardinal quality-" + indexURL.urlQualityLength + " {b64e}, " + // 
            "String language-" + indexURL.urlLanguageLength + ", " + //
            "byte[] doctype-" + indexURL.urlDoctypeLength + ", " + //
            "Cardinal size-" + indexURL.urlSizeLength + " {b64e}, " + // size of file in bytes
            "Cardinal wc-" + indexURL.urlWordCountLength + " {b64e}"); // word count

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

    public plasmaCrawlLURLOldEntry(
            URL url,
            String descr,
            String author,
            String tags,
            String ETag,
            Date mod,
            Date load,
            Date fresh,
            String referrer,
            byte[] md5,
            long size,
            int wc,
            char dt,
            bitfield flags,
            String lang,
            int llocal,
            int lother,
            int laudio,
            int limage,
            int lvideo,
            int lapp) {
        // create new entry and store it into database
        this.urlHash = indexURL.urlHash(url);
        this.url = url;
        this.descr = (descr == null) ? this.url.toString() : descr;
        this.moddate = mod;
        this.loaddate = load;
        this.referrerHash = (referrerHash == null) ? indexURL.dummyHash : referrerHash;
        this.copyCount = 0; // the number of remote (global) copies of this object without this one
        this.flags = "  ";
        this.quality = 0;
        this.language = (language == null) ? "uk" : language;
        this.doctype = dt;
        this.size = (int) size;
        this.wordCount = wc;
        this.snippet = null;
        this.word = null;
    }

    public plasmaCrawlLURLOldEntry(kelondroRow.Entry entry, indexEntry searchedWord) throws IOException {
        try {
            this.urlHash = entry.getColString(0, null);
            this.url = new URL(entry.getColString(1, "UTF-8"));
            this.descr = (entry.empty(2)) ? this.url.toString() : entry.getColString(2, "UTF-8").trim();
            this.moddate = new Date(86400000 * entry.getColLong(3));
            this.loaddate = new Date(86400000 * entry.getColLong(4));
            this.referrerHash = (entry.empty(5)) ? indexURL.dummyHash : entry.getColString(5, "UTF-8");
            this.copyCount = (int) entry.getColLong(6);
            this.flags = entry.getColString(7, "UTF-8");
            this.quality = (int) entry.getColLong(8);
            this.language = entry.getColString(9, "UTF-8");
            this.doctype = (char) entry.getColByte(10);
            this.size = (int) entry.getColLong(11);
            this.wordCount = (int) entry.getColLong(12);
            this.snippet = null;
            this.word = searchedWord;
            return;
        } catch (Exception e) {
            serverLog.logSevere("PLASMA", "INTERNAL ERROR in plasmaLURL.entry/1: " + e.toString(), e);
            throw new IOException("plasmaLURL.entry/1: " + e.toString());
        }
    }

    public plasmaCrawlLURLOldEntry(Properties prop) {
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        this.urlHash = prop.getProperty("hash", indexURL.dummyHash);
        try {
            this.referrerHash = prop.getProperty("referrer", indexURL.dummyHash);
            this.moddate = indexURL.shortDayFormatter.parse(prop.getProperty("mod", "20000101"));
            //System.out.println("DEBUG: moddate = " + moddate + ", prop=" + prop.getProperty("mod"));
            this.loaddate = indexURL.shortDayFormatter.parse(prop.getProperty("load", "20000101"));
            this.copyCount = Integer.parseInt(prop.getProperty("cc", "0"));
            this.flags = ((prop.getProperty("local", "true").equals("true")) ? "L " : "  ");
            this.url = new URL(crypt.simpleDecode(prop.getProperty("url", ""), null));
            this.descr = crypt.simpleDecode(prop.getProperty("descr", ""), null);
            if (this.descr == null) this.descr = this.url.toString();
            this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(prop.getProperty("q", ""));
            this.language = prop.getProperty("lang", "uk");
            this.doctype = prop.getProperty("dt", "t").charAt(0);
            this.size = Integer.parseInt(prop.getProperty("size", "0"));
            this.wordCount = Integer.parseInt(prop.getProperty("wc", "0"));
            this.snippet = prop.getProperty("snippet", "");
            if (snippet.length() == 0) snippet = null;
            else snippet = crypt.simpleDecode(snippet, null);
            this.word = (prop.containsKey("word")) ? new indexURLEntry(kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("word", ""))) : null;
        } catch (Exception e) {
            serverLog.logSevere("PLASMA",
                    "INTERNAL ERROR in plasmaLURL.entry/2:"
                    + "\nProperties: "
                    + ((prop == null) ? null : prop.toString())
                    + ((prop.containsKey("word")) ? "\nWord:       "
                    + kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("word", "")) : "") + "\nErrorMsg:   "
                    + e.toString(), e);
        }
    }

    public static kelondroRow rowdef() {
        return rowdef;
    }
    
    public kelondroRow.Entry toRowEntry() throws IOException {
        final String moddatestr = kelondroBase64Order.enhancedCoder.encodeLong(moddate.getTime() / 86400000, indexURL.urlDateLength);
        final String loaddatestr = kelondroBase64Order.enhancedCoder.encodeLong(loaddate.getTime() / 86400000, indexURL.urlDateLength);

        final byte[][] entry = new byte[][] {
                urlHash.getBytes(),
                url.toString().getBytes(),
                descr.getBytes(), // null?
                moddatestr.getBytes(),
                loaddatestr.getBytes(),
                referrerHash.getBytes(),
                kelondroBase64Order.enhancedCoder.encodeLong(copyCount, indexURL.urlCopyCountLength).getBytes(),
                flags.getBytes(),
                kelondroBase64Order.enhancedCoder.encodeLong(quality, indexURL.urlQualityLength).getBytes(),
                language.getBytes(),
                new byte[] { (byte) doctype },
                kelondroBase64Order.enhancedCoder.encodeLong(size, indexURL.urlSizeLength).getBytes(),
                kelondroBase64Order.enhancedCoder.encodeLong(wordCount, indexURL.urlWordCountLength).getBytes()};
        return rowdef.newEntry(entry);
    }

    public String hash() {
        // return a url-hash, based on the md5 algorithm
        // the result is a String of 12 bytes within a 72-bit space
        // (each byte has an 6-bit range)
        // that should be enough for all web pages on the world
        return this.urlHash;
    }
    
    public Components comp() {
        return new Components(url, descr, "", "", "");
    }

    public Date moddate() {
        return moddate;
    }

    public Date loaddate() {
        return loaddate;
    }

    public Date freshdate() {
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
        return (language == null) ? "en" : language;
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

    public boolean isOlder(plasmaCrawlLURLEntry other) {
        if (other == null) return false;
        if (moddate.before(other.moddate())) return true;
        if (moddate.equals(other.moddate())) {
            if (loaddate.before(other.loaddate())) return true;
            if (loaddate.equals(other.loaddate())) return true;
        }
        return false;
    }

    private StringBuffer corePropList() {
        // generate a parseable string; this is a simple property-list
        final StringBuffer corePropStr = new StringBuffer(300);
        try {
            corePropStr.append("hash=").append(urlHash).append(",referrer=")
                    .append(referrerHash).append(",mod=").append(
                            indexURL.shortDayFormatter.format(moddate)).append(
                            ",load=").append(
                            indexURL.shortDayFormatter.format(loaddate))
                    .append(",size=").append(size).append(",wc=").append(
                            wordCount).append(",cc=").append(copyCount).append(
                            ",local=").append(((local()) ? "true" : "false"))
                    .append(",q=").append(
                            kelondroBase64Order.enhancedCoder.encodeLong(
                                    quality, indexURL.urlQualityLength))
                    .append(",dt=").append(doctype).append(",lang=").append(
                            language).append(",url=").append(
                            crypt.simpleEncode(url.toString())).append(
                            ",descr=").append(crypt.simpleEncode(descr));

            if (this.word != null) {
                // append also word properties
                corePropStr.append(",word=").append(kelondroBase64Order.enhancedCoder.encodeString(word.toPropertyForm(false)));
            }
            return corePropStr;

        } catch (Exception e) {
            return null;
        }
    }

    public String toString(String snippet) {
        // add information needed for remote transport
        final StringBuffer core = corePropList();
        if (core == null)
            return null;

        core.ensureCapacity(core.length() + snippet.length() * 2);
        core.insert(0, "{");
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

        core.insert(0, "{");
        core.append("}");

        return core.toString();
        //return "{" + core + "}";
    }

    public void print() {
        System.out.println("URL           : " + url);
        System.out.println("Description   : " + descr);
        System.out.println("Modified      : " + httpc.dateString(moddate));
        System.out.println("Loaded        : " + httpc.dateString(loaddate));
        System.out.println("Size          : " + size + " bytes, " + wordCount
                + " words");
        System.out.println("Referrer Hash : " + referrerHash);
        System.out.println("Quality       : " + quality);
        System.out.println("Language      : " + language);
        System.out.println("DocType       : " + doctype);
        System.out.println();
    }

}
