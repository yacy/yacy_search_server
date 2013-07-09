// News.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 29.07.2005
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

// You must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.NewsDB;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class News {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final boolean overview = (post == null) || "0".equals(post.get("page", "0"));
        final int tableID = (overview) ? -1 : (post == null ? 0 : post.getInt("page", 0)) - 1;

        // execute commands
        if (post != null) {

            if ((post.containsKey("deletespecific")) && (tableID >= 0)) {
                if (sb.adminAuthenticated(header) < 2) {
                	prop.authenticationRequired();
                    return prop; // this button needs authentication, force log-in
                }
                final Iterator<String> e = post.keySet().iterator();
                String check;
                String id;
                while (e.hasNext()) {
                    check = e.next();
                    if ((check.startsWith("del_")) && "on".equals(post.get(check, "off"))) {
                        id = check.substring(4);
                        try {
                            sb.peers.newsPool.moveOff(tableID, id);
                        } catch (final Exception ee) {ConcurrentLog.logException(ee);}
                    }
                }
            }

            if ((post.containsKey("deleteall")) && (tableID >= 0)) {
                if (sb.adminAuthenticated(header) < 2) {
                	prop.authenticationRequired();
                    return prop; // this button needs authentication, force log-in
                }
                try {
                    if ((tableID == NewsPool.PROCESSED_DB) || (tableID == NewsPool.PUBLISHED_DB)) {
                        sb.peers.newsPool.clear(tableID);
                    } else {
                        sb.peers.newsPool.moveOffAll(tableID);
                    }
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

        // generate properties for output
        if (overview) {
            // show overview
            prop.put("table", "0");
            prop.put("page", "0");
            prop.putNum("table_insize", sb.peers.newsPool.size(NewsPool.INCOMING_DB));
            prop.putNum("table_prsize", sb.peers.newsPool.size(NewsPool.PROCESSED_DB));
            prop.putNum("table_ousize", sb.peers.newsPool.size(NewsPool.OUTGOING_DB));
            prop.putNum("table_pusize", sb.peers.newsPool.size(NewsPool.PUBLISHED_DB));
        } else {
            // generate table
            prop.put("table", "1");
            prop.put("page", tableID + 1);
            prop.put("table_page", tableID + 1);

            if (sb.peers != null) {
                final int maxCount = Math.min(1000, sb.peers.newsPool.size(tableID));
                final Iterator<NewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(tableID);
                NewsDB.Record record;
                Seed seed;
                int i = 0;
                while ((recordIterator.hasNext()) && (i < maxCount)) {
                    record = recordIterator.next();
                    if (record == null) continue;

                    seed = sb.peers.getConnected(record.originator());
                    if (seed == null) seed = sb.peers.getDisconnected(record.originator());
                    final String category = record.category();
                    prop.put("table_list_" + i + "_id", record.id());
                    prop.putHTML("table_list_" + i + "_ori", (seed == null) ? record.originator() : seed.getName());
                    prop.put("table_list_" + i + "_cre", GenericFormatter.SHORT_SECOND_FORMATTER.format(record.created()));
                    prop.put("table_list_" + i + "_crerfcdate", HeaderFramework.formatRFC1123(record.created()));
                    prop.putHTML("table_list_" + i + "_cat", category);
                    prop.put("table_list_" + i + "_rec", (record.received() == null) ? "-" : GenericFormatter.SHORT_SECOND_FORMATTER.format(record.received()));
                    prop.put("table_list_" + i + "_dis", record.distributed());

                    final Map<String, String> attributeMap = record.attributes();
                    prop.putHTML("table_list_" + i + "_att", attributeMap.toString());
                    int j = 0;
                    if (!attributeMap.isEmpty()) {
                        for (final Entry<String, String> attribute: attributeMap.entrySet()) {
                            prop.put("table_list_" + i + "_attributes_" + j + "_name", attribute.getKey());
                            prop.putHTML("table_list_" + i + "_attributes_" + j + "_value", attribute.getValue());
                            j++;
                        }
                    }
                    prop.put("table_list_" + i + "_attributes", j);

                    // generating link / title / description (taken over from Surftips.java)
                    String link, title, description;
                    if (category.equals(NewsPool.CATEGORY_CRAWL_START)) {
                    	link = record.attribute("startURL", "");
                    	title = (record.attribute("intention", "").isEmpty()) ? link : record.attribute("intention", "");
                    	description = "Crawl Start Point";
                    } else if (category.equals(NewsPool.CATEGORY_PROFILE_UPDATE)) {
                    	link = record.attribute("homepage", "");
                    	title = "Home Page of " + record.attribute("nickname", "");
                    	description = "Profile Update";
                    } else if (category.equals(NewsPool.CATEGORY_BOOKMARK_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = "Bookmark: " + record.attribute("description", "");
                    } else if (category.equals(NewsPool.CATEGORY_SURFTIPP_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = "Surf Tipp: " + record.attribute("description", "");
                    } else if (category.equals(NewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = record.attribute("url", "");
                    } else if (category.equals(NewsPool.CATEGORY_WIKI_UPDATE)) {
                    	link = (seed == null)? "" : "http://" + seed.getPublicAddress() + "/Wiki.html?page=" + record.attribute("page", "");
                    	title = record.attribute("author", "Anonymous") + ": " + record.attribute("page", "");
                    	description = "Wiki Update: " + record.attribute("description", "");
                    } else if (category.equals(NewsPool.CATEGORY_BLOG_ADD)) {
                    	link = (seed == null)? "" : "http://" + seed.getPublicAddress() + "/Blog.html?page=" + record.attribute("page", "");
                    	title = record.attribute("author", "Anonymous") + ": " + record.attribute("page", "");
                    	description = "Blog Entry: " + record.attribute("subject", "");
                    } else {
                    	link = "";
                    	title = "";
                    	description = "";
                    }
                    prop.putHTML("table_list_" + i + "_link", link);
                    prop.putHTML("table_list_" + i + "_title", title);
                    prop.putHTML("table_list_" + i + "_description", description);

                    i++;
                }
                prop.put("table_list", i);
            }
        }

        // adding the peer address
        prop.put("address", sb.peers.mySeed().getPublicAddress());

        // return rewrite properties
        return prop;
    }
}
