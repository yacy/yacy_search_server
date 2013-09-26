// CrawlProfileEditor_p.java
// (C) 2005, by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2005 on http://yacy.net
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class CrawlProfileEditor_p {

    private final static String CRAWL_PROFILE_PREFIX = "crawlProfiles_";
    private static final String EDIT_ENTRIES_PREFIX = "edit_entries_";

    public static class eentry {
        public static final int BOOLEAN = 0;
        public static final int INTEGER = 1;
        public static final int STRING = 2;

        public final String name;
        public final String label;
        public final boolean readonly;
        public final int type;

        public eentry(final String name, final String label, final boolean readonly, final int type) {
            this.name = name;
            this.label = label;
            this.readonly = readonly;
            this.type = type;
        }
    }

    private static final List <eentry> labels = new ArrayList<eentry>();
    static {
        labels.add(new eentry(CrawlProfile.NAME,                          "Name",                                  true,  eentry.STRING));
        labels.add(new eentry(CrawlProfile.COLLECTIONS,                   "Collections (comma-separated list)",    false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_URL_MUSTMATCH,         "URL Must-Match Filter",                 false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_URL_MUSTNOTMATCH,      "URL Must-Not-Match Filter",             false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_IP_MUSTMATCH,          "IP Must-Match Filter",                  false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_IP_MUSTNOTMATCH,       "IP Must-Not-Match Filter",              false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_COUNTRY_MUSTMATCH,     "Country Must-Match Filter",             false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CRAWLER_URL_NODEPTHLIMITMATCH, "URL No-Depth-Limit Must-Match Filter",  false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.INDEXING_URL_MUSTMATCH,        "Indexing URL Must-Match Filter",        false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.INDEXING_URL_MUSTNOTMATCH,     "Indexing URL Must-Not-Match Filter",    false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.INDEXING_CONTENT_MUSTMATCH,    "Indexing Content Must-Match Filter",    false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.INDEXING_CONTENT_MUSTNOTMATCH, "Indexing Content Must-Not-Match Filter",false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.CACHE_STRAGEGY,  "Cache Strategy (NOCACHE,IFFRESH,IFEXIST,CACHEONLY)",  false, eentry.STRING));
        labels.add(new eentry(CrawlProfile.DEPTH,               "Crawl Depth",           false, eentry.INTEGER));
        labels.add(new eentry(CrawlProfile.RECRAWL_IF_OLDER,    "Recrawl If Older",      false, eentry.INTEGER));
        labels.add(new eentry(CrawlProfile.DOM_MAX_PAGES,       "Domain Max. Pages",     false, eentry.INTEGER));
        labels.add(new eentry(CrawlProfile.CRAWLING_Q,          "CrawlingQ / '?'-URLs",  false, eentry.BOOLEAN));
        labels.add(new eentry(CrawlProfile.INDEX_TEXT,          "Index Text",            false, eentry.BOOLEAN));
        labels.add(new eentry(CrawlProfile.INDEX_MEDIA,         "Index Media",           false, eentry.BOOLEAN));
        labels.add(new eentry(CrawlProfile.STORE_HTCACHE,       "Store in HTCache",      false, eentry.BOOLEAN));
        labels.add(new eentry(CrawlProfile.REMOTE_INDEXING,     "Remote Indexing",       false, eentry.BOOLEAN));
        labels.add(new eentry(CrawlProfile.DIRECT_DOC_BY_URL,   "Put all linked urls into index without parsing", false, eentry.BOOLEAN));
    }
    
    public static serverObjects respond(
            @SuppressWarnings("unused") final RequestHeader header,
            final serverObjects post,
            final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        final Switchboard sb = (Switchboard)env;

        // read post for handle
        final String handle = (post == null) ? "" : post.get("handle", "");
        if (post != null) {
            if (post.containsKey("terminate")) try {
                // termination of a crawl: shift the crawl from active to passive
                final CrawlProfile p = sb.crawler.getActive(handle.getBytes());
                if (p != null) sb.crawler.putPassive(handle.getBytes(), p);
                // delete all entries from the crawl queue that are deleted here
                sb.crawler.removeActive(handle.getBytes());
                sb.crawler.removePassive(handle.getBytes());
                sb.crawlQueues.noticeURL.removeByProfileHandle(handle, 10000);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
            if (post.containsKey("delete")) {
                // deletion of a terminated crawl profile
                sb.crawler.removePassive(handle.getBytes());
            }
            if (post.containsKey("deleteTerminatedProfiles")) {
                for (final byte[] h: sb.crawler.getPassive()) {
                    sb.crawler.removePassive(h);
                }
            }
        }

        // generate handle list: first sort by handle name
        CrawlProfile selentry;
        final Map<String, String> orderdHandles = new TreeMap<String, String>();
        for (final byte[] h : sb.crawler.getActive()) {
            selentry = sb.crawler.getActive(h);
            if (selentry != null && !CrawlSwitchboard.DEFAULT_PROFILES.contains(selentry.name())) {
                orderdHandles.put(selentry.collectionName(), selentry.handle());
            }
        }

        // then write into pop-up menu list
        int count = 0;
        for (final Map.Entry<String, String> NameHandle: orderdHandles.entrySet()) {
            prop.put("profiles_" + count + "_name", NameHandle.getKey());
            prop.put("profiles_" + count + "_handle", NameHandle.getValue());
            if (handle.equals(NameHandle.getValue())) {
                prop.put("profiles_" + count + "_selected", "1");
            }
            count++;
        }
        prop.put("profiles", count);
        selentry = sb.crawler.getActive(handle.getBytes());
        assert selentry == null || selentry.handle() != null;
        // read post for change submit
        if ((post != null) && (selentry != null)) {
            if (post.containsKey("submit")) {
                try {
                    final Iterator<eentry> lit = labels.iterator();
                    eentry tee;
                    while (lit.hasNext()) {
                        tee = lit.next();
                        final String cval = selentry.get(tee.name);
                        final String val = (tee.type == eentry.BOOLEAN) ? Boolean.toString(post.containsKey(tee.name)) : post.get(tee.name, cval);
                        if (!cval.equals(val)) {
                            selentry.put(tee.name, val);
                            sb.crawler.putActive(selentry.handle().getBytes(), selentry);
                        }
                    }
                } catch (final Exception ex) {
                    ConcurrentLog.logException(ex);
                    prop.put("error", "1");
                    prop.putHTML("error_message", ex.getMessage());
                }
            }
        }

        // generate crawl profile table
        count = 0;
        boolean dark = true;
        final int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        CrawlProfile profile;
        // put active crawls into list
        for (final byte[] h: sb.crawler.getActive()) {
            profile = sb.crawler.getActive(h);
            profile.putProfileEntry(CRAWL_PROFILE_PREFIX, prop, true, dark, count, domlistlength);
            dark = !dark;
            count++;
        }
        // put passive crawls into list
        boolean existPassiveCrawls = false;
        for (final byte[] h: sb.crawler.getPassive()) {
            profile = sb.crawler.getPassive(h);
            profile.putProfileEntry(CRAWL_PROFILE_PREFIX, prop, false, dark, count, domlistlength);
            dark = !dark;
            count++;
            existPassiveCrawls = true;
        }
        prop.put("crawlProfiles", count);

        prop.put("existPassiveCrawls", existPassiveCrawls ? "1" : "0");

        // generate edit field
        if (selentry == null) {
        	prop.put("edit", "0");
        } else {
            prop.put("edit", "1");
            prop.put("edit_name", selentry.collectionName());
            prop.put("edit_handle", selentry.handle());
            final Iterator<eentry> lit = labels.iterator();
            count = 0;
            while (lit.hasNext()) {
                final eentry ee = lit.next();
                final String val = selentry.get(ee.name);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly", ee.readonly ? "1" : "0");
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_name", ee.name);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_label", ee.label);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_type", ee.type);
                if (ee.type == eentry.BOOLEAN) {
                    prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_type_checked",
                            Boolean.parseBoolean(val) ? "1" : "0");
                } else {
                    prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_type_value", val);
                }
                count++;
            }
            prop.put("edit_entries", count);
        }

        return prop;
    }

}
