/**
 *  URIMetadataNode
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.8.2012 at https://yacy.net
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

import java.awt.Dimension;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.SentenceReader;
import net.yacy.document.Tokenizer;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.document.parser.html.IconLinkRelations;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.MapTools;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryParams;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.utils.crypt;


/**
 * This is the URIMetadata object implementation for Solr documents.
 * The purpose of this object is the migration from the old metadata structure to solr document.
 * Future implementations should try to replace URIMetadata objects completely by SolrDocument objects
 */
public class URIMetadataNode extends SolrDocument /* implements Comparable<URIMetadataNode>, Comparator<URIMetadataNode> */ {

    private static final long serialVersionUID = -256046934741561968L;

    protected String keywords = null;
    protected DigestURL url;
    protected Bitfield flags = null;
    protected int imagec = -1, audioc = -1, videoc = -1, appc = -1;
    protected double lat = Double.NaN, lon = Double.NaN;
    protected long score = 0; // during generation of a search result this value is set
    protected String snippet = null;
    protected WordReferenceVars word = null; // this is only used if the url is transported via remote search requests

    // fields for search results (implemented from ResultEntry)
    private String alternative_urlstring;
    private String alternative_urlname;
    private TextSnippet textSnippet = null;

    /**
     * Creates an instance from encoded properties.
     * @param prop encoded properties
     * @param collection collection origin (e.g. "dht")
     * @throws MalformedURLException
     */
    public URIMetadataNode(final Properties prop, String collection) throws MalformedURLException {
        // generates an plasmaLURLEntry using the properties from the argument
        // the property names must correspond to the one from toString
        //System.out.println("DEBUG-ENTRY: prop=" + prop.toString());
        super();
        final String urlRaw = crypt.simpleDecode(prop.getProperty("url", ""));
        this.url = new DigestURL(urlRaw);
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

        this.setField(CollectionSchema.last_modified.name(), parseShortDayDate(prop.getProperty("mod", "20000101")));
        this.setField(CollectionSchema.load_date_dt.name(), parseShortDayDate(prop.getProperty("load", "20000101")));
		this.setField(CollectionSchema.fresh_date_dt.name(), parseShortDayDate(prop.getProperty("fresh", "20000101")));
        this.setField(CollectionSchema.referrer_id_s.name(), prop.getProperty("referrer", ""));
        // this.setField(CollectionSchema.md5_s.name(), prop.getProperty("md5", "")); // always 0 (not used / calculated)
        this.setField(CollectionSchema.size_i.name(), Integer.parseInt(prop.getProperty("size", "0")));
        this.setField(CollectionSchema.wordcount_i.name(), Integer.parseInt(prop.getProperty("wc", "0")));
        final String dt = prop.getProperty("dt", "t");
        final String mime = crypt.simpleDecode(prop.getProperty("mime")); // optional included if it is not equal to doctype2mime()
        if (mime != null && !mime.isEmpty() && Response.docType(mime) == dt.charAt(0)) { // use supplied mime (if docType(mime) is equal it's a known valid mime)
            this.setField(CollectionSchema.content_type.name(), mime);
        } else {
            final String[] mimes = Response.doctype2mime(null, dt.charAt(0));
            this.setField(CollectionSchema.content_type.name(), mimes);
        }
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
        // this.score = Float.parseFloat(prop.getProperty("score", "0.0")); // we don't use the remote rwi ranking but the local rwi ranking profile
        List<String> cs = new ArrayList<String>();
        cs.add(collection);
        this.setField(CollectionSchema.collection_sxt.name(), cs);
        this.word = null;
        if (prop.containsKey("wi")) {
            this.word = new WordReferenceVars(new WordReferenceRow(Base64Order.enhancedCoder.decodeString(prop.getProperty("wi", ""))), false);
        }
        if (prop.containsKey("favicon")) {
        	final String rawFaviconURL = crypt.simpleDecode(prop.getProperty("favicon", ""));
        	DigestURL faviconURL = new DigestURL(rawFaviconURL);
        	this.setIconsFields(faviconURL);
        }
    }


