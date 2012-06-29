/**
 *  URIMetadataNode
 *  Copyright 2012 by Michael Peter Christen
 *  First released 3.4.2012 at http://yacy.net
 *
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.data.meta;

import net.yacy.cora.lod.Node;
import net.yacy.cora.lod.vocabulary.Rdf;
import net.yacy.kelondro.data.word.WordReferenceVars;


public class URIMetadataNode /*extends URIReferenceNode implements URIMetadata*/ {

    private final Node entry;
    private final String snippet;
    private final WordReferenceVars word; // this is only used if the url is transported via remote search requests
    private final long ranking; // during generation of a search result this value is set
    
    public URIMetadataNode() {
        // create a dummy entry, good to produce poison objects
        this.entry = new Node(Rdf.Description);
        this.snippet = null;
        this.word = null;
        this.ranking = 0;
    }
/*
    public URIMetadataNode(
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
        this.entry = new Node();
        this.entry.setSubject(UTF8.getBytes(url.toNormalform(true, false)));
        this.entry.setObject(YaCyMetadata.hash, url.hash());
        this.entry.setObject(DublinCore.Title, UTF8.getBytes(dc_title));
        this.entry.setObject(DublinCore.Creator, UTF8.getBytes(dc_creator));
        this.entry.setObject(DublinCore.Subject, UTF8.getBytes(dc_subject));
        this.entry.setObject(DublinCore.Publisher, UTF8.getBytes(dc_publisher));
        this.entry.setObject(Geo.Lat, ASCII.getBytes(Float.toString(lat)));
        this.entry.setObject(Geo.Long, ASCII.getBytes(Float.toString(lon)));
        
        
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

    private byte[] encodeDate(final Date d) {
        // calculates the number of days since 1.1.1970 and returns this as 4-byte array
        // 86400000 is the number of milliseconds in one day
        return NaturalOrder.encodeLong(d.getTime() / 86400000L, 4);
    }

    private Date decodeDate(final int col) {
        final long t = this.entry.getColLong(col);
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
        s.append(url.toNormalform(false, true)).appendLF();
        s.append(dc_title).appendLF();
        if (dc_creator.length() > 80) s.append(dc_creator, 0, 80); else s.append(dc_creator);
        s.appendLF();
        if (dc_subject.length() > 120) s.append(dc_subject, 0, 120); else s.append(dc_subject);
        s.appendLF();
        if (dc_publisher.length() > 80) s.append(dc_publisher, 0, 80); else s.append(dc_publisher);
        s.appendLF();
        if (lon == 0.0f && lat == 0.0f) s.appendLF(); else s.append(Float.toString(lat)).append(',').append(Float.toString(lon)).appendLF();
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
            url = new DigestURI(crypt.simpleDecode(prop.getProperty("url", ""), null), ASCII.getBytes(prop.getProperty("hash")));
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
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

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
        final String dt = prop.getProperty("dt", "t");
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
        final Components metadata = metadata();
        final StringBuilder s = new StringBuilder(300);
        if (metadata == null) return null;
        //System.out.println("author=" + comp.author());

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            s.append("hash=").append(ASCII.String(hash()));
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
            s.append(",referrer=").append(referrerHash() == null ? "" : ASCII.String(referrerHash()));
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
            s.append(",lang=").append(language() == null ? "EN" : UTF8.String(language()));
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
                final String wprop = this.word.toPropertyForm();
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(wprop));
            }
            assert (s.toString().indexOf(0) < 0);
            return s;

        } catch (final Throwable e) {
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

    public boolean matches(final Pattern matcher) {
        return this.metadata().matches(matcher);
    }
    
    public DigestURI url() {
        return this.metadata().url();
    }
    
    public String  dc_title()  {
        return this.metadata().dc_title();
    }
    
    public String  dc_creator() {
        return this.metadata().dc_creator();
    }
    
    public String  dc_publisher() {
        return this.metadata().dc_publisher();
    }
    
    public String  dc_subject()   {
        return this.metadata().dc_subject();
    }

    public float lat() {
        return this.metadata().lat();
    }

    public float lon() {
        return this.metadata().lon();
    }
    
    private Components metadata() {
        // avoid double computation of metadata elements
        if (this.comp != null) return this.comp;
        // parse elements from comp field;
        final byte[] c = this.entry.getColBytes(col_comp, true);
        final List<byte[]> cl = ByteBuffer.split(c, (byte) 10);
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
        final byte[] r = this.entry.getColBytes(col_referrer, true);
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
        return Digest.encodeHex(this.entry.getColBytes(col_md5, true));
    }

    public char doctype() {
        return (char) this.entry.getColByte(col_dt);
    }

    public byte[] language() {
        byte[] b = this.entry.getColBytes(col_lang, true);
        if (b == null || b[0] == (byte)'[') {
            String tld = this.metadata().url.getTLD();
            if (tld.length() < 2 || tld.length() > 2) return ASCII.getBytes("en");
            return ASCII.getBytes(tld);
        }
        return b;
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
        return this.snippet;
    }

    public WordReferenceVars word() {
        return this.word;
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
                ASCII.getBytes(initiatorHash),
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

    @Override
    public String toString() {
        final StringBuilder core = corePropList();
        if (core == null) return null;

        core.insert(0, "{");
        core.append("}");

        return core.toString();
        //return "{" + core + "}";
    }

    private class Components {
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
        public boolean matches(final Pattern matcher) {
            if (this.urlRaw != null) return matcher.matcher(this.urlRaw.toLowerCase()).matches();
            if (this.url != null) return matcher.matcher(this.url.toNormalform(true, true).toLowerCase()).matches();
            return false;
        }
        public DigestURI url() {
            if (this.url == null) {
                try {
                    this.url = new DigestURI(this.urlRaw, this.urlHash);
                } catch (final MalformedURLException e) {
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
            if (this.latlon == null || this.latlon.length() == 0) return 0.0f;
            final int p = this.latlon.indexOf(',');
            return p < 0 ? 0.0f : Float.parseFloat(this.latlon.substring(0, p));
        }
        public float lon() {
            if (this.latlon == null || this.latlon.length() == 0) return 0.0f;
            final int p = this.latlon.indexOf(',');
            return p < 0 ? 0.0f : Float.parseFloat(this.latlon.substring(p + 1));
        }
    }
    */
}