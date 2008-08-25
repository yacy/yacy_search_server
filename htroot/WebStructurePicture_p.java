// WebStructurePicture.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWebStructure;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;
import de.anomic.ymage.ymageGraph;
import de.anomic.ymage.ymageMatrix;
import de.anomic.ymage.ymageToolPrint;

public class WebStructurePicture_p {
    
    private static final double maxlongd = Long.MAX_VALUE;
    
    public static ymageMatrix respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        int width = 768;
        int height = 576;
        int depth = 3;
        int nodes = 100; // maximum number of host nodes that are painted
        int time = -1;
        String host = null;
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            depth = post.getInt("depth", 3);
            nodes = post.getInt("nodes", width * height * 100 / 768 / 576);
            time = post.getInt("time", -1);
            host = post.get("host", null);
        }
        
        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 32 ) width = 32;
        if (width > 1920) width = 1920;
        if (height < 24) height = 24;
        if (height > 1920) height = 1920;
        if (depth > 8) depth = 8;
        if (depth < 0) depth = 0;
        
        // calculate target time
        final long timeout = (time < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + (time * 8 / 10);
        
        // find start point
        if ((host == null) || (host.length() == 0) || (host.equals("auto"))) {
            // find domain with most references
            host = sb.webStructure.hostWithMaxReferences();
        }
        ymageMatrix graphPicture;
        if (host == null) {
            // probably no information available
            graphPicture = new ymageMatrix(width, height, ymageMatrix.MODE_SUB, ymageGraph.color_back);
            ymageToolPrint.print(graphPicture, width / 2, height / 2, 0, "NO WEB STRUCTURE DATA AVAILABLE.", 0);
            ymageToolPrint.print(graphPicture, width / 2, height / 2 + 16, 0, "START A WEB CRAWL TO OBTAIN STRUCTURE DATA.", 0);
        } else {
            // find start hash
            String hash = null;
            try {
                hash = (new yacyURL("http://" + host, null)).hash().substring(6);
            } catch (final MalformedURLException e) {e.printStackTrace();}
            assert (sb.webStructure.references(hash) != null);
            
            // recursively find domains, up to a specific depth
            final ymageGraph graph = new ymageGraph();
            if (host != null) place(graph, sb.webStructure, hash, host, nodes, timeout, 0.0, 0.0, 0, depth);
            //graph.print();
            
            graphPicture = graph.draw(width, height, 40, 40, 16, 16);
        }
        // print headline
        graphPicture.setColor(ymageGraph.color_text);
        ymageToolPrint.print(graphPicture, 2, 8, 0, "YACY WEB-STRUCTURE ANALYSIS", -1);
        if (host != null) ymageToolPrint.print(graphPicture, 2, 16, 0, "LINK ENVIRONMENT OF DOMAIN " + host.toUpperCase(), -1);
        ymageToolPrint.print(graphPicture, width - 2, 8, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), 1);

        return graphPicture;
        
    }
    
    private static final int place(final ymageGraph graph, final plasmaWebStructure structure, final String centerhash, final String centerhost, int maxnodes, final long timeout, final double x, final double y, int nextlayer, final int maxlayer) {
        // returns the number of nodes that had been placed
        assert centerhost != null;
        ymageGraph.coordinate center = graph.getPoint(centerhost);
        int mynodes = 0;
        if (center == null) {
        	/*center =*/ graph.addPoint(centerhost, x, y, nextlayer);
        	maxnodes--;
        	mynodes++;
        }
        if (nextlayer == maxlayer) return mynodes;
        nextlayer++;
        final double radius = 1.0 / (1 << nextlayer);
        final Map<String, Integer> next = structure.references(centerhash);
        Map.Entry<String, Integer> entry;
        String targethash, targethost;
        // first set points to next hosts
        final Iterator<Map.Entry<String, Integer>> i = next.entrySet().iterator();
        final ArrayList<String[]> targets = new ArrayList<String[]>();
        int maxtargetrefs = 8, maxthisrefs = 8;
        int targetrefs, thisrefs;
        double rr, re;
        while ((i.hasNext()) && (maxnodes > 0) && (System.currentTimeMillis() < timeout)) {
            entry = i.next();
            targethash = entry.getKey();
            targethost = structure.resolveDomHash2DomString(targethash);
            if (targethost == null) continue;
            thisrefs = entry.getValue().intValue();
            targetrefs = structure.referencesCount(targethash); // can be cpu/time-critical
            maxtargetrefs = Math.max(targetrefs, maxtargetrefs);
            maxthisrefs = Math.max(thisrefs, maxthisrefs);
            targets.add(new String[] {targethash, targethost});
            if (graph.getPoint(targethost) != null) continue;
            // set a new point. It is placed on a circle around the host point
            final double angle = kelondroBase64Order.enhancedCoder.cardinal((targethash + "____").getBytes()) / maxlongd * 2 * Math.PI;
            //System.out.println("ANGLE = " + angle);
            rr = radius * 0.25 * (1 - targetrefs / (double) maxtargetrefs);
            re = radius * 0.5 * (thisrefs / (double) maxthisrefs);
            graph.addPoint(targethost, x + (radius - rr - re) * Math.cos(angle), y + (radius - rr - re) * Math.sin(angle), nextlayer);
            maxnodes--;
            mynodes++;
        }
        // recursively set next hosts
        final Iterator<String[]> j = targets.iterator();
        String[] target;
        int nextnodes;
        while (j.hasNext()) {
            target = j.next();
            targethash = target[0];
            targethost = target[1];
            final ymageGraph.coordinate c = graph.getPoint(targethost);
            assert c != null;
            nextnodes = ((maxnodes <= 0) || (System.currentTimeMillis() >= timeout)) ? 0 : place(graph, structure, targethash, targethost, maxnodes, timeout, c.x, c.y, nextlayer, maxlayer);
            mynodes += nextnodes;
            maxnodes -= nextnodes;
            graph.setBorder(centerhost, targethost);
        }
        return mynodes;
    }
    
}
