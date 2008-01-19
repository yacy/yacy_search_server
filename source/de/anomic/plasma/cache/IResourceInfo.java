// IResourceInfo.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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



package de.anomic.plasma.cache;

import java.util.Date;
import java.util.Map;

import de.anomic.yacy.yacyURL;

/**
 * A class containing metadata about a downloaded resource
 */
public interface IResourceInfo {
    
    /**
     * @return the resource information
     */
    public Map<String, String> getMap();
    
    /**
     * @return the URL of this content
     */
    public yacyURL getUrl();
    
    /**
     * Returns the referer URL of this URL
     * @return referer URL
     */
    public yacyURL getRefererUrl();
    
    /**
     * Returns the mimetype of the cached object
     * @return mimetype
     */
    public String getMimeType();
    
    /**
     * Returns the charset of the resource
     * @return returns the name of the charset or <code>null</code> if unknown
     */
    public String getCharacterEncoding();
    
    /**
     * Returns the modification date of the cached object
     * @return the modifiaction date
     */
    public Date getModificationDate();
    
    /**
     * Specifies if the resource was requested with a
     * if modified since date
     * @return the <code>Modified since</code>-header field or <code>null</code>
     * if the request didn't contain this field
     */
    public Date ifModifiedSince();
    
    /**
     * Specifies if the resource was requested with 
     * client specific information (e.g. cookies for http)
     * @return whether additional client specific information were passed
     * along the reuqest for this resource
     */
    public boolean requestWithCookie();
    
    /**
     * Specifies if the request prohibits indexing
     * @return whether indexing is proibited by this request
     */
    public boolean requestProhibitsIndexing();
    
    /**
     * Determines if a resource that was downloaded by the crawler
     * is allowed to be indexed.
     *  
     * @return an error string describing the reason why the
     * resourse should not be indexed or null if indexing is allowed
     */
    public String shallIndexCacheForCrawler();
    
    /**
     * Determines if a resource that was downloaded by the proxy
     * is allowed to be indexed.
     *  
     * @return an error string describing the reason why the
     * resourse should not be indexed or null if indexing is allowed
     */    
    public String shallIndexCacheForProxy();
    
    public String shallStoreCacheForProxy();
    public boolean shallUseCacheForProxy();
    
    public boolean validResponseStatus(String responseStatus);
}
