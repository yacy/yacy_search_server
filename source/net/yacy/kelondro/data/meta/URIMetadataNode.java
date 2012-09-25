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
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.services.federated.solr.SolrType;
import net.yacy.cora.services.federated.solr.YaCySchema;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.utils.crypt;

import org.apache.solr.common.SolrDocument;


/**
 * This is the URIMetadata object implementation for Solr documents.
 * The purpose of this object is the migration from the old metadata structure to solr document.
 * Future implementations should try to replace URIMetadata objects completely by SolrDocument objects
 */
public class URIMetadataNode implements URIMetadata {

    private final byte[] hash;
    private final String urlRaw, keywords;
    private DigestURI url;
    Bitfield flags;
    private final int imagec, audioc, videoc, appc;
    private double lat, lon;
    private long ranking; // during generation of a search result this value is set
    private final SolrDocument doc;
    private final String snippet;
    private WordReference word; // this is only used if the url is transported via remote search requests

    public URIMetadataNode(final SolrDocument doc) {
        this.doc = doc;
        this.snippet = "";
        this.word = null;
        this.ranking = Long.MIN_VALUE;
        this.hash = ASCII.getBytes(getString(YaCySchema.id));
        this.urlRaw = getString(YaCySchema.sku);
        try {
            this.url = new DigestURI(this.urlRaw, this.hash);
        } catch (MalformedURLException e) {
            Log.logException(e);
            this.url = null;
        }

        // to set the flags bitfield we need to pre-load some values from the Solr document
        this.keywords = getString(YaCySchema.keywords);
        this.imagec = getInt(YaCySchema.imagescount_i);
        this.audioc = getInt(YaCySchema.audiolinkscount_i);
        this.videoc = getInt(YaCySchema.videolinkscount_i);
        this.appc = getInt(YaCySchema.videolinkscount_i);
        this.lon = 0.0d;
        this.lat = 0.0d;
        String latlon = (String) this.doc.getFieldValue(YaCySchema.coordinate_p.name());
        if (latlon != null) {
            int p = latlon.indexOf(',');
            if (p > 0) {
                this.lat = Double.parseDouble(latlon.substring(0, p));
                this.lon = Double.parseDouble(latlon.substring(p + 1));
            }
        }
        this.flags = new Bitfield();
        if (this.keywords != null && this.keywords.indexOf("indexof") >= 0) this.flags.set(Condenser.flag_cat_indexof, true);
        if (this.lon != 0.0d || this.lat != 0.0d) this.flags.set(Condenser.flag_cat_haslocation, true);
        if (this.imagec > 0) this.flags.set(Condenser.flag_cat_hasimage, true);
        if (this.audioc > 0) this.flags.set(Condenser.flag_cat_hasaudio, true);
        if (this.videoc > 0) this.flags.set(Condenser.flag_cat_hasvideo, true);
        if (this.appc > 0) this.flags.set(Condenser.flag_cat_hasapp, true);
    }

    public URIMetadataNode(final SolrDocument doc, final WordReference searchedWord, final long ranking) {
        this(doc);
        this.word = searchedWord;
        this.ranking = ranking;
    }

    public URIMetadataRow toRow() {
        return URIMetadataRow.importEntry(this.toString());
    }

    public SolrDocument getDocument() {
        return this.doc;
    }

    private int getInt(YaCySchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.integer;
        Integer x = (Integer) this.doc.getFieldValue(field.name());
        if (x == null) return 0;
        return x.intValue();
    }

    private Date getDate(YaCySchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.date;
        Date x = (Date) this.doc.getFieldValue(field.name());
        if (x == null) return new Date(0);
        return x;
    }

