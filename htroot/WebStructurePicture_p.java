// WebStructurePicture.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
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

import java.net.MalformedURLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.GraphPlotter;
import net.yacy.visualization.GraphPlotter.Point;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

public class WebStructurePicture_p {

    private static final double maxlongd = Long.MAX_VALUE;

    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        String color_text    = "888888";
        String color_back    = "FFFFFF";
        String color_dot0    = "1111BB";
        String color_dota    = "11BB11";
        String color_line    = "222222";
        String color_lineend = "333333";

        int width = 1024;
        int height = 576;
        int depth = 3;
        int nodes = 300; // maximum number of host nodes that are painted
        int bf = 12;    // maximum number of branches around nodes; less nodes makes the graphic look more structured
        int time = -1;
        String hosts = null;
        int cyc = 0;

        if (post != null) {
            width         = post.getInt("width", 1024);
            if (width < 32 ) width = 32;
            if (width > 10000) width = 10000;
            height        = post.getInt("height", 576);
            if (height < 24) height = 24;
            if (height > 10000) height = 10000;
            depth         = post.getInt("depth", 3);
            if (depth > 8) depth = 8;
            if (depth < 0) depth = 0;
            nodes         = post.getInt("nodes", width * height * 100 / 1024 / 576);
            bf            = post.getInt("bf", depth <= 0 ? -1 : (int) Math.round(2.0d * Math.pow(nodes, 1.0d / depth)));
            time          = post.getInt("time", -1);
            hosts         = post.get("host", null);
            color_text    = post.get("colortext",    color_text);
            color_back    = post.get("colorback",    color_back);
            color_dot0    = post.get("colordot0",    color_dot0);
            color_dota    = post.get("colordota",    color_dota);
            color_line    = post.get("colorline",    color_line);
            color_lineend = post.get("colorlineend", color_lineend);
            cyc           = post.getInt("cyc", 0);
        }
        
        // calculate target time
        final long timeout = (time < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + (time * 8 / 10);

        // find start point
        if (hosts == null || hosts.isEmpty() || hosts.equals("auto")) {
            // find domain with most references
            hosts = sb.webStructure.hostWithMaxReferences();
        }
        final RasterPlotter graphPicture;
        if (hosts == null) {
            // probably no information available
            final RasterPlotter.DrawMode drawMode = (RasterPlotter.darkColor(color_back)) ? RasterPlotter.DrawMode.MODE_ADD : RasterPlotter.DrawMode.MODE_SUB;
            graphPicture = new RasterPlotter(width, height, drawMode, color_back);
            PrintTool.print(graphPicture, width / 2, height / 2, 0, "NO WEB STRUCTURE DATA AVAILABLE.", 0);
            PrintTool.print(graphPicture, width / 2, height / 2 + 16, 0, "START A WEB CRAWL TO OBTAIN STRUCTURE DATA.", 0);
        } else {
            // recursively find domains, up to a specific depth
            GraphPlotter graph = new GraphPlotter();
            String[] hostlist = hosts.split(",");
            for (int i = 0; i < hostlist.length; i++) {
                String host = hostlist[i];
                String hash = null;
                try {hash = ASCII.String((new DigestURL("http://" + host)).hash(), 6, 6);} catch (final MalformedURLException e) {ConcurrentLog.logException(e);}
                Map.Entry<String, String> centernode = new AbstractMap.SimpleEntry<String, String>(hash, host);
                double angle = 2.0d * i * Math.PI / hostlist.length;
                if (hostlist.length == 3) angle -= Math.PI / 2;
                if (hostlist.length == 4) angle += Math.PI / 4;
                graph.addNode(centernode.getValue(), Math.cos(angle) / 8, Math.sin(angle) / 8, 0);
                place(graph, sb.webStructure, centernode, bf, nodes, timeout, hostlist.length == 1 ? 0 : 1, hostlist.length == 1 ? depth : depth + 1, cyc);
            }

            // apply physics to it to get a better shape
            if (post != null && post.containsKey("pa")) {
                // test with: http://localhost:8090/WebStructurePicture_p.png?pa=1&ral=0.7&raa=0.5&rar=2&rel=0.5&rea=1&rer=2
                GraphPlotter.Ribbon rAll = new GraphPlotter.Ribbon(post.getFloat("ral", 0.1f), post.getFloat("raa", 0.1f), post.getFloat("rar", 0.1f));
                GraphPlotter.Ribbon rEdge = new GraphPlotter.Ribbon(post.getFloat("rel", 0.05f), post.getFloat("rea", 0.1f), post.getFloat("rer", 0.1f));
                int pa = post.getInt("pa", 0);
                for (int i = 0; i < pa; i++) graph = graph.physics(rAll, rEdge);
            }

            // draw the graph
            graph.normalize();
            graphPicture = graph.draw(width, height, 40, 40, 16, 16, 12, 6, color_back, color_dot0, color_dota, color_line, color_lineend, color_text);
        }
        // print headline
        graphPicture.setColor(Long.parseLong(color_text, 16));
        PrintTool.print(graphPicture, 2, 8, 0, "YACY WEB-STRUCTURE ANALYSIS", -1);
        if (hosts != null) PrintTool.print(graphPicture, 2, 16, 0, "LINK ENVIRONMENT OF DOMAIN " + hosts.toUpperCase(), -1);
        PrintTool.print(graphPicture, width - 2, 8, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), 1);

