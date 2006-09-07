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
import java.io.UnsupportedEncodingException;
import de.anomic.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.plasmaCrawlLURL.Entry;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ViewFile {

    public static final int VIEW_MODE_NO_TEXT = 0;
    public static final int VIEW_MODE_AS_PLAIN_TEXT = 1;
    public static final int VIEW_MODE_AS_PARSED_TEXT = 2;
    public static final int VIEW_MODE_AS_PARSED_SENTENCES = 3;
    public static final int VIEW_MODE_AS_IFRAME = 4;

    public static final String[] highlightingColors = new String[] {
        "255,255,100",
        "255,155,155",
        "0,255,0",
        "0,255,255",
        "204,153,0",
        "204,153,255"
    };

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {

        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard)env;     



        if (post.containsKey("words"))
            try {
                prop.put("error_words",URLEncoder.encode((String) post.get("words"), "UTF-8"));
            } catch (UnsupportedEncodingException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            if (post != null) {
                // getting the url hash from which the content should be loaded
                String urlHash = post.get("urlHash","");       
                if (urlHash.equals("")) {
                    prop.put("error",1);
                    prop.put("viewMode",VIEW_MODE_NO_TEXT);
                    return prop;
                }

                String viewMode = post.get("viewMode","sentences");

                // getting the urlEntry that belongs to the url hash
                Entry urlEntry = null;
                try {
                    urlEntry = sb.urlPool.loadedURL.load(urlHash, null);
                } catch (IOException e) {
                    prop.put("error",2);
                    prop.put("viewMode",VIEW_MODE_NO_TEXT);
                    return prop;
                }            

                // gettin the url that belongs to the entry
                URL url = urlEntry.url();
                if (url == null) {
                    prop.put("error",3);
                    prop.put("viewMode",VIEW_MODE_NO_TEXT);
                    return prop;
                }    

                // loading the resource content as byte array
                byte[] resource = null;
                IResourceInfo resInfo = null;
                String resMime = null;
                try {
                    // trying to load the resource body
                    resource = sb.cacheManager.loadResourceContent(url);

                    // if the resource body was not cached we try to load it from web
                    if (resource == null) {
                        plasmaHTCache.Entry entry = sb.snippetCache.loadResourceFromWeb(url, 5000);                 

                        if (entry != null) {
                            resInfo = entry.getDocumentInfo();
                            resource = sb.cacheManager.loadResourceContent(url);
                        }

                        if (resource == null) {
                            prop.put("error",4);
                            prop.put("viewMode",VIEW_MODE_NO_TEXT);
                            return prop;
                        } 
                    }

                    // try to load resource metadata
                    if (resInfo == null) {

                        // try to load the metadata from cache
                        try {
                            resInfo = sb.cacheManager.loadResourceInfo(urlEntry.url());
                        } catch (Exception e) { /* ignore this */}

                        // if the metadata where not cached try to load it from web
                        if (resInfo == null) {
                            String protocol = url.getProtocol();
                            if (!((protocol.equals("http") || protocol.equals("https")))) {
                                prop.put("error",6);
                                prop.put("viewMode",VIEW_MODE_NO_TEXT);
                                return prop;                                
                            }

                            httpHeader responseHeader = httpc.whead(url,url.getHost(),5000,null,null,sb.remoteProxyConfig);
                            if (responseHeader == null) {
                                prop.put("error",4);
                                prop.put("viewMode",VIEW_MODE_NO_TEXT);
                                return prop;
                            } 
                            resMime = responseHeader.mime();
                        }
                    } else {
                        resMime = resInfo.getMimeType();
                    }
                } catch (IOException e) {
                    if (url == null) {
                        prop.put("error",4);
                        prop.put("viewMode",VIEW_MODE_NO_TEXT);
                        return prop;
                    }   
                }    
                if (viewMode.equals("plain")) {                
                    String content = new String(resource);
                    content = content.replaceAll("<","&lt;")
                    .replaceAll(">","&gt;")
                    .replaceAll("\"","&quot;")
                    .replaceAll("\n","<br>")
                    .replaceAll("\t","&nbsp;&nbsp;&nbsp;&nbsp;");

                    prop.put("error",0);
                    prop.put("viewMode",VIEW_MODE_AS_PLAIN_TEXT);
                    prop.put("viewMode_plainText",content);                     
                } else if (viewMode.equals("parsed") || viewMode.equals("sentences") || viewMode.equals("iframe")) {
                    // parsing the resource content
                    plasmaParserDocument document = sb.snippetCache.parseDocument(url, resource,resInfo);
                    if (document == null) {
                        prop.put("error",5);
                        prop.put("viewMode",VIEW_MODE_NO_TEXT);
                        return prop;                
                    }
                    resMime = document.getMimeType();

                    if (viewMode.equals("parsed")) {
                        String content = new String(document.getText());
                        content = wikiCode.replaceHTML(content); //added by Marc Nause
                        content = content.replaceAll("\n","<br>")
                        .replaceAll("\t","&nbsp;&nbsp;&nbsp;&nbsp;");

                        prop.put("viewMode",VIEW_MODE_AS_PARSED_TEXT);
                        prop.put("viewMode_parsedText",content);
                    } else if (viewMode.equals("iframe")) {
                        prop.put("viewMode",VIEW_MODE_AS_IFRAME);
                        prop.put("viewMode_url",url.toString());
                    } else {
                        prop.put("viewMode",VIEW_MODE_AS_PARSED_SENTENCES);
                        String[] sentences = document.getSentences();

                        boolean dark = true;
                        for (int i=0; i < sentences.length; i++) {
                            String currentSentence = wikiCode.replaceHTML(sentences[i]);

                            // Search word highlighting
                            String words = post.get("words",null);
                            if (words != null) {
                                try {
                                    words = URLDecoder.decode(words,"UTF-8");
                                } catch (UnsupportedEncodingException e) {}

                                String[] wordArray = words.substring(1,words.length()-1).split(",");
                                for (int j=0; j < wordArray.length; j++) {
                                    String currentWord = wordArray[j].trim(); 
                                    currentSentence = currentSentence.replaceAll(currentWord,
                                            "<b style=\"color: black; background-color: rgb(" + highlightingColors[j%6] + ");\">" + currentWord + "</b>");
                                }
                            }

                            prop.put("viewMode_sentences_" + i + "_nr",Integer.toString(i+1)); 
                            prop.put("viewMode_sentences_" + i + "_text",currentSentence);   
                            prop.put("viewMode_sentences_" + i + "_dark",((dark) ? 1 : 0) ); dark=!dark;
                        }
                        prop.put("viewMode_sentences",sentences.length);

                    } 
                }
                prop.put("error",0);
                prop.put("error_url",url.toString());                
                prop.put("error_hash",urlHash);
                prop.put("error_wordCount",Integer.toString(urlEntry.wordCount()));
                prop.put("error_desc",urlEntry.descr());
                prop.put("error_size",urlEntry.size());
                prop.put("error_mimeType",resMime);
            }        

            return prop;
    }

}
