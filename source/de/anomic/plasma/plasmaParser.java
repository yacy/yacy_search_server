// plasmaParser.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 12.04.2005
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


package de.anomic.plasma;

import java.io.*;
import java.net.*;
import java.util.*;
import de.anomic.server.*;
import de.anomic.htmlFilter.*;

public class plasmaParser {
    
    public static String mediaExt =
        "swf,wmv,jpg,jpeg,jpe,rm,mov,mpg,mpeg,mp3,asf,gif,png,avi,zip,rar," +
        "sit,hqx,img,dmg,tar,gz,ps,pdf,doc,xls,ppt,ram,bz2,arj";
    

    public plasmaParser(File parserDispatcherPropertyFile) {
        // this is only a dummy yet because we have only one parser...
        
    }
    
    public document parseSource(URL location, String mimeType, byte[] source) {
        // make a scraper and transformer
        htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
        OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
        try {
            hfos.write(source);
            return transformScraper(location, mimeType, scraper);
        } catch (IOException e) {
            return null;
        }
    }

    public document parseSource(URL location, String mimeType, File sourceFile) {
        // make a scraper and transformer
        htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
        OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
        try {
	    serverFileUtils.copy(sourceFile, hfos);
            return transformScraper(location, mimeType, scraper);
        } catch (IOException e) {
            return null;
        }
    }
    
    public document transformScraper(URL location, String mimeType, htmlFilterContentScraper scraper) {
        try {
            return new document(new URL(urlNormalform(location)),
                                mimeType, null, null, scraper.getHeadline(),
                                null, null,
                                scraper.getText(), scraper.getAnchors(), scraper.getImages());
        } catch (MalformedURLException e) {
            return null;
        }
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
    
    public class document {
        
        URL location;       // the source url
        String mimeType;    // mimeType as taken from http header
        String keywords;    // most resources provide a keyword field
        String shortTitle;  // a shortTitle mostly appears in the window header (border)
        String longTitle;   // the real title of the document, commonly h1-tags
        String[] sections;  // if present: more titles/headlines appearing in the document
        String abstrct;     // an abstract, if present: short content description
        byte[] text;        // the clear text, all that is visible
        Map anchors;        // all links embedded as clickeable entities (anchor tags)
        Map images;         // all visible pictures in document
        // the anchors and images - Maps are URL-to-EntityDescription mappings.
        // The EntityDescription appear either as visible text in anchors or as alternative
        // text in image tags.
        Map hyperlinks;
        Map medialinks;
        Map emaillinks;
                        
        public document(URL location, String mimeType,
                        String keywords, String shortTitle, String longTitle,
                        String[] sections, String abstrct,
                        byte[] text, Map anchors, Map images) {
            this.location = location;
            this.mimeType = mimeType;
            this.keywords = keywords;
            this.shortTitle = shortTitle;
            this.longTitle = longTitle;
            this.sections = sections;
            this.abstrct = abstrct;
            this.text = text;
            this.anchors = anchors;
            this.images = images;
            this.hyperlinks = null;
            this.medialinks = null;
            this.emaillinks = null;
        }
        
        private String absolutePath(String relativePath) {
            try {
                return urlNormalform(new URL(location, relativePath));
            } catch (Exception e) {
                return "";
            }
        }
        
        public String getMainShortTitle() {
            if (shortTitle != null) return shortTitle; else return longTitle;
        }
        
        public String getMainLongTitle() {
            if (longTitle != null) return longTitle; else return shortTitle;
        }
        
        public String[] getSectionTitles() {
            if (sections != null) return sections; else return new String[]{getMainLongTitle()};
        }

        public String getAbstract() {
            if (abstrct != null) return abstrct; else return getMainLongTitle();
        }
        
        public byte[] getText() {
            // returns only the clear (visible) text (not the source data)
            return text;
        }
        
        public Map getAnchors() {
            // returns all links embedded as anchors (clickeable entities)
            return anchors;
        }
        
        public Map getImages() {
            // returns all links enbedded as pictures (visible iin document)
            return images;
        }
        
        // the next three methods provide a calculated view on the getAnchors/getImages:
        
        public Map getHyperlinks() {
            // this is a subset of the getAnchor-set: only links to other hyperrefs
            if (hyperlinks == null) resortLinks();
            return hyperlinks;
        }
        
        public Map getMedialinks() {
            // this is partly subset of getAnchor and getImage: all non-hyperrefs
            if (medialinks == null) resortLinks();
            return medialinks;
        }
        
        public Map getEmaillinks() {
            // this is part of the getAnchor-set: only links to email addresses
            if (emaillinks == null) resortLinks();
            return emaillinks;
        }
        
        private synchronized void resortLinks() {
            Iterator i;
            String url;
            int extpos;
            String ext;
            i = anchors.entrySet().iterator();
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
            i = images.entrySet().iterator();
            String normal;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                url = (String) entry.getKey();
                normal = urlNormalform(url);
                if (normal != null) medialinks.put(normal, entry.getValue()); // avoid NullPointerException
            }
            expandHyperlinks();
        }
        
        
        public synchronized void expandHyperlinks() {
            // we add artificial hyperlinks to the hyperlink set that can be calculated from
            // given hyperlinks and imagelinks
            hyperlinks.putAll(allReflinks(hyperlinks));
            hyperlinks.putAll(allReflinks(medialinks));
            hyperlinks.putAll(allSubpaths(hyperlinks));
            hyperlinks.putAll(allSubpaths(medialinks));
        }
        
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
    
}
