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

import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageChart;
import de.anomic.ymage.ymageMatrix;


public class PerformanceGraph {
    
    public static ymageMatrix respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        ymageChart ip = new ymageChart(660, 240, "000010", 30, 30, 20, 20, "PEER PERFORMANCE GRAPH: PAGES/MINUTE and USED MEMORY");
        ip.declareDimension(ymageChart.DIMENSION_BOTTOM, 60, 60, -600, "000000", "CCCCCC", "TIME/SECONDS");
        ip.declareDimension(ymageChart.DIMENSION_LEFT, 50, 40, 0, "008800", null , "PPM [PAGES/MINUTE]");
        ip.declareDimension(ymageChart.DIMENSION_RIGHT, 100, 20, 0, "0000FF", "CCCCCC", "MEMORY/MEGABYTE");
        
        // draw ppm
        ip.setColor("008800");
        Iterator i = sb.ppmHistory.entrySet().iterator();
        Map.Entry entry;
        int ppm;
        long time, now = System.currentTimeMillis();
        int x0 = 1, x1, y0 = 0, y1;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            time = ((Long) entry.getKey()).longValue() - now;
            ppm = (int) ((Long) entry.getValue()).longValue();
            //System.out.println("PPM: time = " + time + ", ppm = " + ppm);
            x1 = (int) (time/1000);
            y1 = ppm;
            ip.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x1, y1, 2);
            if (x0 < 0) ip.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x0, y0, x1, y1);
            x0 = x1; y0 = y1;
        }
        
        // draw memory
        ip.setColor("0000FF");
        i = sb.usedMemoryHistory.entrySet().iterator();
        long bytes;
        x0 = 1;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            time = ((Long) entry.getKey()).longValue() - now;
            bytes = ((Long) entry.getValue()).longValue();
            //System.out.println("Memory: time = " + time + ", bytes = " + bytes);
            x1 = (int) (time/1000);
            y1 = (int) (bytes / 1024 / 1024);
            ip.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x1, y1, 2);
            if (x0 < 0) ip.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x0, y0, x1, y1);
            x0 = x1; y0 = y1;
        }
        
        return ip;
    }
    
}