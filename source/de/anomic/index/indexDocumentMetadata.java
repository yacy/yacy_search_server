// indexDocumentMetadata.java
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
import java.util.Date;

import de.anomic.crawler.CrawlProfile;
import de.anomic.yacy.yacyURL;

public interface indexDocumentMetadata {

    /**
     * @return an anchor name; can be either the text inside the anchor tag or the
     *         page description after loading of the page
     */
    public String name();

    public yacyURL url();

    public String urlHash();

    public Date lastModified();

    public String language();

    public CrawlProfile.entry profile();

    public String initiator();

    public boolean proxy();

    public long size();

    public int depth();

    public yacyURL referrerURL();

    public File cacheFile();

    public void setCacheArray(final byte[] data);

    public byte[] cacheArray();

    public String getMimeType();
    public Date ifModifiedSince();
    public boolean requestProhibitsIndexing();
    public boolean requestWithCookie();

    /**
     * @return NULL if the answer is TRUE, in case of FALSE, the reason as
     *         String is returned
     */
    public String shallStoreCacheForProxy();
    
    /**
     * decide upon header information if a specific file should be taken from
     * the cache or not
     * 
     * @return whether the file should be taken from the cache
     */
    public boolean shallUseCacheForProxy();
}