    private String getString(YaCySchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general || field.getType() == SolrType.text_en_splitting_tight;
        Object x = this.doc.getFieldValue(field.name());
        if (x == null) return "";
        if (x instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            ArrayList<String> xa = (ArrayList<String>) x;
            return xa.size() == 0 ? "" : xa.get(0);
        }
        return (String) x;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> getArrayList(YaCySchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = this.doc.getFieldValue(field.name());
        if (r == null) return new ArrayList<String>(0);
        if (r instanceof ArrayList) {
            return (ArrayList<String>) r;
        }
        ArrayList<String> a = new ArrayList<String>(1);
        a.add((String) r);
        return a;
    }

    @Override
    public byte[] hash() {
        return this.hash;
    }

    @Override
    public String hosthash() {
        String hosthash = (String) this.doc.getFieldValue(YaCySchema.host_id_s.name());
        if (hosthash == null) hosthash = ASCII.String(this.hash, 6, 6);
        return hosthash;
    }

    @Override
    public Date moddate() {
        return getDate(YaCySchema.last_modified);
    }

    @Override
    public DigestURI url() {
        return this.url;
    }

    @Override
    public boolean matches(Pattern matcher) {
        return matcher.matcher(this.urlRaw.toLowerCase()).matches();
    }

    @Override
    public String dc_title() {
        ArrayList<String> a = getArrayList(YaCySchema.title);
        if (a == null || a.size() == 0) return "";
        return a.get(0);
    }

    @Override
    public String dc_creator() {
        return getString(YaCySchema.author);
    }

    @Override
    public String dc_publisher() {
        return getString(YaCySchema.publisher_t);
    }

    @Override
    public String dc_subject() {
        return this.keywords;
    }

    @Override
    public double lat() {
        return this.lat;
    }

    @Override
    public double lon() {
        return this.lon;
    }

    @Override
    public long ranking() {
        return this.ranking;
    }

    @Override
    public Date loaddate() {
        return getDate(YaCySchema.load_date_dt);
    }

    @Override
    public Date freshdate() {
        return getDate(YaCySchema.fresh_date_dt);
    }

    @Override
    public String md5() {
        return getString(YaCySchema.md5_s);
    }

    @Override
    public char doctype() {
        ArrayList<String> a = getArrayList(YaCySchema.content_type);
        if (a == null || a.size() == 0) return Response.docType(this.url);
        return Response.docType(a.get(0));
    }

    @Override
    public byte[] language() {
        String language = getString(YaCySchema.language_s);
        if (language == null || language.length() == 0) return ASCII.getBytes("en");
        return UTF8.getBytes(language);
    }


    @Override
    public byte[] referrerHash() {
        ArrayList<String>  referrer = getArrayList(YaCySchema.referrer_id_txt);
        if (referrer == null || referrer.size() == 0) return null;
        return ASCII.getBytes(referrer.get(0));
    }

    @Override
    public int size() {
        return getInt(YaCySchema.size_i);
    }

    @Override
    public Bitfield flags() {
        return this.flags;
    }

    @Override
    public int wordCount() {
        return getInt(YaCySchema.wordcount_i);
    }

    @Override
    public int llocal() {
        return getInt(YaCySchema.inboundlinkscount_i);
    }

    @Override
    public int lother() {
        return getInt(YaCySchema.outboundlinkscount_i);
    }

    @Override
    public int limage() {
        return this.imagec;
    }

    @Override
    public int laudio() {
        return this.audioc;
    }

    @Override
    public int lvideo() {
        return this.videoc;
    }

    @Override
    public int lapp() {
        return this.appc;
    }

    @Override
    public String snippet() {
        return this.snippet;
    }

    @Override
    public String[] collections() {
        ArrayList<String> a = getArrayList(YaCySchema.collection_sxt);
        return a.toArray(new String[a.size()]);
    }

    @Override
    public WordReference word() {
        return this.word;
    }

    @Override
    public boolean isOlder(URIMetadata other) {
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

    public static StringBuilder corePropList(URIMetadata md) {
        // generate a parseable string; this is a simple property-list
        final StringBuilder s = new StringBuilder(300);

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            s.append("hash=").append(ASCII.String(md.hash()));
            s.append(",url=").append(crypt.simpleEncode(md.url().toNormalform(false, true)));
            s.append(",descr=").append(crypt.simpleEncode(md.dc_title()));
            s.append(",author=").append(crypt.simpleEncode(md.dc_creator()));
            s.append(",tags=").append(crypt.simpleEncode(Tagging.cleanTagFromAutotagging(md.dc_subject())));
            s.append(",publisher=").append(crypt.simpleEncode(md.dc_publisher()));
            s.append(",lat=").append(md.lat());
            s.append(",lon=").append(md.lon());
            s.append(",mod=").append(formatter.format(md.moddate()));
            s.append(",load=").append(formatter.format(md.loaddate()));
            s.append(",fresh=").append(formatter.format(md.freshdate()));
            s.append(",referrer=").append(md.referrerHash() == null ? "" : ASCII.String(md.referrerHash()));
            s.append(",md5=").append(md.md5());
            s.append(",size=").append(md.size());
            s.append(",wc=").append(md.wordCount());
            s.append(",dt=").append(md.doctype());
            s.append(",flags=").append(md.flags().exportB64());
            s.append(",lang=").append(md.language() == null ? "EN" : UTF8.String(md.language()));
            s.append(",llocal=").append(md.llocal());
            s.append(",lother=").append(md.lother());
            s.append(",limage=").append(md.limage());
            s.append(",laudio=").append(md.laudio());
            s.append(",lvideo=").append(md.lvideo());
            s.append(",lapp=").append(md.lapp());
            if (md.word() != null) {
                // append also word properties
                final String wprop = md.word().toPropertyForm();
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(wprop));
            }
            return s;
        } catch (final Throwable e) {
            Log.logException(e);
            return null;
        }
    }

    /**
     * the toString format must be completely identical to URIMetadataRow because that is used
     * to transport the data over p2p connections.
     */
    @Override
    public String toString(String snippet) {
        // add information needed for remote transport
        final StringBuilder core = corePropList(this);
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
        final StringBuilder core = corePropList(this);
        if (core == null) return null;
        core.insert(0, '{');
        core.append('}');
        return core.toString();
    }

    @Override
    public Request toBalancerEntry(final String initiatorHash) {
        return new Request(
                ASCII.getBytes(initiatorHash),
                url(),
                referrerHash(),
                dc_title(),
                moddate(),
                null,
                0,
                0,
                0,
                0);
    }
}