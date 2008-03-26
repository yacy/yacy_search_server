// indexURLReference.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2006 on http://www.anomic.de
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

package de.anomic.index;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyURL;

public class indexURLReference {

    // this object stores attributes for URL entries
    
    public static final kelondroRow rowdef = new kelondroRow(
        "String hash-12, " +            // the url's hash
        "String comp-360, " +           // components: the url, description, author and tags. As 5th element, an ETag is possible
        "Cardinal mod-4 {b256}, " +     // last-modified from the httpd
        "Cardinal load-4 {b256}, " +    // time when the url was loaded
        "Cardinal fresh-4 {b256}, " +   // time until this url is fresh
        "String referrer-12, " +        // (one of) the url's referrer hash(es)
        "byte[] md5-8, " +              // the md5 of the url content (to identify changes)
        "Cardinal size-6 {b256}, " +    // size of file in bytes
        "Cardinal wc-3 {b256}, " +      // size of file by number of words; for video and audio: seconds
        "byte[] dt-1, " +               // doctype, taken from extension or any other heuristic
        "Bitfield flags-4, " +          // flags; any stuff (see Word-Entity definition)
        "String lang-2, " +             // language
        "Cardinal llocal-2 {b256}, " +  // # of outlinks to same domain; for video and image: width 
        "Cardinal lother-2 {b256}, " +  // # of outlinks to outside domain; for video and image: height
        "Cardinal limage-2 {b256}, " +  // # of embedded image links
        "Cardinal laudio-2 {b256}, " +  // # of embedded audio links; for audio: track number; for video: number of audio tracks
        "Cardinal lvideo-2 {b256}, " +  // # of embedded video links
        "Cardinal lapp-2 {b256}",       // # of embedded links to applications
        kelondroBase64Order.enhancedCoder,
        0);      
    
    /* ===========================================================================
     * Constants to access the various columns of an URL entry
     * =========================================================================== */
    /** the url's hash */
    private static final int col_hash     =  0;
    /** components: the url, description, author and tags. As 5th element, an ETag is possible */
    private static final int col_comp     =  1;
    /** components: the url, description, author and tags. As 5th element, an ETag is possible */
    private static final int col_mod      =  2;
    /** time when the url was loaded */
    private static final int col_load     =  3;
    /** time until this url is fresh */
    private static final int col_fresh    =  4;
    /** time when the url was loaded */
    private static final int col_referrer =  5;
    /** the md5 of the url content (to identify changes) */
    private static final int col_md5      =  6;
    /** size of file in bytes */
    private static final int col_size     =  7;
    /** size of file by number of words; for video and audio: seconds */
    private static final int col_wc       =  8;
    /** doctype, taken from extension or any other heuristic */
    private static final int col_dt       =  9;
    /** flags; any stuff (see Word-Entity definition) */
    private static final int col_flags    = 10;
    /** language */
    private static final int col_lang     = 11;
    /** of outlinks to same domain; for video and image: width */
    private static final int col_llocal   = 12;
    /** of outlinks to outside domain; for video and image: height */
    private static final int col_lother   = 13;
    /** of embedded image links */
    private static final int col_limage   = 14;
    /** of embedded audio links; for audio: track number; for video: number of audio tracks */
    private static final int col_laudio   = 15;
    /** of embedded video links */
    private static final int col_lvideo   = 16;
    /** of embedded links to applications */
    private static final int col_lapp     = 17;
    
    private kelondroRow.Entry entry;
    private String snippet;
    private indexRWIEntry word; // this is only used if the url is transported via remote search requests
    private long ranking; // during generation of a search result this value is set
    
