// plasmaSearchImages.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.net.URL;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverDate;

public final class plasmaSearchImages {

    private TreeSet images;
    
    public plasmaSearchImages(plasmaSnippetCache sc, long maxTime, URL url, int depth) {
        long start = System.currentTimeMillis();
        this.images = new TreeSet();
        if (maxTime > 10) {
            Object[] resource = sc.getResource(url, true, (int) maxTime, false);
            InputStream res = (InputStream) resource[0];
            Long resLength = (Long) resource[1];
            if (res != null) {
                plasmaParserDocument document = null;
                try {
                    // parse the document
                    document = sc.parseDocument(url, resLength.longValue(), res);
                } catch (ParserException e) {
                    // parsing failed
                } finally {
                    try { res.close(); } catch (Exception e) {/* ignore this */}
                }
                if (document == null) return;
                
                // add the image links
                this.addAll(document.getImages());

                // add also links from pages one step deeper, if depth > 0
                if (depth > 0) {
                    Map hl = document.getHyperlinks();
                    Iterator i = hl.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry e = (Map.Entry) i.next();
                        String nexturlstring;
                        try {
                            nexturlstring = new URL((String) e.getKey()).toNormalform(true, true);
                            addAll(new plasmaSearchImages(sc, serverDate.remainingTime(start, maxTime, 10), new URL(nexturlstring), depth - 1));
                        } catch (MalformedURLException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                document.close();
            }
        }
    }
    
    public plasmaSearchImages(plasmaSnippetCache sc, long maxTime, plasmaSearchPostOrder sres, int depth) {
        long start = System.currentTimeMillis();
        this.images = new TreeSet();
        indexURLEntry urlentry;
        while (sres.hasMoreElements()) {
            urlentry = sres.nextElement();
            addAll(new plasmaSearchImages(sc, serverDate.remainingTime(start, maxTime, 10), urlentry.comp().url(), depth));
        }
    }
    
    public void addAll(plasmaSearchImages m) {
        synchronized (m.images) {
            addAll(m.images);
        }
    }
    
    private void addAll(TreeSet ts) {
        Iterator i = ts.iterator();
        htmlFilterImageEntry ie;
        while (i.hasNext()) {
            ie = (htmlFilterImageEntry) i.next();
            if (images.contains(ie)) {
                if ((ie.height() > 0) && (ie.width() > 0)) images.add(ie);
            } else {
                images.add(ie);
            }
        }
    }
    
    public Iterator entries() {
        // returns htmlFilterImageEntry - Objects
        return images.iterator();
    }
    
}
