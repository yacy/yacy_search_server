//RobotsEntry.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
// [MC] moved some methods from robotsParser file that had been created by Alexander Schier to this class
// [MC] redesign: removed entry object from RobotsTxt Class into this separate class

//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General public License for more details.
//
//You should have received a copy of the GNU General public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.crawler.robots;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ByteArray;


public class RobotsTxtEntry {

    private static final String HOST_NAME          = "hostname";
    private static final String ALLOW_PATH_LIST    = "allow";
    private static final String DISALLOW_PATH_LIST = "disallow";
    private static final String SITEMAP_LIST       = "sitemap";
    private static final String LOADED_DATE        = "date";
    private static final String MOD_DATE           = "modDate";
    private static final String ETAG               = "etag";
    private static final String CRAWL_DELAY        = "crawlDelay";
    private static final String CRAWL_DELAY_MILLIS = "crawlDelayMillis";
    private static final String AGENT_NAME         = "agentname";

    // this is a simple record structure that holds all properties of a single crawl start
    private final Map<String, byte[]> mem;
    private final List<String> allowPathList, denyPathList, sitemapList;
    private final String hostName, agentName;
    private String info; // this is filled if robots disallowed access; then the reason is noted there;

    protected RobotsTxtEntry(final String hostName, final Map<String, byte[]> mem) {
        this.hostName = hostName.toLowerCase();
        this.mem = mem;
        this.info = "";

        this.denyPathList = new LinkedList<String>();
        fillMultiValue(this.denyPathList, DISALLOW_PATH_LIST);
        this.allowPathList = new LinkedList<String>();
        fillMultiValue(this.allowPathList, ALLOW_PATH_LIST);
        this.sitemapList = new LinkedList<String>();
        fillMultiValue(this.sitemapList, SITEMAP_LIST);
        this.agentName = this.mem.containsKey(AGENT_NAME) ? UTF8.String(this.mem.get(AGENT_NAME)) : null;
    }

    private void fillMultiValue(List<String> list, String listName) {
        if (this.mem.containsKey(listName)) {
            final String csPl = UTF8.String(this.mem.get(listName));
            if (csPl.length() > 0){
                final String[] pathArray = csPl.split(RobotsTxt.ROBOTS_DB_PATH_SEPARATOR);
                if ((pathArray != null) && (pathArray.length > 0)) {
                    list.addAll(Arrays.asList(pathArray));
                }
            }
        }
    }
    
    protected RobotsTxtEntry(
            final MultiProtocolURL theURL,
            final List<String> allowPathList,
            final List<String> disallowPathList,
            final Date loadedDate,
            final Date modDate,
            final String eTag,
            final List<String> sitemapList,
            final long crawlDelayMillis,
            final String agentName
    ) {
        if (theURL == null) throw new IllegalArgumentException("The url is missing");

        this.hostName = RobotsTxt.getHostPort(theURL).toLowerCase();
        this.allowPathList = new LinkedList<String>();
        this.denyPathList = new LinkedList<String>();
        this.sitemapList = new LinkedList<String>();
        this.agentName = agentName;

        this.mem = new LinkedHashMap<String, byte[]>(10);
        this.mem.put(HOST_NAME, UTF8.getBytes(this.hostName));
        if (loadedDate != null) this.mem.put(LOADED_DATE, ASCII.getBytes(Long.toString(loadedDate.getTime())));
        if (modDate != null) this.mem.put(MOD_DATE, ASCII.getBytes(Long.toString(modDate.getTime())));
        if (eTag != null) this.mem.put(ETAG, UTF8.getBytes(eTag));
        if (crawlDelayMillis > 0) this.mem.put(CRAWL_DELAY_MILLIS, ASCII.getBytes(Long.toString(crawlDelayMillis)));
        if (agentName != null) this.mem.put(AGENT_NAME, UTF8.getBytes(agentName));

        readMultiValue(allowPathList,    this.allowPathList, ALLOW_PATH_LIST);
        readMultiValue(disallowPathList, this.denyPathList,  DISALLOW_PATH_LIST);
        readMultiValue(sitemapList,      this.sitemapList,   SITEMAP_LIST);
    }

