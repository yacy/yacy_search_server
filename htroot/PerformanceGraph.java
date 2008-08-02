// PerformanceGraph.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.11.2007 on http://yacy.net
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

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaProfiling;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;

public class PerformanceGraph {
    
    public static ymageMatrix respond(final httpHeader header, serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        if (post == null) post = new serverObjects();
        
        final int width = post.getInt("width", 660);
        final int height = post.getInt("height", 240);
        
        return plasmaProfiling.performanceGraph(width, height, sb.webIndex.countURL() + " URLS / " + sb.webIndex.collectionsSize() + " WORDS IN COLLECTIONS / " + sb.webIndex.cacheSize() + " WORDS IN CACHE");
    }
    
}