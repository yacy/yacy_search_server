//RobotsEntry.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
// [MC] moved some methods from robotsParser file that had been created by Alexander Schier to this class
// [MC] redesign: removed entry object from RobotsTxt Class into ths separate class

//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class RobotsEntry {
    
    public static final String ROBOTS_DB_PATH_SEPARATOR = ";"; 
    public static final String ALLOW_PATH_LIST    = "allow";
    public static final String DISALLOW_PATH_LIST = "disallow";
    public static final String LOADED_DATE        = "date";
    public static final String MOD_DATE           = "modDate";
    public static final String ETAG               = "etag";
    public static final String SITEMAP            = "sitemap";
    public static final String CRAWL_DELAY        = "crawlDelay";
    public static final String CRAWL_DELAY_MILLIS = "crawlDelayMillis";
    
    // this is a simple record structure that holds all properties of a single crawl start
    Map<String, String> mem;
    private LinkedList<String> allowPathList, denyPathList;
    String hostName;
    
    public RobotsEntry(final String hostName, final Map<String, String> mem) {
        this.hostName = hostName.toLowerCase();
        this.mem = mem; 
        
        if (this.mem.containsKey(DISALLOW_PATH_LIST)) {
            this.denyPathList = new LinkedList<String>();
            final String csPl = this.mem.get(DISALLOW_PATH_LIST);
            if (csPl.length() > 0){
                final String[] pathArray = csPl.split(ROBOTS_DB_PATH_SEPARATOR);
                if ((pathArray != null)&&(pathArray.length > 0)) {
                    this.denyPathList.addAll(Arrays.asList(pathArray));
                }
            }
        } else {
            this.denyPathList = new LinkedList<String>();
        }
        if (this.mem.containsKey(ALLOW_PATH_LIST)) {
            this.allowPathList = new LinkedList<String>();
            final String csPl = this.mem.get(ALLOW_PATH_LIST);
            if (csPl.length() > 0){
                final String[] pathArray = csPl.split(ROBOTS_DB_PATH_SEPARATOR);
                if ((pathArray != null)&&(pathArray.length > 0)) {
                    this.allowPathList.addAll(Arrays.asList(pathArray));
                }
            }
        } else {
            this.allowPathList = new LinkedList<String>();
        }
    }  
    
    public RobotsEntry(
            final String hostName, 
            final ArrayList<String> allowPathList, 
            final ArrayList<String> disallowPathList, 
            final Date loadedDate,
            final Date modDate,
            final String eTag,
            final String sitemap,
            final long crawlDelayMillis
    ) {
        if ((hostName == null) || (hostName.length() == 0)) throw new IllegalArgumentException("The hostname is missing");
        
        this.hostName = hostName.trim().toLowerCase();
        this.allowPathList = new LinkedList<String>();
        this.denyPathList = new LinkedList<String>();
        
        this.mem = new HashMap<String, String>(10);
        if (loadedDate != null) this.mem.put(LOADED_DATE,Long.toString(loadedDate.getTime()));
        if (modDate != null) this.mem.put(MOD_DATE,Long.toString(modDate.getTime()));
        if (eTag != null) this.mem.put(ETAG,eTag);
        if (sitemap != null) this.mem.put(SITEMAP,sitemap);
        if (crawlDelayMillis > 0) this.mem.put(CRAWL_DELAY_MILLIS, Long.toString(crawlDelayMillis));
        
        if (allowPathList != null && !allowPathList.isEmpty()) {
            this.allowPathList.addAll(allowPathList);
            
            final StringBuilder pathListStr = new StringBuilder(allowPathList.size() * 30);
            for (int i=0; i<allowPathList.size();i++) {
                pathListStr.append(allowPathList.get(i))
                           .append(ROBOTS_DB_PATH_SEPARATOR);
            }
            this.mem.put(ALLOW_PATH_LIST,pathListStr.substring(0,pathListStr.length()-1));
        }
        
        if (disallowPathList != null && !disallowPathList.isEmpty()) {
            this.denyPathList.addAll(disallowPathList);
            
            final StringBuilder pathListStr = new StringBuilder(disallowPathList.size() * 30);
            for (int i=0; i<disallowPathList.size();i++) {
                pathListStr.append(disallowPathList.get(i))
                           .append(ROBOTS_DB_PATH_SEPARATOR);
            }
            this.mem.put(DISALLOW_PATH_LIST,pathListStr.substring(0,pathListStr.length()-1));
        }
    }
    
    public String toString() {
        final StringBuilder str = new StringBuilder(6000);
        str.append((this.hostName==null)?"null":this.hostName)
           .append(": ");
        
        if (this.mem != null) {     
            str.append(this.mem.toString());
        } 
        
        return str.toString();
    }    
    
    public String getSitemap() {
        return this.mem.containsKey(SITEMAP)? this.mem.get(SITEMAP): null;
    }
    
    public Date getLoadedDate() {
        if (this.mem.containsKey(LOADED_DATE)) {
            return new Date(Long.valueOf(this.mem.get(LOADED_DATE)).longValue());
        }
        return null;
    }
    
    public void setLoadedDate(final Date newLoadedDate) {
        if (newLoadedDate != null) {
            this.mem.put(LOADED_DATE,Long.toString(newLoadedDate.getTime()));
        }
    }
    
    public Date getModDate() {
        if (this.mem.containsKey(MOD_DATE)) {
            return new Date(Long.valueOf(this.mem.get(MOD_DATE)).longValue());
        }
        return null;
    }        
    
    public String getETag() {
        if (this.mem.containsKey(ETAG)) {
            return this.mem.get(ETAG);
        }
        return null;
    }          
    
    public long getCrawlDelayMillis() {
        if (this.mem.containsKey(CRAWL_DELAY_MILLIS)) try {
            return Long.parseLong(this.mem.get(CRAWL_DELAY_MILLIS));
        } catch (final NumberFormatException e) {
            return 0;
        }
        if (this.mem.containsKey(CRAWL_DELAY)) try {
            return 1000 * Integer.parseInt(this.mem.get(CRAWL_DELAY));
        } catch (final NumberFormatException e) {
            return 0;
        }
        return 0;           
    }
    
    public boolean isDisallowed(String path) {
        if ((this.mem == null) || (this.denyPathList.isEmpty())) return false;   
        
        // if the path is null or empty we set it to /
        if ((path == null) || (path.length() == 0)) path = "/";            
        // escaping all occurences of ; because this char is used as special char in the Robots DB
        else  path = path.replaceAll(ROBOTS_DB_PATH_SEPARATOR,"%3B");
        
        final Iterator<String> pathIter = this.denyPathList.iterator();
        while (pathIter.hasNext()) {
            final String nextPath = pathIter.next();
                
            // disallow rule
            if (path.startsWith(nextPath)) {
                return true;
            }
        }
        return false;
    }

}