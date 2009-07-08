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

package de.anomic.search;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ImageEntry;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class ImageCollector {

    private final HashMap<String, ImageEntry> images;
    
    public ImageCollector(final long maxTime, final yacyURL url, final int depth, final boolean indexing) {
        final long start = System.currentTimeMillis();
        this.images = new HashMap<String, ImageEntry>();
        if (maxTime > 10) {
            Object[] resource = null;
            try {
                resource = plasmaSnippetCache.getResource(url, true, (int) maxTime, false, indexing);
            } catch (IOException e) {
                Log.logWarning("ViewImage", "cannot load: " + e.getMessage());
            }
            if (resource == null) return;
            final InputStream res = (InputStream) resource[0];
            final Long resLength = (Long) resource[1];
            if (res != null) {
                Document document = null;
                try {
                    // parse the document
                    document = plasmaSnippetCache.parseDocument(url, resLength.longValue(), res);
                } catch (final ParserException e) {
                    // parsing failed
                    Log.logWarning("ViewImage", "cannot parse: " + e.getMessage());
                } finally {
                    try { res.close(); } catch (final Exception e) {/* ignore this */}
                }
                if (document == null) return;
                
                // add the image links
                ContentScraper.addAllImages(this.images, document.getImages());

                // add also links from pages one step deeper, if depth > 0
                if (depth > 0) {
                    final Iterator<yacyURL> i = document.getHyperlinks().keySet().iterator();
                    String nexturlstring;
                    while (i.hasNext()) {
                        try {
                            nexturlstring = i.next().toNormalform(true, true);
                            addAll(new ImageCollector(DateFormatter.remainingTime(start, maxTime, 10), new yacyURL(nexturlstring, null), depth - 1, indexing));
                        } catch (final MalformedURLException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                document.close();
            }
        }
    }
    
    public void addAll(final ImageCollector m) {
        synchronized (m.images) {
            ContentScraper.addAllImages(this.images, m.images);
        }
    }
    
    public Iterator<ImageEntry> entries() {
        // returns htmlFilterImageEntry - Objects
        return images.values().iterator();
    }
    
}
