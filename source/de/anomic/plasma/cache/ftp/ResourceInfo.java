// ResourceInfo.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma.cache.ftp;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;
import de.anomic.yacy.yacyURL;

public class ResourceInfo implements IResourceInfo {

    public static final String MIMETYPE = "mimetype";
    public static final String MODIFICATION_DATE = "modificationDate";
    
    private final yacyURL objectURL;
	private yacyURL	refererURL;
    private final TreeMap<String, String> propertyMap; 
    
    /**
     * Constructor used by the {@link ResourceInfoFactory}
     * @param objectURL
     * @param objectInfo
     */
    public ResourceInfo(final yacyURL objectURL, final Map<String, String> objectInfo) {
        if (objectURL == null) throw new NullPointerException();
        if (objectInfo == null) throw new NullPointerException();
        
        // generating the url hash
        this.objectURL = objectURL;
        this.refererURL = null;
        
        // create the http header object
        this.propertyMap =  new TreeMap<String, String>(objectInfo);
    }    
    
    public ResourceInfo(final yacyURL objectURL, final yacyURL refererUrl, final String mimeType, final Date fileDate) {
        if (objectURL == null) throw new NullPointerException();
        
        // generating the url hash
        this.objectURL = objectURL;
        
        // create the http header object
        this.propertyMap =  new TreeMap<String, String>();
        if (refererUrl != null) 
            this.refererURL = refererUrl;
        if (mimeType != null) 
            this.propertyMap.put(MIMETYPE, mimeType);
        if (fileDate != null) 
            this.propertyMap.put(MODIFICATION_DATE, Long.toString(fileDate.getTime()));
    }
    
    public TreeMap<String, String> getMap() {
        return this.propertyMap;
    }

    public String getMimeType() {
        return ((this.propertyMap == null) ? null : this.propertyMap.get(MIMETYPE));        
    }

    public Date getModificationDate() {
        if (this.propertyMap == null || !this.propertyMap.containsKey(MODIFICATION_DATE)) return new Date();
        return new Date(Long.valueOf(this.propertyMap.get(MODIFICATION_DATE)).longValue());
    }

    public yacyURL getRefererUrl() {
        return this.refererURL;
    }

    public yacyURL getUrl() {
        return this.objectURL;
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

    public boolean validResponseStatus(final String responseStatus) {
        return responseStatus != null && responseStatus.equalsIgnoreCase("OK");
    }

    public String getCharacterEncoding() {
        return null;
    }

}
