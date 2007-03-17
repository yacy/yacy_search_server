// htmlFilterContentScraper.java
// -----------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// Contains contributions by Marc Nause [MN]
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.htmlFilter;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import javax.swing.event.EventListenerList;

import de.anomic.net.URL;
import de.anomic.server.serverCharBuffer;

public class htmlFilterContentScraper extends htmlFilterAbstractScraper implements htmlFilterScraper {

    // statics: for initialisation of the HTMLFilterAbstractScraper
    private static TreeSet linkTags0;
    private static TreeSet linkTags1;

    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    static {
        linkTags0 = new TreeSet(insensitiveCollator);
        linkTags0.add("img");
        linkTags0.add("base");
        linkTags0.add("frame");
        linkTags0.add("meta");
        linkTags0.add("area");
        linkTags0.add("link");
        linkTags0.add("embed");     //added by [MN]
        linkTags0.add("param");     //added by [MN]

        linkTags1 = new TreeSet(insensitiveCollator);
        linkTags1.add("a");
        linkTags1.add("h1");
        linkTags1.add("h2");
        linkTags1.add("h3");
        linkTags1.add("h4");
        linkTags1.add("title");
    }

    // class variables: collectors for links
    private HashMap anchors;
    private TreeSet images; // String(absolute url)/ImageEntry relation
    private HashMap metas;
    private String title;
    //private String headline;
    private List[] headlines;
    private serverCharBuffer content;
    private EventListenerList htmlFilterEventListeners = new EventListenerList();
    
    private URL root;

    public htmlFilterContentScraper(URL root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        this.root = root;
        this.anchors = new HashMap();
        this.images = new TreeSet();
        this.metas = new HashMap();
        this.title = "";
        this.headlines = new ArrayList[4];
        for (int i = 0; i < 4; i++) headlines[i] = new ArrayList();
        this.content = new serverCharBuffer(1024);
    }
    
    public final static boolean punctuation(char c) {
        return (c == '.') || (c == '!') || (c == '?');
    }
    
    public void scrapeText(char[] newtext, String insideTag) {
        // System.out.println("SCRAPE: " + new String(newtext));
        serverCharBuffer b = super.stripAll(new serverCharBuffer(newtext, newtext.length + 1)).trim();
        if ((insideTag != null) && (!(insideTag.equals("a")))) {
            // texts inside tags sometimes have no punctuation at the line end
            // this is bad for the text sematics, because it is not possible for the
            // condenser to distinguish headlines from text beginnings.
            // to make it easier for the condenser, a dot ('.') is appended in case that
            // no punctuation is part of the newtext line
            if ((b.length() != 0) && (!(punctuation(b.charAt(b.length() - 1))))) b.append((int) '.');
            //System.out.println("*** Appended dot: " + b.toString());
        }
        if (b.length() != 0) content.append(b).append((int) 32);
    }

    public static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    public static String[] urlComps(String normalizedURL) {
        int p = normalizedURL.indexOf("//");
        if (p > 0) normalizedURL = normalizedURL.substring(p + 2);
        return normalizedURL.toLowerCase().split(splitrex); // word components of the url
    }
    
