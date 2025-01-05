//ViewFile.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//you must compile this file with
//javac -classpath .:../Classes Status.java
//if the shell's current path is HTROOT

package net.yacy.htroot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.CommonPattern;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.SentenceReader;
import net.yacy.document.Tokenizer;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.document.parser.html.IconEntry;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ViewFile {

    public static final int VIEW_MODE_NO_TEXT = 0;
    public static final int VIEW_MODE_AS_PLAIN_TEXT = 1;
    public static final int VIEW_MODE_AS_PARSED_TEXT = 2;
    public static final int VIEW_MODE_AS_PARSED_SENTENCES = 3;
    public static final int VIEW_MODE_AS_IFRAME_FROM_WEB = 4;
    public static final int VIEW_MODE_AS_IFRAME_FROM_CACHE = 5;
    public static final int VIEW_MODE_AS_LINKLIST = 6;
    public static final int VIEW_MODE_AS_PARSED_WORDS = 7;
    public static final int VIEW_MODE_AS_SCHEMA = 8;
    public static final int VIEW_MODE_AS_IFRAME_FROM_CITATION_REPORT = 9;

    private static final String HIGHLIGHT_CSS = "searchHighlight";
    private static final int MAX_HIGHLIGHTS = 6;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
        prop.put("searchindocument", 0);
        prop.put("viewMode", VIEW_MODE_NO_TEXT);
        prop.put("viewModeValue", "sentences");
        prop.putHTML("error_words", "");
        prop.put("error_vMode-sentences", "1");
        prop.put("error", "1");
        prop.put("url", "");
        prop.put("showSnippet", 0);

        if (post == null) return prop;

        // get segment
        final Segment indexSegment = sb.index;
        final boolean authorized = sb.verifyAuthentication(header);

        if (post.containsKey("words"))
            prop.putHTML("error_words", post.get("words"));
        else {
            prop.putHTML("error_words", "");
        }

        prop.put("error_vMode-iframeWeb", "0");
        prop.put("error_vMode-iframeCache", "0");
        prop.put("error_vMode-plain", "0");
        prop.put("error_vMode-parsed", "0");
        prop.put("error_vMode-sentences", "0");
        prop.put("error_vMode-words", "0");
        prop.put("error_vMode-links", "0");
        prop.put("error_vMode-iframeCitations", "0");
        prop.put("error_vMode-schema", "0");
        final boolean showSnippet = post.get("show", "").equals("Show Snippet");
        final String viewMode = showSnippet ? "sentences" : post.get("viewMode", "sentences");
        prop.put("error_vMode-" + viewMode, "1");
        prop.put("viewModeValue", viewMode);

        DigestURL url = null;
        String descr = "";
        final int wordCount = 0;
        int size = 0;
        boolean pre = false;

        // get the url hash from which the content should be loaded
        String urlHash = post.get("urlHash", post.get("urlhash", ""));

        // if the user has made an input of the url string, this overwrites a possibly given url hash
        final String urlString = post.get("url", "");
        if (urlString.length() > 0) try {
            // this call forces the peer to download  web pages
            // it is therefore protected by the admin password

            if (!sb.verifyAuthentication(header)) {
                prop.authenticationRequired();
                return prop;
            }

            // define an url by post parameter
            url = new DigestURL(MultiProtocolURL.unescape(urlString));
            urlHash = ASCII.String(url.hash());
            pre = post.getBoolean("pre");
        } catch (final MalformedURLException e) {}

        // get the urlEntry that belongs to the url hash
        URIMetadataNode urlEntry = null; // to be overwritten if we succeed in finding the url in the current document index
        //boolean ue = urlHash.length() > 0 && indexSegment.exists(ASCII.getBytes(urlHash));
        //if (ue) Log.logInfo("ViewFile", "exists(" + urlHash + ")");
        if (urlHash.length() > 0 && (urlEntry = indexSegment.fulltext().getMetadata(ASCII.getBytes(urlHash))) == null) {
            // could not find the url, we try a commit to get the latest data and the try again
            indexSegment.fulltext().commit(true);
        }
        if (urlHash.length() > 0 && (urlEntry = indexSegment.fulltext().getMetadata(ASCII.getBytes(urlHash))) != null) {
            // get the url that belongs to the entry
            if (urlEntry == null || urlEntry.url() == null) {
                prop.put("error", "3");
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }
            url = urlEntry.url();
            descr = urlEntry.dc_title();
            //urlEntry.wordCount();
            size = urlEntry.filesize();
            pre = urlEntry.flags().get(Tokenizer.flag_cat_indexof);
            prop.put("searchindocument", 1);
            prop.putHTML("searchindocument_query", post.get("query",""));
        }

        prop.put("error_inurldb", urlEntry == null ? 0 : 1);

        if (url == null) {
            prop.put("error", "1");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            prop.put("url", "");
            return prop;
        }
        prop.put("url", url.toNormalform(true));

        // loading the resource content as byte array
        prop.put("error_incache", Cache.has(url.hash()) ? 1 : 0);

        // load the resource content, if user is not authorized, use cache only
        Response response = null;
        try {
            final ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
            // use sb.loader.requst( , , global=true) to use crawlprofile to allow index update
            response = sb.loader.load(sb.loader.request(url, true, true), authorized ? CacheStrategy.IFEXIST : CacheStrategy.CACHEONLY, Integer.MAX_VALUE, null, agent);
        } catch (final IOException e) {
            prop.put("error", "4");
            prop.put("error_errorText", "error loading resource: " + e.getMessage());
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        // no resource available, return an error
        if (response == null) {
            prop.put("error", "4");
            prop.put("error_errorText", "No resource available");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        final String[] wordArray = wordArray(post.get("words", null));
        if (viewMode.equals("iframeWeb")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_WEB);
            prop.put("viewMode_url", url.toNormalform(true));

        } else if (viewMode.equals("iframeCache")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_CACHE);
            prop.put("viewMode_png", 0);
            prop.put("viewMode_html", 0);
            if (response.docType() == Response.DT_IMAGE) {
                prop.put("viewMode_png", 1);
                prop.put("viewMode_png_url", url.toNormalform(true));
            } else {
                prop.put("viewMode_html", 1);
                prop.put("viewMode_html_url", url.toNormalform(true));
            }
        } else if (viewMode.equals("plain")) {

            // TODO: how to handle very large files here ?
            String content;
            try {
                String charsetName = response.getCharacterEncoding();
                try {
                    if(charsetName == null) {
                        /* Encoding is unknown from response headers : default decode using UTF-8 */
                        charsetName = StandardCharsets.UTF_8.name();
                    } else if(!Charset.isSupported(charsetName)) {
                        /* Encoding is known but not supported on this system : default decode using UTF-8 */
                        charsetName = StandardCharsets.UTF_8.name();
                    }
                } catch(final IllegalCharsetNameException e) {
                    /* Encoding is known but charset name is not valid : default decode using UTF-8 */
                    charsetName = StandardCharsets.UTF_8.name();
                }
                content = new String(response.getContent(), charsetName);
            } catch (final Exception e) {
                prop.put("error", "4");
                prop.putHTML("error_errorText", e.getMessage());
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }

            prop.put("error", "0");
            prop.put("viewMode", VIEW_MODE_AS_PLAIN_TEXT);
            prop.put("viewMode_plainText", markup(wordArray, content).replaceAll("\n", "<br />").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));

        } else if (viewMode.equals("parsed") || viewMode.equals("sentences")  || viewMode.equals("words") || viewMode.equals("links")) {
            // parsing the resource content
            Document document = null;
            try {
                document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
                if (document == null) {
                    prop.put("error", "5");
                    prop.put("error_errorText", "Unknown error");
                    prop.put("viewMode", VIEW_MODE_NO_TEXT);
                    return prop;
                }
            } catch (final Parser.Failure e) {
                prop.put("error", "5");
                prop.putHTML("error_errorText", e.getMessage());
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }

            if (viewMode.equals("parsed")) {
                final String content = document.getTextString();
                // content = wikiCode.replaceHTML(content); // added by Marc Nause
                prop.put("viewMode", VIEW_MODE_AS_PARSED_TEXT);
                prop.put("viewMode_title", document.dc_title());
                prop.put("viewMode_creator", document.dc_creator());
                prop.put("viewMode_subject", document.dc_subject(','));
                prop.put("viewMode_description", document.dc_description().length == 0 ? new String[]{""} : document.dc_description());
                prop.put("viewMode_publisher", document.dc_publisher());
                prop.put("viewMode_format", document.dc_format());
                prop.put("viewMode_identifier", document.dc_identifier());
                prop.put("viewMode_source", url.toNormalform(false));
                prop.put("viewMode_lat", document.lat());
                prop.put("viewMode_lon", document.lon());
                prop.put("viewMode_parsedText", markup(wordArray, content).replaceAll("\n", "<br />").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));

            } else if (viewMode.equals("sentences")) {
                prop.put("viewMode", VIEW_MODE_AS_PARSED_SENTENCES);
                final Collection<StringBuilder> sentences = document.getSentences(pre);

                boolean dark = true;
                int i = 0;
                String sentence;
                if (sentences != null) {

                    // Search word highlighting
                    for (final StringBuilder s: sentences) {
                        sentence = s.toString();
                        if (sentence.trim().length() > 0) {
                            prop.put("viewMode_sentences_" + i + "_nr", i + 1);
                            prop.put("viewMode_sentences_" + i + "_text", markup(wordArray, sentence));
                            prop.put("viewMode_sentences_" + i + "_dark", dark ? "1" : "0");
                            dark = !dark;
                            i++;
                        }
                    }
                }
                prop.put("viewMode_sentences", i);

            } else if (viewMode.equals("words")) {
                prop.put("viewMode", VIEW_MODE_AS_PARSED_WORDS);
                final Collection<StringBuilder> sentences = document.getSentences(pre);

                boolean dark = true;
                int i = 0;
                String sentence;
                StringBuilder token;
                if (sentences != null) {

                    // Search word highlighting
                    for (final StringBuilder s: sentences) {
                        sentence = s.toString();
                        WordTokenizer tokens = new WordTokenizer(new SentenceReader(sentence), LibraryProvider.dymLib);
                        try {
                            while (tokens.hasMoreElements()) {
                                token = tokens.nextElement();
                                if (token.length() > 0) {
                                    prop.put("viewMode_words_" + i + "_nr", i + 1);
                                    prop.put("viewMode_words_" + i + "_word", token.toString());
                                    prop.put("viewMode_words_" + i + "_dark", dark ? "1" : "0");
                                    dark = !dark;
                                    i++;
                                }
                            }
                        } finally {
                            tokens.close();
                            tokens = null;
                        }
                    }
                }
                prop.put("viewMode_words", i);

            } else if (viewMode.equals("links")) {
                putLinks(prop, wordArray, document, post.get("agentName"));
            }

            // optional: generate snippet
            if (showSnippet) {
                final QueryGoal goal = new QueryGoal(post.get("search", ""));
                try {
                    final TextSnippet snippet = new TextSnippet(
                            sb.loader,
                            urlEntry,
                            goal.getIncludeWordsSet(),
                            goal.getIncludeHashes(),
                            CacheStrategy.CACHEONLY,
                            false,
                            SearchEvent.SNIPPET_MAX_LENGTH,
                            false);
                    String titlestr = urlEntry.dc_title();
                    // if title is empty use filename as title
                    if (titlestr.isEmpty()) { // if url has no filename, title is still empty (e.g. "www.host.com/" )
                        titlestr = urlEntry.url() != null ? urlEntry.url().getFileName() : "";
                    }
                    final String desc = (snippet == null) ? "" : snippet.descriptionline(goal);
                    prop.put("showSnippet_headline", titlestr);
                    prop.put("showSnippet_teasertext", desc);
                    prop.put("showSnippet", 1);
                } catch (UnsupportedOperationException e) {
                    prop.put("showSnippet_headline", "<no snippet found>");
                    prop.put("showSnippet_teasertext", "<no snippet found>");
                    prop.put("showSnippet", 1);
                }
            }
            // update index with parsed resource if index entry is older or missing
			final long responseSize = response.size();
			if (urlEntry == null || urlEntry.loaddate().before(response.lastModified())) {
				/* Also check resource size is lower than configured crawler limits */
				if (responseSize >= 0
						&& responseSize <= Switchboard.getSwitchboard().loader.protocolMaxFileSize(response.url())) {
					Switchboard.getSwitchboard().toIndexer(response);
				}
			}
            if (document != null) document.close();
        } else if (viewMode.equals("schema")) {
            prop.put("viewMode", VIEW_MODE_AS_SCHEMA);
            prop.put("viewMode_url", url.toNormalform(true));
            // list all fields in the document which have text or string content
            // first we must load the solr document from the index
            try {
                final SolrDocument solrDocument = indexSegment.fulltext().getDefaultConnector().getDocumentById(ASCII.String(url.hash()));
                if (solrDocument != null) {
                    int c = 0;
                    for (final String fieldName : solrDocument.getFieldNames()) {
                        final Object value = solrDocument.getFieldValue(fieldName);
                        if (value instanceof String || value instanceof Collection) {
                            prop.put("viewMode_fields_" + c + "_key", fieldName);
                            prop.put("viewMode_fields_" + c + "_value", value.toString());
                            c++;
                        }
                    }
                    prop.put("viewMode_fields", c);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (viewMode.equals("iframeCitations")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_CITATION_REPORT);
            prop.put("viewMode_url", url.toNormalform(true));
        }
        prop.put("error", "0");
        prop.put("error_url", url.toNormalform(true));
        prop.put("error_hash", urlHash);
        prop.put("error_inurldb_hash", urlHash);
        prop.put("error_wordCount", wordCount);
        prop.put("error_firstSeen", "");
        final long firstseen = sb.index.getFirstSeenTime(ASCII.getBytes(urlHash));
        prop.put("error_firstSeen", firstseen < 0 ? "" : new Date(firstseen).toString());
        prop.putHTML("error_desc", (descr.isEmpty()) ? "&nbsp;" : descr);
        prop.putNum("error_size", size);
        prop.put("error_mimeTypeAvailable", (response.getMimeType() == null) ? "0" : "1");
        prop.put("error_mimeTypeAvailable_mimeType", response.getMimeType());

        if (urlEntry == null) {
            prop.put("error_referrerHash", "");
            prop.put("error_moddate", "");
            prop.put("error_loaddate", "");
            prop.put("error_freshdate", "");
            prop.put("error_hosthash", "");
            prop.putHTML("error_dc_creator", "");
            prop.putHTML("error_dc_publisher", "");
            prop.putHTML("error_dc_subject", "");
            prop.put("error_lat", "");
            prop.put("error_lon", "");
            prop.put("error_language", "");
            prop.put("error_flags", "");
            prop.put("error_wordCount", "");
            prop.put("error_llocal", "");
            prop.put("error_lother", "");
            prop.put("error_limage", "");
            prop.put("error_laudio", "");
            prop.put("error_lvideo", "");
            prop.put("error_lapp", "");
            prop.put("error_collections", "");
        } else {
            prop.put("error_referrerHash", urlEntry.referrerHash());
            prop.put("error_moddate", urlEntry.moddate());
            prop.put("error_loaddate", urlEntry.loaddate());
            prop.put("error_freshdate", urlEntry.freshdate());
            prop.put("error_hosthash", urlEntry.hosthash());
            prop.putHTML("error_dc_creator", urlEntry.dc_creator());
            prop.putHTML("error_dc_publisher", urlEntry.dc_publisher());
            prop.putHTML("error_dc_subject", urlEntry.dc_subject());
            prop.put("error_lat", urlEntry.lat());
            prop.put("error_lon", urlEntry.lon());
            prop.put("error_language", urlEntry.language());
            prop.put("error_flags", urlEntry.flags().toString());
            prop.put("error_wordCount", urlEntry.wordCount());
            prop.put("error_llocal", urlEntry.llocal());
            prop.put("error_lother", urlEntry.lother());
            prop.put("error_limage", urlEntry.limage());
            prop.put("error_laudio", urlEntry.laudio());
            prop.put("error_lvideo", urlEntry.lvideo());
            prop.put("error_lapp", urlEntry.lapp());
            prop.put("error_collections", Arrays.toString(urlEntry.collections()));
        }

        return prop;
    }

    /**
     * Fill prop object with document links.
     * @param prop object to be filled. Must not be null
     * @param wordArray aray of words from word post parameter
     * @param document document to process
     * @param agentName the eventual custom identification agent name used to load documents
     */
	private static void putLinks(final serverObjects prop, final String[] wordArray, final Document document, final String agentName) {
		prop.put("viewMode", VIEW_MODE_AS_LINKLIST);
		boolean dark = true;
		int i = 0;

		i += putMediaInfo(prop, wordArray, i, document.getVideolinks(), "video", (i % 2 == 0), agentName);
		i += putMediaInfo(prop, wordArray, i, document.getAudiolinks(), "audio", (i % 2 == 0), agentName);
		dark = (i % 2 == 0);
		i += putIconsInfos(prop, wordArray, i, document.getIcons().values(), (i % 2 == 0), agentName);
		dark = (i % 2 == 0);

		final Map<DigestURL, ImageEntry> ts = document.getImages();
		final Iterator<ImageEntry> tsi = ts.values().iterator();
		ImageEntry entry;
		while (tsi.hasNext()) {
		    entry = tsi.next();
		    final String urlStr = entry.url().toNormalform(true);
		    prop.put("viewMode_links_" + i + "_nr", i);
		    prop.put("viewMode_links_" + i + "_dark", dark ? "1" : "0");
		    prop.put("viewMode_links_" + i + "_type", "image");
		    prop.put("viewMode_links_" + i + "_text", (entry.alt().isEmpty()) ? "&nbsp;" : markup(wordArray, entry.alt()));
			prop.put("viewMode_links_" + i + "_encodedUrl", UTF8.encodeUrl(urlStr));
			if(agentName != null) {
				prop.put("viewMode_links_" + i + "_agent", true);
				prop.put("viewMode_links_" + i + "_agent_name", UTF8.encodeUrl(agentName));
			} else {
				prop.put("viewMode_links_" + i + "_agent", false);
			}
		    prop.put("viewMode_links_" + i + "_url", urlStr);
		    prop.put("viewMode_links_" + i + "_link", markup(wordArray, urlStr));
		    if (entry.width() > 0 && entry.height() > 0) {
		        prop.put("viewMode_links_" + i + "_rel", entry.width() + "x" + entry.height() + " Pixel");
		    } else {
		        prop.put("viewMode_links_" + i + "_rel", "");
		    }
		    prop.put("viewMode_links_" + i + "_name", "");
		    dark = !dark;
		    i++;
		}
		i += putMediaInfo(prop, wordArray, i, document.getApplinks(), "app", (i % 2 == 0), agentName);
		i += putMediaInfo(prop, wordArray, i, document.getHyperlinks(), "link", (i % 2 == 0), agentName);
		prop.put("viewMode_links", i);
	}

    private static final String[] wordArray(String words) {
        String[] w = new String[0];
        if (words == null || words.isEmpty()) return w;
        if (words.length() > 1 && words.charAt(0) == '[' && words.charAt(words.length() - 1) == ']') {
            words = words.substring(1, words.length() - 1);
        }
        words = UTF8.decodeURL(words);
        if (words.indexOf(' ',0) >= 0) return CommonPattern.SPACES.split(words);
        if (words.indexOf(',',0) >= 0) return CommonPattern.COMMA.split(words);
        if (words.indexOf('+',0) >= 0) return words.split("\\+");
        w = new String[1];
        w[0] = words;
        return w;
    }

    private static final String markup(final String[] wordArray, String message) {
        message = CharacterCoding.unicode2html(message, true);
        if (wordArray != null) {
            int j = 0;
            for (String currentWord : wordArray) {
                currentWord = currentWord.trim();
                // TODO: replace upper-/lowercase words as well
                message = message.replaceAll(currentWord,
                                "<span class=\"" + HIGHLIGHT_CSS + ((j++ % MAX_HIGHLIGHTS) + 1) + "\">" +
                                currentWord +
                                "</span>");
            }
        }
        return message;
    }

    /**
     * Fill prop object with media links.
     * @param prop object ot be filled
     * @param wordArray words array
     * @param c current links count
     * @param media media links
     * @param type type of media link
     * @param dark current result line style
     * @param agentName the eventual custom identification agent name used to load documents
     * @return number of links added to prop
     */
    private static int putMediaInfo(
                    final serverObjects prop,
                    final String[] wordArray,
                    int c,
                    final Map<AnchorURL, String> media,
                    final String type,
                    boolean dark,
                    final String agentName) {
        int i = 0;
        for (final Map.Entry<AnchorURL, String> entry : media.entrySet()) {
            final String name = entry.getKey().getNameProperty(); // the name attribute
            final String rel = entry.getKey().getRelProperty();   // the rel-attribute
            final String text = entry.getKey().getTextProperty(); // the text between the <a></a> tag
            final String urlStr = entry.getKey().toNormalform(true);

            prop.put("viewMode_links_" + c + "_nr", c);
            prop.put("viewMode_links_" + c + "_dark", ((dark) ? 1 : 0));
            prop.putHTML("viewMode_links_" + c + "_type", type);
            prop.put("viewMode_links_" + c + "_text", text);
            prop.put("viewMode_links_" + c + "_link", markup(wordArray, urlStr));
            prop.put("viewMode_links_" + c + "_url", urlStr);
			prop.put("viewMode_links_" + c + "_encodedUrl", UTF8.encodeUrl(urlStr));
			if(agentName != null) {
				prop.put("viewMode_links_" + c + "_agent", true);
				prop.put("viewMode_links_" + c + "_agent_name", UTF8.encodeUrl(agentName));
			} else {
				prop.put("viewMode_links_" + c + "_agent", false);
			}
            prop.put("viewMode_links_" + c + "_rel", rel);
            prop.put("viewMode_links_" + c + "_name", name);
            dark = !dark;
            c++;
            i++;
        }
        return i;
    }

    /**
     * Fill prop object with icon links.
     * @param prop object ot be filled
     * @param wordArray words array
     * @param c current links count
     * @param icons icon links
     * @param dark current result line style
     * @param agentName the eventual custom identification agent name used to load documents
     * @return number of links added to prop
     */
    private static int putIconsInfos(
                    final serverObjects prop,
                    final String[] wordArray,
                    int c,
                    final Collection<IconEntry> icons,
                    boolean dark,
                    final String agentName) {
        int i = 0;
        for (final IconEntry entry : icons) {
            final String name = ""; // the name attribute
            final String rel = entry.relToString();   // the rel-attribute
            final String text = ""; // the text between the <a></a> tag
            final String urlStr = entry.getUrl().toNormalform(true);

            prop.put("viewMode_links_" + c + "_nr", c);
            prop.put("viewMode_links_" + c + "_dark", ((dark) ? 1 : 0));
            prop.putHTML("viewMode_links_" + c + "_type", "icon");
            prop.put("viewMode_links_" + c + "_text", text);
            prop.put("viewMode_links_" + c + "_link", markup(wordArray, urlStr));
            prop.put("viewMode_links_" + c + "_url", urlStr);
			prop.put("viewMode_links_" + c + "_encodedUrl", UTF8.encodeUrl(urlStr));
			if(agentName != null) {
				prop.put("viewMode_links_" + c + "_agent", true);
				prop.put("viewMode_links_" + c + "_agent_name", UTF8.encodeUrl(agentName));
			} else {
				prop.put("viewMode_links_" + c + "_agent", false);
			}
            prop.put("viewMode_links_" + c + "_rel", rel);
            prop.put("viewMode_links_" + c + "_name", name);
            dark = !dark;
            c++;
            i++;
        }
        return i;
    }
}
