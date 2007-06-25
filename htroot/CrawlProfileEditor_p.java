// CrawlProfileEditor_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 04.07.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes CrawlProfileEditor_p.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlProfile.entry;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
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
        
        public eentry(String name, String label, boolean readonly, int type) {
            this.name = name;
            this.label = label;
            this.readonly = readonly;
            this.type = type;
        }
    }
    
    private static final ArrayList /*<eentry>*/ labels = new ArrayList();
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
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final servletProperties prop = new servletProperties();
        final plasmaSwitchboard sb = (plasmaSwitchboard)env;
        
        // read post for handle
        String handle = (post == null) ? "" : post.get("handle", "");
        if ((post != null) &&  (post.containsKey("deleteprofile"))) {
            // deletion of a crawl
            sb.profiles.removeEntry(handle);
        }
        
        // generate handle list
        int count = 0;
        Iterator it = sb.profiles.profiles(true);
        entry selentry;
        while (it.hasNext()) {
            selentry = (entry)it.next();
            if (selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_PROXY) ||
                    selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_REMOTE) ||
                    selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_TEXT) ||
                    selentry.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_MEDIA))
                continue;
            prop.put("profiles_" + count + "_name", selentry.name());
            prop.put("profiles_" + count + "_handle", selentry.handle());
            if (handle.equals(selentry.handle()))
                prop.put("profiles_" + count + "_selected", 1);
            count++;
        }
        prop.put("profiles", count);
        selentry = sb.profiles.getEntry(handle);
        
        // read post for change submit
        if ((post != null) && (selentry != null)) {
			if (post.containsKey("submit")) {
				try {
					it = labels.iterator();
					eentry tee;
					while (it.hasNext()) {
						tee = (eentry) it.next();
						String cval = (String) selentry.map().get(tee.name);
						String val = (tee.type == eentry.BOOLEAN) ? Boolean.toString(post.containsKey(tee.name)) : post.get(tee.name, cval);
						if (!cval.equals(val)) selentry.changeEntry(tee.name, val);
					}
				} catch (IOException ex) {
					prop.put("error", 1);
					prop.put("error_message", ex.getMessage());
				}
			}
		}
        
        // generate crawl profile table
        count = 0;
        int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        it = sb.profiles.profiles(true);
        plasmaCrawlProfile.entry profile;
        boolean dark = true;
        while (it.hasNext()) {
            profile = (plasmaCrawlProfile.entry) it.next();
            prop.put("crawlProfiles_"+count+"_dark", ((dark) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_name", profile.name());
            prop.put("crawlProfiles_"+count+"_startURL", profile.startURL());
            prop.put("crawlProfiles_"+count+"_handle", profile.handle());
            prop.put("crawlProfiles_"+count+"_depth", profile.generalDepth());
            prop.put("crawlProfiles_"+count+"_filter", profile.generalFilter());
            prop.put("crawlProfiles_"+count+"_crawlingIfOlder", (profile.recrawlIfOlder() == Long.MAX_VALUE) ? "no re-crawl" : ""+profile.recrawlIfOlder());
            prop.put("crawlProfiles_"+count+"_crawlingDomFilterDepth", (profile.domFilterDepth() == Integer.MAX_VALUE) ? "inactive" : Integer.toString(profile.domFilterDepth()));

            //start contrib [MN]
            int i = 0;
            String item;
            while((i <= domlistlength) && !((item = profile.domName(true, i)).equals(""))){
                if(i == domlistlength){
                    item = item + " ...";
                }
                prop.put("crawlProfiles_"+count+"_crawlingDomFilterContent_"+i+"_item", item);
                i++;
            }

            prop.put("crawlProfiles_"+count+"_crawlingDomFilterContent", i);
            //end contrib [MN]

            prop.put("crawlProfiles_"+count+"_crawlingDomMaxPages", (profile.domMaxPages() == Integer.MAX_VALUE) ? "unlimited" : ""+profile.domMaxPages());
            prop.put("crawlProfiles_"+count+"_withQuery", ((profile.crawlingQ()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_storeCache", ((profile.storeHTCache()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_indexText", ((profile.indexText()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_indexMedia", ((profile.indexMedia()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_remoteIndexing", ((profile.remoteIndexing()) ? 1 : 0));
            prop.put("crawlProfiles_"+count+"_deleteButton", (((profile.name().equals("remote")) ||
                                                               (profile.name().equals("proxy")) ||
                                                               (profile.name().equals("snippetText")) ||
                                                               (profile.name().equals("snippetMedia")) ? 0 : 1)));
            prop.put("crawlProfiles_"+count+"_deleteButton_handle", profile.handle());
            
            dark = !dark;
            count++;
        }
        prop.put("crawlProfiles", count);
        
        // generate edit field
        if (selentry == null) {
        	prop.put("edit", 0);
        } else {
        	prop.put("edit", 1);
			prop.put("edit_name", selentry.name());
			prop.put("edit_handle", selentry.handle());
			it = labels.iterator();
			count = 0;
			while (it.hasNext()) {
				eentry ee = (eentry) it.next();
				Object val = selentry.map().get(ee.name);
				prop.put("edit_entries_" + count + "_readonly", ee.readonly ? 1 : 0);
				prop.put("edit_entries_" + count + "_readonly_name", ee.name);
				prop.put("edit_entries_" + count + "_readonly_label", ee.label);
				prop.put("edit_entries_" + count + "_readonly_type", ee.type);
				if (ee.type == eentry.BOOLEAN) {
					prop.put("edit_entries_" + count + "_readonly_type_checked", Boolean.valueOf((String) val).booleanValue() ? 1 : 0);
				} else {
					prop.put("edit_entries_" + count + "_readonly_type_value", val);
				}
				count++;
			}
			prop.put("edit_entries", count);
		}
        
        return prop;
    }
}
