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
import java.util.List;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.search.index.YaCySchema;

import org.apache.solr.common.SolrDocument;

import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;
import de.anomic.tools.crypt;

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
    private final double lon, lat;
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
        this.lon = getDouble(YaCySchema.lon_coordinate);
        this.lat = getDouble(YaCySchema.lat_coordinate);
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

    private int getInt(YaCySchema field) {
        Integer x = (Integer) this.doc.getFieldValue(field.name());
        if (x == null) return 0;
        return x.intValue();
    }

    private double getDouble(YaCySchema field) {
        Double x = (Double) this.doc.getFieldValue(field.name());
        if (x == null) return 0.0d;
        return x.doubleValue();
    }

    private Date getDate(YaCySchema field) {
        Date x = (Date) this.doc.getFieldValue(field.name());
        if (x == null) return new Date(0);
        return x;
    }

    private String getString(YaCySchema field) {
        String x = (String) this.doc.getFieldValue(field.name());
        if (x == null) return "";
        return x;
    }

    private ArrayList<Object> getArrayList(YaCySchema field) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArrayList<Object> x = (ArrayList) this.doc.getFieldValue(field.name());
        if (x == null) return new ArrayList<Object>(0);
        return x;
    }

    @Override
    public byte[] hash() {
        return this.hash;
    }

    @Override
    public String hosthash() {
        return (String) this.doc.getFieldValue(YaCySchema.host_id_s.name());
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
        @SuppressWarnings("unchecked")
        List<String> titles = (List<String>) this.doc.getFieldValue(YaCySchema.title.name());
        if (titles == null || titles.size() == 0) return "";
        return titles.get(0);
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
        ArrayList<Object> a = getArrayList(YaCySchema.content_type);
        if (a == null || a.size() == 0) return Response.docType(this.url);
        return Response.docType((String) a.get(0));
    }

    @Override
    public byte[] language() {
        ArrayList<Object> languages = getArrayList(YaCySchema.language_txt);
        if (languages == null || languages.size() == 0) return ASCII.getBytes("en");
        return UTF8.getBytes((String) languages.get(0));
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

    private StringBuilder corePropList() {
        // generate a parseable string; this is a simple property-list
        final StringBuilder s = new StringBuilder(300);
        //System.out.println("author=" + comp.author());

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            s.append("hash=").append(ASCII.String(hash()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",url=").append(crypt.simpleEncode(url().toNormalform(false, true)));
            assert (s.toString().indexOf(0) < 0);
            s.append(",descr=").append(crypt.simpleEncode(dc_title()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",author=").append(crypt.simpleEncode(dc_creator()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",tags=").append(crypt.simpleEncode(Tagging.cleanTagFromAutotagging(dc_subject())));
            assert (s.toString().indexOf(0) < 0);
            s.append(",publisher=").append(crypt.simpleEncode(dc_publisher()));
            assert (s.toString().indexOf(0) < 0);
            s.append(",lat=").append(lat());
            assert (s.toString().indexOf(0) < 0);
            s.append(",lon=").append(lon());
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

    @Override
    public byte[] referrerHash() {
        String[] referrer = (String[]) this.doc.getFieldValue(YaCySchema.referrer_id_txt.name());
        if (referrer == null || referrer.length == 0) return null;
        return ASCII.getBytes(referrer[0]);
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