// PerformanceGraph.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.11.2007 on http://yacy.net
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

import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ProfilingGraph;
import de.anomic.ymage.ymageMatrix;

public class PerformanceGraph {
    
    public static ymageMatrix respond(final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        
        if (post == null) post = new serverObjects();
        
        final int width = post.getInt("width", 660);
        final int height = post.getInt("height", 240);
        
        return ProfilingGraph.performanceGraph(width, height, sb.indexSegment.urlMetadata().size() + " URLS / " + sb.indexSegment.termIndex().sizesMax() + " WORDS IN INDEX / " + sb.indexSegment.termIndex().getBufferSize() + " WORDS IN CACHE");
    }
    
}