    private String absolutePath(String relativePath) {
        try {
            return new URL(root, relativePath).toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void scrapeTag0(String tagname, Properties tagopts) {
        if (tagname.equalsIgnoreCase("img")) {
            int width = -1, height = -1;
            try {
                width = Integer.parseInt(tagopts.getProperty("width", "-1"));
                height = Integer.parseInt(tagopts.getProperty("height", "-1"));
            } catch (NumberFormatException e) {}
            try {
                URL url = new URL(absolutePath(tagopts.getProperty("src", "")));
                htmlFilterImageEntry ie = new htmlFilterImageEntry(url, tagopts.getProperty("alt",""), width, height);
                images.add(ie);
            } catch (MalformedURLException e) {}
        }
        if (tagname.equalsIgnoreCase("base")) try {
            root = new URL(tagopts.getProperty("href", ""));
        } catch (MalformedURLException e) {}
        if (tagname.equalsIgnoreCase("frame")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("name",""));
        }
        if (tagname.equalsIgnoreCase("meta")) {
            String name = tagopts.getProperty("name", "");
            if (name.length() > 0) {
                metas.put(name.toLowerCase(), tagopts.getProperty("content",""));
            } else {
                name = tagopts.getProperty("http-equiv", "");
                if (name.length() > 0) {
                    metas.put(name.toLowerCase(), tagopts.getProperty("content",""));
                }
            }
        }
        if (tagname.equalsIgnoreCase("area")) {
            String areatitle = cleanLine(tagopts.getProperty("title",""));
            //String alt   = tagopts.getProperty("alt","");
            String href  = tagopts.getProperty("href", "");
            if (href.length() > 0) anchors.put(absolutePath(href), areatitle);
        }
        if (tagname.equalsIgnoreCase("link")) {
            URL newLink = null;
            try {
                newLink = new URL(absolutePath(tagopts.getProperty("href", "")));
            } catch (MalformedURLException e) {}

            if (newLink != null) {
                String type = tagopts.getProperty("rel", "");
                String linktitle = tagopts.getProperty("title", "");

                if (type.equalsIgnoreCase("shortcut icon")) {
                    htmlFilterImageEntry ie = new htmlFilterImageEntry(newLink, linktitle, -1,-1);
                    images.add(ie);                
                } else if (!type.equalsIgnoreCase("stylesheet") && !type.equalsIgnoreCase("alternate stylesheet")) {
                    anchors.put(newLink.toString(), linktitle);                    
                }
            }
        }
        //start contrib [MN]
        if (tagname.equalsIgnoreCase("embed")) {
            anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("name",""));
        }
        if (tagname.equalsIgnoreCase("param")) {
            String name = tagopts.getProperty("name", "");
            if (name.equalsIgnoreCase("movie")) {
                anchors.put(absolutePath(tagopts.getProperty("value", "")),name);
            }
        }
        //end contrib [MN]

        // fire event
        fireScrapeTag0(tagname, tagopts);
    }

