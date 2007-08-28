// yacysearchitem.java
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

import de.anomic.http.httpHeader;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;


public class yacysearchitem {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        String eventID = post.get("eventID", "");
        int item = post.getInt("item", -1);
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        
        // find search event
        plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(eventID);
        plasmaSearchQuery theQuery = theSearch.getQuery();
        plasmaSearchRankingProfile ranking = theSearch.getRanking();

        long startprofiling = System.currentTimeMillis();
        
        // generate result object
        ArrayList accu = theSearch.computeResults(plasmaSwitchboard.blueList, true);
        
        plasmaSearchEvent.Entry result = (plasmaSearchEvent.Entry) accu.get(item);
        System.out.println("PROFILING_DEBUG: " + (System.currentTimeMillis() - startprofiling) + " millisekunden fuer item " + item);
        
        prop.put("content", 1); // switch on content
        prop.put("content_authorized", (authenticated) ? 1 : 0);
        prop.put("content_authorized_recommend", (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_ADD, "url", result.urlstring()) == null) ? 1 : 0);
        prop.put("content_authorized_recommend_deletelink", "/yacysearch.html?search=" + theQuery.queryString + "&Enter=Search&count=" + theQuery.wantedResults + "&order=" + crypt.simpleEncode(ranking.toExternalString()) + "&resource=local&time=3&deleteref=" + result.hash() + "&urlmaskfilter=.*");
        prop.put("content_authorized_recommend_recommendlink", "/yacysearch.html?search=" + theQuery.queryString + "&Enter=Search&count=" + theQuery.wantedResults + "&order=" + crypt.simpleEncode(ranking.toExternalString()) + "&resource=local&time=3&recommendref=" + result.hash() + "&urlmaskfilter=.*");
        prop.put("content_authorized_urlhash", result.hash());
        prop.put("content_description", result.title());
        prop.put("content_url", result.urlstring());
        
        int port=result.url().getPort();
        URL faviconURL;
        try {
            faviconURL = new URL(result.url().getProtocol() + "://" + result.url().getHost() + ((port != -1) ? (":" + String.valueOf(port)) : "") + "/favicon.ico");
        } catch (MalformedURLException e1) {
            faviconURL = null;
        }
        
        prop.put("content_faviconCode", sb.licensedURLs.aquireLicense(faviconURL)); // aquire license for favicon url loading
        prop.put("content_urlhash", result.hash());
        prop.put("content_urlhexhash", yacySeed.b64Hash2hexHash(result.hash()));
        prop.put("content_urlname", nxTools.shortenURLString(result.urlname(), 120));
        prop.put("content_date", plasmaSwitchboard.dateString(result.modified()));
        prop.put("content_ybr", plasmaSearchPreOrder.ybr(result.hash()));
        prop.put("content_size", Long.toString(result.filesize()));
        
        TreeSet[] query = theQuery.queryWords();
        URL wordURL = null;
        try {
            prop.put("content_words", URLEncoder.encode(query[0].toString(),"UTF-8"));
        } catch (UnsupportedEncodingException e) {}
        prop.put("content_former", theQuery.queryString);
        prop.put("content_rankingprops", result.word().toPropertyForm() + ", domLengthEstimated=" + plasmaURL.domLengthEstimation(result.hash()) +
                        ((plasmaURL.probablyRootURL(result.hash())) ? ", probablyRootURL" : "") + 
                        (((wordURL = plasmaURL.probablyWordURL(result.hash(), query[0])) != null) ? ", probablyWordURL=" + wordURL.toNormalform(false, true) : ""));
 
        /*
        // adding snippet if available
        if (result.hasSnippet()) {
            prop.put("content_snippet", result.textSnippet().getLineMarked(theQuery.queryHashes));
        } else {
            // snippet fetch timeout
            int textsnippet_timeout = Integer.parseInt(env.getConfig("timeout_media", "10000"));
                       
            // boolean line_end_with_punctuation
            boolean pre = post.get("pre", "false").equals("true");
                        
            // if 'remove' is set to true, then RWI references to URLs that do not have the snippet are removed
            boolean remove = post.get("remove", "false").equals("true");
                        
            plasmaSnippetCache.TextSnippet snippet = plasmaSnippetCache.retrieveTextSnippet(
                                result.url(), 
                                theQuery.queryHashes, 
                                true, 
                                pre, 
                                260, 
                                textsnippet_timeout
                );
                                                   
            if (snippet.getErrorCode() < 11) {
                // no problems occurred
                //prop.put("text", (snippet.exists()) ? snippet.getLineMarked(queryHashes) : "unknown");
                prop.putASIS("content_snippet", (snippet.exists()) ? snippet.getLineMarked(theQuery.queryHashes) : "unknown");
            } else {
                // problems with snippet fetch
                prop.put("content_snippet", (remove) ? plasmaSnippetCache.failConsequences(snippet, theQuery.id()) : snippet.getError());
            }
        }
        */
        prop.put("content_snippet","temporary no snippet computed");
        return prop;
    }
    
}
