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

import net.yacy.document.content.RSSMessage;
import net.yacy.document.geolocalization.Location;
import de.anomic.data.LibraryProvider;
import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;

public class yacysearch_location {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        //final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        prop.put("kml", 0);
        if (post == null) return prop;
        
        if (header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("kml") || header.get(HeaderFramework.CONNECTION_PROP_EXT, "").equals("xml")) {
            // generate a kml output page
            prop.put("kml", 1);
            String query = post.get("query", "");
            long maximumTime = post.getLong("maximumTime", 1000);
            int maximumRecords = post.getInt("maximumRecords", 100);
            //i.e. http://localhost:8080/yacysearch_location.kml?query=berlin&maximumTime=2000&maximumRecords=100
            
            // get a queue of search results
            BlockingQueue<RSSMessage> results = yacyClient.search(null, query, false, false, maximumTime, Integer.MAX_VALUE);
            
            // take the results and compute some locations
            RSSMessage message;
            int placemarkCounter = 0;
            try {
                loop: while ((message = results.take()) != RSSMessage.POISON) {
                    // find all associated locations
                    Set<Location> locations = new HashSet<Location>();
                    String words = message.getTitle() + " " + message.getCopyright() + " " + message.getAuthor();
                    String subject = "";
                    for (String s: message.getSubject()) subject += " " + s;
                    words += subject;
                    for (String word: words.split(" ")) if (word.length() >= 3) locations.addAll(LibraryProvider.geoDB.find(word, true, true, false, false, false));

                    String locnames = "";
                    for (Location location: locations) locnames += ", " + location.getName();
                    if (locations.size() > 0) locnames = locnames.substring(2);
                    
                    for (Location location: locations) {
                        // write for all locations a point to this message
                        prop.put("kml_placemark_" + placemarkCounter + "_location", locnames);
                        prop.put("kml_placemark_" + placemarkCounter + "_name", message.getTitle());
                        prop.put("kml_placemark_" + placemarkCounter + "_author", message.getAuthor());
                        prop.put("kml_placemark_" + placemarkCounter + "_copyright", message.getCopyright());
                        prop.put("kml_placemark_" + placemarkCounter + "_subject", subject.trim());
                        prop.put("kml_placemark_" + placemarkCounter + "_description", message.getDescription());
                        prop.put("kml_placemark_" + placemarkCounter + "_date", message.getPubDate());
                        prop.put("kml_placemark_" + placemarkCounter + "_url", message.getLink());
                        prop.put("kml_placemark_" + placemarkCounter + "_pointname", location.getName());
                        prop.put("kml_placemark_" + placemarkCounter + "_lon", location.lon());
                        prop.put("kml_placemark_" + placemarkCounter + "_lat", location.lat());
                        placemarkCounter++;
                        if (placemarkCounter >= maximumRecords) break loop;
                    }
                }
                prop.put("kml_placemark", placemarkCounter);
            } catch (InterruptedException e) {}
        }
        // return rewrite properties
        return prop;
    }
    
}
