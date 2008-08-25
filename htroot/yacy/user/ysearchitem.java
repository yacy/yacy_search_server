// ysearchitem.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.08.2007 on http://yacy.net
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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.TreeSet;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.tools.yFormatter;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class ysearchitem {

    private static boolean col = true;
    private static final int namelength = 60;
    private static final int urllength = 120;
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        final String eventID = post.get("eventID", "");
        final boolean rss = post.get("rss", "false").equals("true");
        final int item = post.getInt("item", -1);
        final boolean auth = ((String) header.get(httpRequestHeader.CONNECTION_PROP_CLIENTIP, "")).equals("localhost") || sb.verifyAuthentication(header, true);
        
        // default settings for blank item
        prop.put("content", "0");
        prop.put("rss", "0");
        prop.put("references", "0");
        prop.put("dynamic", "0");
        
        // find search event
        final plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final plasmaSearchQuery theQuery = theSearch.getQuery();
        final int offset = theQuery.neededResults() - theQuery.displayResults();
        
        // dynamically update count values
        if (!rss) {
            prop.put("dynamic_offset", offset + 1);
            prop.put("dynamic_itemscount", (item < 0) ? theQuery.neededResults() : item + 1);
            prop.put("dynamic_totalcount", yFormatter.number(theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize(), !rss));
            prop.put("dynamic_localResourceSize", yFormatter.number(theSearch.getRankingResult().getLocalResourceSize(), !rss));
            prop.put("dynamic_remoteResourceSize", yFormatter.number(theSearch.getRankingResult().getRemoteResourceSize(), !rss));
            prop.put("dynamic_remoteIndexCount", yFormatter.number(theSearch.getRankingResult().getRemoteIndexCount(), !rss));
            prop.put("dynamic_remotePeerCount", yFormatter.number(theSearch.getRankingResult().getRemotePeerCount(), !rss));
            prop.put("dynamic_resnav", "");
            prop.put("dynamic", "1");
        }

        if (theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_TEXT) {
            // text search

            // generate result object
            final plasmaSearchEvent.ResultEntry result = theSearch.oneResult(item);
            if (result == null) return prop; // no content
                
            if (rss) {
                // text search for rss output
                prop.put("rss", "1"); // switch on specific content
                prop.putHTML("rss_title", result.title(), true);
                prop.putHTML("rss_description", result.textSnippet().getLineRaw(), true);
                prop.putHTML("rss_link", result.urlstring(), true);
                prop.put("rss_urlhash", result.hash());
                prop.put("rss_date", plasmaSwitchboard.dateString822(result.modified()));
                return prop;
            }
            
            prop.put("content", theQuery.contentdom + 1); // switch on specific content
            prop.putHTML("content_description", result.title());
            prop.put("content_url", result.urlstring());
        
            final int port=result.url().getPort();
            yacyURL faviconURL;
            try {
                faviconURL = new yacyURL(result.url().getProtocol() + "://" + result.url().getHost() + ((port != -1) ? (":" + String.valueOf(port)) : "") + "/favicon.ico", null);
            } catch (final MalformedURLException e1) {
                faviconURL = null;
            }
        
            prop.putHTML("content_faviconCode", sb.licensedURLs.aquireLicense(faviconURL)); // aquire license for favicon url loading
            prop.put("content_urlhash", result.hash());
            prop.put("content_urlhexhash", yacySeed.b64Hash2hexHash(result.hash()));
            prop.putHTML("content_urlname", nxTools.shortenURLString(result.urlname(), urllength));
            prop.put("content_date", plasmaSwitchboard.dateString(result.modified()));
            prop.put("content_ybr", plasmaSearchRankingProcess.ybr(result.hash()));
            prop.putNum("content_size", result.filesize());
        
            final TreeSet<String>[] query = theQuery.queryWords();
            yacyURL wordURL = null;
            try {
                prop.putHTML("content_words", URLEncoder.encode(query[0].toString(),"UTF-8"));
            } catch (final UnsupportedEncodingException e) {}
            prop.putHTML("content_former", theQuery.queryString);
            prop.put("content_rankingprops", result.word().toPropertyForm() + ", domLengthEstimated=" + yacyURL.domLengthEstimation(result.hash()) +
                    ((yacyURL.probablyRootURL(result.hash())) ? ", probablyRootURL" : "") + 
                    (((wordURL = yacyURL.probablyWordURL(result.hash(), query[0])) != null) ? ", probablyWordURL=" + wordURL.toNormalform(false, true) : ""));
            final plasmaSnippetCache.TextSnippet snippet = result.textSnippet();
            prop.put("content_snippet", (snippet == null) ? "(snippet not found)" : snippet.getLineMarked(theQuery.queryHashes));
            return prop;
        }
        
        if (theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) {
            // image search; shows thumbnails

            prop.put("content", theQuery.contentdom + 1); // switch on specific content
            final plasmaSnippetCache.MediaSnippet ms = theSearch.oneImage(item);
            if (ms == null) {
                prop.put("content_items", "0");
            } else {
                prop.putHTML("content_items_0_hrefCache", (auth) ? "/ViewImage.png?url=" + ms.href.toNormalform(true, false) : ms.href.toNormalform(true, false));
                prop.putHTML("content_items_0_href", ms.href.toNormalform(true, false));
                prop.put("content_items_0_code", sb.licensedURLs.aquireLicense(ms.href));
                prop.putHTML("content_items_0_name", shorten(ms.name, namelength));
                prop.put("content_items_0_attr", (ms.attr.equals("-1 x -1")) ? "" : "(" + ms.attr + ")"); // attributes, here: original size of image
                prop.put("content_items_0_source", ms.source.toNormalform(true, false));
                prop.put("content_items_0_sourcedom", ms.source.getHost());
                prop.put("content_items", 1);
            }
            return prop;
        }
        
        if ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) ||
            (theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) ||
            (theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_APP)) {
            // any other media content

            // generate result object
            final plasmaSearchEvent.ResultEntry result = theSearch.oneResult(item);
            if (result == null) return prop; // no content
            
            prop.put("content", theQuery.contentdom + 1); // switch on specific content
            final ArrayList<plasmaSnippetCache.MediaSnippet> media = result.mediaSnippets();
            if (item == 0) col = true;
            if (media != null) {
                plasmaSnippetCache.MediaSnippet ms;
                int c = 0;
                for (int i = 0; i < media.size(); i++) {
                    ms = media.get(i);
                    prop.putHTML("content_items_" + i + "_href", ms.href.toNormalform(true, false));
                    prop.putHTML("content_items_" + i + "_hrefshort", nxTools.shortenURLString(ms.href.toNormalform(true, false), urllength));
                    prop.putHTML("content_items_" + i + "_name", shorten(ms.name, namelength));
                    prop.put("content_items_" + i + "_col", (col) ? "0" : "1");
                    c++;
                    col = !col;
                }
                prop.put("content_items", c);
            } else {
                prop.put("content_items", "0");
            }
            return prop;
        }

        return prop;
    }
    
    private static String shorten(final String s, final int length) {
        if (s.length() <= length) return s;
        final int p = s.lastIndexOf('.');
        if (p < 0) return s.substring(0, length - 3) + "...";
        return s.substring(0, length - (s.length() - p) - 3) + "..." + s.substring(p);
    }

}
