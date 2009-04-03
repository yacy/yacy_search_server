// timeline.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-02-10 01:06:59 +0100 (Di, 10 Feb 2009) $
// $LastChangedRevision: 5586 $
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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.text.ReferenceContainer;
import de.anomic.kelondro.text.referencePrototype.WordReferenceRow;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.iso639;
import de.anomic.yacy.yacyCore;

public final class timeline {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        
        final String  querystring = post.get("query", "");  // a string of word hashes that shall be searched and combined
        final int     count  = Math.min((authenticated) ? 1000 : 10, post.getInt("maximumRecords", 1000)); // SRU syntax
        final int     maxdist= post.getInt("maxdist", Integer.MAX_VALUE);
        String  language = post.get("language", "");
        if (!iso639.exists(language)) {
            // take language from the user agent
            String agent = header.get("User-Agent");
            if (agent == null) agent = System.getProperty("user.language");
            language = (agent == null) ? "en" : iso639.userAgentLanguageDetection(agent);
            if (language == null) language = "en";
        }
        final TreeSet<String>[] query = plasmaSearchQuery.cleanQuery(querystring); // converts also umlaute
        
        
        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(3000);

        // prepare search
        final long timestamp = System.currentTimeMillis();
        
        // prepare an abstract result
        int indexabstractContainercount = 0;
        int joincount = 0;

        // retrieve index containers
        yacyCore.log.logInfo("INIT TIMELINE SEARCH: " + plasmaSearchQuery.anonymizedQueryHashes(query[0]) + " - " + count + " links");
        
        // get the index container with the result vector
        HashMap<String, ReferenceContainer>[] localSearchContainerMaps = sb.webIndex.localSearchContainers(query[0], query[1], null);
        final ReferenceContainer index =
            ReferenceContainer.joinExcludeContainers(
                localSearchContainerMaps[0].values(),
                localSearchContainerMaps[1].values(),
                maxdist);
        
        Iterator<WordReferenceRow> i = index.entries();
        WordReferenceRow entry;
        int c = 0;
        Date lm;
        String lms;
        while (i.hasNext() && c < count) {
            entry = i.next();
            lm = new Date(entry.lastModified());
            lms = DateFormatter.formatANSIC(lm);
            prop.put("event_" + c + "_start", lms); // like "Wed May 01 1963 00:00:00 GMT-0600"
            prop.put("event_" + c + "_end", lms); // like "Sat Jun 01 1963 00:00:00 GMT-0600"
            prop.put("event_" + c + "_isDuration", 0); // 0 (only a point) or 1 (period of time)
            prop.putHTML("event_" + c + "_title", "test"); // short title of the event
            prop.putHTML("event_" + c + "_description", ""); // long description of the event
            c++;
        }
        prop.put("event", c);
        
        // log
        yacyCore.log.logInfo("EXIT TIMELINE SEARCH: " +
                plasmaSearchQuery.anonymizedQueryHashes(query[0]) + " - " + joincount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstracts, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");
 
        return prop;
    }

}