    public URIMetadataNode(final SolrDocument doc) throws MalformedURLException {
        super();
        for (String name : doc.getFieldNames()) {
            this.setField(name, doc.getFieldValue(name));
        }
        /* score shall contain the YaCy score, getFieldValue("score") moved to
        *  SearchEvent.addNodes() where the YaCy ranking for nodes is calculated
        Float scorex = (Float) doc.getFieldValue("score"); // this is a special Solr field containing the ranking score of a search result
        this.score = scorex == null ? 0.0f : scorex.floatValue();
        */
        final String hashstr = getString(CollectionSchema.id); // id or empty string
        final String urlRaw = getString(CollectionSchema.sku);
        this.url = new DigestURL(urlRaw);
        if (!hashstr.isEmpty()) { // remote id might not correspond in all cases
            final String myhash = ASCII.String(this.url.hash());
            if (!hashstr.equals(myhash)) {
                this.setField(CollectionSchema.id.getSolrFieldName(), myhash);
                ConcurrentLog.fine("URIMetadataNode", "updated document.ID of " + urlRaw + " from " + hashstr + " to " + myhash);
                // ususally the hosthash matches but just to be on the safe site
                final String hostidstr = getString(CollectionSchema.host_id_s); // id or empty string
                if (!hostidstr.isEmpty() && !hostidstr.equals(this.url.hosthash())) {
                    this.setField(CollectionSchema.host_id_s.getSolrFieldName(), this.url.hosthash());
                }
            }
        }
    }

    /**
     * @param doc metadata from (embedded) Solr index
     * @param searchedWord rwi WordReference the metadata belong to
     * @param scorex rwi score
     * @throws MalformedURLException
     */
    public URIMetadataNode(final SolrDocument doc, final WordReferenceVars searchedWord, final long scorex) throws MalformedURLException {
        this(doc);
        this.word = searchedWord; // rwi index WordReference this document (metadata) belong to
        this.score = scorex; // rwi/YaCy score
    }

    public URIMetadataNode(DigestURL theurl) {
        super();
        this.url = theurl;
        this.setField(CollectionSchema.sku.name(), this.url.toNormalform(true));
        this.setField(CollectionSchema.id.name(), ASCII.String(this.url.hash()));
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
        return this.url.hash();
    }

    public String hosthash() {
        String hosthash = (String) this.getFieldValue(CollectionSchema.host_id_s.getSolrFieldName());
        if (hosthash == null) hosthash = this.url.hosthash();
        return hosthash;
    }

    public Date moddate() {
        return getDate(CollectionSchema.last_modified);
    }

    public Date[] datesInContent() {
        return getDates(CollectionSchema.dates_in_content_dts);
    }

    public DigestURL url() {
        return this.url;
    }

    public boolean matches(Pattern pattern) {
        return pattern.matcher(this.url.toNormalform(true)).matches();
        //CharacterRunAutomaton automaton = new CharacterRunAutomaton(matcher);
        //boolean match = automaton.run(this.url.toNormalform(true).toLowerCase(Locale.ROOT));
        //return match;
    }

    public String dc_title() {
        ArrayList<String> a = getStringList(CollectionSchema.title);
        if (a == null || a.size() == 0) return "";
        return a.get(0);
    }

