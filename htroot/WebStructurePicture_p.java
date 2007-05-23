// WebStructurePicture.java
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
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
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaWebStructure;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageGraph;
import de.anomic.ymage.ymageMatrix;

public class WebStructurePicture_p {
    
    private static final double maxlongd = (double) Long.MAX_VALUE;
    
    public static ymageMatrix respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        int width = 768;
        int height = 576;
        int depth = 3;
        String host = null;
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            depth = post.getInt("depth", 3);
            host = post.get("host", null);
        }
        
        //too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 320 ) width = 320;
        if (width > 1920) width = 1920;
        if (height < 240) height = 240;
        if (height > 1920) height = 1920;
        if (depth > 8) depth = 8;
        if (depth < 1) depth = 1;
        
        // find start point
        if (host == null) {
            // find domain with most references
            host = sb.webStructure.hostWithMaxReferences();
        }
        // find start hash
        String hash = null;
        try {
            hash = plasmaURL.urlHash(new URL("http://" + host)).substring(6);
        } catch (MalformedURLException e) {e.printStackTrace();}
        assert (sb.webStructure.references(hash) != null);
        
        // recursively find domains, up to a specific depth
        ymageGraph graph = new ymageGraph();
        if (host != null) place(graph, sb.webStructure, hash, host, 0.0, 0.0, 0, depth);
        //graph.print();
        
        return graph.draw(width, height, 20, 20, 20, 20);
        
    }
    
    private static final void place(ymageGraph graph, plasmaWebStructure structure, String centerhash, String centerhost, double x, double y, int nextlayer, int maxlayer) {
        // returns the host string
        assert centerhost != null;
        ymageGraph.coordinate center = graph.getPoint(centerhost);
        if (center == null) center = graph.addPoint(centerhost, x, y, nextlayer);
        if (nextlayer == maxlayer) return;
        nextlayer++;
        Map next = structure.references(centerhash);
        Map.Entry entry;
        String targethash, targethost;
        // first set points to next hosts
        Iterator i = next.entrySet().iterator();
        ArrayList targets = new ArrayList();
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            targethash = (String) entry.getKey();
            targethost = structure.resolveDomHash2DomString(targethash);
            if (targethost == null) continue;
            targets.add(new String[] {targethash, targethost});
            if (graph.getPoint(targethost) != null) continue;
            // set a new point. It is placed on a circle around the host point
            double angle = ((double) kelondroBase64Order.enhancedCoder.cardinal((targethash + "____").getBytes())) / maxlongd * 2 * Math.PI;
            System.out.println("ANGLE = " + angle);
            double radius = 1.0 / ((double) (1 << nextlayer));
            graph.addPoint(targethost, x + radius * Math.cos(angle), y + radius * Math.sin(angle), nextlayer);
        }
        // recursively set next hosts
        i = targets.iterator();
        String[] target;
        while (i.hasNext()) {
            target = (String[]) i.next();
            targethash = target[0];
            targethost = target[1];
            ymageGraph.coordinate c = graph.getPoint(targethost);
            assert c != null;
            place(graph, structure, targethash, targethost, c.x, c.y, nextlayer, maxlayer);
            graph.setBorder(centerhost, targethost);
        }
        return;
    }
    
}
