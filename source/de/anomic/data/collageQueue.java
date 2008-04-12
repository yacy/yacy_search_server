// collageQueue.java
// (C) 2008 by by Detlef Reichl; detlef!reichl()gmx!org and Michael Peter Christen; mc@yacy.net
// first published 13.04.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.data;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.yacy.yacyURL;

public class collageQueue {

    private static final ConcurrentLinkedQueue<ImageOriginEntry> privateImageQueue = new ConcurrentLinkedQueue<ImageOriginEntry>();
    private static final ConcurrentLinkedQueue<ImageOriginEntry> publicImageQueue = new ConcurrentLinkedQueue<ImageOriginEntry>();
    
    public static void registerImages(plasmaParserDocument document, boolean privateEntry) {
        if (document == null) return;
        if (document.dc_source() == null) return;
        
        HashMap<String, htmlFilterImageEntry> images = document.getImages();
        for (htmlFilterImageEntry image: images.values()) {
            String name = image.url().getFile();

            if (image.width() > 120 &&
                image.height() > 100 &&
                image.width() < 1200 &&
                image.height() < 1000 &&
                name.lastIndexOf(".gif") == -1) {
                // && ((urlString.lastIndexOf(".jpg") != -1)) ||
                // ((urlString.lastIndexOf(".png") != -1)){
                float ratio;
                if (image.width() > image.height()) {
                    ratio = (float) image.width() / (float) image.height();
                } else {
                    ratio = (float) image.height() / (float) image.width();
                }
                if (ratio >= 1.0f && ratio <= 2.0f) {
                    if (privateEntry) {
                        privateImageQueue.add(new ImageOriginEntry(image, document.dc_source()));
                    } else {
                        publicImageQueue.add(new ImageOriginEntry(image, document.dc_source()));
                    }
                }
            }
        }
    }
    
    public static ImageOriginEntry next(boolean privateEntryOnly) {
        ImageOriginEntry e = null;
        if (privateEntryOnly) {
            e = privateImageQueue.poll();
        } else {
            e = publicImageQueue.poll();
            if (e == null) e = privateImageQueue.poll();
        }
        return e;
    }
    
    public static class ImageOriginEntry {
        public htmlFilterImageEntry imageEntry;
        public yacyURL baseURL;
        public ImageOriginEntry(htmlFilterImageEntry imageEntry, yacyURL baseURL) {
            this.imageEntry = imageEntry;
            this.baseURL = baseURL;
        }
    }
    
}
