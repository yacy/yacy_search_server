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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.SentenceReader;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
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
    public static final int VIEW_MODE_AS_IFRAME_FROM_CITATION_REPORT = 8;

    private static final String HIGHLIGHT_CSS = "searchHighlight";
    private static final int MAX_HIGHLIGHTS = 6;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);

        if (post == null) {
            prop.putHTML("error_words", "");
            prop.put("error_vMode-sentences", "1");
            prop.put("error", "1");
            prop.put("url", "");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        // get segment
        Segment indexSegment = sb.index;
        final boolean authorized = sb.verifyAuthentication(header);

        if (post.containsKey("words"))
            prop.putHTML("error_words", post.get("words"));
        else {
            prop.putHTML("error_words", "");
        }

        final String viewMode = post.get("viewMode","parsed");
        prop.put("error_vMode-" + viewMode, "1");

        DigestURL url = null;
        String descr = "";
        final int wordCount = 0;
        int size = 0;
        boolean pre = false;

        // get the url hash from which the content should be loaded
        String urlHash = post.get("urlHash", post.get("urlhash", ""));

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

        URIMetadataNode urlEntry = null;
        // get the urlEntry that belongs to the url hash
        //boolean ue = urlHash.length() > 0 && indexSegment.exists(ASCII.getBytes(urlHash));
        //if (ue) Log.logInfo("ViewFile", "exists(" + urlHash + ")");
        if (urlHash.length() > 0 && (urlEntry = indexSegment.fulltext().getMetadata(ASCII.getBytes(urlHash))) == null) {
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
            size = urlEntry.size();
            pre = urlEntry.flags().get(Condenser.flag_cat_indexof);
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

        Response response = null;
        try {
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
            response = sb.loader.load(sb.loader.request(url, true, false), authorized ? CacheStrategy.IFEXIST : CacheStrategy.CACHEONLY, Integer.MAX_VALUE, null, agent);
        } catch (final IOException e) {
            prop.put("error", "4");
            prop.put("error_errorText", "error loading resource: " + e.getMessage());
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        if (response == null) {
            prop.put("error", "4");
            prop.put("error_errorText", "No resource available");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        final String[] wordArray = wordArray(post.get("words", null));
        final String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        if (viewMode.equals("plain")) {

            // TODO: how to handle very large files here ?
            String content;
            try {
                content = UTF8.String(response.getContent());
            } catch (final Exception e) {
                prop.put("error", "4");
                prop.putHTML("error_errorText", e.getMessage());
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }

            prop.put("error", "0");
            prop.put("viewMode", VIEW_MODE_AS_PLAIN_TEXT);
            prop.put("viewMode_plainText", markup(wordArray, content).replaceAll("\n", "<br />").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));

        } else if (viewMode.equals("iframeWeb")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_WEB);
            prop.put("viewMode_url", url.toNormalform(true));

        } else if (viewMode.equals("iframeCache")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_CACHE);
            prop.put("viewMode_png", 0);
            prop.put("viewMode_html", 0);
            if (ext.length() > 0 && "jpg.jpeg.png.gif".indexOf(ext) >= 0) {
                prop.put("viewMode_png", 1);
                prop.put("viewMode_png_url", url.toNormalform(true));
            } else {
                prop.put("viewMode_html", 1);
                prop.put("viewMode_html_url", url.toNormalform(true));
            }
        } else if (viewMode.equals("iframeCitations")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_CITATION_REPORT);
            prop.put("viewMode_url", url.toNormalform(true));
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
                prop.put("viewMode_source", url.toString());
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
                prop.put("viewMode", VIEW_MODE_AS_LINKLIST);
                boolean dark = true;
                int i = 0;
                i += putMediaInfo(prop, wordArray, i, document.getVideolinks(), "video", (i % 2 == 0));
                i += putMediaInfo(prop, wordArray, i, document.getAudiolinks(), "audio", (i % 2 == 0));
                dark = (i % 2 == 0);

                final Map<AnchorURL, ImageEntry> ts = document.getImages();
                final Iterator<ImageEntry> tsi = ts.values().iterator();
                ImageEntry entry;
                while (tsi.hasNext()) {
                    entry = tsi.next();
                    prop.put("viewMode_links_" + i + "_nr", i);
                    prop.put("viewMode_links_" + i + "_dark", dark ? "1" : "0");
                    prop.put("viewMode_links_" + i + "_type", "image");
                    prop.put("viewMode_links_" + i + "_text", (entry.alt().isEmpty()) ? "&nbsp;" : markup(wordArray, entry.alt()));
                    prop.put("viewMode_links_" + i + "_url", entry.url().toNormalform(true));
                    prop.put("viewMode_links_" + i + "_link", markup(wordArray, entry.url().toNormalform(true)));
                    if (entry.width() > 0 && entry.height() > 0) {
                        prop.put("viewMode_links_" + i + "_rel", entry.width() + "x" + entry.height() + " Pixel");
                    } else {
                        prop.put("viewMode_links_" + i + "_rel", "");
                    }
                    prop.put("viewMode_links_" + i + "_name", "");
                    dark = !dark;
                    i++;
                }
                i += putMediaInfo(prop, wordArray, i, document.getApplinks(), "app", (i % 2 == 0));
                i += putMediaInfo(prop, wordArray, i, document.getHyperlinks(), "link", (i % 2 == 0));
                prop.put("viewMode_links", i);

            }
            if (document != null) document.close();
        }
        prop.put("error", "0");
        prop.put("error_url", url.toNormalform(true));
        prop.put("error_hash", urlHash);
        prop.put("error_wordCount", wordCount);
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
            prop.put("error_md5", "");
            prop.put("error_lat", "");
            prop.put("error_lon", "");
            prop.put("error_doctype", "");
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
            prop.put("error_md5", urlEntry.md5());
            prop.put("error_lat", urlEntry.lat());
            prop.put("error_lon", urlEntry.lon());
            prop.put("error_doctype", Response.doctype2mime(ext, urlEntry.doctype()));
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

    private static final String[] wordArray(String words) {
        String[] w = new String[0];
        if (words == null || words.isEmpty()) return w;
        if (words.length() > 1 && words.charAt(0) == '[' && words.charAt(words.length() - 1) == ']') {
            words = words.substring(1, words.length() - 1);
        }
        words = UTF8.decodeURL(words);
        if (words.indexOf(' ',0) >= 0) return words.split(" ");
        if (words.indexOf(',',0) >= 0) return words.split(",");
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

    private static int putMediaInfo(
                    final serverObjects prop,
                    final String[] wordArray,
                    int c,
                    final Map<AnchorURL, String> media,
                    final String type,
                    boolean dark) {
        int i = 0;
        for (final Map.Entry<AnchorURL, String> entry : media.entrySet()) {
            final String name = entry.getKey().getNameProperty(); // the name attribute
            final String rel = entry.getKey().getRelProperty();   // the rel-attribute
            final String text = entry.getKey().getTextProperty(); // the text between the <a></a> tag

            prop.put("viewMode_links_" + c + "_nr", c);
            prop.put("viewMode_links_" + c + "_dark", ((dark) ? 1 : 0));
            prop.putHTML("viewMode_links_" + c + "_type", type);
            prop.put("viewMode_links_" + c + "_text", text);
            prop.put("viewMode_links_" + c + "_link", markup(wordArray, entry.getKey().toNormalform(true)));
            prop.put("viewMode_links_" + c + "_url", entry.getKey().toNormalform(true));
            prop.put("viewMode_links_" + c + "_rel", rel);
            prop.put("viewMode_links_" + c + "_name", name);
            dark = !dark;
            c++;
            i++;
        }
        return i;
    }

}
