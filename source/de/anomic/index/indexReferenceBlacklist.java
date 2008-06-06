// indexReferenceBlacklist.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.03.2008 on http://yacy.net
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
import java.util.Arrays;
import java.util.HashSet;

import de.anomic.yacy.yacyURL;

public interface indexReferenceBlacklist {

    public static final String BLACKLIST_DHT      = "dht";
    public static final String BLACKLIST_CRAWLER  = "crawler";
    public static final String BLACKLIST_PROXY    = "proxy";
    public static final String BLACKLIST_SEARCH   = "search";
    public static final String BLACKLIST_SURFTIPS = "surftips";
    public static final String BLACKLIST_NEWS     = "news";
    
    public static final class blacklistFile {
        
        private final String filename;
        private final String type;
        
        public blacklistFile(String filename, String type) {
            this.filename = filename;
            this.type = type;
        }
        
        public String getFileName() { return this.filename; }
        
        
        /**
         * Construct a unified array of file names from comma seperated file name
         * list.
         * 
         * @return unified String array of file names
         */
        public String[] getFileNamesUnified() {
            HashSet<String> hs = new HashSet<String>(Arrays.asList(this.filename.split(",")));
            
            return hs.toArray(new String[hs.size()]);
        }
        
        public String getType() { return this.type; }
    }

    public String getEngineInfo();
    
    public void setRootPath(File rootPath);

    public int blacklistCacheSize();

    public int size();

    public void clear();
    public void removeAll(String blacklistType, String host);
    public void remove(String blacklistType, String host, String path);
    public void add(String blacklistType, String host, String path);

    
    public void loadList(String blacklistType, String filenames, String sep);    
    public void loadList(blacklistFile[] blFiles, String sep);


    public boolean hashInBlacklistedCache(String blacklistType, String urlHash);
    
    public boolean isListed(String blacklistType, yacyURL url);
    
    public boolean isListed(String blacklistType, String hostlow, String path);    
    
}
