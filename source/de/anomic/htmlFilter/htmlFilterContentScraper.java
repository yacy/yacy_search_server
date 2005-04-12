// htmlFilterContentScraper.java 
// -----------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 18.02.2004
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

import java.net.*;
import java.util.*;
import de.anomic.server.*;


public class htmlFilterContentScraper extends htmlFilterAbstractScraper implements htmlFilterScraper {


    // statics: for initialisation of the HTMLFilterAbstractTransformer
    private static HashSet linkTags0;
    private static HashSet linkTags1;

    public static String mediaExt =
        "swf,wmv,jpg,jpeg,jpe,rm,mov,mpg,mpeg,mp3,asf,gif,png,avi,zip,rar," +
        "sit,hqx,img,dmg,tar,gz,ps,pdf,doc,xls,ppt,ram,bz2,arj";
    
    static {
	linkTags0 = new HashSet();
	linkTags0.add("img");

	linkTags1 = new HashSet();
	linkTags1.add("a");
	linkTags1.add("h1");
	linkTags1.add("title");
    }

    // class variables: collectors for links
    private Properties anchor;
    private Properties image;
    private String title;
    private String headline;
    private serverByteBuffer text;
    private URL root;

    public htmlFilterContentScraper(URL root) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
	super(linkTags0, linkTags1);
	this.root = root;
	this.anchor = new Properties();
	this.image = new Properties();
	this.title = "";
	this.headline = "";
	this.text = new serverByteBuffer();
    }


    public void scrapeText(byte[] newtext) {
	//System.out.println("SCRAPE: " + new String(newtext));
	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
	text.append(new serverByteBuffer(super.stripAll(new serverByteBuffer(newtext))).trim()).append((byte) ' ');
    }

    public static String urlNormalform(URL url) {
        if (url == null) return null;
        return urlNormalform(url.toString());
    }
    
    public static String urlNormalform(String us) {
        if (us == null) return null;
        if (us.length() == 0) return null;
        int p;
        if ((p = us.indexOf("#")) >= 0) us = us.substring(0, p);
        if (us.endsWith(":80")) us = us.substring(0, us.length() - 3);
        if (((us.endsWith("/")) && (us.lastIndexOf('/', us.length() - 2) < 8))) us = us.substring(0, us.length() - 1);
        return us;
    }        
    
    private String absolutePath(String relativePath) {
	try {
	    return urlNormalform(new URL(root, relativePath));
	} catch (Exception e) {
	    return "";
	}
    }

    public void scrapeTag0(String tagname, Properties tagopts) {
	if (tagname.equals("img")) image.setProperty(absolutePath(tagopts.getProperty("src", "")), tagopts.getProperty("alt",""));
    }

    public void scrapeTag1(String tagname, Properties tagopts, byte[] text) {
	//System.out.println("ScrapeTag1: tagname=" + tagname + ", opts=" + tagopts.toString() + ", text=" + new String(text));
	if (tagname.equals("a")) anchor.setProperty(absolutePath(tagopts.getProperty("href", "")),
						    new serverByteBuffer(super.stripAll(new serverByteBuffer(text)).getBytes()).trim().toString());
	if (tagname.equals("h1")) headline = new String(super.stripAll(new serverByteBuffer(text)).getBytes());
	if (tagname.equals("title")) title = new String(super.stripAll(new serverByteBuffer(text)).getBytes());
    }


    public String getHeadline() {
	String hl = "";

        // extract headline from content
	if (title.length() > 0) hl = title.trim();
	else if (headline.length() > 0) hl = headline.trim();
	else if (text.length() > 80) hl = new String(text.getBytes(), 0, 80).trim();
	else hl = text.toString().trim();

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
	return text.getBytes();
    }
    
    public Properties getAnchor() {
	return anchor;
    }

    public Properties getImage() {
	return image;
    }

    public Map getHyperlinks() {
	if (hyperlinks == null) resortLinks();
	return hyperlinks;
    }

    public Map getMedialinks() {
	if (medialinks == null) resortLinks();
	return medialinks;
    }

    public Map getEmaillinks() {
	if (emaillinks == null) resortLinks();
	return emaillinks;
    }

    HashMap hyperlinks = null;
    HashMap medialinks = null;
    HashMap emaillinks = null;

            private synchronized void resortLinks() {
            Iterator i;
            String url;
            int extpos;
            String ext;
            i = anchor.entrySet().iterator();
            hyperlinks = new HashMap();
            medialinks = new HashMap();
            emaillinks = new HashMap();
            Map.Entry entry;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                url = (String) entry.getKey();
                if ((url != null) && (url.startsWith("mailto:"))) {
                    emaillinks.put(url.substring(7), entry.getValue());
                } else {
                    extpos = url.lastIndexOf(".");
                    String normal;
                    if (extpos > 0) {
                        ext = url.substring(extpos).toLowerCase();
                        normal = urlNormalform(url);
                        if (normal != null) {
                            if (mediaExt.indexOf(ext.substring(1)) >= 0) {
                                // this is not an normal anchor, its a media link
                                medialinks.put(normal, entry.getValue());
                            } else {
                                hyperlinks.put(normal, entry.getValue());
                            }
                        }
                    }
                }
            }
            // finally add the images to the medialinks
            i = image.entrySet().iterator();
            String normal;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                url = (String) entry.getKey();
                normal = urlNormalform(url);
                if (normal != null) medialinks.put(normal, entry.getValue()); // avoid NullPointerException
            }
            expandHyperlinks();
        }
        
            /*
    private synchronized void resortLinks() {
	Enumeration e;
	String url;
	int extpos;
	String ext;
	e = anchor.propertyNames();
	hyperlinks = new Properties();
	medialinks = new Properties();
	emaillinks = new Properties();
	while (e.hasMoreElements()) {
	    url = (String) e.nextElement();
	    if ((url != null) && (url.startsWith("mailto:"))) {
		emaillinks.setProperty(url.substring(7), anchor.getProperty(url));
	    } else {
		extpos = url.lastIndexOf(".");
		String normal;
		if (extpos > 0) {
		    ext = url.substring(extpos).toLowerCase();
		    normal = urlNormalform(url);
		    if (normal != null) {
			if (mediaExt.indexOf(ext.substring(1)) >= 0) {
			    // this is not an normal anchor, its a media link
			    medialinks.setProperty(normal, anchor.getProperty(url));
			} else {
			    hyperlinks.setProperty(normal, anchor.getProperty(url));
			}
		    }
		}
	    }
	}
	// finally add the images to the medialinks
	e = image.propertyNames();
	String normal;
	while (e.hasMoreElements()) {
	    url = (String) e.nextElement();
	    normal = urlNormalform(url);
	    if (normal != null) medialinks.setProperty(normal, image.getProperty(url)); // avoid NullPointerException
	}
    }
*/

    public synchronized void expandHyperlinks() {
	// we add artificial hyperlinks to the hyperlink set that can be calculated from
	// given hyperlinks and imagelinks
	hyperlinks.putAll(allReflinks(hyperlinks));
	hyperlinks.putAll(allReflinks(medialinks));
	hyperlinks.putAll(allSubpaths(hyperlinks));
	hyperlinks.putAll(allSubpaths(medialinks));
    }

    private static Map allReflinks(Map links) {
	// we find all links that are part of a reference inside a url
	HashMap v = new HashMap();
	Iterator i = links.keySet().iterator();
	String s;
	int pos;
	loop: while (i.hasNext()) {
	    s = (String) i.next();
	    if ((pos = s.toLowerCase().indexOf("http://",7)) > 0) {
		i.remove();
		s = s.substring(pos);
		while ((pos = s.toLowerCase().indexOf("http://",7)) > 0) s = s.substring(pos);
		if (!(v.containsKey(s))) v.put(s, "ref");
		continue loop;
	    }
	    if ((pos = s.toLowerCase().indexOf("/www.",7)) > 0) {
		i.remove();
		s = "http:/" + s.substring(pos);
		while ((pos = s.toLowerCase().indexOf("/www.",7)) > 0) s = "http:/" + s.substring(pos);
		if (!(v.containsKey(s))) v.put(s, "ref");
		continue loop;
	    }
	}
	return v;
    }

    private static Map allSubpaths(Map links) {
	HashMap v = new HashMap();
	Iterator i = links.keySet().iterator();
	String s;
	int pos;
	while (i.hasNext()) {
	    s = (String) i.next();
	    if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
	    pos = s.lastIndexOf("/");
	    while (pos > 8) {
		s = s.substring(0, pos + 1);
		if (!(v.containsKey(s))) v.put(s, "sub");
		s = s.substring(0, pos);
		pos = s.lastIndexOf("/");
	    }
	}
	return v;
    }


    public void print() {
	System.out.println("TITLE   :" + title);
	System.out.println("HEADLINE:" + headline);
	System.out.println("ANCHORS :" + anchor.toString());
	System.out.println("IMAGES  :" + image.toString());
	System.out.println("TEXT    :" + new String(text.getBytes()));
    }

}