    public List<String> h1() {
        ArrayList<String> a = getStringList(CollectionSchema.h1_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
    }

    public List<String> h2() {
        ArrayList<String> a = getStringList(CollectionSchema.h2_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
    }

    public List<String> h3() {
        ArrayList<String> a = getStringList(CollectionSchema.h3_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
    }

    public List<String> h4() {
        ArrayList<String> a = getStringList(CollectionSchema.h4_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
    }

    public List<String> h5() {
        ArrayList<String> a = getStringList(CollectionSchema.h5_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
    }

    public List<String> h6() {
        ArrayList<String> a = getStringList(CollectionSchema.h6_txt);
        if (a == null || a.size() == 0) return new ArrayList<String>(0);
        return a;
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

    /**
     * Get the YaCy ranking score for this entry
     * (the value is updated while adding to the result queue where score calc takes place)
     * @return YaCy calculated score (number > 0)
     */
    public long score() {
        return this.score;
    }

    /**
     * Set the YaCy ranking score to make it accessible in the search interface/api
     * (should be set to the effective value of result queues getWeight)
     * @param theScore YaCy ranking of search results
     */
    public void setScore(long theScore) {
        this.score = theScore;
    }

    public Date loaddate() {
        return getDate(CollectionSchema.load_date_dt);
    }

    /**
     * Get calculated date until resource shall be considered as fresh
     * this may be a date in future
     *
     * @return Date initally calculated to (loaddate + (loaddate - lastmodified)/2)
     */
    public Date freshdate() {
        // getDate() can't be used as it checks for date <= now
        Date x = (Date) this.getFieldValue(CollectionSchema.fresh_date_dt.getSolrFieldName());
        if (x == null) return new Date(0);
        return x;
    }

    /**
     * @deprecated obsolete, never assigned a value
     */
    @Deprecated
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

    /**
     * Content language
     * @return 2-char language code or empty string
     */
    public String language() {
        String language = getString(CollectionSchema.language_s);
        return language;
    }

    public byte[] referrerHash() {
        String  referrer = getString(CollectionSchema.referrer_id_s);
        if (referrer == null || referrer.length() == 0) return null;
        return ASCII.getBytes(referrer);
    }

    /**
     * gives the size in byte of the original url document
     * @return filesize of url
     */
    public int filesize() {
        return getInt(CollectionSchema.size_i);
    }

    public Bitfield flags() {
        if (this.flags == null) {
            this.flags = new Bitfield();
            if (dc_subject() != null && dc_subject().indexOf("indexof") >= 0) this.flags.set(Tokenizer.flag_cat_indexof, true);
            ContentDomain cd = getContentDomain();
            if (lon() != 0.0d || lat() != 0.0d) this.flags.set(Tokenizer.flag_cat_haslocation, true);
            if (cd == ContentDomain.IMAGE || limage() > 0) this.flags.set(Tokenizer.flag_cat_hasimage, true);
            if (cd == ContentDomain.AUDIO || laudio() > 0) this.flags.set(Tokenizer.flag_cat_hasaudio, true);
            if (cd == ContentDomain.VIDEO || lvideo() > 0) this.flags.set(Tokenizer.flag_cat_hasvideo, true);
            if (cd == ContentDomain.APP) this.flags.set(Tokenizer.flag_cat_hasapp, true);
            if (lapp() > 0) this.flags.set(Tokenizer.flag_cat_hasapp, true);
        }
        return this.flags;
    }

    public int wordCount() {
        return getInt(CollectionSchema.wordcount_i);
    }

    /**
     * in case that images are embedded in the document, get one image which can be used as thumbnail
     * @return the first embedded image url
     * @throws UnsupportedOperationException when there is no image URL referenced on this document
     */
    public String imageURL() throws UnsupportedOperationException {
    	if (limage() == 0) throw new UnsupportedOperationException();
    	List<String> images_protocol = CollectionConfiguration.indexedList2protocolList(getFieldValues(CollectionSchema.images_protocol_sxt.getSolrFieldName()), limage());
    	List<String> images_stub = getStringList(CollectionSchema.images_urlstub_sxt);
    	int c = Math.min(images_protocol.size(), images_stub.size());
    	if (c == 0) throw new UnsupportedOperationException();
    	String url = images_protocol.get(0) + "://" + images_stub.get(0);
    	return url;
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
    
    public ArrayList<String> getSynonyms() {
        return getStringList(CollectionSchema.synonyms_sxt);
    }

    public static Iterator<String> getLinks(SolrDocument doc, boolean inbound) {
        Collection<Object> urlstub = doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_urlstub_sxt :  CollectionSchema.outboundlinks_urlstub_sxt).getSolrFieldName());
        Collection<String> urlprot = urlstub == null ? null : CollectionConfiguration.indexedList2protocolList(doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_protocol_sxt : CollectionSchema.outboundlinks_protocol_sxt).getSolrFieldName()), urlstub.size());
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

    /**
     * Extracts icon entries from this solr document
     * @return icon entries collection eventually empty
     */
	public final Collection<IconEntry> getIcons() {
		Collection<IconEntry> icons = new ArrayList<>();
		List<?> iconsUrlStubsList = getFieldValuesAsList(CollectionSchema.icons_urlstub_sxt.getSolrFieldName());
		if (iconsUrlStubsList != null) {

			List<String> ports = CollectionConfiguration.indexedList2protocolList(
					getFieldValues(CollectionSchema.icons_protocol_sxt.getSolrFieldName()), iconsUrlStubsList.size());
			List<?> allSizes = getFieldValuesAsList(CollectionSchema.icons_sizes_sxt.getSolrFieldName());
			List<?> allRels = getFieldValuesAsList(CollectionSchema.icons_rel_sxt.getSolrFieldName());

			Object item;
			for (int index = 0; index < iconsUrlStubsList.size(); index++) {
				item = iconsUrlStubsList.get(index);
				String urlStub = null;
				if (item instanceof String) {
					urlStub = (String) item;
					String iconURLStr = (ports != null && ports.size() > index ? ports.get(index) : "http") + "://" + urlStub;

					DigestURL iconURL;
					try {
						iconURL = new DigestURL(iconURLStr);
					} catch (MalformedURLException e) {
						continue;
					}

					Set<String> rels = null;
					if (allRels.size() > index) {
						item = allRels.get(index);
						if (item instanceof String) {
							rels = ContentScraper.parseSpaceSeparatedTokens((String) item);
						}
					}
					/* This may happen when icons_rel_sxt field has been disabled in solr schema */
					if(rels == null) {
						rels = new HashSet<>();
						rels.add("unknown");
					}

					Set<Dimension> sizes = null;
					if (allSizes.size() > index) {
						item = allSizes.get(index);
						if (item instanceof String) {
							sizes = ContentScraper.parseSizes((String) item);
						}
					}

					icons.add(new IconEntry(iconURL, rels, sizes));
				}
			}
		}
		return icons;
	}

	/**
	 * Try to extract icon entry with preferred size from this solr document.
	 * We look preferably for a standard icon but accept as a fallback other icons.
	 * @param preferredSize preferred size
	 * @return icon entry or null
	 */
	public IconEntry getFavicon(Dimension preferredSize) {
		IconEntry faviconEntry = null;
		boolean foundStandard = false;
		double closestDistance = Double.MAX_VALUE;
		for (IconEntry icon : this.getIcons()) {
			boolean isStandard = icon.isStandardIcon();
			double distance = IconEntry.getDistance(icon.getClosestSize(preferredSize), preferredSize);
			boolean match = false;
			if (foundStandard) {
				/*
				 * Already found a standard icon : now must find a standard icon
				 * with closer size
				 */
				match = isStandard && distance < closestDistance;
			} else {
				/*
				 * No standard icon yet found : prefer a standard icon, or check
				 * size
				 */
				match = isStandard || distance <= closestDistance;
			}
			if (match) {
				faviconEntry = icon;
				closestDistance = distance;
				foundStandard = isStandard;
				if (isStandard && distance == 0.0) {
					break;
				}
			}
		}

		return faviconEntry;
	}

	/**
	 * Use iconURL to set icons related field on this solr document.
	 *
	 * @param iconURL icon URL
	 */
	private void setIconsFields(DigestURL iconURL) {
		final List<String> protocols = new ArrayList<String>(1);
		final List<String> sizes = new ArrayList<String>(1);
		final List<String> stubs = new ArrayList<String>(1);
		final List<String> rels = new ArrayList<String>(1);

		if (iconURL != null) {
			String protocol = iconURL.getProtocol();
			protocols.add(protocol);

			sizes.add("");
			stubs.add(iconURL.toString().substring(protocol.length() + 3));
			rels.add(IconLinkRelations.ICON.getRelValue());
		}

		this.setField(CollectionSchema.icons_protocol_sxt.name(), protocols);
		this.setField(CollectionSchema.icons_urlstub_sxt.name(), stubs);
		this.setField(CollectionSchema.icons_rel_sxt.name(), rels);
		this.setField(CollectionSchema.icons_sizes_sxt.name(), sizes);
	}

    /**
     * @param name field name
     * @return field values from field name eventually immutable empty list when field has no values or is not a List
     */
    public List<?> getFieldValuesAsList(String name) {
		Collection<Object> fieldValues = getFieldValues(name);
		List<?> list;
		if (fieldValues instanceof List<?>) {
			list = (List<?>) fieldValues;
		} else {
			list = Collections.EMPTY_LIST;
		}
		return list;
    }

    public static Date getDate(SolrDocument doc, final CollectionSchema key) {
        Date x = doc == null ? null : (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

    public String getText() {
        return getString(CollectionSchema.text_t);
    }

    public List<StringBuilder> getSentences(final boolean pre) {
        List<StringBuilder> sentences = new ArrayList<>();
        String text = this.getText();
        if (text == null || text.length() == 0) return sentences;
        SentenceReader sr = new SentenceReader(text, pre);
        while (sr.hasNext()) sentences.add(sr.next());
        sr.close();
        sr = null;
        text = null;
        return sentences;
    }

    public ArrayList<String> getDescription() {
        return getStringList(CollectionSchema.description_txt);
    }

    public static URIMetadataNode importEntry(final String propStr, String collection) {
        if (propStr == null || propStr.isEmpty() || propStr.charAt(0) != '{' || !propStr.endsWith("}")) {
            ConcurrentLog.severe("URIMetadataNode", "importEntry: propStr is not proper: " + propStr);
            return null;
        }
        try {
            return new URIMetadataNode(MapTools.s2p(propStr.substring(1, propStr.length() - 1)), collection);
        } catch (final kelondroException | MalformedURLException e) {
            // wrong format
            ConcurrentLog.severe("URIMetadataNode", e.getMessage());
            return null;
        }
    }

    /**
     * Format a date using the short day format.
     * @param date the date to format. Must not be null.
     * @return the formatted date
     * @throws NullPointerException when date is null.
     */
	private String formatShortDayDate(final Date date) {
		String formattedDate;
		try {
			/* Prefer using first the thread-safe shared instance of DateTimeFormatter */
			formattedDate = GenericFormatter.FORMAT_SHORT_DAY.format(date.toInstant());
		} catch (final DateTimeException e) {
			/*
			 * Should not happen, but rather than failing it is preferable to use the old
			 * formatter which uses synchronization locks
			 */
			formattedDate = GenericFormatter.SHORT_DAY_FORMATTER.format(date);
		}
		return formattedDate;
	}

	/**
	 * Parse a date string with the short day format.
	 *
	 * @param dateStr
	 *            a date representation as a String. Must not be null.
	 * @return the parsed Date or the current date when an parsing error occurred.
	 */
	private Date parseShortDayDate(final String dateStr) {
		Date parsed;
		try {
			/* Prefer using first the thread-safe shared instance of DateTimeFormatter */
			parsed = Date.from(LocalDate.parse(dateStr, GenericFormatter.FORMAT_SHORT_DAY).atStartOfDay()
					.toInstant(ZoneOffset.UTC));
		} catch (final RuntimeException e) {
			/* Retry with the old formatter which uses synchronization locks */
			try {
				parsed = GenericFormatter.SHORT_DAY_FORMATTER.parse(dateStr, 0).getTime();
			} catch (final ParseException pe) {
				parsed = new Date();
			}
		}
		return parsed;
	}

    protected StringBuilder corePropList() {
        // generate a parseable string; this is a simple property-list
        final StringBuilder s = new StringBuilder(300);

        try {
            s.append("hash=").append(ASCII.String(this.hash()));
            s.append(",url=").append(crypt.simpleEncode(this.url().toNormalform(true)));
            s.append(",descr=").append(crypt.simpleEncode(this.dc_title()));
            s.append(",author=").append(crypt.simpleEncode(this.dc_creator()));
            s.append(",tags=").append(crypt.simpleEncode(Tagging.cleanTagFromAutotagging(this.dc_subject())));
            s.append(",publisher=").append(crypt.simpleEncode(this.dc_publisher()));
            s.append(",lat=").append(this.lat());
            s.append(",lon=").append(this.lon());
            s.append(",mod=").append(formatShortDayDate(this.moddate()));
            s.append(",load=").append(formatShortDayDate(this.loaddate()));
            s.append(",fresh=").append(formatShortDayDate(this.freshdate()));
            s.append(",referrer=").append(this.referrerHash() == null ? "" : ASCII.String(this.referrerHash()));
            //s.append(",md5=").append(this.md5()); // md5 never calculated / not used, also removed from this(prop) 2015-11-27
            s.append(",size=").append(this.filesize());
            s.append(",wc=").append(this.wordCount());
            final char dt = this.doctype();
            s.append(",dt=").append(dt);
            // if default revert from doctype to mime doesn't match actual mime,
            // include mime in the properties
            final String mime = this.mime();
            if (mime != null) {
                final String[] mimex = Response.doctype2mime(null,dt);
                if (!mime.equals(mimex[0])) { // include mime if not equal to recalc by dt (to make sure correct mime is recorded)
                    s.append(",mime=").append(crypt.simpleEncode(mime));
                }
            }
            s.append(",flags=").append(this.flags().exportB64());
            s.append(",lang=").append(this.language());
            s.append(",llocal=").append(this.llocal());
            s.append(",lother=").append(this.lother());
            s.append(",limage=").append(this.limage());
            s.append(",laudio=").append(this.laudio());
            s.append(",lvideo=").append(this.lvideo());
            s.append(",lapp=").append(this.lapp());
            s.append(",score=").append(Long.toString(this.score()));
            if (this.word() != null) {
                // append also word properties
                final String wprop = this.word().toPropertyForm();
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(wprop));
            }
            /* Add favicon URL with preferred size being 16x16 pixels if known */
            if(!this.getIcons().isEmpty()) {
            	IconEntry faviconEntry = this.getFavicon(new Dimension(16, 16));
            	if(faviconEntry != null) {
            		s.append(",favicon=").append(crypt.simpleEncode(faviconEntry.getUrl().toNormalform(false)));
            	}
            }
            return s;
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    /**
     * the toString format to transport the data over p2p connections.
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
     * and is used in the peer to peer exchange*
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

    private Date[] getDates(CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.date;
        Object content = this.getFieldValue(field.getSolrFieldName());
        if (content == null) return new Date[0];
        if (content instanceof Date) {
        	return new Date[] {(Date) content};
        }
        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<Date> x = (List<Date>) content;
            return x.toArray(new Date[x.size()]);
        }
        return new Date[0];
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

    // --- implementation for use as search result ----------
    /**
     * Initialisize some variables only needed for search results
     * and eleminates underlaying fields not needed for search results
     *
     * ! never put this back to the index because of the reduced content fields
     * @param indexSegment
     * @param peers
     * @param textSnippet
     * @return
     */
    public URIMetadataNode makeResultEntry(
                       final Segment indexSegment,
                       SeedDB peers,
                       final TextSnippet textSnippet) {
        this.removeFields(CollectionSchema.text_t.getSolrFieldName()); // clear the text field which eats up most of the space; it was used for snippet computation which is in a separate field here
        this.alternative_urlstring = null;
        this.alternative_urlname = null;
        this.textSnippet = textSnippet;
        final String host = this.url().getHost();
        if (host != null && host.endsWith(".yacyh")) {
            // translate host into current IP
            int p = host.indexOf('.');
            final String hash = Seed.hexHash2b64Hash(host.substring(p + 1, host.length() - 6));
            final Seed seed = peers.getConnected(hash);
            final String path = this.url().getFile();
            String address = null;
            if(seed != null) {
            	final Set<String> ips = seed.getIPs();
            	if(!ips.isEmpty()) {
            		address = seed.getPublicAddress(ips.iterator().next());
            	}
            }
            if (address == null) {
                // seed is not known from here
                try {
                    if (indexSegment.termIndex() != null) indexSegment.termIndex().remove(
                        Word.words2hashesHandles(Tokenizer.getWords(
                            (path.replace('?', ' ') +
                             " " +
                             this.dc_title()), null).keySet()),
                             this.hash());
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
                indexSegment.fulltext().remove(this.hash()); // clean up
            } else {
                this.alternative_urlstring = "http://" + address + "/" + host.substring(0, p) + path;
                this.alternative_urlname = "http://" + seed.getName() + ".yacy" + path;
                if ((p = this.alternative_urlname.indexOf('?')) > 0) this.alternative_urlname = this.alternative_urlname.substring(0, p);
            }
        }
        return this;
    }
    /**
     * used for search result entry
     */
    public String urlstring() {
        if (this.alternative_urlstring != null) return this.alternative_urlstring;

        return this.url().toNormalform(true);
    }
    /**
     * used for search result entry
     */
    public String urlname() {
        return (this.alternative_urlname == null) ? MultiProtocolURL.unescape(urlstring()) : this.alternative_urlname;
    }
    /**
     * used for search result entry
     */
    public String title() {
        String titlestr = this.dc_title();
        // if title is empty use filename as title
        if (titlestr.isEmpty()) { // if url has no filename, title is still empty (e.g. "www.host.com/" )
            titlestr = this.url() != null ? this.url().getFileName() : "";
        }
        return titlestr;
    }
    /**
     * used for search result entry
     */
    public TextSnippet textSnippet() {
        return this.textSnippet;
    }
    /**
     * used for search result entry
     */
    public Date[] events() {
        return this.datesInContent();
    }
    /**
     * used for search result entry
     */
    public boolean hasTextSnippet() {
        return (this.textSnippet != null) && (!this.textSnippet.getErrorCode().fail());
    }
    /**
     * used for search result entry
     */
    public String resource() {
        // generate transport resource
        if ((this.textSnippet == null) || (!this.textSnippet.exists())) {
            return this.toString();
        }
        return this.toString(this.textSnippet.getLineRaw());
    }

    @Override
    public int hashCode() {
        return this.url().hashCode();
    }
}