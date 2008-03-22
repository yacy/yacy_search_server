//ViewFile.java
//-----------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

//last major change: 12.07.2004

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

//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.

//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

//you must compile this file with
//javac -classpath .:../Classes Status.java
//if the shell's current path is HTROOT

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.data.htmlTools;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class ViewFile {

    public static final int VIEW_MODE_NO_TEXT = 0;
    public static final int VIEW_MODE_AS_PLAIN_TEXT = 1;
    public static final int VIEW_MODE_AS_PARSED_TEXT = 2;
    public static final int VIEW_MODE_AS_PARSED_SENTENCES = 3;
    public static final int VIEW_MODE_AS_IFRAME = 4;
    public static final int VIEW_MODE_AS_LINKLIST = 5;
    
    private static final String HIGHLIGHT_CSS = "searchHighlight";
    private static final int MAX_HIGHLIGHTS = 6;

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {

        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard)env;     

        if (post != null && post.containsKey("words"))
            prop.putHTML("error_words", (String)post.get("words"));
        else {
            prop.put("error", "1");
            prop.put("viewmode", "0");    
            return prop;
        }

        String viewMode = post.get("viewMode","sentences");
        prop.put("error_vMode-" + viewMode, "1");
        
        yacyURL url = null;
        String descr = "";
        int wordCount = 0;
        int size = 0;
        boolean pre = false;
        
        // getting the url hash from which the content should be loaded
        String urlHash = post.get("urlHash","");
        if (urlHash.length() > 0) {
            // getting the urlEntry that belongs to the url hash
            indexURLEntry urlEntry = null;
            urlEntry = sb.wordIndex.loadedURL.load(urlHash, null, 0);
            if (urlEntry == null) {
                prop.put("error", "2");
                prop.put("viewMode",VIEW_MODE_NO_TEXT);
                return prop;
            }            
            
                // gettin the url that belongs to the entry
            indexURLEntry.Components comp = urlEntry.comp();
            if ((comp == null) || (comp.url() == null)) {
                prop.put("error", "3");
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            }
            url = comp.url();
            descr = comp.dc_title();
            urlEntry.wordCount();
            size = urlEntry.size();
            pre = urlEntry.flags().get(plasmaCondenser.flag_cat_indexof);
        }

        // alternatively, get the url simply from a url String
        // this can be used as a simple tool to test the text parser
        String urlString = post.get("url", "");
        if (urlString.length() > 0) try {
            // this call forces the peer to download  web pages
            // it is therefore protected by the admin password
            
            if (!sb.verifyAuthentication(header, false)) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }

            // define an url by post parameter
            url = new yacyURL(urlString, null);
            pre = post.get("pre", "false").equals("true");
        } catch (MalformedURLException e) {}
        
        
        if (url == null) {
            prop.put("error", "1");
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }

        // loading the resource content as byte array
        InputStream resource = null;
        long resourceLength = -1;
        IResourceInfo resInfo = null;
        String resMime = null;
        try {
            // trying to load the resource body
            resource = plasmaHTCache.getResourceContentStream(url);
            resourceLength = plasmaHTCache.getResourceContentLength(url);

            // if the resource body was not cached we try to load it from web
            if (resource == null) {
                plasmaHTCache.Entry entry = null;
                try {
                    entry = sb.crawlQueues.loadResourceFromWeb(url, 5000, false, true);
                } catch (Exception e) {
                    prop.put("error", "4");
                    prop.putHTML("error_errorText", e.getMessage());
                    prop.put("viewMode", VIEW_MODE_NO_TEXT);
                    return prop;
                }

                if (entry != null) {
                    resInfo = entry.getDocumentInfo();
                    resource = plasmaHTCache.getResourceContentStream(url);
                    resourceLength = plasmaHTCache.getResourceContentLength(url);
                }

                if (resource == null) {
                    prop.put("error", "4");
                    prop.put("error_errorText", "No resource available");
                    prop.put("viewMode", VIEW_MODE_NO_TEXT);
                    return prop;
                }
            }

            // try to load resource metadata
            if (resInfo == null) {

                // try to load the metadata from cache
                try {
                    resInfo = plasmaHTCache.loadResourceInfo(url);
                } catch (Exception e) {
                    /* ignore this */
                }

                // if the metadata was not cached try to load it from web
                if (resInfo == null) {
                    String protocol = url.getProtocol();
                    if (!((protocol.equals("http") || protocol.equals("https")))) {
                        prop.put("error", "6");
                        prop.put("viewMode", VIEW_MODE_NO_TEXT);
                        return prop;
                    }

                    httpHeader responseHeader = httpc.whead(url, url.getHost(), 5000, null, null, sb.remoteProxyConfig);
                    if (responseHeader == null) {
                        prop.put("error", "4");
                        prop.put("error_errorText", "Unable to load resource metadata.");
                        prop.put("viewMode", VIEW_MODE_NO_TEXT);
                        return prop;
                    }
                    try {
                        resInfo = plasmaHTCache.getResourceInfoFactory().buildResourceInfoObj(url, responseHeader);
                    } catch (Exception e) {
                        prop.put("error", "4");
                        prop.putHTML("error_errorText", e.getMessage());
                        prop.put("viewMode", VIEW_MODE_NO_TEXT);
                        return prop;
                    }
                    resMime = responseHeader.mime();
                }
            } else {
                resMime = resInfo.getMimeType();
            }
        } catch (IOException e) {
            if (resource != null)
                try {
                    resource.close();
                } catch (Exception ex) {
                    /* ignore this */
                }
            prop.put("error", "4");
            prop.putHTML("error_errorText", e.getMessage());
            prop.put("viewMode", VIEW_MODE_NO_TEXT);
            return prop;
        }
        
        String[] wordArray = wordArray(post.get("words", null));

        if (viewMode.equals("plain")) {

            // TODO: how to handle very large files here ?
            String content;
            try {
                content = new String(serverFileUtils.read(resource), "UTF-8");
            } catch (Exception e) {
                prop.put("error", "4");
                prop.putHTML("error_errorText", e.getMessage());
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            } finally {
                if (resource != null)
                    try {
                        resource.close();
                    } catch (Exception e) {
                        /* ignore this */
                    }
            }

            prop.put("error", "0");
            prop.put("viewMode", VIEW_MODE_AS_PLAIN_TEXT);
            prop.put("viewMode_plainText", markup(wordArray, content).replaceAll("\n", "<br />").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
            
        } else if (viewMode.equals("iframe")) {
            prop.put("viewMode", VIEW_MODE_AS_IFRAME);
            prop.put("viewMode_url", url.toNormalform(false, true));
            
        } else if (viewMode.equals("parsed") || viewMode.equals("sentences") || viewMode.equals("links")) {
            // parsing the resource content
            plasmaParserDocument document = null;
            try {
                document = plasmaSnippetCache.parseDocument(url, resourceLength, resource, resInfo);
                if (document == null) {
                    prop.put("error", "5");
                    prop.put("error_errorText", "Unknown error");
                    prop.put("viewMode", VIEW_MODE_NO_TEXT);
                    return prop;
                }
            } catch (ParserException e) {
                prop.put("error", "5");
                prop.putHTML("error_errorText", e.getMessage());
                prop.put("viewMode", VIEW_MODE_NO_TEXT);
                return prop;
            } finally {
                if (resource != null)
                    try {
                        resource.close();
                    } catch (Exception e) {
                        /* ignore this */
                    }
            }

            resMime = document.dc_format();
            
            if (viewMode.equals("parsed")) {
                String content = new String(document.getTextBytes());
                // content = wikiCode.replaceHTML(content); // added by Marc Nause

                prop.put("viewMode", VIEW_MODE_AS_PARSED_TEXT);
                prop.put("viewMode_parsedText", markup(wordArray, content).replaceAll("\n", "<br />").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
                
            } else if (viewMode.equals("sentences")) {
                prop.put("viewMode", VIEW_MODE_AS_PARSED_SENTENCES);
                final Iterator<StringBuffer> sentences = document.getSentences(pre);

                boolean dark = true;
                int i = 0;
                String sentence;
                if (sentences != null) {
                    
                    // Search word highlighting
                    while (sentences.hasNext()) {
                        sentence = sentences.next().toString();
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

            } else if (viewMode.equals("links")) {
                prop.put("viewMode", VIEW_MODE_AS_LINKLIST);
                boolean dark = true;
                int i = 0;
                i += putMediaInfo(prop, wordArray, i, document.getVideolinks(), "video", (i % 2 == 0));
                i += putMediaInfo(prop, wordArray, i, document.getAudiolinks(), "audio", (i % 2 == 0));
                dark = (i % 2 == 0);
                
                HashMap<String, htmlFilterImageEntry> ts = document.getImages();
                Iterator<htmlFilterImageEntry> tsi = ts.values().iterator();
                htmlFilterImageEntry entry;
                while (tsi.hasNext()) {
                    entry = tsi.next();
                    prop.put("viewMode_links_" + i + "_nr", i);
                    prop.put("viewMode_links_" + i + "_dark", dark ? "1" : "0");
                    prop.put("viewMode_links_" + i + "_type", "image");
                    prop.put("viewMode_links_" + i + "_text", markup(wordArray, entry.alt()));
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
                i += putMediaInfo(prop, wordArray, i, document.getHyperlinks(), "href", (i % 2 == 0));
                prop.put("viewMode_links", i);

            }
            if (document != null) document.close();
        }
        prop.put("error", "0");
        prop.put("error_url", url.toNormalform(false, true));
        prop.put("error_hash", urlHash);
        prop.put("error_wordCount", wordCount);
        prop.putHTML("error_desc", descr);
        prop.putNum("error_size", size);
        prop.put("error_mimeTypeAvailable", (resMime == null) ? "0" : "1");
        prop.put("error_mimeTypeAvailable_mimeType", resMime);
        return prop;
    }

    private static final String[] wordArray(String words) {
        String[] w = null;
        if (words != null) try {
            words = URLDecoder.decode(words, "UTF-8");
            w = words.substring(1, words.length() - 1).split(",");
            if (w.length == 0) return null;
        } catch (UnsupportedEncodingException e) {}
        return w;
    }
    
    private static final String markup(String[] wordArray, String message) {
        message = htmlTools.encodeUnicode2html(message, true);
        if (wordArray != null)
            for (int j = 0; j < wordArray.length; j++) {
                String currentWord = wordArray[j].trim();
                // TODO: replace upper-/lowercase words as well
                message = message.replaceAll(currentWord,
                                "<span class=\"" + HIGHLIGHT_CSS + ((j % MAX_HIGHLIGHTS) + 1) + "\">" +
                                currentWord + 
                                "</span>");
            }
        return message;
    }
    
    private static int putMediaInfo(serverObjects prop, String[] wordArray, int c, Map<yacyURL, String> media, String name, boolean dark) {
        Iterator<Map.Entry<yacyURL, String>> mi = media.entrySet().iterator();
        Map.Entry<yacyURL, String> entry;
        int i = 0;
        while (mi.hasNext()) {
            entry = mi.next();
            prop.put("viewMode_links_" + c + "_nr", c);
            prop.put("viewMode_links_" + c + "_dark", ((dark) ? 1 : 0));
            prop.putHTML("viewMode_links_" + c + "_type", name);
            prop.put("viewMode_links_" + c + "_text", markup(wordArray, (String) entry.getValue()));
            prop.put("viewMode_links_" + c + "_link", markup(wordArray, entry.getKey().toNormalform(true, false)));
            prop.put("viewMode_links_" + c + "_url", entry.getKey().toNormalform(true, false));
            prop.putHTML("viewMode_links_" + c + "_attr", "");
            dark = !dark;
            c++;
            i++;
        }
        return i;
    }
    
}
