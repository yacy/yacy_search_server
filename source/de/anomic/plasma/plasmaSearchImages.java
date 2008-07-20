// plasmaSearchImages.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created: 04.04.2006
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

package de.anomic.plasma;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverDate;
import de.anomic.yacy.yacyURL;

public final class plasmaSearchImages {

    private HashMap<String, htmlFilterImageEntry> images;
    
    public plasmaSearchImages(long maxTime, yacyURL url, int depth, boolean indexing) {
        long start = System.currentTimeMillis();
        this.images = new HashMap<String, htmlFilterImageEntry>();
        if (maxTime > 10) {
            Object[] resource = plasmaSnippetCache.getResource(url, true, (int) maxTime, false, indexing);
            InputStream res = (InputStream) resource[0];
            Long resLength = (Long) resource[1];
            if (res != null) {
                plasmaParserDocument document = null;
                try {
                    // parse the document
                    document = plasmaSnippetCache.parseDocument(url, resLength.longValue(), res);
                } catch (ParserException e) {
                    // parsing failed
                } finally {
                    try { res.close(); } catch (Exception e) {/* ignore this */}
                }
                if (document == null) return;
                
                // add the image links
                htmlFilterContentScraper.addAllImages(this.images, document.getImages());

                // add also links from pages one step deeper, if depth > 0
                if (depth > 0) {
                    Iterator<yacyURL> i = document.getHyperlinks().keySet().iterator();
                    String nexturlstring;
                    while (i.hasNext()) {
                        try {
                            nexturlstring = i.next().toNormalform(true, true);
                            addAll(new plasmaSearchImages(serverDate.remainingTime(start, maxTime, 10), new yacyURL(nexturlstring, null), depth - 1, indexing));
                        } catch (MalformedURLException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                document.close();
            }
        }
    }
    
    public void addAll(plasmaSearchImages m) {
        synchronized (m.images) {
            htmlFilterContentScraper.addAllImages(this.images, m.images);
        }
    }
    
    public Iterator<htmlFilterImageEntry> entries() {
        // returns htmlFilterImageEntry - Objects
        return images.values().iterator();
    }
    
}
