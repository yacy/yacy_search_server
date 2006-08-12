package de.anomic.plasma.urlPattern;

import java.io.File;

import de.anomic.net.URL;

public interface plasmaURLPattern {

    public static final String BLACKLIST_DHT = "dht";
    public static final String BLACKLIST_CRAWLER = "crawler";
    public static final String BLACKLIST_PROXY = "proxy";
    public static final String BLACKLIST_SEARCH = "search";


    public String getEngineInfo();
    
    public void setRootPath(File rootPath);

    public int blacklistCacheSize();

    public int size();

    public void clear();
    public void remove(String blacklistType, String host);
    public void add(String blacklistType, String host, String path);

    
    public void loadList(String blacklistType, String filenames, String sep);    
    public void loadList(String[][] filenames, String sep);


    public boolean hashInBlacklistedCache(String blacklistType, String urlHash);

    public boolean isListed(String blacklistType, String urlHash, URL url);
    
    public boolean isListed(String blacklistType, URL url);
    
    public boolean isListed(String blacklistType, String hostlow, String path);    
    
}