        return graphPicture;
    }

    private static final int place(
                    final GraphPlotter graph, final WebStructureGraph structure, Map.Entry<String, String> pivotnode,
                    int bf, int maxnodes, final long timeout, int nextlayer, final int maxlayer, final int cyc) {
        Point pivotpoint = graph.getNode(pivotnode.getValue());
        int branches = 0;
        if (nextlayer == maxlayer) return branches;
        nextlayer++;
        final double radius = 1.0 / (1 << nextlayer);
        final WebStructureGraph.StructureEntry sr = structure.outgoingReferences(pivotnode.getKey());
        final Map<String, Integer> next = (sr == null) ? new HashMap<String, Integer>() : sr.references;
        ClusteredScoreMap<String> next0 = new ClusteredScoreMap<String>();
        for (Map.Entry<String, Integer> entry: next.entrySet()) next0.set(entry.getKey(), entry.getValue());
        // first set points to next hosts
        final List<Map.Entry<String, String>> targets = new ArrayList<Map.Entry<String, String>>();
        int maxtargetrefs = 8, maxthisrefs = 8;
        int targetrefs, thisrefs;
        double rr, re;
        Iterator<String> i = next0.keys(false);
        while (i.hasNext()) {
            String targethash = i.next();
            String targethost = structure.hostHash2hostName(targethash);
            if (targethost == null) continue;
            thisrefs = next.get(targethash).intValue();
            targetrefs = structure.referencesCount(targethash); // can be cpu/time-critical
            maxtargetrefs = Math.max(targetrefs, maxtargetrefs);
            maxthisrefs = Math.max(thisrefs, maxthisrefs);
            targets.add(new AbstractMap.SimpleEntry<String, String>(targethash, targethost));
            if (graph.getNode(targethost) != null) continue;
            // set a new point. It is placed on a circle around the host point
            final double angle = ((Base64Order.enhancedCoder.cardinal((targethash + "____").getBytes()) / maxlongd) + (cyc / 360.0d)) * 2.0d * Math.PI;
            //System.out.println("ANGLE = " + angle);
            rr = radius * 0.25 * (1 - targetrefs / (double) maxtargetrefs);
            re = radius * 0.5 * (thisrefs / (double) maxthisrefs);
            graph.addNode(targethost, pivotpoint.x + (radius - rr - re) * Math.cos(angle), pivotpoint.y + (radius - rr - re) * Math.sin(angle), nextlayer);
            branches++;
            if (maxnodes-- <= 0 || (bf > 0 && branches >= bf) || System.currentTimeMillis() >= timeout) break;
        }
        // recursively set next hosts
        int nextnodes;
        for (Map.Entry<String, String> target: targets) {
            nextnodes = ((maxnodes <= 0) || (System.currentTimeMillis() >= timeout)) ? 0 : place(graph, structure, target, bf, maxnodes, timeout, nextlayer, maxlayer, cyc);
            branches += nextnodes;
            maxnodes -= nextnodes;
            graph.setEdge(pivotnode.getValue(), target.getValue());
        }
        return branches;
    }
}