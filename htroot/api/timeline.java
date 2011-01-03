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
import java.util.TreeSet;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.TermSearch;
import net.yacy.kelondro.util.ISO639;

import de.anomic.search.QueryParams;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public final class timeline {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        
        Segment segment = null;
        if (post.containsKey("segment") && authenticated) {
            segment = sb.indexSegments.segment(post.get("segment"));
        } else {
            segment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
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
        final TreeSet<String>[] query = QueryParams.cleanQuery(querystring); // converts also umlaute
        HandleSet q = Word.words2hashesHandles(query[0]);
        
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
            search = segment.termIndex().query(q, Word.words2hashesHandles(query[1]), null, Segment.wordReferenceFactory, maxdist);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
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
        yacyCore.log.logInfo("EXIT TIMELINE SEARCH: " +
                QueryParams.anonymizedQueryHashes(q) + " - " + joincount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstracts, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");
 
        return prop;
    }

}
