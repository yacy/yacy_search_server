// yacysearchitem.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 28.08.2007 on http://yacy.net
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.TreeSet;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.EventTracker;
import net.yacy.kelondro.util.Formatter;

import de.anomic.search.ContentDomain;
import de.anomic.search.MediaSnippet;
import de.anomic.search.QueryParams;
import de.anomic.search.SearchEvent;
import de.anomic.search.ResultEntry;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.search.TextSnippet;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.graphics.ProfilingGraph;


public class yacysearchitem {

    private static boolean col = true;
    private static final int namelength = 60;
    private static final int urllength = 120;
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        final String eventID = post.get("eventID", "");
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int item = post.getInt("item", -1);
        final boolean auth = (header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "")).equals("localhost") || sb.verifyAuthentication(header, true);
        final int display = (post == null) ? 0 : post.getInt("display", 0);
        
        // default settings for blank item
        prop.put("content", "0");
        prop.put("rss", "0");
        prop.put("references", "0");
        prop.put("rssreferences", "0");
        prop.put("dynamic", "0");
        boolean isHtml = header.get(HeaderFramework.CONNECTION_PROP_PATH).endsWith(".html");
        
        // find search event
        final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
        if (theSearch == null) {
            // the event does not exist, show empty page
            return prop;
        }
        final QueryParams theQuery = theSearch.getQuery();
        
        // dynamically update count values
        final int totalcount = theSearch.getRankingResult().getLocalIndexCount() + theSearch.getRankingResult().getRemoteResourceSize();
        final int offset = theQuery.neededResults() - theQuery.displayResults() + 1;
        prop.put("offset", offset);
        prop.put("itemscount", Formatter.number(Math.min((item < 0) ? theQuery.neededResults() : item + 1, totalcount)));
        prop.put("totalcount", Formatter.number(totalcount, true));
        prop.put("localResourceSize", Formatter.number(theSearch.getRankingResult().getLocalIndexCount(), true));
        prop.put("remoteResourceSize", Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
        prop.put("remoteIndexCount", Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
        prop.put("remotePeerCount", Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));
        
        if (theQuery.contentdom == ContentDomain.TEXT) {
            // text search

            // generate result object
            final ResultEntry result = theSearch.oneResult(item, theQuery.isLocal() ? 1000 : 5000);
            if (result == null) return prop; // no content

            
            final int port=result.url().getPort();
            DigestURI faviconURL = null;
            if (isHtml && !sb.isIntranetMode() && !result.url().isLocal()) try {
                faviconURL = new DigestURI(result.url().getProtocol() + "://" + result.url().getHost() + ((port != -1) ? (":" + port) : "") + "/favicon.ico");
            } catch (final MalformedURLException e1) {
                Log.logException(e1);
                faviconURL = null;
            }
            
            prop.put("content", 1); // switch on specific content
            
            prop.put("content_authorized", authenticated ? "1" : "0");
            prop.put("content_authorized_recommend", (sb.peers.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_ADD, "url", result.urlstring()) == null) ? "1" : "0");
            prop.putHTML("content_authorized_recommend_deletelink", "/yacysearch.html?query=" + theQuery.queryString.replace(' ', '+') + "&Enter=Search&count=" + theQuery.displayResults() + "&offset=" + (theQuery.neededResults() - theQuery.displayResults()) + "&order=" + crypt.simpleEncode(theQuery.ranking.toExternalString()) + "&resource=local&time=3&deleteref=" + new String(result.hash()) + "&urlmaskfilter=.*");
            prop.putHTML("content_authorized_recommend_recommendlink", "/yacysearch.html?query=" + theQuery.queryString.replace(' ', '+') + "&Enter=Search&count=" + theQuery.displayResults() + "&offset=" + (theQuery.neededResults() - theQuery.displayResults()) + "&order=" + crypt.simpleEncode(theQuery.ranking.toExternalString()) + "&resource=local&time=3&recommendref=" + new String(result.hash()) + "&urlmaskfilter=.*");
            prop.put("content_authorized_urlhash", new String(result.hash()));
            String resulthashString = new String(result.hash());
            prop.putHTML("content_title", result.title());
            prop.putXML("content_title-xml", result.title());
            prop.putJSON("content_title-json", result.title());
            prop.putHTML("content_link", result.urlstring());
            prop.putHTML("content_target", sb.getConfig(SwitchboardConstants.SEARCH_TARGET, "_self"));
            prop.put("content_display", display);
            if (faviconURL != null && isHtml) sb.loader.loadIfNotExistBackground(faviconURL.toNormalform(true, false), 1024 * 1024 * 10);
            prop.putHTML("content_faviconCode", sb.licensedURLs.aquireLicense(faviconURL)); // acquire license for favicon url loading
            prop.put("content_urlhash", resulthashString);
            prop.put("content_urlhexhash", yacySeed.b64Hash2hexHash(resulthashString));
            prop.putHTML("content_urlname", nxTools.shortenURLString(result.urlname(), urllength));
            prop.put("content_date", DateFormatter.formatRFC1123_short(result.modified()));
            prop.put("content_date822", HeaderFramework.formatRFC1123(result.modified()));
            //prop.put("content_ybr", RankingProcess.ybr(result.hash()));
            prop.putHTML("content_size", Integer.toString(result.filesize())); // we don't use putNUM here because that number shall be usable as sorting key. To print the size, use 'sizename'
            prop.putHTML("content_sizename", sizename(result.filesize()));
            prop.putHTML("content_host", result.url().getHost() == null ? "" : result.url().getHost());
            prop.putHTML("content_file", result.url().getFile());
            prop.putHTML("content_path", result.url().getPath());
            prop.put("content_nl", (item == 0) ? 0 : 1);
            prop.putHTML("content_publisher", result.publisher());
            prop.putHTML("content_creator", result.creator());// author
            prop.putHTML("content_subject", result.subject());
            final TreeSet<String>[] query = theQuery.queryWords();
            String s = ""; for (String t: query[0]) s += "+" + t;
            if (s.length() > 0) s = s.substring(1);
            prop.putHTML("content_words", s);
            prop.putHTML("content_former", theQuery.queryString);
            final TextSnippet snippet = result.textSnippet();
            final String desc = (snippet == null) ? "" : snippet.getLineMarked(theQuery.fullqueryHashes);
            prop.put("content_description", desc);
            prop.putXML("content_description-xml", desc);
            prop.putJSON("content_description-json", desc);
            SearchEvent.HeuristicResult heuristic = theSearch.getHeuristic(result.hash());
            if (heuristic == null) {
                prop.put("content_heuristic", 0);
            } else {
                if (heuristic.redundant) {
                    prop.put("content_heuristic", 1);
                } else {
                    prop.put("content_heuristic", 2);
                }
                prop.put("content_heuristic_name", heuristic.heuristicName);
            }
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.searchEvent(theQuery.id(true), SearchEvent.Type.FINALIZATION, "" + item, 0, 0), false);
            
            return prop;
        }
        
        if (theQuery.contentdom == ContentDomain.IMAGE) {
            // image search; shows thumbnails

            prop.put("content", theQuery.contentdom.getCode() + 1); // switch on specific content
            final MediaSnippet ms = theSearch.result().oneImage(item);
            if (ms == null) {
                prop.put("content_item", "0");
            } else {
                sb.loader.loadIfNotExistBackground(ms.href.toNormalform(true, false), 1024 * 1024 * 10);
                prop.putHTML("content_item_hrefCache", (auth) ? "/ViewImage.png?url=" + ms.href.toNormalform(true, false) : ms.href.toNormalform(true, false));
                prop.putHTML("content_item_href", ms.href.toNormalform(true, false));
                prop.put("content_item_code", sb.licensedURLs.aquireLicense(ms.href));
                prop.putHTML("content_item_name", shorten(ms.name, namelength));
                prop.put("content_item_mimetype", ms.mime);
                prop.put("content_item_fileSize", ms.fileSize);
                prop.put("content_item_width", ms.width);
                prop.put("content_item_height", ms.height);
                prop.put("content_item_attr", (ms.attr.equals("-1 x -1")) ? "" : "(" + ms.attr + ")"); // attributes, here: original size of image
                prop.put("content_item_urlhash", new String(ms.source.hash()));
                prop.put("content_item_source", ms.source.toNormalform(true, false));
                prop.putXML("content_item_source-xml", ms.source.toNormalform(true, false));
                prop.put("content_item_sourcedom", ms.source.getHost());
                prop.put("content_item_nl", (item == 0) ? 0 : 1);
                prop.put("content_item", 1);
            }
            return prop;
        }
        
        if ((theQuery.contentdom == ContentDomain.AUDIO) ||
            (theQuery.contentdom == ContentDomain.VIDEO) ||
            (theQuery.contentdom == ContentDomain.APP)) {
            // any other media content

            // generate result object
            final ResultEntry result = theSearch.oneResult(item, 500);
            if (result == null) return prop; // no content
            
            prop.put("content", theQuery.contentdom.getCode() + 1); // switch on specific content
            final ArrayList<MediaSnippet> media = result.mediaSnippets();
            if (item == 0) col = true;
            if (media != null) {
                int c = 0;
                for (MediaSnippet ms : media) {
                    prop.putHTML("content_items_" + c + "_href", ms.href.toNormalform(true, false));
                    prop.putHTML("content_items_" + c + "_hrefshort", nxTools.shortenURLString(ms.href.toNormalform(true, false), urllength));
                    prop.putHTML("content_items_" + c + "_name", shorten(ms.name, namelength));
                    prop.put("content_items_" + c + "_col", (col) ? "0" : "1");
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
        assert p >= 0;
        assert length - (s.length() - p) - 3 >= 0: "length = " + length + ", s.length() = " + s.length() + ", p = " + p;
        return s.substring(0, length - (s.length() - p) - 3) + "..." + s.substring(p); // TODO check oob
    }
    
    private static String sizename(int size) {
        if (size < 1024) return size + " bytes";
        size = size / 1024;
        if (size < 1024) return size + " kbyte";
        size = size / 1024;
        if (size < 1024) return size + " mbyte";
        size = size / 1024;
        return size + " gbyte";
    }
}
