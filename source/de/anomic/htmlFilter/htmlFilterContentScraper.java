// htmlFilterContentScraper.java
// -----------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

import de.anomic.server.logging.serverLog;
import de.anomic.server.serverByteBuffer;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeSet;

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
    private HashMap images;
    private HashMap metas;
    private String title;
    //private String headline;
    private List[] headlines;
    private serverByteBuffer content;
    private URL root;

    public htmlFilterContentScraper(URL root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        this.root = root;
        this.anchors = new HashMap();
        this.images = new HashMap();
        this.metas = new HashMap();
        this.title = "";
        this.headlines = new ArrayList[4];
        for (int i = 0; i < 4; i++) headlines[i] = new ArrayList();
        this.content = new serverByteBuffer(1024);
    }

    public void scrapeText(byte[] newtext) {
//      System.out.println("SCRAPE: " + new String(newtext));
        if ((content.length() != 0) && (content.byteAt(content.length() - 1) != 32)) content.append(32);
        content.append(super.stripAll(new serverByteBuffer(newtext, newtext.length + 1)).trim()).append(32);
    }

    
    /*
    public static String urlNormalform(String us) {
        if (us == null) { return null; }
        if (us.length() == 0) { return null; }

        serverLog.logFiner("htmlFilter", "urlNormalform:  IN=" + us);
        
        // TODO: what about 
        // - case insensitive domain names
        // - chars that should be escaped in URLs

        // cutting of everything behind #
        int cpos = us.indexOf("#");
        if (cpos >= 0) { us = us.substring(0, cpos); }
        if (us.startsWith("https")) {
            if (us.endsWith(":443")) {
                us = us.substring(0, us.length() - 4);
                serverLog.logFinest("htmlFilter", "urlNormalform: :443=" + us);
            } else {
                cpos = us.indexOf(":443/");
                if (cpos >= 0) {
                    us = us.substring(0, cpos).concat(us.substring(cpos + 4));
                    serverLog.logFinest("htmlFilter", "urlNormalform: :443/=" + us);
                }
            }
        } else if (us.startsWith("http")) {
            if (us.endsWith(":80")) {
                us = us.substring(0, us.length() - 3);
                serverLog.logFinest("htmlFilter", "urlNormalform: :80=" + us);
            } else {
                cpos = us.indexOf(":80/");
                if (cpos >= 0) {
                    us = us.substring(0, cpos).concat(us.substring(cpos + 3));
                    serverLog.logFinest("htmlFilter", "urlNormalform: :80/=" + us);
                }
            } 
        }
        if (((us.endsWith("/")) && (us.lastIndexOf('/', us.length() - 2) < 8))) us = us.substring(0, us.length() - 1);
        serverLog.logFine("htmlFilter", "urlNormalform: OUT=" + us);        
        return us;
    }
    */

    public static String urlNormalform(URL url) {
        boolean defaultPort = false;
        // serverLog.logFinest("htmlFilter", "urlNormalform: '" + url.toString() + "'");
        if (url.getProtocol().equals("http")) {
            if (url.getPort() < 0 || url.getPort() == 80)  { defaultPort = true; }
        } else if (url.getProtocol().equals("ftp")) {
            if (url.getPort() < 0 || url.getPort() == 21)  { defaultPort = true; }
        } else if (url.getProtocol().equals("https")) {
            if (url.getPort() < 0 || url.getPort() == 443) { defaultPort = true; }
        }
        String path = url.getFile();

        // (this is different from previous normal forms where a '/' must not appear in root paths; here it must appear. Makes everything easier.)
        if (path.length() == 0 || path.charAt(0) != '/') { path = "/" + path; }

        Pattern pathPattern = Pattern.compile("(/[^/\\.]+/)[.]{2}(?=/)|/\\.(?=/)|/(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }

        if (defaultPort) { return url.getProtocol() + "://" + url.getHost().toLowerCase() + path; }
        return url.getProtocol() + "://" + url.getHost().toLowerCase() + ":" + url.getPort() + path;
    }

    public static String urlNormalform(URL baseURL, String us) {
        if (us == null || us.length() == 0) { return null; }
        try {
            if (baseURL == null) return urlNormalform(new URL(us));
            return urlNormalform(new URL(baseURL, us));
        } catch (MalformedURLException e) {
            serverLog.logSevere("urlNormalform", e.toString());
            return null;
        }
    }
    
    public static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    public static String[] urlComps(String normalizedURL) {
        return normalizedURL.toLowerCase().split(splitrex); // word components of the url
    }
    
    private String absolutePath(String relativePath) {
        try {
            return urlNormalform(new URL(root, relativePath));
        } catch (Exception e) {
            return "";
        }
    }

    public void scrapeTag0(String tagname, Properties tagopts) {
        if (tagname.equalsIgnoreCase("img")) images.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("alt",""));
        if (tagname.equalsIgnoreCase("base")) try {root = new URL(tagopts.getProperty("href", ""));} catch (MalformedURLException e) {}
        if (tagname.equalsIgnoreCase("frame")) anchors.put(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("name",""));
        if (tagname.equalsIgnoreCase("meta")) {
            String name = tagopts.getProperty("name", "");
            if (name.length() > 0) {
                metas.put(name.toLowerCase(), tagopts.getProperty("content",""));
                return;
            }
            name = tagopts.getProperty("http-equiv", "");
            if (name.length() > 0) {
                metas.put(name.toLowerCase(), tagopts.getProperty("content",""));
                return;
            }
        }
    }

    public void scrapeTag1(String tagname, Properties tagopts, byte[] text) {
//      System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + new String(text));
        if ((tagname.equalsIgnoreCase("a")) && (text.length < 2048)) anchors.put(absolutePath(tagopts.getProperty("href", "")), super.stripAll(new serverByteBuffer(text)).trim().toString());
        String h;
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverByteBuffer(text)).toString());
            if (h.length() > 0) headlines[0].add(h);
        }
        if ((tagname.equalsIgnoreCase("h2")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverByteBuffer(text)).toString());
            if (h.length() > 0) headlines[1].add(h);
        }
        if ((tagname.equalsIgnoreCase("h3")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverByteBuffer(text)).toString());
            if (h.length() > 0) headlines[2].add(h);
        }
        if ((tagname.equalsIgnoreCase("h4")) && (text.length < 1024)) {
            h = cleanLine(super.stripAll(new serverByteBuffer(text)).toString());
            if (h.length() > 0) headlines[3].add(h);
        }
        if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) title = cleanLine(super.stripAll(new serverByteBuffer(text)).toString());        
    }

    private static String cleanLine(String s) {
        // may contain too many funny symbols
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) < ' ') s = s.substring(0, i) + " " + s.substring(i + 1);

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
        // if there is one, return it
        if (title.length() > 0) return title;
        
        // othervise take any headline
        for (int i = 0; i < 4; i++) {
            if (headlines[i].size() > 0) return (String) headlines[i].get(0);
        }
        
        // take description tag
        String s = getDescription();
        if (s.length() > 0) return s;
        
        // extract headline from content
        if (content.length() > 80) return cleanLine(new String(content.getBytes(), 0, 80));
        return cleanLine(content.trim().toString());
    }
    
    public String[] getHeadlines(int i) {
        assert ((i >= 1) && (i <= 4));
        String[] s = new String[headlines[i - 1].size()];
        for (int j = 0; j < headlines[i - 1].size(); j++) s[j] = (String) headlines[i - 1].get(j);
        return s;
    }

    public byte[] getText() {
       return content.getBytes();
    }

    public Map getAnchors() {
        return anchors;
    }

    public Map getImages() {
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
    
    public String getCopyright() {
        String s = (String) metas.get("copyright");
        if (s == null) return ""; else return s;
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
        System.out.println("TEXT     :" + new String(content.getBytes()));
    }

/*
    public static void main(String[] args) {  
        try {
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(new URL("http://localhost"));
            scraper.scrapeText(test.getBytes());
            System.out.println(new String(scraper.getText()));
        } catch (MalformedURLException e) {}
    }
*/
}