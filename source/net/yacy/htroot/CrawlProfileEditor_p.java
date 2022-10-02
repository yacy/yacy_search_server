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

package net.yacy.htroot;

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

    public static serverObjects respond(final RequestHeader header,
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
                    for (final CrawlProfile.CrawlAttribute attribute: CrawlProfile.CrawlAttribute.values()) {
                        final String cval = selentry.get(attribute.key);
                        final String val = (attribute.type == CrawlProfile.CrawlAttribute.BOOLEAN) ? Boolean.toString(post.containsKey(attribute.key)) : post.get(attribute.key, cval);
                        if (!cval.equals(val)) {
                            selentry.put(attribute.key, val);
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
            count = 0;
            for (final CrawlProfile.CrawlAttribute attribute: CrawlProfile.CrawlAttribute.values()) {
                final String val = selentry.get(attribute.key);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly", attribute.readonly ? "1" : "0");
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_name", attribute.key);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_label", attribute.label);
                prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_type", attribute.type);
                if (attribute.type == CrawlProfile.CrawlAttribute.BOOLEAN) {
                    prop.put(EDIT_ENTRIES_PREFIX + count + "_readonly_type_checked",
                            val == null ? "0" : Boolean.parseBoolean(val) ? "1" : "0");
                } else {
                	prop.put(header.fileType(), EDIT_ENTRIES_PREFIX + count + "_readonly_type_value", val == null ? "" : val);
                }
                count++;
            }
            prop.put("edit_entries", count);
        }

        return prop;
    }

}