    public indexURLReference(
            yacyURL url,
            String dc_title,
            String dc_creator,
            String dc_subject,
            String ETag,
            Date mod,
            Date load,
            Date fresh,
            String referrer,
            byte[] md5,
            long size,
            int wc,
            char dt,
            kelondroBitfield flags,
            String lang,
            int llocal,
            int lother,
            int laudio,
            int limage,
            int lvideo,
            int lapp) {
        // create new entry and store it into database
        this.entry = rowdef.newEntry();
        this.entry.setCol(col_hash, url.hash(), null);
        this.entry.setCol(col_comp, encodeComp(url, dc_title, dc_creator, dc_subject, ETag));
        encodeDate(col_mod, mod);
        encodeDate(col_load, load);
        encodeDate(col_fresh, fresh);
        this.entry.setCol(col_referrer, (referrer == null) ? null : referrer.getBytes());
        this.entry.setCol(col_md5, md5);
        this.entry.setCol(col_size, size);
        this.entry.setCol(col_wc, wc);
        this.entry.setCol(col_dt, new byte[]{(byte) dt});
        this.entry.setCol(col_flags, flags.bytes());
        this.entry.setCol(col_lang, lang.getBytes());
        this.entry.setCol(col_llocal, llocal);
        this.entry.setCol(col_lother, lother);
        this.entry.setCol(col_limage, limage);
        this.entry.setCol(col_laudio, laudio);
        this.entry.setCol(col_lvideo, lvideo);
        this.entry.setCol(col_lapp, lapp);
        //System.out.println("===DEBUG=== " + load.toString() + ", " + decodeDate(col_load).toString());
        this.snippet = null;
        this.word = null;
        this.ranking = 0;
    }

    private void encodeDate(int col, Date d) {
        // calculates the number of days since 1.1.1970 and returns this as 4-byte array
        this.entry.setCol(col, kelondroNaturalOrder.encodeLong(d.getTime() / 86400000, 4));
    }

    private Date decodeDate(int col) {
        return new Date(86400000 * this.entry.getColLong(col));
    }
    
    public static byte[] encodeComp(yacyURL url, String dc_title, String dc_creator, String dc_subject, String ETag) {
        serverCharBuffer s = new serverCharBuffer(200);
        s.append(url.toNormalform(false, true)).append(10);
        s.append(dc_title).append(10);
        s.append(dc_creator).append(10);
        s.append(dc_subject).append(10);
        s.append(ETag).append(10);
        return s.toString().getBytes();
    }
    
    public indexURLReference(kelondroRow.Entry entry, indexRWIEntry searchedWord, long ranking) {
        this.entry = entry;
        this.snippet = null;
        this.word = searchedWord;
        this.ranking = ranking;
    }

