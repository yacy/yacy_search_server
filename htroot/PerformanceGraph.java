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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.RasterPlotter;

public class PerformanceGraph {

    private static long indeSizeCache = 0;
    private static long indexSizeTime = 0;
    
    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        if (post == null) post = new serverObjects();

        final int width = post.getInt("width", 660);
        final int height = post.getInt("height", 240);
        final boolean showMemory = !post.containsKey("nomem");
        final boolean showPeers = !post.containsKey("nopeers");

        long t = System.currentTimeMillis();
        if (t - indexSizeTime > 10000) {
            indeSizeCache = sb.index.fulltext().collectionSize();
            indexSizeTime = t;
        }
        RasterPlotter graph = ProfilingGraph.performanceGraph(
                width, height,
                indeSizeCache + " URLS / " + sb.index.RWICount() + " WORDS IN INDEX / " + sb.index.RWIBufferCount() + " WORDS IN CACHE",
                showMemory, showPeers);
        return graph;
    }

}