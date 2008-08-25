// indexDocumentCache.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
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


package de.anomic.index;

import java.io.File;
import java.io.InputStream;

import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;


public class indexDocumentCache {
    
    public static final serverLog log = new serverLog("DOCUMENT-CACHE");

    public indexDocumentCache(final File htCachePath, final long CacheSizeMax) {
        
    }


    public int size() {
        return 0; // dummy
    }
    

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
     */
    public void setCacheSize(final long newCacheSize) {
    }

    /**
     * This method returns the free HTCache size.<br>
     * @return the cache size in bytes
     */
    public long getFreeSize() {
        return 0; // dummy
    }

    public void writeResourceContent(final yacyURL url, final byte[] array) {
    }
    
    public void deleteURLfromCache(final yacyURL url) {
    }

    public void close() {
    }
    
    public boolean full() {
        return false;
    }

    public boolean empty() {
        return false;
    }

    /**
     * Returns the content of a cached resource as {@link InputStream}
     * @param url the requested resource
     * @return the resource content as {@link InputStream}. In no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public InputStream getResourceContentStream(final yacyURL url) {
        return null;
    }
    
    public long getResourceContentLength(final yacyURL url) {
        return 0; // dummy
    }

}
