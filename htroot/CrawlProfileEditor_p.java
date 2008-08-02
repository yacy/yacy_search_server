// CrawlProfileEditor_p.java
// (C) 2005, by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2005 on http://yacy.net
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.CrawlProfile.entry;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class CrawlProfileEditor_p {
    
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
    
    private static final ArrayList <eentry> labels = new ArrayList<eentry>();
    static {
        labels.add(new eentry(entry.NAME,             "Name",                 true,  eentry.STRING));
        labels.add(new eentry(entry.START_URL,        "Start URL",            true,  eentry.STRING));
        labels.add(new eentry(entry.GENERAL_FILTER,   "General Filter",       false, eentry.STRING));
        labels.add(new eentry(entry.SPECIFIC_FILTER,  "Specific Filter",      false, eentry.STRING));
        labels.add(new eentry(entry.GENERAL_DEPTH,    "General Depth",        false, eentry.INTEGER));
        labels.add(new eentry(entry.SPECIFIC_DEPTH,   "Specific Depth",       false, eentry.INTEGER));
        labels.add(new eentry(entry.RECRAWL_IF_OLDER, "Recrawl If Older",     false, eentry.INTEGER));
        labels.add(new eentry(entry.DOM_FILTER_DEPTH, "Domain Filter Depth",  false, eentry.INTEGER));
        labels.add(new eentry(entry.DOM_MAX_PAGES,    "Domain Max. Pages",    false, eentry.INTEGER));
        labels.add(new eentry(entry.CRAWLING_Q,       "CrawlingQ / '?'-URLs", false, eentry.BOOLEAN));
        labels.add(new eentry(entry.INDEX_TEXT,       "Index Text",           false, eentry.BOOLEAN));
        labels.add(new eentry(entry.INDEX_MEDIA,      "Index Media",          false, eentry.BOOLEAN));
        labels.add(new eentry(entry.STORE_HTCACHE,    "Store in HTCache",     false, eentry.BOOLEAN));
        labels.add(new eentry(entry.STORE_TXCACHE,    "Store in TXCache",     false, eentry.BOOLEAN));
        labels.add(new eentry(entry.REMOTE_INDEXING,  "Remote Indexing",      false, eentry.BOOLEAN));
        labels.add(new eentry(entry.XSSTOPW,          "Static stop-words",    false, eentry.BOOLEAN));
        labels.add(new eentry(entry.XDSTOPW,          "Dynamic stop-words",   false, eentry.BOOLEAN));
        labels.add(new eentry(entry.XPSTOPW,          "Parent stop-words",    false, eentry.BOOLEAN));
    }
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final servletProperties prop = new servletProperties();
        final plasmaSwitchboard sb = (plasmaSwitchboard)env;
        
        // read post for handle
        final String handle = (post == null) ? "" : post.get("handle", "");
        if (post != null) {
            if (post.containsKey("terminate")) {
                // termination of a crawl: shift the crawl from active to passive
                final CrawlProfile.entry entry = sb.webIndex.profilesActiveCrawls.getEntry(handle);
                if (entry != null) sb.webIndex.profilesPassiveCrawls.newEntry(entry.map());
                sb.webIndex.profilesActiveCrawls.removeEntry(handle);
                // delete all entries from the crawl queue that are deleted here
                sb.crawlQueues.noticeURL.removeByProfileHandle(handle, 10000);
            }
            if (post.containsKey("delete")) {
                // deletion of a terminated crawl profile
                sb.webIndex.profilesPassiveCrawls.removeEntry(handle);
            }
            if (post.containsKey("deleteTerminatedProfiles")) {
                Iterator<CrawlProfile.entry> profiles = sb.webIndex.profilesPassiveCrawls.profiles(false);
                while (profiles.hasNext()) {
                    profiles.next();
                    profiles.remove();
                    profiles = sb.webIndex.profilesPassiveCrawls.profiles(false);
                }
            }
        }
        
        // generate handle list
        int count = 0;
        Iterator<CrawlProfile.entry> it = sb.webIndex.profilesActiveCrawls.profiles(true);
        entry selentry;
        while (it.hasNext()) {
            selentry = it.next();
            if (selentry.name().equals(plasmaWordIndex.CRAWL_PROFILE_PROXY) ||
                    selentry.name().equals(plasmaWordIndex.CRAWL_PROFILE_REMOTE) /*||
                    selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_TEXT) ||
                    selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_MEDIA)*/)
                continue;
            prop.put("profiles_" + count + "_name", selentry.name());
            prop.put("profiles_" + count + "_handle", selentry.handle());
            if (handle.equals(selentry.handle()))
                prop.put("profiles_" + count + "_selected", "1");
            count++;
        }
        prop.put("profiles", count);
        selentry = sb.webIndex.profilesActiveCrawls.getEntry(handle);
        
        // read post for change submit
        if ((post != null) && (selentry != null)) {
			if (post.containsKey("submit")) {
				try {
					final Iterator<eentry> lit = labels.iterator();
					eentry tee;
					while (lit.hasNext()) {
						tee = lit.next();
						final String cval = selentry.map().get(tee.name);
						final String val = (tee.type == eentry.BOOLEAN) ? Boolean.toString(post.containsKey(tee.name)) : post.get(tee.name, cval);
						if (!cval.equals(val)) sb.webIndex.profilesActiveCrawls.changeEntry(selentry, tee.name, val);
					}
				} catch (final IOException ex) {
					prop.put("error", "1");
					prop.putHTML("error_message", ex.getMessage());
				}
			}
		}
        
        // generate crawl profile table
        count = 0;
        boolean dark = true;
        final int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        CrawlProfile.entry profile;
        // put active crawls into list
        it = sb.webIndex.profilesActiveCrawls.profiles(true);
        while (it.hasNext()) {
            profile = it.next();
            putProfileEntry(prop, profile, true, dark, count, domlistlength);
            dark = !dark;
            count++;
        }
        // put passive crawls into list
        boolean existPassiveCrawls = false;
        it = sb.webIndex.profilesPassiveCrawls.profiles(true);
        while (it.hasNext()) {
            profile = it.next();
            putProfileEntry(prop, profile, false, dark, count, domlistlength);
            dark = !dark;
            count++;
            existPassiveCrawls = true;
        }
        prop.put("crawlProfiles", count);
        
        if(existPassiveCrawls) {
            prop.put("existPassiveCrawls", "1");
        } else {
            prop.put("existPassiveCrawls", "0");
        }
        
        // generate edit field
        if (selentry == null) {
        	prop.put("edit", "0");
        } else {
        	prop.put("edit", "1");
			prop.put("edit_name", selentry.name());
			prop.put("edit_handle", selentry.handle());
			final Iterator<eentry> lit = labels.iterator();
			count = 0;
			while (lit.hasNext()) {
				final eentry ee = lit.next();
				final String val = selentry.map().get(ee.name);
				prop.put("edit_entries_" + count + "_readonly", ee.readonly ? "1" : "0");
				prop.put("edit_entries_" + count + "_readonly_name", ee.name);
				prop.put("edit_entries_" + count + "_readonly_label", ee.label);
				prop.put("edit_entries_" + count + "_readonly_type", ee.type);
				if (ee.type == eentry.BOOLEAN) {
					prop.put("edit_entries_" + count + "_readonly_type_checked", Boolean.valueOf(val).booleanValue() ? "1" : "0");
				} else {
					prop.put("edit_entries_" + count + "_readonly_type_value", val);
				}
				count++;
			}
			prop.put("edit_entries", count);
		}
        
        return prop;
    }
    
    private static void putProfileEntry(final servletProperties prop, final CrawlProfile.entry profile, final boolean active, final boolean dark, final int count, final int domlistlength) {
        prop.put("crawlProfiles_" + count + "_dark", dark ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_status", active ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_name", profile.name());
        prop.putHTML("crawlProfiles_" + count + "_startURL", profile.startURL(), true);
        prop.put("crawlProfiles_" + count + "_handle", profile.handle());
        prop.put("crawlProfiles_" + count + "_depth", profile.generalDepth());
        prop.put("crawlProfiles_" + count + "_filter", profile.generalFilter());
        prop.put("crawlProfiles_" + count + "_crawlingIfOlder", (profile.recrawlIfOlder() == Long.MAX_VALUE) ? "no re-crawl" : ""+profile.recrawlIfOlder());
        prop.put("crawlProfiles_" + count + "_crawlingDomFilterDepth", (profile.domFilterDepth() == Integer.MAX_VALUE) ? "inactive" : Integer.toString(profile.domFilterDepth()));

        // start contrib [MN]
        int i = 0;
        String item;
        while ((i <= domlistlength) && !((item = profile.domName(true, i)).equals(""))){
            if(i == domlistlength){
                item = item + " ...";
            }
            prop.putHTML("crawlProfiles_"+count+"_crawlingDomFilterContent_"+i+"_item", item);
            i++;
        }

        prop.put("crawlProfiles_"+count+"_crawlingDomFilterContent", i);
        // end contrib [MN]

        prop.put("crawlProfiles_" + count + "_crawlingDomMaxPages", (profile.domMaxPages() == Integer.MAX_VALUE) ? "unlimited" : ""+profile.domMaxPages());
        prop.put("crawlProfiles_" + count + "_withQuery", (profile.crawlingQ()) ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_storeCache", (profile.storeHTCache()) ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_indexText", (profile.indexText()) ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_indexMedia", (profile.indexMedia()) ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_remoteIndexing", (profile.remoteIndexing()) ? "1" : "0");
        prop.put("crawlProfiles_" + count + "_terminateButton", ((!active) || (profile.name().equals("remote")) ||
                                                           (profile.name().equals("proxy")) ||
                                                           (profile.name().equals("snippetText")) ||
                                                           (profile.name().equals("snippetMedia"))) ? "0" : "1");
        prop.put("crawlProfiles_" + count + "_terminateButton_handle", profile.handle());
        prop.put("crawlProfiles_" + count + "_deleteButton", (active) ? "0" : "1");
        prop.put("crawlProfiles_" + count + "_deleteButton_handle", profile.handle());
    }
}