    private void readMultiValue(List<String> externallist, List<String> internallist, String listName) {
        if (externallist != null && !externallist.isEmpty()) {
            internallist.addAll(externallist);

            final StringBuilder pathListStr = new StringBuilder(externallist.size() * 30);
            for (final String element : externallist) {
                pathListStr.append(element).append(RobotsTxt.ROBOTS_DB_PATH_SEPARATOR);
            }
            this.mem.put(listName, UTF8.getBytes(pathListStr.substring(0, pathListStr.length()-1)));
        }
    }
    
    protected String getHostName() {
        return this.hostName;
    }

    public String getAgentName() {
        return this.agentName;
    }

    protected Map<String, byte[]> getMem() {
        if (!this.mem.containsKey(HOST_NAME)) this.mem.put(HOST_NAME, UTF8.getBytes(this.hostName));
        return this.mem;
    }

    @Override
    public String toString() {
        final StringBuilder str = new StringBuilder(6000);
        str.append((this.hostName == null) ? "null" : this.hostName).append(": ");
        if (this.mem != null) {
            str.append('{');
            for (Map.Entry<String, byte[]> entry: this.mem.entrySet()) {
                str.append(entry.getKey());
                str.append('=');
                str.append(UTF8.String(entry.getValue()));
                str.append(',');
            }
            str.append('}');
        }
        return str.toString();
    }

    /**
     * get the sitemap urls
     * @return a list of sitemap urls (possibly empty), but not null
     */
    public List<String> getSitemaps() {
        return this.sitemapList;
    }

    protected Date getLoadedDate() {
        if (this.mem.containsKey(LOADED_DATE)) {
            return new Date(ByteArray.parseDecimal(this.mem.get(LOADED_DATE)));
        }
        return null;
    }

    protected void setLoadedDate(final Date newLoadedDate) {
        if (newLoadedDate != null) {
            this.mem.put(LOADED_DATE, ASCII.getBytes(Long.toString(newLoadedDate.getTime())));
        }
    }

    protected Date getModDate() {
        if (this.mem.containsKey(MOD_DATE)) {
            return new Date(ByteArray.parseDecimal(this.mem.get(MOD_DATE)));
        }
        return null;
    }

    protected String getETag() {
        if (this.mem.containsKey(ETAG)) {
            return ASCII.String(this.mem.get(ETAG));
        }
        return null;
    }

    public int getCrawlDelayMillis() {
        if (this.mem.containsKey(CRAWL_DELAY_MILLIS)) try {
            return (int) ByteArray.parseDecimal(this.mem.get(CRAWL_DELAY_MILLIS));
        } catch (final NumberFormatException e) {
            return 0;
        }
        if (this.mem.containsKey(CRAWL_DELAY)) try {
            return 1000 * ((int) ByteArray.parseDecimal(this.mem.get(CRAWL_DELAY)));
        } catch (final NumberFormatException e) {
            return 0;
        }
        return 0;
    }

    public boolean isDisallowed(final MultiProtocolURL subpathURL) {
        String path = subpathURL.getFile();
        if (this.mem == null) {
            this.info = "no robots file available";
            return false;
        }
        if (this.denyPathList.isEmpty()) {
            this.info = "no entry in robots.txt";
            return false;
        }

        // if the path is null or empty we set it to /
        if (path == null || path.isEmpty()) path = "/";
        // escaping all occurences of ; because this char is used as special char in the Robots DB
        else  path = RobotsTxt.ROBOTS_DB_PATH_SEPARATOR_MATCHER.matcher(path).replaceAll("%3B");

        for (final String element : this.denyPathList) {

            // disallow rule
            if (path.startsWith(element)) {
                this.info = "path '" + path + "' starts with '" + element + "' from deny path list = " + this.denyPathList.toString();
                return true;
            }
        }
        this.info = "path '" + path + "' does not start with any element from deny path list";
        return false;
    }

    public String getInfo() {
        return this.info;
    }
}