package de.anomic.index;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.Properties;
import java.util.ArrayList;

import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverCodings;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;

public class indexURLEntryNew implements indexURLEntry {

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
        "Cardinal lapp-2 {b256}");      // # of embedded links to applications
    
    private static final int col_hash     =  0;
    private static final int col_comp     =  1;
    private static final int col_mod      =  2;
    private static final int col_load     =  3;
    private static final int col_fresh    =  4;
    private static final int col_referrer =  5;
    private static final int col_md5      =  6;
    private static final int col_size     =  7;
    private static final int col_wc       =  8;
    private static final int col_dt       =  9;
    private static final int col_flags    = 10;
    private static final int col_lang     = 11;
    private static final int col_llocal   = 12;
    private static final int col_lother   = 13;
    private static final int col_limage   = 14;
    private static final int col_laudio   = 15;
    private static final int col_lvideo   = 16;
    private static final int col_lapp     = 17;
    
    private kelondroRow.Entry entry;
    private String snippet;
    private indexRWIEntryNew word; // this is only used if the url is transported via remote search requests

    public indexURLEntryNew(
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
        this.entry.setCol(col_hash, plasmaURL.urlHash(url), null);
        this.entry.setCol(col_comp, encodeComp(url, descr, author, tags, ETag));
        this.entry.setCol(col_mod, encodeDate(mod));
        this.entry.setCol(col_load, encodeDate(load));
        this.entry.setCol(col_fresh, encodeDate(fresh));
        this.entry.setCol(col_referrer, referrer.getBytes());
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
        this.snippet = null;
        this.word = null;
    }

    public static byte[] encodeDate(Date d) {
        // calculates the number of days since 1.1.1970 and returns this as 4-byte array
        return kelondroNaturalOrder.encodeLong(d.getTime() / 86400000, 4);
    }
    
    public static byte[] encodeComp(URL url, String descr, String author, String tags, String ETag) {
        serverCharBuffer s = new serverCharBuffer(200);
        s.append(url.toNormalform()).append(10);
        s.append(descr).append(10);
        s.append(author).append(10);
        s.append(tags).append(10);
        s.append(ETag).append(10);
        return s.toString().getBytes();
    }
    
    public indexURLEntryNew(kelondroRow.Entry entry, indexRWIEntryNew searchedWord) {
        this.entry = entry;
        this.snippet = null;
        this.word = searchedWord;
    }

    public indexURLEntryNew(Properties prop){
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        URL url;
        try {
            url = new URL(crypt.simpleDecode(prop.getProperty("url", ""), null));
        } catch (MalformedURLException e) {
            url = null;
        }
        String descr = crypt.simpleDecode(prop.getProperty("descr", ""), null); if (descr == null) descr = "";
        String author = crypt.simpleDecode(prop.getProperty("author", ""), null); if (author == null) author = "";
        String tags = crypt.simpleDecode(prop.getProperty("tags", ""), null); if (tags == null) tags = "";
        String ETag = crypt.simpleDecode(prop.getProperty("ETag", ""), null); if (ETag == null) ETag = "";
        
        this.entry = rowdef.newEntry();
        this.entry.setCol(col_hash, plasmaURL.urlHash(url), null);
        this.entry.setCol(col_comp, encodeComp(url, descr, author, tags, ETag));
        try {
            this.entry.setCol(col_mod, encodeDate(plasmaURL.shortDayFormatter.parse(prop.getProperty("mod", "20000101"))));
        } catch (ParseException e) {
            this.entry.setCol(col_mod, encodeDate(new Date()));
        }
        try {
            this.entry.setCol(col_load, encodeDate(plasmaURL.shortDayFormatter.parse(prop.getProperty("load", "20000101"))));
        } catch (ParseException e) {
            this.entry.setCol(col_load, encodeDate(new Date()));
        }
        try {
            this.entry.setCol(col_fresh, encodeDate(plasmaURL.shortDayFormatter.parse(prop.getProperty("fresh", "20000101"))));
        } catch (ParseException e) {
            this.entry.setCol(col_fresh, encodeDate(new Date()));
        }
        this.entry.setCol(col_referrer, prop.getProperty("referrer", plasmaURL.dummyHash).getBytes());
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
        if (prop.containsKey("word")) try {
            // convert old data format
            this.word = new indexRWIEntryNew(new indexRWIEntryOld(kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("word", ""))));
        } catch (kelondroException e) {
            this.word = null;
        }
        if (prop.containsKey("wi")) {
            this.word = new indexRWIEntryNew(kelondroBase64Order.enhancedCoder.decodeString(prop.getProperty("wi", "")));
        }
    }

    private StringBuffer corePropList() {
        // generate a parseable string; this is a simple property-list
        indexURLEntry.Components comp = this.comp();
        final StringBuffer s = new StringBuffer(300);
        //System.out.println("author=" + comp.author());
        try {
            s.append("hash=").append(hash());
            s.append(",url=").append(crypt.simpleEncode(comp.url().toNormalform()));
            s.append(",descr=").append(crypt.simpleEncode(comp.descr()));
            s.append(",author=").append(crypt.simpleEncode(comp.author()));
            s.append(",tags=").append(crypt.simpleEncode(comp.tags()));
            s.append(",ETag=").append(crypt.simpleEncode(comp.ETag()));
            s.append(",mod=").append(plasmaURL.shortDayFormatter.format(moddate()));
            s.append(",load=").append(plasmaURL.shortDayFormatter.format(loaddate()));
            s.append(",fresh=").append(plasmaURL.shortDayFormatter.format(freshdate()));
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
            //          e.printStackTrace();
            return null;
        }
    }

    public kelondroRow.Entry toRowEntry() throws IOException {
        return this.entry;
    }

    public String hash() {
        // return a url-hash, based on the md5 algorithm
        // the result is a String of 12 bytes within a 72-bit space
        // (each byte has an 6-bit range)
        // that should be enough for all web pages on the world
        return this.entry.getColString(col_hash, null);
    }

    public indexURLEntry.Components comp() {
        ArrayList cl = nxTools.strings(this.entry.getCol("comp", null), "UTF-8");
        return new indexURLEntry.Components(
                (cl.size() > 0) ? ((String) cl.get(0)).trim() : "",
                (cl.size() > 1) ? ((String) cl.get(1)).trim() : "",
                (cl.size() > 2) ? ((String) cl.get(2)).trim() : "",
                (cl.size() > 3) ? ((String) cl.get(3)).trim() : "",
                (cl.size() > 4) ? ((String) cl.get(4)).trim() : "");
    }
    
    public Date moddate() {
        return new Date(86400000 * entry.getColLong(col_mod));
    }

    public Date loaddate() {
        return new Date(86400000 * entry.getColLong(col_load));
    }

    public Date freshdate() {
        return new Date(86400000 * entry.getColLong(col_fresh));
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

    public boolean isOlder(indexURLEntry other) {
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

}
