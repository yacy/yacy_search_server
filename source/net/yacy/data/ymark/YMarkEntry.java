// YMarkEntry.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2011 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.data.ymark;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.content.DCEntry;
import net.yacy.kelondro.blob.Tables;
import net.yacy.search.Switchboard;

public class YMarkEntry extends TreeMap<String, String> {

    private static final long serialVersionUID = 2179622977348536148L;

    public static final YMarkEntry POISON = new YMarkEntry();
    public static final YMarkEntry EMPTY = new YMarkEntry();
    public static final String BOOKMARKS_ID = "id";
    public static final String BOOKMARKS_REF = "ref";
    public static final String FOLDERS_IMPORTED = "/imported";

    public static enum BOOKMARK {
    	//             key                 dc_attrb            dflt            html_attrb          xbel_attrb      json_attrb      type			index	separator
    	URL            ("url",             "dc:identifier",    "",             "href",             "href",         "uri",          "link",		false,	YMarkUtil.EMPTY_STRING),
    	TITLE          ("title",           "dc:title",         "",             "",                 "",             "title",        "meta",		false,	YMarkUtil.EMPTY_STRING),
    	DESC           ("desc",            "dc:description",   "",             "",                 "",             "",             "comment",	false,	YMarkUtil.EMPTY_STRING),
    	DATE_ADDED     ("date_added",      "",                 "",             "add_date",         "added",        "dateAdded",    "date",		false,	YMarkUtil.EMPTY_STRING),
    	DATE_MODIFIED  ("date_modified",   "",                 "",             "last_modified",    "modified",     "lastModified", "date",		false,	YMarkUtil.EMPTY_STRING),
    	DATE_VISITED   ("date_visited",    "",                 "",             "last_visited",     "visited",      "",             "date",		false,	YMarkUtil.EMPTY_STRING),
    	PUBLIC         ("public",          "",                 "false",        "private",          "yacy:public",  "",             "lock",		false,	YMarkUtil.EMPTY_STRING),
    	TAGS           ("tags",            "dc:subject",       "unsorted",     "shortcuturl",      "yacy:tags",    "keyword",      "tag",		true,	YMarkUtil.TAGS_SEPARATOR),
    	VISITS         ("visits",          "",                 "0",            "",                 "yacy:visits",  "",             "stat",		false,	YMarkUtil.EMPTY_STRING),
    	FOLDERS        ("folders",         "",                 "/unsorted",    "",                 "",             "",             "folder",	true,	YMarkUtil.TAGS_SEPARATOR),
    	FILTER         ("filter",          "",                 "",             "",                 "yacy:filter",  "",             "filter",    false,  YMarkUtil.EMPTY_STRING),
    	OAI            ("oai",             "",                 "",             "",                 "yacy:oai",     "",             "oai",       false,  YMarkUtil.EMPTY_STRING),
    	URLHASH        ("urlhash",         "",                 "",             "",                 "yacy:urlhash", "",             "urlhash",   false,  YMarkUtil.EMPTY_STRING),
    	STARRATING     ("starrating",      "",                 "",             "",                 "yacy:starrating", "",          "stat",      false,  YMarkUtil.EMPTY_STRING);

    	private String key;
    	private String dc_attrb;
    	private String dflt;
    	private String html_attrb;
    	private String xbel_attrb;
    	private String json_attrb;
    	private String type;
    	private boolean index;
    	private String seperator;

        private static final Map<String,BOOKMARK> lookup = new HashMap<String,BOOKMARK>();
        private static final Map<String,String> indexColumns = new HashMap<String,String>();
        static {
        	for(BOOKMARK b : EnumSet.allOf(BOOKMARK.class)) {
        		lookup.put(b.key, b);
        		if(b.index) {
        			indexColumns.put(b.key, b.seperator);
        		}
        	}
        }

        private static StringBuilder buffer = new StringBuilder(25);

    	private BOOKMARK(final String k, final String d, final String s, final String a, final String x, final String j, final String t, final boolean index, final String separator) {
    		this.key = k;
    		this.dc_attrb = d;
    		this.dflt = s;
    		this.html_attrb = a;
    		this.xbel_attrb = x;
    		this.json_attrb = j;
    		this.type = t;
    		this.index = index;
    		this.seperator = separator;
    	}
    	public static Map<String,String> indexColumns() {
    		return Collections.unmodifiableMap(indexColumns);
    	}
    	public static BOOKMARK get(String key) {
            return lookup.get(key);
    	}
    	public static boolean contains(String key) {
    		return lookup.containsKey(key);
    	}
    	public String key() {
    		return this.key;
    	}
    	public String deflt() {
    		return  this.dflt;
    	}
    	public String html_attrb() {
    		return this.html_attrb;
    	}
    	public String xbel_attrb() {
    		return this.xbel_attrb;
    	}
        public String json_attrb() {
            return this.json_attrb;
        }
        public String dc_attrb() {
            return this.dc_attrb;
        }
    	public String xbel() {
    		buffer.setLength(0);
    		buffer.append('"');
    		buffer.append('\n');
    		buffer.append(' ');
    		buffer.append(this.xbel_attrb);
    		buffer.append('=');
    		buffer.append('"');
    		return buffer.toString();
    	}
    	public String type() {
    		return this.type;
    	}
    	public boolean index() {
    		return this.index;
    	}
    	public String seperator() {
    		return this.seperator;
    	}

    }

