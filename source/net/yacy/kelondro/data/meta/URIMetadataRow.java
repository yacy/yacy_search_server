// URLMetadataRow.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2006 on http://www.anomic.de
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

package net.yacy.kelondro.data.meta;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.kelondro.util.MapTools;

import de.anomic.crawler.retrieval.Request;
import de.anomic.search.QueryParams;
import de.anomic.tools.crypt;

public class URIMetadataRow implements URIMetadata {

    // this object stores attributes for URL entries
    
    public static final Row rowdef = new Row(
        "String hash-12, " +            // the url's hash
        "String comp-360, " +           // components: the url, description, author, tags and publisher
        "Cardinal mod-4 {b256}, " +     // last-modified from the httpd
        "Cardinal load-4 {b256}, " +    // time when the url was loaded
        "Cardinal fresh-4 {b256}, " +   // time until this url is fresh
        "String referrer-12, " +        // (one of) the url's referrer hash(es)
        "byte[] md5-8, " +              // the md5 of the url content (to identify changes)
        "Cardinal size-6 {b256}, " +    // size of file in bytes
        "Cardinal wc-3 {b256}, " +      // size of file by number of words; for video and audio: seconds
        "byte[] dt-1, " +               // doctype, taken from extension or any other heuristic
        "Bitfield flags-4, " +          // flags; any stuff (see Word-Entity definition)
        "byte[] lang-2, " +             // language
        "Cardinal llocal-2 {b256}, " +  // # of outlinks to same domain; for video and image: width 
        "Cardinal lother-2 {b256}, " +  // # of outlinks to outside domain; for video and image: height
        "Cardinal limage-2 {b256}, " +  // # of embedded image links
        "Cardinal laudio-2 {b256}, " +  // # of embedded audio links; for audio: track number; for video: number of audio tracks
        "Cardinal lvideo-2 {b256}, " +  // # of embedded video links
        "Cardinal lapp-2 {b256}",       // # of embedded links to applications
        Base64Order.enhancedCoder
    );      
    
    /* ===========================================================================
     * Constants to access the various columns of an URL entry
     * =========================================================================== */
    private static final int col_hash     =  0; // the url's hash
    private static final int col_comp     =  1; // components: the url, description, author and tags. As 5th element, an ETag is possible
    private static final int col_mod      =  2; // the modifed-date time from the server (servertime in row)
    private static final int col_load     =  3; // time when the url was loaded
    private static final int col_fresh    =  4; // time until this url is fresh
    private static final int col_referrer =  5; // a referrer of the url (there may be several, but this is the one that was acually referring to this one)
    private static final int col_md5      =  6; // the md5 of the url content (to identify changes)
    private static final int col_size     =  7; // size of file in bytes
    private static final int col_wc       =  8; // size of file by number of words; for video and audio: seconds
    private static final int col_dt       =  9; // doctype, taken from extension or any other heuristic
    private static final int col_flags    = 10; // flags; any stuff (see Word-Entity definition)
    private static final int col_lang     = 11; // language
    private static final int col_llocal   = 12; // # of outlinks to same domain; for video and image: width
    private static final int col_lother   = 13; // # of outlinks to outside domain; for video and image: height
    private static final int col_limage   = 14; // # of embedded image links
    private static final int col_laudio   = 15; // # of embedded audio links; for audio: track number; for video: number of audio tracks
    private static final int col_lvideo   = 16; // # of embedded video links
    private static final int col_lapp     = 17; // # of embedded links to applications
    
    private final Row.Entry entry;
    private final String snippet;
    private WordReferenceVars word; // this is only used if the url is transported via remote search requests
    private final long ranking; // during generation of a search result this value is set
    private Components comp;
    
    public URIMetadataRow() {
        // create a dummy entry, good to produce poison objects
        this.entry = rowdef.newEntry();
        this.snippet = null;
        this.word = null;
        this.ranking = 0;
        this.comp = null;
    }
    
