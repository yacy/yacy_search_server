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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import de.anomic.server.serverFileUtils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.net.URL;

public class plasmaParserDocument {
    
    private URL location;       // the source url
    private String mimeType;    // mimeType as taken from http header
    private String charset;     // the charset of the document
    private String[] keywords;  // most resources provide a keyword field
    private String shortTitle;  // a shortTitle mostly appears in the window header (border)
    private String longTitle;   // the real title of the document, commonly h1-tags
    private String[] sections;  // if present: more titles/headlines appearing in the document
    private String abstrct;     // an abstract, if present: short content description
    private Object text;  // the clear text, all that is visible
    private Map anchors;        // all links embedded as clickeable entities (anchor tags)
    private TreeSet images;     // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    private Map hyperlinks, audiolinks, videolinks, applinks;
    private Map emaillinks;
    private boolean resorted;
    private InputStream textStream; 
                    
    public plasmaParserDocument(URL location, String mimeType, String charset,
                    String[] keywords, String shortTitle, String longTitle,
                    String[] sections, String abstrct,
                    byte[] text, Map anchors, TreeSet images) {
        this.location = location;
        this.mimeType = (mimeType==null)?"application/octet-stream":mimeType;
        this.charset = charset;
        this.keywords = (keywords==null) ? new String[0] : keywords;
        this.shortTitle = (shortTitle==null)?"":shortTitle;
        this.longTitle = (longTitle==null)?"":longTitle;
        this.sections = (sections==null)?new String[0]:sections;
        this.abstrct = (abstrct==null)?"":abstrct;
        this.text = (text==null)?new byte[0]:text;
        this.anchors = (anchors==null)?new HashMap(0):anchors;
        this.images = (images==null)?new TreeSet():images;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.emaillinks = null;
        this.resorted = false;
    }
    
    public plasmaParserDocument(URL location, String mimeType, String charset,
            String[] keywords, String shortTitle, String longTitle,
            String[] sections, String abstrct,
            File text, Map anchors, TreeSet images) {
        this.location = location;
        this.mimeType = (mimeType==null)?"application/octet-stream":mimeType;
        this.charset = charset;
        this.keywords = (keywords==null) ? new String[0] : keywords;
        this.shortTitle = (shortTitle==null)?"":shortTitle;
        this.longTitle = (longTitle==null)?"":longTitle;
        this.sections = (sections==null)?new String[0]:sections;
        this.abstrct = (abstrct==null)?"":abstrct;
        this.text = text;
        if (text != null) text.deleteOnExit();
        this.anchors = (anchors==null)?new HashMap(0):anchors;
        this.images = (images==null)?new TreeSet():images;
        this.hyperlinks = null;
        this.audiolinks = null;
        this.videolinks = null;
        this.applinks = null;
        this.emaillinks = null;
        this.resorted = false;
    }    

    public URL getLocation() {
        return this.location;
    }
    
    public String getMimeType() {
        return this.mimeType;
    }
    
