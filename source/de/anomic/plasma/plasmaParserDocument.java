//plasmaParserDocument.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//last major change: 24.04.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
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
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import de.anomic.htmlFilter.htmlFilterContentScraper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class plasmaParserDocument {
    
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
    plasmaCondenser condenser;
                    
    public plasmaParserDocument(URL location, String mimeType,
                    String keywords, String shortTitle, String longTitle,
                    String[] sections, String abstrct,
                    byte[] text, Map anchors, Map images) {
        this.location = location;
        this.mimeType = (mimeType==null)?"application/octet-stream":mimeType;
        this.keywords = (keywords==null)?"":keywords;
        this.shortTitle = (shortTitle==null)?"":shortTitle;
        this.longTitle = (longTitle==null)?"":longTitle;
        this.sections = (sections==null)?new String[0]:sections;
        this.abstrct = (abstrct==null)?"":abstrct;
        this.text = (text==null)?new byte[0]:text;
        this.anchors = (anchors==null)?new HashMap(0):anchors;
        this.images = (images==null)?new HashMap(0):images;
        this.hyperlinks = null;
        this.medialinks = null;
        this.emaillinks = null;
        this.condenser = null;
    }
    
    /*
    private String absolutePath(String relativePath) {
        try {
            return htmlFilterContentScraper.urlNormalform(location, relativePath);
        } catch (Exception e) {
            return "";
        }
    }
    */
    
    public String getMimeType() {
        return this.mimeType;
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
    
    public plasmaCondenser getCondenser() {
        if (condenser == null) try {
            condenser = new plasmaCondenser(new ByteArrayInputStream(getText()), 0, 0);
        } catch (IOException e) {}
        return condenser;
    }
    
    public String[] getSentences() {
        return getCondenser().sentences();
    }
    
    public String getKeywords() {
        return this.keywords;
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
        int extpos, qpos;
        String ext = null;
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
                    if (((qpos = url.indexOf("?")) >= 0) && (qpos > extpos)) {
                        ext = url.substring(extpos, qpos).toLowerCase();
                    } else {
			ext = url.substring(extpos).toLowerCase();
                    }
                    normal = htmlFilterContentScraper.urlNormalform(null, url);
                    if (normal != null) {
                        if (plasmaParser.mediaExtContains(ext.substring(1))) {
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
            normal = htmlFilterContentScraper.urlNormalform(null, url);
            if (normal != null) medialinks.put(normal, entry.getValue()); // avoid NullPointerException
        }
        expandHyperlinks();
    }
    
    
    public synchronized void expandHyperlinks() {
        // we add artificial hyperlinks to the hyperlink set that can be calculated from
        // given hyperlinks and imagelinks
        hyperlinks.putAll(plasmaParser.allReflinks(hyperlinks));
        hyperlinks.putAll(plasmaParser.allReflinks(medialinks));
        hyperlinks.putAll(plasmaParser.allSubpaths(hyperlinks));
        hyperlinks.putAll(plasmaParser.allSubpaths(medialinks));
    }


    
}