    public URIMetadataRow(
            final DigestURI url,
            final String dc_title,
            final String dc_creator,
            final String dc_subject,
            final String dc_publisher,
            final float lon, final float lat, // decimal degrees as in WGS84; if unknown both values may be 0.0f; 
            final Date mod,
            final Date load,
            final Date fresh,
            final String referrer,
            final byte[] md5,
            final long size,
            final int wc,
            final char dt,
            final Bitfield flags,
            final byte[] lang,
            final int llocal,
            final int lother,
            final int laudio,
            final int limage,
            final int lvideo,
            final int lapp) {
        // create new entry
        this.entry = rowdef.newEntry();
        this.entry.setCol(col_hash, url.hash());
        this.entry.setCol(col_comp, encodeComp(url, dc_title, dc_creator, dc_subject, dc_publisher, lat, lon));
        encodeDate(col_mod, mod);
        encodeDate(col_load, load);
        encodeDate(col_fresh, fresh);
        this.entry.setCol(col_referrer, (referrer == null) ? null : UTF8.getBytes(referrer));
        this.entry.setCol(col_md5, md5);
        this.entry.setCol(col_size, size);
        this.entry.setCol(col_wc, wc);
        this.entry.setCol(col_dt, new byte[]{(byte) dt});
        this.entry.setCol(col_flags, flags.bytes());
        this.entry.setCol(col_lang, lang);
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
        this.comp = null;
    }

    private void encodeDate(final int col, final Date d) {
        // calculates the number of days since 1.1.1970 and returns this as 4-byte array
        // 86400000 is the number of milliseconds in one day
        this.entry.setCol(col, NaturalOrder.encodeLong(d.getTime() / 86400000L, 4));
    }

    private Date decodeDate(final int col) {
        long t = this.entry.getColLong(col);
        /*if (t < 14600) */return new Date(86400000L * t); // time was stored as number of days since epoch
        /*
        if (t < 350400) return new Date(3600000L * t); // hours since epoch
        if (t < 21024000) return new Date(60000L * t); // minutes since epoch
        */
    }
    
    public static byte[] encodeComp(
            final DigestURI url,
            final String dc_title,
            final String dc_creator,
            final String dc_subject,
            final String dc_publisher,
            final float lat,
            final float lon) {
        final CharBuffer s = new CharBuffer(360);
        s.append(url.toNormalform(false, true)).append(10);
        s.append(dc_title).append(10);
        s.append(dc_creator.length() > 80 ? dc_creator.substring(0, 80) : dc_creator).append(10);
        s.append(dc_subject.length() > 120 ? dc_subject.substring(0, 120) : dc_subject).append(10);
        s.append(dc_publisher).append(10);
        if (lon == 0.0f && lat == 0.0f) s.append(10); else s.append(Float.toString(lat)).append(',').append(Float.toString(lon)).append(10);
		return UTF8.getBytes(s.toString());
    }
    
    public URIMetadataRow(final Row.Entry entry, final WordReferenceVars searchedWord, final long ranking) {
        this.entry = entry;
        this.snippet = null;
        this.word = searchedWord;
        this.ranking = ranking;
        this.comp = null;
    }

    public URIMetadataRow(final Properties prop) {
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        DigestURI url;
        try {
            url = new DigestURI(crypt.simpleDecode(prop.getProperty("url", ""), null), UTF8.getBytes(prop.getProperty("hash")));
        } catch (final MalformedURLException e) {
            url = null;
        }
        String descr = crypt.simpleDecode(prop.getProperty("descr", ""), null); if (descr == null) descr = "";
        String dc_creator = crypt.simpleDecode(prop.getProperty("author", ""), null); if (dc_creator == null) dc_creator = "";
        String tags = crypt.simpleDecode(prop.getProperty("tags", ""), null); if (tags == null) tags = "";
        String dc_publisher = crypt.simpleDecode(prop.getProperty("publisher", ""), null); if (dc_publisher == null) dc_publisher = "";
        String lons = crypt.simpleDecode(prop.getProperty("lon", "0.0"), null); if (lons == null) lons = "0.0";
        String lats = crypt.simpleDecode(prop.getProperty("lat", "0.0"), null); if (lats == null) lats = "0.0";
        
        this.entry = rowdef.newEntry();
        this.entry.setCol(col_hash, url.hash()); // FIXME potential null pointer access
        this.entry.setCol(col_comp, encodeComp(url, descr, dc_creator, tags, dc_publisher, Float.parseFloat(lats), Float.parseFloat(lons)));

        // create new formatters to make concurrency possible
        GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);
        
