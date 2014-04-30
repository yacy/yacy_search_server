/**
 *  URIMetadataNode
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.8.2012 at http://yacy.net
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

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MapTools;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.search.query.QueryParams;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.utils.crypt;

import org.apache.solr.common.SolrDocument;


/**
 * This is the URIMetadata object implementation for Solr documents.
 * The purpose of this object is the migration from the old metadata structure to solr document.
 * Future implementations should try to replace URIMetadata objects completely by SolrDocument objects
 */
public class URIMetadataNode extends SolrDocument {
    
    private static final long serialVersionUID = -256046934741561968L;
    
    protected byte[] hash = null;
    protected String urlRaw = null, keywords = null;
    protected DigestURL url = null;
    protected Bitfield flags = null;
    protected int imagec = -1, audioc = -1, videoc = -1, appc = -1;
    protected double lat = Double.NaN, lon = Double.NaN;
    protected long ranking = 0; // during generation of a search result this value is set
    protected String snippet = null;
    protected WordReferenceVars word = null; // this is only used if the url is transported via remote search requests

    public URIMetadataNode(final Properties prop) {
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        super();
        urlRaw = crypt.simpleDecode(prop.getProperty("url", ""));
        try {
            url = new DigestURL(urlRaw);
            this.hash = url.hash();
        } catch (final MalformedURLException e) {
            ConcurrentLog.logException(e);
            this.url = null;
            this.hash = null;
        }
        String descr = crypt.simpleDecode(prop.getProperty("descr", "")); if (descr == null) descr = "";
        String dc_creator = crypt.simpleDecode(prop.getProperty("author", "")); if (dc_creator == null) dc_creator = "";
        String tags = crypt.simpleDecode(prop.getProperty("tags", "")); if (tags == null) tags = "";
        this.keywords = Tagging.cleanTagFromAutotagging(tags);
        String dc_publisher = crypt.simpleDecode(prop.getProperty("publisher", "")); if (dc_publisher == null) dc_publisher = "";
        String lons = crypt.simpleDecode(prop.getProperty("lon"));
        String lats = crypt.simpleDecode(prop.getProperty("lat"));
        
        this.setField(CollectionSchema.title.name(), descr);
        this.setField(CollectionSchema.author.name(), dc_creator);
        this.setField(CollectionSchema.publisher_t.name(), dc_publisher);
        this.lon = (lons == null) ? 0.0d : Double.parseDouble(lons);
        this.lat = (lats == null) ? 0.0d : Double.parseDouble(lats);

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            this.setField(CollectionSchema.last_modified.name(), formatter.parse(prop.getProperty("mod", "20000101")));
        } catch (final ParseException e) {
            this.setField(CollectionSchema.last_modified.name(), new Date());
        }
        try {
            this.setField(CollectionSchema.load_date_dt.name(), formatter.parse(prop.getProperty("load", "20000101")));
        } catch (final ParseException e) {
            this.setField(CollectionSchema.load_date_dt.name(), new Date());
        }
        try {
            this.setField(CollectionSchema.fresh_date_dt.name(), formatter.parse(prop.getProperty("fresh", "20000101")));
        } catch (final ParseException e) {
            this.setField(CollectionSchema.fresh_date_dt.name(), new Date());
        }
        this.setField(CollectionSchema.referrer_id_s.name(), prop.getProperty("referrer", ""));
        this.setField(CollectionSchema.md5_s.name(), prop.getProperty("md5", ""));
        this.setField(CollectionSchema.size_i.name(), Integer.parseInt(prop.getProperty("size", "0")));
        this.setField(CollectionSchema.wordcount_i.name(), Integer.parseInt(prop.getProperty("wc", "0")));
        final String dt = prop.getProperty("dt", "t");
        String[] mime = Response.doctype2mime(null,dt.charAt(0));
        this.setField(CollectionSchema.content_type.name(), mime);
        final String flagsp = prop.getProperty("flags", "AAAAAA");
        this.flags = (flagsp.length() > 6) ? QueryParams.empty_constraint : (new Bitfield(4, flagsp));
        this.setField(CollectionSchema.language_s.name(), prop.getProperty("lang", ""));
        this.setField(CollectionSchema.inboundlinkscount_i.name(), Integer.parseInt(prop.getProperty("llocal", "0")));
        this.setField(CollectionSchema.outboundlinkscount_i.name(), Integer.parseInt(prop.getProperty("lother", "0")));
        this.imagec = Integer.parseInt(prop.getProperty("limage", "0"));
        this.audioc = Integer.parseInt(prop.getProperty("laudio", "0"));
        this.videoc = Integer.parseInt(prop.getProperty("lvideo", "0"));
        this.appc = Integer.parseInt(prop.getProperty("lapp", "0"));
        this.snippet = crypt.simpleDecode(prop.getProperty("snippet", ""));
        this.word = null;
        if (prop.containsKey("wi")) {
            this.word = new WordReferenceVars(new WordReferenceRow(Base64Order.enhancedCoder.decodeString(prop.getProperty("wi", ""))), false);
        }
    }

    public URIMetadataNode(final SolrDocument doc) {
        super();
        for (String name : doc.getFieldNames()) {
            this.addField(name, doc.getFieldValue(name));
        }
        this.snippet = "";
        Float score = (Float) doc.getFieldValue("score"); // this is a special field containing the ranking score of a search result
        this.ranking = score == null ? 0 : (long) (1000000.0f * score.floatValue()); // solr score values are sometimes very low
        this.hash = ASCII.getBytes(getString(CollectionSchema.id));
        this.urlRaw = getString(CollectionSchema.sku);
        try {
            this.url = new DigestURL(this.urlRaw, this.hash);
        } catch (final MalformedURLException e) {
            ConcurrentLog.logException(e);
            this.url = null;
        }
    }

    public URIMetadataNode(final SolrDocument doc, final WordReferenceVars searchedWord, final long ranking) {
        this(doc);
        this.word = searchedWord;
        this.ranking = ranking;
    }

    /**
     * Get the content domain of a document. This tries to get the content domain from the mime type
     * and if this fails it uses alternatively the content domain from the file extension.
     * @return the content domain which classifies the content type
     */
    public ContentDomain getContentDomain() {
        String mime = mime();
        if (mime == null) return this.url.getContentDomainFromExt();
        ContentDomain contentDomain = Classification.getContentDomainFromMime(mime);
        if (contentDomain != ContentDomain.ALL) return contentDomain;
        return this.url.getContentDomainFromExt();
    }

    public byte[] hash() {
        return this.hash;
    }

    public String hosthash() {
        String hosthash = (String) this.getFieldValue(CollectionSchema.host_id_s.getSolrFieldName());
        if (hosthash == null) hosthash = ASCII.String(this.hash, 6, 6);
        return hosthash;
    }

    public Date moddate() {
        return getDate(CollectionSchema.last_modified);
    }

    public DigestURL url() {
        return this.url;
    }

    public boolean matches(Pattern matcher) {
        return matcher.matcher(this.urlRaw.toLowerCase()).matches();
    }

    public String dc_title() {
        ArrayList<String> a = getStringList(CollectionSchema.title);
        if (a == null || a.size() == 0) return "";
        return a.get(0);
    }

    public String dc_creator() {
        return getString(CollectionSchema.author);
    }

    public String dc_publisher() {
        return getString(CollectionSchema.publisher_t);
    }

    public String dc_subject() {
        if (this.keywords == null) {
            this.keywords = getString(CollectionSchema.keywords);
        }
        return this.keywords;
    }

    public double lat() {
        if (Double.isNaN(this.lat)) {
            this.lon = 0.0d;
            this.lat = 0.0d;
            String latlon = (String) this.getFieldValue(CollectionSchema.coordinate_p.getSolrFieldName());
            if (latlon != null) {
                int p = latlon.indexOf(',');
                if (p > 0) {
                    // only needed if not already checked by solr coordinate
                    if (latlon.charAt(0) <= '9') { // prevent alpha's
                        this.lat = Double.parseDouble(latlon.substring(0, p));
                        if (this.lat < -90.0d || this.lat > 90.0d) this.lat = 0.0d;
                    }

                    if ( (p < latlon.length()-1) && (latlon.charAt(p+1) <= '9') ) { 
                        this.lon=Double.parseDouble(latlon.substring(p + 1));
                        if (this.lon < -180.0d || this.lon > 180.0d) this.lon = 0.0d;
                    }
                }
            }
        }
        return this.lat;
    }

    public double lon() {
        if (Double.isNaN(this.lon)) lat();
        return this.lon;
    }

    public long ranking() {
        return this.ranking;
    }

    public Date loaddate() {
        return getDate(CollectionSchema.load_date_dt);
    }

    public Date freshdate() {
        return getDate(CollectionSchema.fresh_date_dt);
    }

    public String md5() {
        return getString(CollectionSchema.md5_s);
    }

    public char doctype() {
        ArrayList<String> a = getStringList(CollectionSchema.content_type);
        if (a == null || a.size() == 0) return Response.docType(url());
        return Response.docType(a.get(0));
    }

    public String mime() {
        ArrayList<String> mime = getStringList(CollectionSchema.content_type);
        return mime == null || mime.size() == 0 ? null : mime.get(0);
    }

    public String language() {
        String language = getString(CollectionSchema.language_s);
        if (language == null || language.length() == 0) return "en";
        return language;
    }

    public byte[] referrerHash() {
        String  referrer = getString(CollectionSchema.referrer_id_s);
        if (referrer == null || referrer.length() == 0) return null;
        return ASCII.getBytes(referrer);
    }

    @Override
    public int size() {
        return getInt(CollectionSchema.size_i);
    }

    public Bitfield flags() {
        if (flags == null) {
            this.flags = new Bitfield();
            if (dc_subject() != null && dc_subject().indexOf("indexof") >= 0) this.flags.set(Condenser.flag_cat_indexof, true);
            ContentDomain cd = getContentDomain();
            if (lon() != 0.0d || lat() != 0.0d) this.flags.set(Condenser.flag_cat_haslocation, true);
            if (cd == ContentDomain.IMAGE || limage() > 0) this.flags.set(Condenser.flag_cat_hasimage, true);
            if (cd == ContentDomain.AUDIO || laudio() > 0) this.flags.set(Condenser.flag_cat_hasaudio, true);
            if (cd == ContentDomain.VIDEO || lvideo() > 0) this.flags.set(Condenser.flag_cat_hasvideo, true);
            if (cd == ContentDomain.APP) this.flags.set(Condenser.flag_cat_hasapp, true);
            if (lapp() > 0) this.flags.set(Condenser.flag_cat_hasapp, true);
        }
        return this.flags;
    }

    public int wordCount() {
        return getInt(CollectionSchema.wordcount_i);
    }

    public int llocal() {
        return getInt(CollectionSchema.inboundlinkscount_i);
    }

    public int lother() {
        return getInt(CollectionSchema.outboundlinkscount_i);
    }

    public int limage() {
        if (this.imagec == -1) {
            this.imagec = getInt(CollectionSchema.imagescount_i);
        }
        return this.imagec;
    }

    public int laudio() {
        if (this.audioc == -1) {
            this.audioc = getInt(CollectionSchema.audiolinkscount_i);
        }
        return this.audioc;
    }

    public int lvideo() {
        if (this.videoc == -1) {
            this.videoc = getInt(CollectionSchema.videolinkscount_i);
        }
        return this.videoc;
    }

    public int lapp() {
        if (this.appc == -1) {
            this.appc = getInt(CollectionSchema.applinkscount_i);
        }
        return this.appc;
    }

    public int virtualAge() {
        return MicroDate.microDateDays(moddate());
    }

    public int wordsintitle() {
        ArrayList<Integer>  x = getIntList(CollectionSchema.title_words_val);
        if (x == null || x.size() == 0) return 0;
        return x.get(0).intValue();
    }

    public int urllength() {
        return getInt(CollectionSchema.url_chars_i);
    }

    public String snippet() {
        return this.snippet;
    }

    public String[] collections() {
        ArrayList<String> a = getStringList(CollectionSchema.collection_sxt);
        return a.toArray(new String[a.size()]);
    }

    public WordReferenceVars word() {
        return this.word;
    }
    
    private static List<String> indexedList2protocolList(Collection<Object> iplist, int dimension) {
        List<String> a = new ArrayList<String>(dimension);
        for (int i = 0; i < dimension; i++) a.add("http");
        if (iplist == null) return a;
        for (Object ip: iplist) a.set(Integer.parseInt(((String) ip).substring(0, 3)), ((String) ip).substring(4));
        return a;
    }

    public static Iterator<String> getLinks(SolrDocument doc, boolean inbound) {
        Collection<Object> urlstub = doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_urlstub_sxt :  CollectionSchema.outboundlinks_urlstub_sxt).getSolrFieldName());
        Collection<String> urlprot = urlstub == null ? null : indexedList2protocolList(doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_protocol_sxt : CollectionSchema.outboundlinks_protocol_sxt).getSolrFieldName()), urlstub.size());
        String u;
        LinkedHashSet<String> list = new LinkedHashSet<String>();
        if (urlprot != null && urlstub != null) {
            assert urlprot.size() == urlstub.size();
            Object[] urlprota = urlprot.toArray();
            Object[] urlstuba = urlstub.toArray();
            for (int i = 0; i < urlprota.length; i++) {
                u = ((String) urlprota[i]) + "://" + ((String) urlstuba[i]);
                int hp = u.indexOf('#');
                if (hp > 0) u = u.substring(0, hp);
                list.add(u);
            }
        }
        return list.iterator();
    }

    public static Date getDate(SolrDocument doc, final CollectionSchema key) {
        Date x = doc == null ? null : (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

    public String getText() {
        return getString(CollectionSchema.text_t);
    }

    public ArrayList<String> getDescription() {
        return getStringList(CollectionSchema.description_txt);
    }    

    public static URIMetadataNode importEntry(final String propStr) {
        if (propStr == null || propStr.isEmpty() || propStr.charAt(0) != '{' || !propStr.endsWith("}")) {
            ConcurrentLog.severe("URIMetadataNode", "importEntry: propStr is not proper: " + propStr);
            return null;
        }
        try {
            return new URIMetadataNode(MapTools.s2p(propStr.substring(1, propStr.length() - 1)));
        } catch (final kelondroException e) {
            // wrong format
            ConcurrentLog.severe("URIMetadataNode", e.getMessage());
            return null;
        }
    }

    protected StringBuilder corePropList() {
        // generate a parseable string; this is a simple property-list
        final StringBuilder s = new StringBuilder(300);

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            s.append("hash=").append(ASCII.String(this.hash()));
            s.append(",url=").append(crypt.simpleEncode(this.url().toNormalform(true)));
            s.append(",descr=").append(crypt.simpleEncode(this.dc_title()));
            s.append(",author=").append(crypt.simpleEncode(this.dc_creator()));
            s.append(",tags=").append(crypt.simpleEncode(Tagging.cleanTagFromAutotagging(this.dc_subject())));
            s.append(",publisher=").append(crypt.simpleEncode(this.dc_publisher()));
            s.append(",lat=").append(this.lat());
            s.append(",lon=").append(this.lon());
            s.append(",mod=").append(formatter.format(this.moddate()));
            s.append(",load=").append(formatter.format(this.loaddate()));
            s.append(",fresh=").append(formatter.format(this.freshdate()));
            s.append(",referrer=").append(this.referrerHash() == null ? "" : ASCII.String(this.referrerHash()));
            s.append(",md5=").append(this.md5());
            s.append(",size=").append(this.size());
            s.append(",wc=").append(this.wordCount());
            s.append(",dt=").append(this.doctype());
            s.append(",flags=").append(this.flags().exportB64());
            s.append(",lang=").append(this.language());
            s.append(",llocal=").append(this.llocal());
            s.append(",lother=").append(this.lother());
            s.append(",limage=").append(this.limage());
            s.append(",laudio=").append(this.laudio());
            s.append(",lvideo=").append(this.lvideo());
            s.append(",lapp=").append(this.lapp());
            if (this.word() != null) {
                // append also word properties
                final String wprop = this.word().toPropertyForm();
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(wprop));
            }
            return s;
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    /**
     * the toString format must be completely identical to URIMetadataRow because that is used
     * to transport the data over p2p connections.
     */
    public String toString(String snippet) {
        // add information needed for remote transport
        final StringBuilder core = corePropList();
        if (core == null)
            return null;

        core.ensureCapacity(core.length() + snippet.length() * 2);
        core.insert(0, '{');
        core.append(",snippet=").append(crypt.simpleEncode(snippet));
        core.append('}');

        return core.toString();
        //return "{" + core + ",snippet=" + crypt.simpleEncode(snippet) + "}";
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
        core.insert(0, '{');
        core.append('}');
        return core.toString();
    }
    
    private int getInt(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.num_integer;
        Object x = this.getFieldValue(field.getSolrFieldName());
        if (x == null) return 0;
        if (x instanceof Integer) return ((Integer) x).intValue();
        if (x instanceof Long) return ((Long) x).intValue();
        return 0;
    }

    private Date getDate(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.date;
        Date x = (Date) this.getFieldValue(field.getSolrFieldName());
        if (x == null) return new Date(0);
        Date now = new Date();
        return x.after(now) ? now : x;
    }

    private String getString(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general || field.getType() == SolrType.text_en_splitting_tight;
        Object x = this.getFieldValue(field.getSolrFieldName());
        if (x == null) return "";
        if (x instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            ArrayList<String> xa = (ArrayList<String>) x;
            return xa.size() == 0 ? "" : xa.get(0);
        }
        return (String) x;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> getStringList(CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = this.getFieldValue(field.getSolrFieldName());
        if (r == null) return new ArrayList<String>(0);
        if (r instanceof ArrayList) {
            return (ArrayList<String>) r;
        }
        ArrayList<String> a = new ArrayList<String>(1);
        a.add((String) r);
        return a;
    }
    
    @SuppressWarnings("unchecked")
    private ArrayList<Integer> getIntList(CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.num_integer;
        Object r = this.getFieldValue(field.getSolrFieldName());
        if (r == null) return new ArrayList<Integer>(0);
        if (r instanceof ArrayList) {
            return (ArrayList<Integer>) r;
        }
        ArrayList<Integer> a = new ArrayList<Integer>(1);
        a.add((Integer) r);
        return a;
    }

}