    public void scrapeTag1(String tagname, Properties tagopts, char[] text) {
        // System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + new String(text));
        if ((tagname.equalsIgnoreCase("a")) && (text.length < 2048)) {
            String href = tagopts.getProperty("href", "");
            if (href.length() > 0) anchors.put(absolutePath(href), super.stripAll(new serverCharBuffer(text)).trim().toString());
        }
        String h;
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverCharBuffer(text)).toString());
            if (h.length() > 0) headlines[0].add(h);
        }
        if ((tagname.equalsIgnoreCase("h2")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverCharBuffer(text)).toString());
            if (h.length() > 0) headlines[1].add(h);
        }
        if ((tagname.equalsIgnoreCase("h3")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverCharBuffer(text)).toString());
            if (h.length() > 0) headlines[2].add(h);
        }
        if ((tagname.equalsIgnoreCase("h4")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverCharBuffer(text)).toString());
            if (h.length() > 0) headlines[3].add(h);
        }
        if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) {
            title = cleanLine(super.stripAll(new serverCharBuffer(text)).toString());
        }

        // fire event
        fireScrapeTag1(tagname, tagopts, text);
    }

    private static String cleanLine(String s) {
        /*
        // may contain too many funny symbols
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) < ' ') s = s.substring(0, i) + " " + s.substring(i + 1);
        */
        
        // remove double-spaces
        int p;
        while ((p = s.indexOf("  ")) >= 0) s = s.substring(0, p) + s.substring(p + 1);        

        // we don't accept headlines that are too short
        s = s.trim();
        if (s.length() < 4) s = "";
        
        // return result
        return s;
    }
    
    public String getTitle() {
        // construct a title string, even if the document has no title
        
        // some documents have a title tag as meta tag
        String s = (String) metas.get("title");
        
        // try to construct the title with the content of the title tag
        if (title.length() > 0) {
            if (s == null) {
                return title;
            } else {
                if ((title.compareToIgnoreCase(s) == 0) || (title.indexOf(s) >= 0)) return s; else return title + ": " + s;
            }
        } else {
            if (s != null) {
                return s;
            }
        }
        
        // othervise take any headline
        for (int i = 0; i < 4; i++) {
            if (headlines[i].size() > 0) return (String) headlines[i].get(0);
        }
        
        // take description tag
        s = getDescription();
        if (s.length() > 0) return s;
        
        // extract headline from content
        if (content.length() > 80) {
            return cleanLine(new String(content.getChars(), 0, 80));
        }
        return cleanLine(content.trim().toString());
    }
    
    public String[] getHeadlines(int i) {
        assert ((i >= 1) && (i <= 4));
        String[] s = new String[headlines[i - 1].size()];
        for (int j = 0; j < headlines[i - 1].size(); j++) s[j] = (String) headlines[i - 1].get(j);
        return s;
    }
    
    public byte[] getText() {
        return this.getText("UTF-8");
    }
    
    public byte[] getText(String charSet) {
        try {
            return content.toString().getBytes(charSet);
        } catch (UnsupportedEncodingException e) {
            return content.toString().getBytes();
        }
    }

    public Map getAnchors() {
        return anchors;
    }

    public TreeSet getImages() {
        // this resturns a String(absolute url)/htmlFilterImageEntry - relation
        return images;
    }

    public Map getMetas() {
        return metas;
    }

    public String getDescription() {
        String s = (String) metas.get("description");
        if (s == null) return ""; else return s;
    }
    
    public String getContentType() {
        String s = (String) metas.get("content-type");
        if (s == null) return ""; else return s;
    }
    
    public String getAuthor() {
        String s = (String) metas.get("author");
        if (s == null) s = (String) metas.get("copyright");
        if (s == null) return "";
        return s;
    }
    
    public String[] getContentLanguages() {
        String s = (String) metas.get("content-language");
        if (s == null) s = "";
        return s.split(" |,");
    }
    
    public String[] getKeywords() {
        String s = (String) metas.get("keywords");
        if (s == null) s = "";
        if (s.length() == 0) {
            return getTitle().toLowerCase().split(splitrex);
        } else {
            return s.split(" |,");
        }
    }
    
    public int getRefreshSeconds() {
        String s = (String) metas.get("refresh");
        if (s == null) return 9999; else try {
            int pos = s.indexOf(';');
            if (pos < 0) return 9999;
            int i = Integer.parseInt(s.substring(0, pos));
            return i;
        } catch (NumberFormatException e) {
            return 9999;
        }
    }

    public String getRefreshPath() {
        String s = (String) metas.get("refresh");
        if (s == null) return ""; else {
            int pos = s.indexOf(';');
            if (pos < 0) return "";
            s = s.substring(pos + 1);
            if (s.toLowerCase().startsWith("url=")) return s.substring(4).trim(); else return "";
        }
    }

    /*
     *  (non-Javadoc)
     * @see de.anomic.htmlFilter.htmlFilterScraper#close()
     */
    public void close() {
        // free resources
        super.close();
        anchors = null;
        images = null;
        title = null;
        headlines = null;
        content = null;
        root = null;
    }

    public void print() {
        System.out.println("TITLE    :" + title);
        for (int i = 0; i < 4; i++) {
            System.out.println("HEADLINE" + i + ":" + headlines[i].toString());
        }
        System.out.println("ANCHORS  :" + anchors.toString());
        System.out.println("IMAGES   :" + images.toString());
        System.out.println("METAS    :" + metas.toString());
        System.out.println("TEXT     :" + content.toString());
    }

    public void registerHtmlFilterEventListener(htmlFilterEventListener listener) {
        if (listener != null) {
            this.htmlFilterEventListeners.add(htmlFilterEventListener.class, listener);
        }        
    }

    public void deregisterHtmlFilterEventListener(htmlFilterEventListener listener) {
        if (listener != null) {
            this.htmlFilterEventListeners.remove(htmlFilterEventListener.class, listener);
        }        
    }
    
    void fireScrapeTag0(String tagname, Properties tagopts) {
        Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]==htmlFilterEventListener.class) {
                    ((htmlFilterEventListener)listeners[i+1]).scrapeTag0(tagname, tagopts);
            }
        }
    }    
    
    void fireScrapeTag1(String tagname, Properties tagopts, char[] text) {
        Object[] listeners = this.htmlFilterEventListeners.getListenerList();
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]==htmlFilterEventListener.class) {
                    ((htmlFilterEventListener)listeners[i+1]).scrapeTag1(tagname, tagopts, text);
            }
        }
    }      
}