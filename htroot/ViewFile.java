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
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.http.client.Cache;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ViewFile {

    public static final int VIEW_MODE_NO_TEXT = 0;
    public static final int VIEW_MODE_AS_PLAIN_TEXT = 1;
    public static final int VIEW_MODE_AS_PARSED_TEXT = 2;
    public static final int VIEW_MODE_AS_PARSED_SENTENCES = 3;
    public static final int VIEW_MODE_AS_IFRAME_FROM_WEB = 4;
    public static final int VIEW_MODE_AS_IFRAME_FROM_CACHE = 5;
    public static final int VIEW_MODE_AS_LINKLIST = 6;
    public static final int VIEW_MODE_AS_PARSED_WORDS = 7;
    
    private static final String HIGHLIGHT_CSS = "searchHighlight";
    private static final int MAX_HIGHLIGHTS = 6;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard)env;
        
        if (post == null) {
            prop.put("display", 1);
            prop.put("error_display", 0);
            prop.putHTML("error_words", "");
            prop.put("error_vMode-sentences", "1");
            prop.put("error", "1");
            prop.put("url", "");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }
        
        final int display = post.getInt("display", 1);
        
        // get segment
        Segment indexSegment = null;
        boolean authorized = sb.verifyAuthentication(header, false);
        if (post != null && post.containsKey("segment") && authorized) {
            indexSegment = sb.indexSegments.segment(post.get("segment"));
        } else {
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }        
        
        prop.put("display", display);
        prop.put("error_display", display);

        if (post.containsKey("words"))
            prop.putHTML("error_words", post.get("words"));
        else {
            prop.putHTML("error_words", "");
        }

        final String viewMode = post.get("viewMode","parsed");
        prop.put("error_vMode-" + viewMode, "1");
        
        DigestURI url = null;
        String descr = "";
        final int wordCount = 0;
        int size = 0;
        boolean pre = false;
        
        // get the url hash from which the content should be loaded
        String urlHash = post.get("urlHash", "");
        URIMetadataRow urlEntry = null;
        // get the urlEntry that belongs to the url hash
        if (urlHash.length() > 0 && (urlEntry = indexSegment.urlMetadata().load(UTF8.getBytes(urlHash))) != null) {
            // get the url that belongs to the entry
            final URIMetadataRow.Components metadata = urlEntry.metadata();
            if ((metadata == null) || (metadata.url() == null)) {
                prop.put("error", "3");
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }
            url = metadata.url();
            descr = metadata.dc_title();
            //urlEntry.wordCount();
            size = urlEntry.size();
            pre = urlEntry.flags().get(Condenser.flag_cat_indexof);
        }

        prop.put("error_inurldb", urlEntry == null ? 0 : 1);
        
        // alternatively, get the url simply from a url String
        // this can be used as a simple tool to test the text parser
        final String urlString = post.get("url", "");
        if (urlString.length() > 0) try {
            // this call forces the peer to download  web pages
            // it is therefore protected by the admin password
            
            if (!sb.verifyAuthentication(header, false)) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }

            // define an url by post parameter
            url = new DigestURI(MultiProtocolURI.unescape(urlString));
            urlHash = UTF8.String(url.hash());
            pre = post.getBoolean("pre", false);
        } catch (final MalformedURLException e) {}
        
        
        if (url == null) {
            prop.put("error", "1");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            prop.put("url", "");
            return prop;
        } else {
            prop.put("url", url.toNormalform(false, true));
        }

        // loading the resource content as byte array
        prop.put("error_incache", Cache.has(url) ? 1 : 0);
        
        Response response = null;
        try {
            response = sb.loader.load(sb.loader.request(url, true, false), authorized ? CrawlProfile.CacheStrategy.IFEXIST : CrawlProfile.CacheStrategy.CACHEONLY, Long.MAX_VALUE, true);
        } catch (IOException e) {
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
            prop.put("viewMode_url", url.toNormalform(false, true));
            
        } else if (viewMode.equals("iframeCache")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME_FROM_CACHE);
            final String ext = url.getFileExtension();
            if ("jpg.jpeg.png.gif".indexOf(ext) >= 0) {
                prop.put("viewMode_png", 1);
                prop.put("viewMode_png_url", url.toNormalform(false, true));
            } else {
                prop.put("viewMode_html", 1);
                prop.put("viewMode_html_url", url.toNormalform(false, true));
            }
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
                final String content = UTF8.String(document.getTextBytes());
                // content = wikiCode.replaceHTML(content); // added by Marc Nause
                prop.put("viewMode", VIEW_MODE_AS_PARSED_TEXT);
                prop.put("viewMode_title", document.dc_title());
                prop.put("viewMode_creator", document.dc_creator());
                prop.put("viewMode_subject", document.dc_subject(','));
                prop.put("viewMode_description", document.dc_description());
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
                String sentence, token;
                if (sentences != null) {
                    
                    // Search word highlighting
                    for (final StringBuilder s: sentences) {
                        sentence = s.toString();
                        Enumeration<String> tokens = null;
                        tokens = new WordTokenizer(new ByteArrayInputStream(UTF8.getBytes(sentence)), LibraryProvider.dymLib);
                        while (tokens.hasMoreElements()) {
                            token = tokens.nextElement();
                            if (token.length() > 0) {
                                prop.put("viewMode_words_" + i + "_nr", i + 1);
                                prop.put("viewMode_words_" + i + "_word", token);
                                prop.put("viewMode_words_" + i + "_dark", dark ? "1" : "0");
                                dark = !dark;
                                i++;
                            }
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
                
                final Map<MultiProtocolURI, ImageEntry> ts = document.getImages();
                final Iterator<ImageEntry> tsi = ts.values().iterator();
                ImageEntry entry;
                while (tsi.hasNext()) {
                    entry = tsi.next();
                    prop.put("viewMode_links_" + i + "_nr", i);
                    prop.put("viewMode_links_" + i + "_dark", dark ? "1" : "0");
                    prop.put("viewMode_links_" + i + "_type", "image");
                    prop.put("viewMode_links_" + i + "_text", (entry.alt().isEmpty()) ? "&nbsp;" : markup(wordArray, entry.alt()));
                    prop.put("viewMode_links_" + i + "_url", entry.url().toNormalform(false, true));
                    prop.put("viewMode_links_" + i + "_link", markup(wordArray, entry.url().toNormalform(false, true)));
                    if (entry.width() > 0 && entry.height() > 0)
                        prop.put("viewMode_links_" + i + "_attr", entry.width() + "x" + entry.height() + " Pixel");
                    else
                        prop.put("viewMode_links_" + i + "_attr", "unknown");
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
        prop.put("error_url", url.toNormalform(false, true));
        prop.put("error_hash", urlHash);
        prop.put("error_wordCount", wordCount);
        prop.putHTML("error_desc", (descr.isEmpty()) ? "&nbsp;" : descr);
        prop.putNum("error_size", size);
        prop.put("error_mimeTypeAvailable", (response.getMimeType() == null) ? "0" : "1");
        prop.put("error_mimeTypeAvailable_mimeType", response.getMimeType());
        return prop;
    }

    private static final String[] wordArray(String words) {
        String[] w = new String[0];
        if (words == null || words.length() == 0) return w;
        if (words.length() > 1 && words.charAt(0) == '[' && words.charAt(words.length() - 1) == ']') {
            words = words.substring(1, words.length() - 1);
        }
        try {
            words = URLDecoder.decode(words, "UTF-8");
            if (words.indexOf(' ') >= 0) return words.split(" ");
            if (words.indexOf(',') >= 0) return words.split(",");
            if (words.indexOf('+') >= 0) return words.split("\\+");
            w = new String[1];
            w[0] = words;
        } catch (final UnsupportedEncodingException e) {}
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
    
    private static int putMediaInfo(final serverObjects prop, final String[] wordArray, int c, final Map<MultiProtocolURI, String> media, final String name, boolean dark) {
        int i = 0;
        for (Map.Entry<MultiProtocolURI, String> entry : media.entrySet()) {
            prop.put("viewMode_links_" + c + "_nr", c);
            prop.put("viewMode_links_" + c + "_dark", ((dark) ? 1 : 0));
            prop.putHTML("viewMode_links_" + c + "_type", name);
            prop.put("viewMode_links_" + c + "_text", ((entry.getValue().isEmpty()) ? "&nbsp;" : markup(wordArray, entry.getValue()) ));
            prop.put("viewMode_links_" + c + "_link", markup(wordArray, entry.getKey().toNormalform(true, false)));
            prop.put("viewMode_links_" + c + "_url", entry.getKey().toNormalform(true, false));
            prop.put("viewMode_links_" + c + "_attr", "&nbsp;");
            dark = !dark;
            c++;
            i++;
        }
        return i;
    }
    
}
