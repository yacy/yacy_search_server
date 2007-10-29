// ResourceInfo.java 
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

package de.anomic.plasma.cache.ftp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;
import de.anomic.yacy.yacyURL;

public class ResourceInfo implements IResourceInfo {

    public static final String MIMETYPE = "mimetype";
    public static final String MODIFICATION_DATE = "modificationDate";
    public static final String REFERER = "referer";
    
    private yacyURL url;
    private HashMap propertyMap; 
    
    /**
     * Constructor used by the {@link ResourceInfoFactory}
     * @param objectURL
     * @param objectInfo
     */
    public ResourceInfo(yacyURL objectURL, Map objectInfo) {
        if (objectURL == null) throw new NullPointerException();
        if (objectInfo == null) throw new NullPointerException();
        
        // generating the url hash
        this.url = objectURL;
        
        // create the http header object
        this.propertyMap =  new HashMap(objectInfo);
    }    
    
    public ResourceInfo(yacyURL objectURL, yacyURL refererUrl, String mimeType, Date fileDate) {
        if (objectURL == null) throw new NullPointerException();
        
        // generating the url hash
        this.url = objectURL;
        
        // create the http header object
        this.propertyMap =  new HashMap();
        if (refererUrl != null) 
            this.propertyMap.put(REFERER, refererUrl);
        if (mimeType != null) 
            this.propertyMap.put(MIMETYPE, mimeType);
        if (fileDate != null) 
            this.propertyMap.put(MODIFICATION_DATE, Long.toString(fileDate.getTime()));
    }
    
    public Map getMap() {
        return this.propertyMap;
    }

    public String getMimeType() {
        return (String) ((this.propertyMap == null) ? null : this.propertyMap.get(MIMETYPE));        
    }

    public Date getModificationDate() {
        if (this.propertyMap == null || !this.propertyMap.containsKey(MODIFICATION_DATE)) return new Date();
        return new Date(Long.valueOf((String) this.propertyMap.get(MODIFICATION_DATE)).longValue());
    }

    public yacyURL getRefererUrl() {
        return (this.propertyMap == null) ? null : ((yacyURL) this.propertyMap.get(REFERER));
    }

    public yacyURL getUrl() {
        return this.url;
    }
    
    public Date ifModifiedSince() {
        return null;
    }

    public boolean requestProhibitsIndexing() {
        return false;
    }

    public boolean requestWithCookie() {
        return false;
    }

    public String shallIndexCacheForCrawler() {
        return null;
    }

    public String shallIndexCacheForProxy() {
        return null;
    }

    public String shallStoreCacheForProxy() {
        return null;
    }

    public boolean shallUseCacheForProxy() {
        return false;
    }

    public boolean validResponseStatus(String responseStatus) {
        return responseStatus != null && responseStatus.equalsIgnoreCase("OK");
    }

    public String getCharacterEncoding() {
        return null;
    }

}