    public YMarkEntry() {
    	this(true);
    }

    public YMarkEntry(final boolean setDefaults) {
        super();
        if(setDefaults) {
            setCurrentTimeMillis(BOOKMARK.DATE_ADDED);
            setCurrentTimeMillis(BOOKMARK.DATE_MODIFIED);
            setDefaults();
        }
    }

    public YMarkEntry(final DCEntry dc) {
        super();
    	for (BOOKMARK b : BOOKMARK.values()) {
            if (dc.getMap().containsKey(b.dc_attrb)) {
                this.put(b.key(), dc.get(b.dc_attrb));
            }
        }
        setCurrentTimeMillis(BOOKMARK.DATE_ADDED);
        setCurrentTimeMillis(BOOKMARK.DATE_MODIFIED);
        setDefaults();
    }

    public YMarkEntry(final Tables.Row bmk_row) {
        super();
    	for (BOOKMARK b : BOOKMARK.values()) {
            if(bmk_row.containsKey(b.key())) {
                this.put(b.key(), bmk_row.get(b.key(), b.deflt()));
            }
        }
    }

    private void setCurrentTimeMillis(BOOKMARK b) {
        switch(b) {
        	case DATE_ADDED:
        	case DATE_MODIFIED:
        	case DATE_VISITED:
        		this.put(b.key(), String.valueOf(System.currentTimeMillis()));
    		    break;
            default:
    			break;
        }
    }

    public void setDefaults() {
        for (BOOKMARK b : BOOKMARK.values()) {
            if(!b.deflt().isEmpty() && !this.containsKey(b.key())) {
                this.put(b.key(), b.deflt());
            }
        }
    }

    public byte[] getUrlHash() {
    	if(this.containsKey(YMarkEntry.BOOKMARK.URL.key()))
			try {
				return YMarkUtil.getBookmarkId(this.get(YMarkEntry.BOOKMARK.URL.key()));
			} catch (final MalformedURLException e) {
				ConcurrentLog.warn(YMarkTables.BOOKMARKS_LOG, "getUrlHash - MalformedURLException for YMarkEntry: "+this.get(YMarkEntry.BOOKMARK.URL.key()));
			}
    	return null;
    }

    public DCEntry getDCEntry() {
        final DCEntry dc = new DCEntry();
        for (BOOKMARK b : BOOKMARK.values()) {
            if(!b.dc_attrb.isEmpty() && this.containsKey(b.key())) {
                dc.getMap().put(b.dc_attrb, new String[]{this.get(b.key())});
            }
        }
        return dc;
    }

    public Tables.Data getData() {
        final Tables.Data data = new Tables.Data();
        for (BOOKMARK b : BOOKMARK.values()) {
            if(this.containsKey(b.key()) && this.get(b.key()) != null) {
                data.put(b.key(), this.get(b.key()));
            } else {
                data.put(b.key(), b.deflt());
            }
        }
        return data;
    }

    public void crawl(final YMarkCrawlStart.CRAWLSTART type, final boolean medialink, final Switchboard sb) throws MalformedURLException {
		final DigestURL url = new DigestURL(this.get(BOOKMARK.URL.key()));
		switch(type) {
			case SINGLE:
				YMarkCrawlStart.crawlStart(sb, url, CrawlProfile.MATCH_ALL_STRING, CrawlProfile.MATCH_NEVER_STRING, 0, true, medialink);
				break;
			case ONE_LINK:
				YMarkCrawlStart.crawlStart(sb, url, CrawlProfile.MATCH_ALL_STRING, CrawlProfile.MATCH_NEVER_STRING, 1, true, medialink);
				break;
			case FULL_DOMAIN:
				YMarkCrawlStart.crawlStart(sb, url, CrawlProfile.mustMatchFilterFullDomain(url), CrawlProfile.MATCH_NEVER_STRING, 99, false, medialink);
				break;
			default:
				break;
		}
    }
}
