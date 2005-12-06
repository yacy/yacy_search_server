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

import java.net.MalformedURLException;
import java.net.URL;
import java.text.Collator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.server.logging.serverLog;
import de.anomic.server.serverByteBuffer;

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

        linkTags1 = new TreeSet(insensitiveCollator);
        linkTags1.add("a");
        linkTags1.add("h1");
        linkTags1.add("title");
    }

    // class variables: collectors for links
    private HashMap anchors;
    private HashMap images;
    private String title;
    private String headline;
    private serverByteBuffer content;
    private URL root;

    public htmlFilterContentScraper(URL root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super(linkTags0, linkTags1);
        this.root = root;
        this.anchors = new HashMap();
        this.images = new HashMap();
        this.title = "";
        this.headline = "";
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
        //serverLog.logFinest("htmlFilter", "urlNormalform: '" + url.toString() + "'");
        if (url.getProtocol().equals("http")) {
            if (url.getPort() < 0 || url.getPort() == 80)  { defaultPort = true; }
        } else if (url.getProtocol().equals("ftp")) {
            if (url.getPort() < 0 || url.getPort() == 21)  { defaultPort = true; }
        } else if (url.getProtocol().equals("https")) {
            if (url.getPort() < 0 || url.getPort() == 443) { defaultPort = true; }
        }
        String path = url.getFile();
        if ((path.length() == 0) || (path.charAt(0) != '/')) path = "/" + path;
        // (this is different from previous normal forms where a '/' must not appear in root paths; here it must appear. Makes everything easier.)
        int cpos = path.indexOf("#");
        if (cpos >= 0) path = path.substring(0, cpos);
        
        Pattern pathPattern = Pattern.compile("(/[^/\\.]+/)(?<!/[.]{2}/)[.]{2}(?=/)|/\\.(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }  
        
        if (defaultPort) return url.getProtocol() + "://" + url.getHost() + path;
        return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + path;
    }
    
    public static String urlNormalform(URL baseURL, String us) {
        if (us == null) { return null; }
        if (us.length() == 0) { return null; }
        try {
            if (baseURL == null) return urlNormalform(new URL(us));
            return urlNormalform(new URL(baseURL, us));
        } catch (MalformedURLException e) {
            serverLog.logSevere("urlNormalform", e.toString());
            return null;
        }
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
    }

    public void scrapeTag1(String tagname, Properties tagopts, byte[] text) {
//      System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + new String(text));
        if ((tagname.equalsIgnoreCase("a")) && (text.length < 2048)) anchors.put(absolutePath(tagopts.getProperty("href", "")), super.stripAll(new serverByteBuffer(text)).trim().toString());
        if ((tagname.equalsIgnoreCase("h1")) && (text.length < 1024)) headline = super.stripAll(new serverByteBuffer(text)).toString();
        if ((tagname.equalsIgnoreCase("title")) && (text.length < 1024)) title = super.stripAll(new serverByteBuffer(text)).toString();        
    }

    public String getHeadline() {
        String hl = "";

        // extract headline from content
        if (title.length() > 0) hl = title.trim();
        else if (headline.length() > 0) hl = headline.trim();
        else if (content.length() > 80) hl = new String(content.getBytes(), 0, 80).trim();
        else hl = content.trim().toString();

        // clean the line: may contain too many funny symbols
        for (int i = 0; i < hl.length(); i++)
            if (hl.charAt(i) < ' ') hl = hl.substring(0, i) + " " + hl.substring(i + 1);

        // clean the line: remove double-spaces
        int p;
        while ((p = hl.indexOf("  ")) >= 0) hl = hl.substring(0, p) + hl.substring(p + 1);        

        // return result
        return hl.trim();
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

    public void close() {
        // free resources
        super.close();
        anchors = null;
        images = null;
        title = null;
        headline = null;
        content = null;
        root = null;
    }

    public void print() {
    System.out.println("TITLE   :" + title);
    System.out.println("HEADLINE:" + headline);
    System.out.println("ANCHORS :" + anchors.toString());
    System.out.println("IMAGES  :" + images.toString());
    System.out.println("TEXT    :" + new String(content.getBytes()));
    }

    public static void main(String[] args) {
        /*
        try {
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(new URL("http://localhost"));
            scraper.scrapeText(test.getBytes());
            System.out.println(new String(scraper.getText()));
        } catch (MalformedURLException e) {}
         */
    }

}