        try {
            encodeDate(col_mod, formatter.parse(prop.getProperty("mod", "20000101")));
        } catch (final ParseException e) {
            encodeDate(col_mod, new Date());
        }
        try {
            encodeDate(col_load, formatter.parse(prop.getProperty("load", "20000101")));
        } catch (final ParseException e) {
            encodeDate(col_load, new Date());
        }
        try {
            encodeDate(col_fresh, formatter.parse(prop.getProperty("fresh", "20000101")));
        } catch (final ParseException e) {
            encodeDate(col_fresh, new Date());
        }
        this.entry.setCol(col_referrer, UTF8.getBytes(prop.getProperty("referrer", "")));
        this.entry.setCol(col_md5, Digest.decodeHex(prop.getProperty("md5", "")));
        this.entry.setCol(col_size, Integer.parseInt(prop.getProperty("size", "0")));
        this.entry.setCol(col_wc, Integer.parseInt(prop.getProperty("wc", "0")));
        String dt = prop.getProperty("dt", "t");
        this.entry.setCol(col_dt, dt.length() > 0 ? new byte[]{(byte) dt.charAt(0)} : new byte[]{(byte) 't'});
        final String flags = prop.getProperty("flags", "AAAAAA");
        this.entry.setCol(col_flags, (flags.length() > 6) ? QueryParams.empty_constraint.bytes() : (new Bitfield(4, flags)).bytes());
        this.entry.setCol(col_lang, UTF8.getBytes(prop.getProperty("lang", "uk")));
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
            this.word = new WordReferenceVars(new WordReferenceRow(Base64Order.enhancedCoder.decodeString(prop.getProperty("wi", ""))));
        }
        this.ranking = 0;
        this.comp = null;
    }

    public static URIMetadataRow importEntry(final String propStr) {
        if (propStr == null || (propStr.length() > 0 && propStr.charAt(0) != '{') || !propStr.endsWith("}")) {
            return null;
        }
        try {
            return new URIMetadataRow(MapTools.s2p(propStr.substring(1, propStr.length() - 1)));
        } catch (final kelondroException e) {
                // wrong format
                return null;
        }
    }

    private StringBuilder corePropList() {
        // generate a parseable string; this is a simple property-list
        final Components metadata = this.metadata();
        final StringBuilder s = new StringBuilder(300);
        if (metadata == null) return null;
        //System.out.println("author=" + comp.author());
        
        // create new formatters to make concurrency possible
        GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);
        
        try {
            s.append("hash=").append(UTF8.String(hash()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",url=").append(crypt.simpleEncode(metadata.url().toNormalform(false, true)));
            assert (s.toString().indexOf(0) < 0);
            s.append(",descr=").append(crypt.simpleEncode(metadata.dc_title()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",author=").append(crypt.simpleEncode(metadata.dc_creator()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",tags=").append(crypt.simpleEncode(metadata.dc_subject()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",publisher=").append(crypt.simpleEncode(metadata.dc_publisher()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",lat=").append(metadata.lat());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lon=").append(metadata.lon());
            assert (s.toString().indexOf(0) < 0);
            s.append(",mod=").append(formatter.format(moddate()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",load=").append(formatter.format(loaddate()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",fresh=").append(formatter.format(freshdate()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",referrer=").append(referrerHash() == null ? "" : UTF8.String(referrerHash()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",md5=").append(md5());
            assert (s.toString().indexOf(0) < 0);
            s.append(",size=").append(size());
            assert (s.toString().indexOf(0) < 0);
            s.append(",wc=").append(wordCount());
            assert (s.toString().indexOf(0) < 0);
            s.append(",dt=").append(doctype());
            assert (s.toString().indexOf(0) < 0);
            s.append(",flags=").append(flags().exportB64());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lang=").append(language());
            assert (s.toString().indexOf(0) < 0);
            s.append(",llocal=").append(llocal());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lother=").append(lother());
            assert (s.toString().indexOf(0) < 0);
            s.append(",limage=").append(limage());
            assert (s.toString().indexOf(0) < 0);
            s.append(",laudio=").append(laudio());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lvideo=").append(lvideo());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lapp=").append(lapp());
            assert (s.toString().indexOf(0) < 0);
            
            if (this.word != null) {
                // append also word properties
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(word.toPropertyForm()));
            }
            assert (s.toString().indexOf(0) < 0);
            return s;

        } catch (final Exception e) {
            //          serverLog.logFailure("plasmaLURL.corePropList", e.getMessage());
            //          if (moddate == null) serverLog.logFailure("plasmaLURL.corePropList", "moddate=null");
            //          if (loaddate == null) serverLog.logFailure("plasmaLURL.corePropList", "loaddate=null");
            Log.logException(e);
            return null;
        }
    }

    public Row.Entry toRowEntry() {
        return this.entry;
    }

    public byte[] hash() {
        // return a url-hash, based on the md5 algorithm
        // the result is a String of 12 bytes within a 72-bit space
        // (each byte has an 6-bit range)
        // that should be enough for all web pages on the world
        return this.entry.getPrimaryKeyBytes();
    }

    public long ranking() {
    	return this.ranking;
    }
    
    public Components metadata() {
        // avoid double computation of metadata elements
        if (this.comp != null) return this.comp;
        // parse elements from comp field;
        byte[] c = this.entry.getColBytes(col_comp, true);
        List<byte[]> cl = ByteBuffer.split(c, (byte) 10);
        this.comp = new Components(
                    (cl.size() > 0) ? UTF8.String(cl.get(0)) : "",
                    hash(),
                    (cl.size() > 1) ? UTF8.String(cl.get(1)) : "",
                    (cl.size() > 2) ? UTF8.String(cl.get(2)) : "",
                    (cl.size() > 3) ? UTF8.String(cl.get(3)) : "",
                    (cl.size() > 4) ? UTF8.String(cl.get(4)) : "",
                    (cl.size() > 5) ? UTF8.String(cl.get(5)) : "");
        return this.comp;
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

    public byte[] referrerHash() {
        // return the creator's hash or null if there is none
        // FIXME: There seem to be some malformed entries in the databasees like "null\0\0\0\0\0\0\0\0"
        final byte[] r = entry.getColBytes(col_referrer, true);
        if (r != null) {
            int i = r.length;
            while (i > 0) {
                if (r[--i] == 0) return null;
            }
        }
        return r;
    }

    public String md5() {
        // returns the md5 in hex representation
        return Digest.encodeHex(entry.getColBytes(col_md5, true));
    }

    public char doctype() {
        return (char) entry.getColByte(col_dt);
    }

    public byte[] language() {
        return this.entry.getColBytes(col_lang, true);
    }

    public int size() {
        return (int) this.entry.getColLong(col_size);
    }

    public Bitfield flags() {
        return new Bitfield(this.entry.getColBytes(col_flags, true));
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

    public WordReferenceVars word() {
        return word;
    }

    public boolean isOlder(final URIMetadata other) {
        if (other == null) return false;
        final Date tmoddate = moddate();
        final Date omoddate = other.moddate();
        if (tmoddate.before(omoddate)) return true;
        if (tmoddate.equals(omoddate)) {
            final Date tloaddate = loaddate();
            final Date oloaddate = other.loaddate();
            if (tloaddate.before(oloaddate)) return true;
            if (tloaddate.equals(oloaddate)) return true;
        }
        return false;
    }

    public String toString(final String snippet) {
        // add information needed for remote transport
        final StringBuilder core = corePropList();
        if (core == null)
            return null;

        core.ensureCapacity(core.length() + snippet.length() * 2);
        core.insert(0, "{");
        core.append(",snippet=").append(crypt.simpleEncode(snippet));
        core.append("}");

        return core.toString();
        //return "{" + core + ",snippet=" + crypt.simpleEncode(snippet) + "}";
    }

    public Request toBalancerEntry(final String initiatorHash) {
        return new Request(
                UTF8.getBytes(initiatorHash), 
                metadata().url(), 
                referrerHash(), 
                metadata().dc_title(),
                moddate(),
                null,
                0, 
                0, 
                0,
                0);
    }
    
    /**
     * @return the object as String.<br> 
     * This e.g. looks like this:
     * <pre>{hash=jmqfMk7Y3NKw,referrer=------------,mod=20050610,load=20051003,size=51666,wc=1392,cc=0,local=true,q=AEn,dt=h,lang=uk,url=b|aHR0cDovL3d3dy50cmFuc3BhcmVuY3kub3JnL3N1cnZleXMv,descr=b|S25vd2xlZGdlIENlbnRyZTogQ29ycnVwdGlvbiBTdXJ2ZXlzIGFuZCBJbmRpY2Vz}</pre>
     */
    @Override
    public String toString() {
        final StringBuilder core = corePropList();
        if (core == null) return null;

        core.insert(0, "{");
        core.append("}");

        return core.toString();
        //return "{" + core + "}";
    }
    
    public class Components {
        private DigestURI url;
        private String urlRaw;
        private byte[] urlHash;
        private final String dc_title, dc_creator, dc_subject, dc_publisher;
        private final String latlon; // a comma-separated tuple as "<latitude>,<longitude>" where the coordinates are given as WGS84 spatial coordinates in decimal degrees
        
        public Components(
                final String urlRaw,
                final byte[] urlhash,
                final String title,
                final String author,
                final String tags,
                final String publisher,
                final String latlon) {
            this.url = null;
            this.urlRaw = urlRaw;
            this.urlHash = urlhash;
            this.dc_title = title;
            this.dc_creator = author;
            this.dc_subject = tags;
            this.dc_publisher = publisher;
            this.latlon = latlon;
        }
        public boolean matches(Pattern matcher) {
            if (this.urlRaw != null) return matcher.matcher(this.urlRaw.toLowerCase()).matches();
            if (this.url != null) return matcher.matcher(this.url.toNormalform(true, true).toLowerCase()).matches();
            return false;
        }
        public DigestURI url() {
            if (this.url == null) {
                try {
                    this.url = new DigestURI(this.urlRaw, this.urlHash);
                } catch (MalformedURLException e) {
                    this.url = null;
                }
                this.urlRaw = null;
                this.urlHash = null;
            }
            return this.url;
        }
        public String  dc_title()  { return this.dc_title; }
        public String  dc_creator() { return this.dc_creator; }
        public String  dc_publisher() { return this.dc_publisher; }
        public String  dc_subject()   { return this.dc_subject; }
        public float lat() {
            if (latlon == null || latlon.length() == 0) return 0.0f;
            int p = latlon.indexOf(',');
            return p < 0 ? 0.0f : Float.parseFloat(latlon.substring(0, p));
        }
        public float lon() {
            if (latlon == null || latlon.length() == 0) return 0.0f;
            int p = latlon.indexOf(',');
            return p < 0 ? 0.0f : Float.parseFloat(latlon.substring(p + 1));
        }
    }
}
