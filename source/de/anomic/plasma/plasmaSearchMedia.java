// plasmaSearchMedia.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created: 03.04.2006
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;

public final class plasmaSearchMedia {

    private HashSet ext;
    private TreeSet media;
    
    public plasmaSearchMedia(plasmaSnippetCache sc, String exts, URL url, int depth) {
        this(sc, extGen(exts), url, depth);
    }
    
    public plasmaSearchMedia(plasmaSnippetCache sc, HashSet exts, URL url, int depth) {
        this.ext = exts;
        this.media = new TreeSet();
        byte[] res = sc.getResource(url, true);
        if (res != null) {
            plasmaParserDocument document = sc.parseDocument(url, res);
            
            // add the media links
            Map ml = document.getMedialinks();
            Iterator i = ml.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                String nexturlstring = htmlFilterContentScraper.urlNormalform(null, (String) e.getKey());
                int p = nexturlstring.lastIndexOf(".");
                if ((p > 0) && (this.ext.contains(nexturlstring.substring(p + 1)))) {
                    try {
                        media.add(new Entry(new URL(nexturlstring), 0));
                    } catch (MalformedURLException e1) {}
                }
            }
            
            // add also links from pages one step deeper, if depth > 0
            if (depth > 0) {
                Map hl = document.getHyperlinks();
                i = hl.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry e = (Map.Entry) i.next();
                    String nexturlstring = htmlFilterContentScraper.urlNormalform(null, (String) e.getKey());
                    try {
                        addAll(new plasmaSearchMedia(sc, ext, new URL(nexturlstring), depth - 1));
                    } catch (MalformedURLException e2) {}
                }
            }
        }
    }
    
    public plasmaSearchMedia(plasmaSnippetCache sc, String exts, plasmaSearchResult sres) {
        this(sc, extGen(exts), sres);
    }
    
    public plasmaSearchMedia(plasmaSnippetCache sc, HashSet exts, plasmaSearchResult sres) {
        this.ext = exts;
        this.media = new TreeSet();
        plasmaCrawlLURL.Entry urlentry;
        while (sres.hasMoreElements()) {
            urlentry = sres.nextElement();
            addAll(new plasmaSearchMedia(sc, ext, urlentry.url(), 0));
        }
    }

    private static HashSet extGen(String ext) {
        ext.replaceAll(",", " ");
        String[] exts = ext.split(" ");
        HashSet s = new HashSet(exts.length);
        for (int i = 0; i < exts.length; i++) s.add(exts[i]);
        return s;
    }
    
    public void addAll(plasmaSearchMedia m) {
        this.media.addAll(m.media);
    }
    
    public Iterator entries() {
        // returns Entry-Objects
        return media.iterator();
    }
    
    public class Entry {

        private URL url;
        private int size, width, height;
        
        public Entry(URL url, int size) {
            this.url = url;
            this.size = size;
            this.width = -1;
            this.height = -1;
        }
        
        public Entry(URL url, int width, int height) {
            this.url = url;
            this.size = -1;
            this.width = width;
            this.height = height;
        }
        
        public URL url() {
            return this.url;
        }
        
        public int size() {
            return this.size;
        }
        
        public int width() {
            return this.width;
        }
        
        public int height() {
            return this.height;
        }
        
        public int hashCode() {
            if ((width > 0) && (height > 0))
                return (((width * height) >> 8) << 16) | (url.hashCode() & 0xFFFF);
            else
                return ((size >> 8) << 16) | (url.hashCode() & 0xFFFF);
        }
        
        public int compareTo(Object h) {
            // this is needed if this object is stored in a TreeSet
            assert (url != null);
            assert (h instanceof plasmaSearchMedia.Entry);
            int thc = this.hashCode();
            int ohc = ((plasmaSearchMedia.Entry) h).hashCode();
            if (thc < ohc) return -1;
            if (thc > ohc) return 1;
            return 0;
        }
    }
    
}
