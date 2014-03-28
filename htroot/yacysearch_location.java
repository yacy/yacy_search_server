// yacysearch_location.java
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 09.05.2010 in Frankfurt, Germany on http://yacy.net
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

import java.util.Date;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.federate.opensearch.SRURSSConnector;
import net.yacy.cora.geo.GeoLocation;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class yacysearch_location {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        prop.put("kml", 0);

        if (header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("kml") ||
            header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("xml") ||
            header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("rss")
           ) {
            // generate a kml output page
            prop.put("kml", 1);
            if (post == null) return prop;
            final String query = post.get("query", "");
            final boolean search_query = post.get("dom", "").indexOf("location",0) >= 0;
            final boolean metatag = post.get("dom", "").indexOf("metatag",0) >= 0;
            final boolean alltext = post.get("dom", "").indexOf("alltext",0) >= 0;
            final boolean search_title = alltext || post.get("dom", "").indexOf("title",0) >= 0;
            final boolean search_publisher = alltext || post.get("dom", "").indexOf("publisher",0) >= 0;
            final boolean search_creator = alltext || post.get("dom", "").indexOf("creator",0) >= 0;
            final boolean search_subject = alltext || post.get("dom", "").indexOf("subject",0) >= 0;
            final long maximumTime = post.getLong("maximumTime", 5000);
            final int maximumRecords = post.getInt("maximumRecords", 6000);
            final double lon = post.getDouble("lon", 0.0d);
            final double lat = post.getDouble("lat", 0.0d);
            final double radius = post.getDouble("r", 0.0d);
            //i.e. http://localhost:8090/yacysearch_location.kml?query=berlin&maximumTime=2000&maximumRecords=100

            int placemarkCounter = 0;
            if (query.length() > 0 && search_query) {
                final Set<GeoLocation> locations = LibraryProvider.geoLoc.find(query, true);
                for (final String qp: query.split(" ")) {
                    locations.addAll(LibraryProvider.geoLoc.find(qp, true));
                }
                for (final GeoLocation location: locations) {
                    // write for all locations a point to this message
                    prop.put("kml_placemark_" + placemarkCounter + "_location", location.getName());
                    prop.put("kml_placemark_" + placemarkCounter + "_name", location.getName());
                    prop.put("kml_placemark_" + placemarkCounter + "_author", "");
                    prop.put("kml_placemark_" + placemarkCounter + "_copyright", "");
                    prop.put("kml_placemark_" + placemarkCounter + "_subject", "");
                    prop.put("kml_placemark_" + placemarkCounter + "_description", "");
                    prop.put("kml_placemark_" + placemarkCounter + "_date", "");
                    prop.putXML("kml_placemark_" + placemarkCounter + "_url", "http://" + sb.peers.mySeed().getPublicAddress() + "/yacysearch.html?query=" + location.getName());
                    prop.put("kml_placemark_" + placemarkCounter + "_pointname", location.getName());
                    prop.put("kml_placemark_" + placemarkCounter + "_lon", location.lon());
                    prop.put("kml_placemark_" + placemarkCounter + "_lat", location.lat());
                    placemarkCounter++;
                }
            }

            if (query.length() > 0 && (metatag || search_title || search_publisher || search_creator || search_subject)) try {
                // get a queue of search results
                final String rssSearchServiceURL = "http://127.0.0.1:" + sb.getConfig("port", "8090") + "/yacysearch.rss";
                final BlockingQueue<RSSMessage> results = new LinkedBlockingQueue<RSSMessage>();
                SRURSSConnector.searchSRURSS(results, rssSearchServiceURL, lon == 0.0d && lat == 0.0d ? query : query + " /radius/" + lat + "/" + lon + "/" + radius, maximumTime, Integer.MAX_VALUE, null, false, ClientIdentification.yacyInternetCrawlerAgent);

                // take the results and compute some locations
                RSSMessage message;
                loop: while ((message = results.poll(maximumTime, TimeUnit.MILLISECONDS)) != RSSMessage.POISON) {
                    if (message == null) break loop;
                    prop.put("kml_placemark_" + placemarkCounter + "_location", message.getTitle());
                    prop.put("kml_placemark_" + placemarkCounter + "_name", message.getTitle());
                    prop.put("kml_placemark_" + placemarkCounter + "_author", message.getAuthor());
                    prop.put("kml_placemark_" + placemarkCounter + "_copyright", message.getCopyright());
                    prop.put("kml_placemark_" + placemarkCounter + "_subject", message.getSubject());
                    prop.put("kml_placemark_" + placemarkCounter + "_description", message.getDescriptions().size() > 0 ? message.getDescriptions().get(0) : "");
                    prop.put("kml_placemark_" + placemarkCounter + "_date", message.getPubDate());
                    prop.putXML("kml_placemark_" + placemarkCounter + "_url", message.getLink());
                    prop.put("kml_placemark_" + placemarkCounter + "_pointname", message.getTitle());
                    prop.put("kml_placemark_" + placemarkCounter + "_lon", message.getLon());
                    prop.put("kml_placemark_" + placemarkCounter + "_lat", message.getLat());
                    placemarkCounter++;
                    if (placemarkCounter >= maximumRecords) break loop;
                }
            } catch (final InterruptedException e) {}
            prop.put("kml_placemark", placemarkCounter);
        }
        if (header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("rss")) {
            if (post == null) return prop;
            String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
            if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
            String hostName = header.get("Host", Domains.LOCALHOST);
            if (hostName.indexOf(':',0) == -1) hostName += ":" + env.getConfig("port", "8090");
            final String originalquerystring = (post == null) ? "" : post.get("query", post.get("search", "")).trim(); // SRU compliance
            final boolean global = post.get("kml_resource", "local").equals("global");

            prop.put("kml_date822", HeaderFramework.formatRFC1123(new Date()));
            prop.put("kml_promoteSearchPageGreeting", promoteSearchPageGreeting);
            prop.put("kml_rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.png");
            prop.put("kml_searchBaseURL", "http://" + hostName + "/yacysearch_location.rss");
            prop.putXML("kml_rss_query", originalquerystring);
            prop.put("kml_rss_queryenc", originalquerystring.replace(' ', '+'));
            prop.put("kml_resource", global ? "global" : "local");
            prop.put("kml_contentdom", (post == null ? "text" : post.get("contentdom", "text")));
            prop.put("kml_verify", (post == null) ? "true" : post.get("verify", "true"));

        }
        if (header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("html")) {
            prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
            prop.put("promoteSearchPageGreeting", sb.getConfig(SwitchboardConstants.GREETING, ""));
            if (post == null || post.get("query") == null) {
                prop.put("initsearch", 0);
            } else {
                prop.put("initsearch", 1);
                prop.put("initsearch_query", post.get("query"));
            }
        }

        // return rewrite properties
        return prop;
    }

}