    /**
     * @return the supposed charset of this document or <code>null</code> if unknown
     */
    public String getCharset() {
        return this.charset;
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
    
    public InputStream getText() {
        try {
            if (this.text == null) return null;

            if (this.text instanceof File) {
                this.textStream = new BufferedInputStream(new FileInputStream((File)this.text));
            } else if (this.text instanceof byte[]) {
                this.textStream =  new ByteArrayInputStream((byte[])this.text);
            }
            return this.textStream;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; 
    }
    
    public byte[] getTextBytes() {
        try {
            if (this.text == null) return new byte[0];

            if (this.text instanceof File) return serverFileUtils.read((File)this.text);
            else if (this.text instanceof byte[]) return (byte[])this.text;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];             
    }
    
    public long getTextLength() {
        if (this.text == null) return 0;
        if (this.text instanceof File) return ((File)this.text).length();
        else if (this.text instanceof byte[]) return ((byte[])this.text).length;
        
        return -1; 
    }
    
    public Enumeration getSentences(boolean pre) {
        if (this.text == null) return null;
        plasmaCondenser.sentencesFromInputStreamEnum e = plasmaCondenser.sentencesFromInputStream(getText(), this.charset);
        e.pre(pre);
        return e;
    }
    
    public String getKeywords(char separator) {
        // sort out doubles and empty words
        TreeSet hs = new TreeSet();
        String s;
        for (int i = 0; i < this.keywords.length; i++) {
            if (this.keywords[i] == null) continue;
            s = this.keywords[i].trim();
            if (s.length() > 0) hs.add(s.toLowerCase());
        }
        if (hs.size() == 0) return "";
        // generate a new list
        StringBuffer sb = new StringBuffer(this.keywords.length * 6);
        Iterator i = hs.iterator();
        while (i.hasNext()) sb.append((String) i.next()).append(separator);
        return sb.substring(0, sb.length() - 1);
    }
    
    public Map getAnchors() {
        // returns all links embedded as anchors (clickeable entities)
        // this is a url(String)/text(String) map
        return anchors;
    }
    
    
    // the next three methods provide a calculated view on the getAnchors/getImages:
    
    public Map getHyperlinks() {
        // this is a subset of the getAnchor-set: only links to other hyperrefs
        if (!resorted) resortLinks();
        return hyperlinks;
    }
    
    public Map getAudiolinks() {
        if (!resorted) resortLinks();
        return this.audiolinks;
    }
    
    public Map getVideolinks() {
        if (!resorted) resortLinks();
        return this.videolinks;
    }
    
    public TreeSet getImages() {
        // returns all links enbedded as pictures (visible in document)
        // this resturns a htmlFilterImageEntry collection
        if (!resorted) resortLinks();
        return images;
    }
    
    public Map getApplinks() {
        if (!resorted) resortLinks();
        return this.applinks;
    }
    
    public Map getEmaillinks() {
        // this is part of the getAnchor-set: only links to email addresses
        if (!resorted) resortLinks();
        return emaillinks;
    }
    
    private synchronized void resortLinks() {
        
        // extract hyperlinks, medialinks and emaillinks from anchorlinks
        Iterator i;
        URL url;
        String u;
        int extpos, qpos;
        String ext = null;
        i = anchors.entrySet().iterator();
        hyperlinks = new HashMap();
        videolinks = new HashMap();
        audiolinks = new HashMap();
        applinks   = new HashMap();
        emaillinks = new HashMap();
        TreeSet collectedImages = new TreeSet(); // this is a set that is collected now and joined later to the imagelinks
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            u = (String) entry.getKey();
            if ((u != null) && (u.startsWith("mailto:"))) {
                emaillinks.put(u.substring(7), entry.getValue());
            } else {
                extpos = u.lastIndexOf(".");
                if (extpos > 0) {
                    if (((qpos = u.indexOf("?")) >= 0) && (qpos > extpos)) {
                        ext = u.substring(extpos + 1, qpos).toLowerCase();
                    } else {
                        ext = u.substring(extpos + 1).toLowerCase();
                    }
                    try {
                        url = new URL(u);
                        u = url.toNormalform();
                        if (plasmaParser.mediaExtContains(ext)) {
                            // this is not a normal anchor, its a media link
                            if (plasmaParser.imageExtContains(ext)) {
                                collectedImages.add(new htmlFilterImageEntry(url, (String) entry.getValue(), -1, -1));
                            }
                            else if (plasmaParser.audioExtContains(ext)) audiolinks.put(u, entry.getValue());
                            else if (plasmaParser.videoExtContains(ext)) videolinks.put(u, entry.getValue());
                            else if (plasmaParser.appsExtContains(ext)) applinks.put(u, entry.getValue());
                        } else {
                            hyperlinks.put(u, entry.getValue());
                        }
                    } catch (MalformedURLException e1) {
                    }
                }
            }
        }
        
        // add image links that we collected from the anchors to the image map
        i = collectedImages.iterator();
        htmlFilterImageEntry iEntry;
        while (i.hasNext()) {
            iEntry = (htmlFilterImageEntry) i.next();
            if (!images.contains(iEntry)) images.add(iEntry);
        }
        
        // expand the hyperlinks:
        // we add artificial hyperlinks to the hyperlink set
        // that can be calculated from given hyperlinks and imagelinks
        hyperlinks.putAll(plasmaParser.allReflinks(hyperlinks.keySet()));
        hyperlinks.putAll(plasmaParser.allReflinks(images));
        hyperlinks.putAll(plasmaParser.allReflinks(audiolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allReflinks(videolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allReflinks(applinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(hyperlinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(images));
        hyperlinks.putAll(plasmaParser.allSubpaths(audiolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(videolinks.keySet()));
        hyperlinks.putAll(plasmaParser.allSubpaths(applinks.keySet()));
        
        // don't do this again
        this.resorted = true;
    }
    
    public void close() {
        // try close the output stream
        if (this.textStream != null) {
            try {
                this.textStream.close();
            } catch (Exception e) { 
                /* ignore this */
            } finally {
                this.textStream = null;
            }
        }
        
        // delete the temp file
        if ((this.text != null) && (this.text instanceof File)) {
            try { 
                ((File)this.text).delete(); 
            } catch (Exception e) {
                /* ignore this */
            } finally {
                this.text = null;
            }
        }        
    }
    
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }
    
}
