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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.opensearch.SRURSSConnector;
import net.yacy.document.LibraryProvider;
import net.yacy.document.geolocalization.Location;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

import java.util.Date;

public class yacysearch_location {
    
    private static final String space = " ";
    
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
            String query = post.get("query", "");
            boolean search_query = post.get("dom", "").indexOf("query") >= 0;
            boolean metatag = post.get("dom", "").indexOf("metatag") >= 0;
            boolean alltext = post.get("dom", "").indexOf("alltext") >= 0;
            boolean search_title = alltext || post.get("dom", "").indexOf("title") >= 0;
            boolean search_publisher = alltext || post.get("dom", "").indexOf("publisher") >= 0;
            boolean search_creator = alltext || post.get("dom", "").indexOf("creator") >= 0;
            boolean search_subject = alltext || post.get("dom", "").indexOf("subject") >= 0;
            long maximumTime = post.getLong("maximumTime", 5000);
            int maximumRecords = post.getInt("maximumRecords", 3000);
            //i.e. http://localhost:8090/yacysearch_location.kml?query=berlin&maximumTime=2000&maximumRecords=100
            
            int placemarkCounter = 0;
            if (search_query) {
                Set<Location> locations = LibraryProvider.geoLoc.find(query, true);
                for (String qp: query.split(" ")) {
                    locations.addAll(LibraryProvider.geoLoc.find(qp, true));
                }
                for (Location location: locations) {
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
            
            if (metatag || search_title || search_publisher || search_creator || search_subject) try {
                // get a queue of search results
                String rssSearchServiceURL = "http://127.0.0.1:" + sb.getConfig("port", "8090") + "/yacysearch.rss";
                BlockingQueue<RSSMessage> results = new LinkedBlockingQueue<RSSMessage>();
                SRURSSConnector.searchSRURSS(results, rssSearchServiceURL, query, maximumTime, Integer.MAX_VALUE, null, false, null);
                
                // take the results and compute some locations
                RSSMessage message;
                loop: while ((message = results.poll(maximumTime, TimeUnit.MILLISECONDS)) != RSSMessage.POISON) {

                    // find all associated locations
                    Set<Location> locations = new HashSet<Location>();
                    StringBuilder words = new StringBuilder(120);
                    if (search_title) words.append(message.getTitle().trim()).append(space);
                    if (search_publisher) words.append(message.getCopyright().trim()).append(space);
                    if (search_creator) words.append(message.getAuthor().trim()).append(space);
                    String subject = "";
                    assert message != null;
                    assert message.getSubject() != null;
                    for (String s: message.getSubject()) subject += s.trim() + space;
                    if (search_subject) words.append(subject).append(space);
                    String[] wordlist = words.toString().trim().split(space);
                    for (String word: wordlist) if (word.length() >= 3) locations.addAll(LibraryProvider.geoLoc.find(word, true));
                    for (int i = 0; i < wordlist.length - 1; i++) locations.addAll(LibraryProvider.geoLoc.find(wordlist[i] + space + wordlist[i + 1], true));
                    for (int i = 0; i < wordlist.length - 2; i++) locations.addAll(LibraryProvider.geoLoc.find(wordlist[i] + space + wordlist[i + 1] + space + wordlist[i + 2], true));
                    
                    // add locations from metatag
                    if (metatag) {
                        if (message.getLat() != 0.0f && message.getLon() != 0.0f) {
                            locations.add(new Location(message.getLon(), message.getLat(), message.getTitle().trim()));
                        }
                    }
                    
                    for (Location location: locations) {
                        // write for all locations a point to this message
                        prop.put("kml_placemark_" + placemarkCounter + "_location", location.getName());
                        prop.put("kml_placemark_" + placemarkCounter + "_name", message.getTitle());
                        prop.put("kml_placemark_" + placemarkCounter + "_author", message.getAuthor());
                        prop.put("kml_placemark_" + placemarkCounter + "_copyright", message.getCopyright());
                        prop.put("kml_placemark_" + placemarkCounter + "_subject", subject.trim());
                        prop.put("kml_placemark_" + placemarkCounter + "_description", message.getDescription());
                        prop.put("kml_placemark_" + placemarkCounter + "_date", message.getPubDate());
                        prop.putXML("kml_placemark_" + placemarkCounter + "_url", message.getLink());
                        prop.put("kml_placemark_" + placemarkCounter + "_pointname", location.getName());
                        prop.put("kml_placemark_" + placemarkCounter + "_lon", location.lon());
                        prop.put("kml_placemark_" + placemarkCounter + "_lat", location.lat());
                        placemarkCounter++;
                        if (placemarkCounter >= maximumRecords) break loop;
                    }
                }
            } catch (InterruptedException e) {}
            prop.put("kml_placemark", placemarkCounter);
        }
        if (header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("rss")) {
            if (post == null) return prop;
            String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
            if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
            String hostName = header.get("Host", "localhost");
            if (hostName.indexOf(':') == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8090"));
            final String originalquerystring = (post == null) ? "" : post.get("query", post.get("search", "")).trim(); // SRU compliance
            final boolean global = post.get("kml_resource", "local").equals("global");

            prop.put("kml_date822", HeaderFramework.formatRFC1123(new Date()));
            prop.put("kml_promoteSearchPageGreeting", promoteSearchPageGreeting);
            prop.put("kml_rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");
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
            prop.put("promoteSearchPageGreeting.homepage", sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
            prop.put("promoteSearchPageGreeting.smallImage", sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
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
