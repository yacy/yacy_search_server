// suggestionsjava
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 11.10.2010 in Frankfurt, Germany on http://yacy.net
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


import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;

import de.anomic.data.DidYouMean;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

/**
 * implementation of the opensearch suggestion extension, see
 * http://www.opensearch.org/Specifications/OpenSearch/Extensions/Suggestions/1.1
 * or
 * https://wiki.mozilla.org/Search_Service/Suggestions
 */
public class suggest {
    
    private static final int meanMax = 30;
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        
        // get query
        String originalquerystring = (post == null) ? "" : post.get("query", post.get("q", "")).trim();
        String querystring =  originalquerystring.replace('+', ' ');
        
        // get segment
        Segment indexSegment = null;
        if (post != null && post.containsKey("segment")) {
            String segmentName = post.get("segment");
            if (sb.indexSegments.segmentExist(segmentName)) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
        DidYouMean didYouMean = new DidYouMean(indexSegment.termIndex(), querystring);
        Iterator<String> meanIt = didYouMean.getSuggestions(300, 10).iterator();
        int meanCount = 0;
        String suggestion;
        StringBuilder suggestions = new StringBuilder(120);
        while (meanCount < meanMax && meanIt.hasNext()) {
            suggestion = meanIt.next();
            suggestions.append(',').append('"').append(suggestion).append('"');
            meanCount++;
        }

        prop.put("query", '"' + originalquerystring + '"');
        prop.put("suggestions", suggestions.length() > 0 ? suggestions.substring(1) : "");
        
        // return rewrite properties
        return prop;
    }
    
}
