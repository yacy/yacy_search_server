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
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.kelondro.util.ISO639;
import net.yacy.peers.Network;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryParams;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class timeline {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;

        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;

        Segment segment = sb.index;

        final String  querystring = post.get("query", "");  // a string of word hashes that shall be searched and combined
        final int     count  = Math.min((authenticated) ? 1000 : 10, post.getInt("maximumRecords", 1000)); // SRU syntax
        final int     maxdist= post.getInt("maxdist", Integer.MAX_VALUE);
        String  language = post.get("language", "");
        if (!ISO639.exists(language)) {
            // take language from the user agent
            String agent = header.get("User-Agent");
            if (agent == null) agent = System.getProperty("user.language");
            language = (agent == null) ? "en" : ISO639.userAgentLanguageDetection(agent);
            if (language == null) language = "en";
        }
        final QueryGoal qg = new QueryGoal(querystring);
        HandleSet q = qg.getIncludeHashes();

        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(3000);

        // prepare search
        final long timestamp = System.currentTimeMillis();

        // prepare an abstract result
        int indexabstractContainercount = 0;
        int joincount = 0;

        // retrieve index containers
        //yacyCore.log.logInfo("INIT TIMELINE SEARCH: " + plasmaSearchQuery.anonymizedQueryHashes(query[0]) + " - " + count + " links");

        // get the index container with the result vector
        TermSearch<WordReference> search = null;
        try {
            search = segment.termIndex().query(q, qg.getExcludeHashes(), null, Segment.wordReferenceFactory, maxdist);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        ReferenceContainer<WordReference> index = search.joined();

        Iterator<WordReference> i = index.entries();
        WordReference entry;
        int c = 0;
        Date lm;
        String lms;
        while (i.hasNext() && c < count) {
            entry = i.next();
            lm = new Date(entry.lastModified());
            lms = GenericFormatter.ANSIC_FORMATTER.format(lm);
            prop.put("event_" + c + "_start", lms); // like "Wed May 01 1963 00:00:00 GMT-0600"
            prop.put("event_" + c + "_end", lms); // like "Sat Jun 01 1963 00:00:00 GMT-0600"
            prop.put("event_" + c + "_isDuration", 0); // 0 (only a point) or 1 (period of time)
            prop.putHTML("event_" + c + "_title", "test"); // short title of the event
            prop.putHTML("event_" + c + "_description", ""); // long description of the event
            c++;
        }
        prop.put("event", c);

        // log
        Network.log.info("EXIT TIMELINE SEARCH: " +
                QueryParams.anonymizedQueryHashes(q) + " - " + joincount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstracts, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");

        return prop;
    }

}
