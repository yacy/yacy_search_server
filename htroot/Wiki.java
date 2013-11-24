// Wiki.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// Contains contributions from Alexander Schier [AS]
// and Marc Nause [MN]

// You must compile this file with
// javac -classpath .:../classes Wiki.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.data.Diff;
import net.yacy.data.wiki.WikiBoard;
import net.yacy.peers.NewsPool;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Wiki {
    private static final String ANONYMOUS = "anonymous";

    //private static String ListLevel = "";
    //private static String numListLevel = "";

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }


    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env) throws IOException {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        if (post == null) {
            post = new serverObjects();
            post.put("page", "start");
        }

        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);

        String access = sb.getConfig("WikiAccess", "admin");
        final String pagename = get(post, "page", "start");
        final String ip = get(post, HeaderFramework.CONNECTION_PROP_CLIENTIP, Domains.LOCALHOST);
        String author = get(post, "author", ANONYMOUS);
        if (author.equals(ANONYMOUS)) {
            author = WikiBoard.guessAuthor(ip);
            if (author == null) {
                author = (sb.peers.mySeed() == null) ? ANONYMOUS : sb.peers.mySeed().get(Seed.NAME, ANONYMOUS);
            }
        }

        if (post != null && post.containsKey("access")) {
            // only the administrator may change the access right
            if (!sb.verifyAuthentication(header)) {
                // check access right for admin
            	prop.authenticationRequired();
                return prop;
            }

            access = post.get("access", "admin");
            sb.setConfig("WikiAccess", access);
        }
        if (access.equals("admin")) {
            prop.put("mode_access", "0");
        } else if (access.equals("all")) {
            prop.put("mode_access", "1");
        }

        WikiBoard.Entry page = sb.wikiDB.read(pagename);

        if (post != null && post.containsKey("submit")) {

            if ((access.equals("admin") && (!sb.verifyAuthentication(header)))) {
                // check access right for admin
            	prop.authenticationRequired();
                return prop;
            }

            // store a new page
            byte[] content;
            content = UTF8.getBytes(post.get("content", ""));
            final WikiBoard.Entry newEntry = sb.wikiDB.newEntry(pagename, author, ip, post.get("reason", "edit"), content);
            sb.wikiDB.write(newEntry);
            // create a news message
            final Map<String, String> map = new HashMap<String, String>();
            map.put("page", pagename);
            map.put("author", author.replace(',', ' '));
            if (!sb.isRobinsonMode() && post.get("content", "").trim().length() > 0 && !ByteBuffer.equals(page.page(), content)) {
                sb.peers.newsPool.publishMyNews(sb.peers.mySeed(), NewsPool.CATEGORY_WIKI_UPDATE, map);
            }
            page = newEntry;
            prop.putHTML(serverObjects.ACTION_LOCATION, "/Wiki.html?page=" + pagename);
            prop.put(serverObjects.ACTION_LOCATION, prop.get(serverObjects.ACTION_LOCATION));
        }

        if (post != null && post.containsKey("edit")) {
            if ((access.equals("admin") && (!sb.verifyAuthentication(header)))) {
                // check access right for admin
            	prop.authenticationRequired();
                return prop;
            }

            prop.put("mode", "1"); //edit
            prop.putHTML("mode_author", author);
            prop.putHTML("mode_page-code", UTF8.String(page.page()));
            prop.putHTML("mode_pagename", pagename);        }

        //contributed by [MN]
        else if (post != null && post.containsKey("preview")) {
            // preview the page
            prop.put("mode", "2");//preview
            prop.putHTML("mode_pagename", pagename);
            prop.putHTML("mode_author", author);
            prop.put("mode_date", dateString(new Date()));
            prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_page", post.get("content", ""));
            prop.putHTML("mode_page-code", post.get("content", ""));
        }
        //end contrib of [MN]

        else if (post != null && post.containsKey("index")) {
            // view an index
            prop.put("mode", "3"); //Index
            try {
                final Iterator<byte[]> i = sb.wikiDB.keys(true);
                int count=0;
                while (i.hasNext()) {
                    final String subject = UTF8.String(i.next());
                    final WikiBoard.Entry entry = sb.wikiDB.read(subject);
                    prop.putHTML("mode_pages_" + count + "_name",WikiBoard.webalize(subject));
                    prop.putHTML("mode_pages_" + count + "_subject", subject);
                    prop.put("mode_pages_" + count + "_date", dateString(entry.date()));
                    prop.putHTML("mode_pages_" + count + "_author", entry.author());
                    count++;
                }
                prop.put("mode_pages", count);
            } catch (final IOException e) {
                prop.put("mode_error", "1"); //IO Error reading Wiki
                prop.putHTML("mode_error_message", e.getMessage());
            }
            prop.putHTML("mode_pagename", pagename);
        } else if (post != null && post.containsKey("diff")) {
            // Diff
            prop.put("mode", "4");
            prop.putHTML("mode_page", pagename);
            prop.putHTML("mode_error_page", pagename);

            try {
                final Iterator<byte[]> it = sb.wikiDB.keysBkp(true);
                WikiBoard.Entry entry;
                WikiBoard.Entry oentry = null;
                WikiBoard.Entry nentry = null;
                int count = 0;
                boolean oldselected = false, newselected = false;
                while (it.hasNext()) {
                    entry = sb.wikiDB.readBkp(UTF8.String(it.next()));
                    prop.put("mode_error_versions_" + count + "_date", WikiBoard.dateString(entry.date()));
                    prop.put("mode_error_versions_" + count + "_fdate", dateString(entry.date()));
                    if (WikiBoard.dateString(entry.date()).equals(post.get("old", null))) {
                        prop.put("mode_error_versions_" + count + "_oldselected", "1");
                        oentry = entry;
                        oldselected = true;
                    } else if (WikiBoard.dateString(entry.date()).equals(post.get("new", null))) {
                        prop.put("mode_error_versions_" + count + "_newselected", "1");
                        nentry = entry;
                        newselected = true;
                    }
                    count++;
                }
                count--;    // don't show current version

                if (!oldselected) { // select latest old entry
                    prop.put("mode_error_versions_" + (count - 1) + "_oldselected", "1");
                }
                if (!newselected) { // select latest new entry (== current)
                    prop.put("mode_error_curselected", "1");
                }

                if (count == 0) {
                    prop.put("mode_error", "2"); // no entries found
                } else {
                    prop.put("mode_error_versions", count);
                }

                entry = sb.wikiDB.read(pagename);
                if (entry != null) {
                    prop.put("mode_error_curdate", WikiBoard.dateString(entry.date()));
                    prop.put("mode_error_curfdate", dateString(entry.date()));
                }

                if (nentry == null) {
                    nentry = entry;
                }
                if (post.containsKey("compare") && oentry != null && nentry != null) {
                    // TODO: split into paragraphs and compare them with the same diff-algo
                    final Diff diff = new Diff(
                            UTF8.String(oentry.page()),
                            UTF8.String(nentry.page()), 3);
                    prop.put("mode_versioning_diff", net.yacy.data.Diff.toHTML(new Diff[] { diff }));
                    prop.put("mode_versioning", "1");
                } else if (post.containsKey("viewold") && oentry != null) {
                    prop.put("mode_versioning", "2");
                    prop.putHTML("mode_versioning_pagename", pagename);
                    prop.putHTML("mode_versioning_author", oentry.author());
                    prop.put("mode_versioning_date", dateString(oentry.date()));
                    prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_versioning_page", oentry.page());
                    prop.putHTML("mode_versioning_page-code", UTF8.String(oentry.page()));
                }
            } catch (final IOException e) {
                prop.put("mode_error", "1"); //IO Error reading Wiki
                prop.putHTML("mode_error_message", e.getMessage());
            }
        }

        else {
            // show page
            prop.put("mode", "0"); //viewing
            prop.putHTML("mode_pagename", pagename);
            prop.putHTML("mode_author", page.author());
            prop.put("mode_date", dateString(page.date()));
            prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_page", page.page());

            prop.put("controls", "0");
            prop.putHTML("controls_pagename", pagename);
        }

        // return rewrite properties
        return prop;
    }


    /**
     * get key from post, use dflt if (not present or post == null)
     *
     * @param post
     * @param string
     * @param string2
     * @return
     */
    private static String get(final serverObjects post, final String key, final String dflt) {
        return (post == null ? dflt : post.get(key, dflt));
    }
}
