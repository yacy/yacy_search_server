package de.anomic.plasma.cache.ftp;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import de.anomic.index.indexURL;
import de.anomic.net.URL;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;

public class ResourceInfo implements IResourceInfo {

    public static final String MIMETYPE = "mimetype";
    public static final String MODIFICATION_DATE = "modificationDate";
    public static final String REFERER = "referer";
    
    private URL url;
    private String urlHash;    
    private HashMap propertyMap; 
    
    /**
     * Constructor used by the {@link ResourceInfoFactory}
     * @param objectURL
     * @param objectInfo
     */
    public ResourceInfo(URL objectURL, Map objectInfo) {
        if (objectURL == null) throw new NullPointerException();
        if (objectInfo == null) throw new NullPointerException();
        
        // generating the url hash
        this.url = objectURL;
        this.urlHash = indexURL.urlHash(this.url.toNormalform());
        
        // create the http header object
        this.propertyMap =  new HashMap(objectInfo);
    }    
    
    public ResourceInfo(URL objectURL, String refererUrl, String mimeType, Date fileDate) {
        if (objectURL == null) throw new NullPointerException();
        
        // generating the url hash
        this.url = objectURL;
        this.urlHash = indexURL.urlHash(this.url.toNormalform());
        
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

    public URL getRefererUrl() {
        try {
            return (this.propertyMap == null) ? null : new URL((String)this.propertyMap.get(REFERER));
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public URL getUrl() {
        return this.url;
    }

    public String getUrlHash() {
        return this.urlHash;
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

}