    public indexURLReference(Properties prop){
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        yacyURL url;
        try {
            url = new yacyURL(crypt.simpleDecode(prop.getProperty("url", ""), null), prop.getProperty("hash"));
        } catch (MalformedURLException e) {
            url = null;
        }
        String descr = crypt.simpleDecode(prop.getProperty("descr", ""), null); if (descr == null) descr = "";
        String dc_creator = crypt.simpleDecode(prop.getProperty("author", ""), null); if (dc_creator == null) dc_creator = "";
        String tags = crypt.simpleDecode(prop.getProperty("tags", ""), null); if (tags == null) tags = "";
        String ETag = crypt.simpleDecode(prop.getProperty("ETag", ""), null); if (ETag == null) ETag = "";
        
        this.entry = rowdef.newEntry();
        this.entry.setCol(col_hash, url.hash(), null);
        this.entry.setCol(col_comp, encodeComp(url, descr, dc_creator, tags, ETag));
        try {
            encodeDate(col_mod, serverDate.parseShortDay(prop.getProperty("mod", "20000101")));
        } catch (ParseException e) {
            encodeDate(col_mod, new Date());
        }
        try {
            encodeDate(col_load, serverDate.parseShortDay(prop.getProperty("load", "20000101")));
        } catch (ParseException e) {
            encodeDate(col_load, new Date());
        }
        try {
            encodeDate(col_fresh, serverDate.parseShortDay(prop.getProperty("fresh", "20000101")));
        } catch (ParseException e) {
            encodeDate(col_fresh, new Date());
        }
        this.entry.setCol(col_referrer, prop.getProperty("referrer", yacyURL.dummyHash).getBytes());
        this.entry.setCol(col_md5, serverCodings.decodeHex(prop.getProperty("md5", "")));
        this.entry.setCol(col_size, Integer.parseInt(prop.getProperty("size", "0")));
        this.entry.setCol(col_wc, Integer.parseInt(prop.getProperty("wc", "0")));
        this.entry.setCol(col_dt, new byte[]{(byte) prop.getProperty("dt", "t").charAt(0)});
        String flags = prop.getProperty("flags", "AAAAAA");
        this.entry.setCol(col_flags, (flags.length() > 6) ? plasmaSearchQuery.empty_constraint.bytes() : (new kelondroBitfield(4, flags)).bytes());
        this.entry.setCol(col_lang, prop.getProperty("lang", "uk").getBytes());
        this.entry.setCol(col_llocal, Integer.parseInt(prop.getProperty("llocal", "0")));
        this.entry.setCol(col_lother, Integer.parseInt(prop.getProperty("lother", "0")));
        this.entry.setCol(col_limage, Integer.parseInt(prop.getProperty("limage", "0")));
        this.entry.setCol(col_laudio, Integer.parseInt(prop.getProperty("laudio", "0")));
        this.entry.setCol(col_lvideo, Integer.parseInt(prop.getProperty("lvideo", "0")));
        this.entry.setCol(col_lapp, Integer.parseInt(prop.getProperty("lapp", "0")));
        this.snippet = crypt.simpleDecode(prop.getProperty("snippet", ""), null);
        this.word = null;
        if (prop.containsKey("word")) throw new kelondroException("old database structure is not supported");
        if (prop.containsKey("wi")) {
            this.word = new indexRWIRowEntry(kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("wi", ""), "de.anomic.index.indexURLEntry.indexURLEntry()"));
        }
        this.ranking = 0;
    }

    public static indexURLReference importEntry(String propStr) {
        if (propStr != null && propStr.startsWith("{") && propStr.endsWith("}")) try {
            return new indexURLReference(serverCodings.s2p(propStr.substring(1, propStr.length() - 1)));
        } catch (kelondroException e) {
                // wrong format
                return null;
        } else {
            return null;
        }
    }

    private StringBuffer corePropList() {
        // generate a parseable string; this is a simple property-list
        indexURLReference.Components comp = this.comp();
        final StringBuffer s = new StringBuffer(300);
        //System.out.println("author=" + comp.author());
        try {
            s.append("hash=").append(hash());
            s.append(",url=").append(crypt.simpleEncode(comp.url().toNormalform(false, true)));
            s.append(",descr=").append(crypt.simpleEncode(comp.dc_title()));
            s.append(",author=").append(crypt.simpleEncode(comp.dc_creator()));
            s.append(",tags=").append(crypt.simpleEncode(comp.dc_subject()));
            s.append(",ETag=").append(crypt.simpleEncode(comp.ETag()));
            s.append(",mod=").append(serverDate.formatShortDay(moddate()));
            s.append(",load=").append(serverDate.formatShortDay(loaddate()));
            s.append(",fresh=").append(serverDate.formatShortDay(freshdate()));
            s.append(",referrer=").append(referrerHash());
            s.append(",md5=").append(md5());
            s.append(",size=").append(size());
            s.append(",wc=").append(wordCount());
            s.append(",dt=").append(doctype());
            s.append(",flags=").append(flags().exportB64());
            s.append(",lang=").append(language());
            s.append(",llocal=").append(llocal());
            s.append(",lother=").append(lother());
            s.append(",limage=").append(limage());
            s.append(",laudio=").append(laudio());
            s.append(",lvideo=").append(lvideo());
            s.append(",lapp=").append(lapp());
            
            if (this.word != null) {
                // append also word properties
                s.append(",wi=").append(kelondroBase64Order.enhancedCoder.encodeString(word.toPropertyForm()));
            }
            return s;

        } catch (Exception e) {
            //          serverLog.logFailure("plasmaLURL.corePropList", e.getMessage());
            //          if (moddate == null) serverLog.logFailure("plasmaLURL.corePropList", "moddate=null");
            //          if (loaddate == null) serverLog.logFailure("plasmaLURL.corePropList", "loaddate=null");
            e.printStackTrace();
            return null;
        }
    }

    public kelondroRow.Entry toRowEntry() {
        return this.entry;
    }

    public String hash() {
        // return a url-hash, based on the md5 algorithm
        // the result is a String of 12 bytes within a 72-bit space
        // (each byte has an 6-bit range)
        // that should be enough for all web pages on the world
        return this.entry.getColString(col_hash, null);
    }

    public long ranking() {
    	return this.ranking;
    }
    
    public indexURLReference.Components comp() {
        ArrayList<String> cl = nxTools.strings(this.entry.getCol("comp", null), "UTF-8");
        return new indexURLReference.Components(
                (cl.size() > 0) ? ((String) cl.get(0)).trim() : "",
                hash(),
                (cl.size() > 1) ? ((String) cl.get(1)).trim() : "",
                (cl.size() > 2) ? ((String) cl.get(2)).trim() : "",
                (cl.size() > 3) ? ((String) cl.get(3)).trim() : "",
                (cl.size() > 4) ? ((String) cl.get(4)).trim() : "");
    }
    
    public Date moddate() {
        return decodeDate(col_mod);
    }

    public Date loaddate() {
        return decodeDate(col_load);
    }

    public Date freshdate() {
        return decodeDate(col_fresh);
    }

    public String referrerHash() {
        // return the creator's hash
        return entry.getColString(col_referrer, null);
    }

    public String md5() {
        // returns the md5 in hex representation
        return serverCodings.encodeHex(entry.getColBytes(col_md5));
    }

    public char doctype() {
        return (char) entry.getColByte(col_dt);
    }

    public String language() {
        return this.entry.getColString(col_lang, null);
    }

    public int size() {
        return (int) this.entry.getColLong(col_size);
    }

    public kelondroBitfield flags() {
        return new kelondroBitfield(this.entry.getColBytes(col_flags));
    }

    public int wordCount() {
        return (int) this.entry.getColLong(col_wc);
    }

    public int llocal() {
        return (int) this.entry.getColLong(col_llocal);
    }

    public int lother() {
        return (int) this.entry.getColLong(col_lother);
    }

    public int limage() {
        return (int) this.entry.getColLong(col_limage);
    }

    public int laudio() {
        return (int) this.entry.getColLong(col_laudio);
    }

    public int lvideo() {
        return (int) this.entry.getColLong(col_lvideo);
    }

    public int lapp() {
        return (int) this.entry.getColLong(col_lapp);
    }
    
    public String snippet() {
        // the snippet may appear here if the url was transported in a remote search
        // it will not be saved anywhere, but can only be requested here
        return snippet;
    }

    public indexRWIEntry word() {
        return word;
    }

    public boolean isOlder(indexURLReference other) {
        if (other == null) return false;
        Date tmoddate = moddate();
        Date omoddate = other.moddate();
        if (tmoddate.before(omoddate)) return true;
        if (tmoddate.equals(omoddate)) {
            Date tloaddate = loaddate();
            Date oloaddate = other.loaddate();
            if (tloaddate.before(oloaddate)) return true;
            if (tloaddate.equals(oloaddate)) return true;
        }
        return false;
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

        return new String(core);
        //return "{" + core + ",snippet=" + crypt.simpleEncode(snippet) + "}";
    }

    public plasmaCrawlEntry toBalancerEntry() {
        return new plasmaCrawlEntry(
                null, 
                comp().url(), 
                referrerHash(), 
                comp().dc_title(),
                loaddate(), 
                null,
                0, 
                0, 
                0);
    }
    
    /**
     * @return the object as String.<br> 
     * This e.g. looks like this:
     * <pre>{hash=jmqfMk7Y3NKw,referrer=------------,mod=20050610,load=20051003,size=51666,wc=1392,cc=0,local=true,q=AEn,dt=h,lang=uk,url=b|aHR0cDovL3d3dy50cmFuc3BhcmVuY3kub3JnL3N1cnZleXMv,descr=b|S25vd2xlZGdlIENlbnRyZTogQ29ycnVwdGlvbiBTdXJ2ZXlzIGFuZCBJbmRpY2Vz}</pre>
     */
    public String toString() {
        final StringBuffer core = corePropList();
        if (core == null) return null;

        core.insert(0, "{");
        core.append("}");

        return new String(core);
        //return "{" + core + "}";
    }

    public class Components {
        private yacyURL url;
        private String dc_title, dc_creator, dc_subject, ETag;
        
        public Components(String url, String urlhash, String title, String author, String tags, String ETag) {
            try {
                this.url = new yacyURL(url, urlhash);
            } catch (MalformedURLException e) {
                this.url = null;
            }
            this.dc_title = title;
            this.dc_creator = author;
            this.dc_subject = tags;
            this.ETag = ETag;
        }
        public Components(yacyURL url, String descr, String author, String tags, String ETag) {
            this.url = url;
            this.dc_title = descr;
            this.dc_creator = author;
            this.dc_subject = tags;
            this.ETag = ETag;
        }
        public yacyURL url()    { return this.url; }
        public String  dc_title()  { return this.dc_title; }
        public String  dc_creator() { return this.dc_creator; }
        public String  dc_subject()   { return this.dc_subject; }
        public String  ETag()   { return this.ETag; }
    }
    
}