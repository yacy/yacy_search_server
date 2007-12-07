// plasmaProfiling.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.12.2007 on http://yacy.net
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

package de.anomic.plasma;

import java.util.Iterator;

import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacyCore;
import de.anomic.ymage.ymageChart;
import de.anomic.ymage.ymageMatrix;

public class plasmaProfiling {

    public static long lastPPMUpdate = System.currentTimeMillis()- 30000;

    public static void updateIndexedPage(plasmaSwitchboardQueue.Entry entry) {
        if (System.currentTimeMillis() - lastPPMUpdate > 30000) {
            // we don't want to do this too often
            yacyCore.peerActions.updateMySeed();
            serverProfiling.update("ppm", new Long(yacyCore.seedDB.mySeed().getPPM()));
            lastPPMUpdate = System.currentTimeMillis();
        }
        serverProfiling.update("indexed", entry.url().toNormalform(true, false));
    }
    
    public static long maxPayload(String eventname, long min) {
    	Iterator i = serverProfiling.history(eventname);
        serverProfiling.Event event;
        long max = min, l;
        while (i.hasNext()) {
        	event = (serverProfiling.Event) i.next();
            l = ((Long) event.payload).longValue();
            if (l > max) max = l;
        }
        return max;
    }
    
    public static ymageMatrix performanceGraph(int width, int height) {        
        // find maximum values for automatic graph dimension adoption
        int maxppm = (int) maxPayload("ppm", 25);
        long maxbytes = maxPayload("memory", 110 * 1024 * 1024);
        
        // declare graph and set dimensions
        int leftborder = 30;
        int rightborder = 30;
        int topborder = 20;
        int bottomborder = 20;
        int leftscale = 20;
        int rightscale = 100;
        int bottomscale = 60;
        int vspace = height - topborder - bottomborder;
        int hspace = width - leftborder - rightborder;
        int maxtime = 600;
        ymageChart chart = new ymageChart(width, height, "FFFFFF", "000000", leftborder, rightborder, topborder, bottomborder, "PEER PERFORMANCE GRAPH: PAGES/MINUTE and USED MEMORY");
        chart.declareDimension(ymageChart.DIMENSION_BOTTOM, bottomscale, hspace / (maxtime / bottomscale), -maxtime, "000000", "CCCCCC", "TIME/SECONDS");
        chart.declareDimension(ymageChart.DIMENSION_LEFT, leftscale, vspace * leftscale / maxppm, 0, "008800", null , "PPM [PAGES/MINUTE]");
        chart.declareDimension(ymageChart.DIMENSION_RIGHT, rightscale, vspace * rightscale / (int)(maxbytes / 1024 / 1024), 0, "0000FF", "CCCCCC", "MEMORY/MEGABYTE");
        
        // draw ppm
        Iterator i = serverProfiling.history("ppm");
        long time, now = System.currentTimeMillis(), bytes;
        int x0 = 1, x1, y0 = 0, y1, ppm;
        serverProfiling.Event event;
        while (i.hasNext()) {
        	event = (serverProfiling.Event) i.next();
            time = event.time - now;
            ppm = (int) ((Long) event.payload).longValue();
            x1 = (int) (time/1000);
            y1 = ppm;
            chart.setColor("228822");
            chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x1, y1, 2);
            chart.setColor("008800");
            if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x0, y0, x1, y1);
            x0 = x1; y0 = y1;
        }
        
        // draw memory
        i = serverProfiling.history("memory");
        x0 = 1;
        while (i.hasNext()) {
        	event = (serverProfiling.Event) i.next();
            time = event.time - now;
            bytes = ((Long) event.payload).longValue();
            x1 = (int) (time/1000);
            y1 = (int) (bytes / 1024 / 1024);
            chart.setColor("AAAAFF");
            chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x1, y1, 2);
            chart.setColor("0000FF");
            if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x0, y0, x1, y1);
            x0 = x1; y0 = y1;
        }
        
        return chart;
    }
    
    public static class searchEvent {
    	public String queryID, processName;
    	public long duration;
    	public int resultCount;
    	
    	public searchEvent(String queryID, String processName, int resultCount, long duration) {
    		this.queryID = queryID;
    		this.processName = processName;
    		this.resultCount = resultCount;
    		this.duration = duration;
    	}
    }
    
}
