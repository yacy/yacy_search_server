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

import java.util.ConcurrentModificationException;
import java.util.Iterator;

import de.anomic.server.serverProfiling;
import de.anomic.server.serverProfiling.Event;
import de.anomic.ymage.ymageChart;
import de.anomic.ymage.ymageMatrix;

public class plasmaProfiling {
    
    private static ymageChart bufferChart = null;
    
    public static long maxPayload(final String eventname, final long min) {
    	final Iterator<Event> i = serverProfiling.history(eventname);
        serverProfiling.Event event;
        long max = min, l;
        while (i.hasNext()) {
        	event = i.next();
            l = ((Long) event.payload).longValue();
            if (l > max) max = l;
        }
        return max;
    }
    
    public static ymageMatrix performanceGraph(final int width, final int height, final String subline) {        
        // find maximum values for automatic graph dimension adoption
        final int maxppm = (int) maxPayload("ppm", 25);
        final int maxwords = (int) maxPayload("wordcache", 12000);
        final long maxbytes = maxPayload("memory", 110 * 1024 * 1024);
        
        // declare graph and set dimensions
        final int leftborder = 30;
        final int rightborder = 30;
        final int topborder = 20;
        final int bottomborder = 20;
        final int leftscale = 10000;
        final int rightscale = 100;
        final int anotscale = 50;
        final int bottomscale = 60;
        final int vspace = height - topborder - bottomborder;
        final int hspace = width - leftborder - rightborder;
        final int maxtime = 600;
        ymageChart chart = new ymageChart(width, height, "FFFFFF", "000000", "AAAAAA", leftborder, rightborder, topborder, bottomborder, "YACY PEER PERFORMANCE: MAIN MEMORY, WORD CACHE AND PAGES/MINUTE (PPM)", subline);
        chart.declareDimension(ymageChart.DIMENSION_BOTTOM, bottomscale, hspace / (maxtime / bottomscale), -maxtime, "000000", "CCCCCC", "TIME/SECONDS");
        chart.declareDimension(ymageChart.DIMENSION_LEFT, leftscale, vspace * leftscale / maxwords, 0, "008800", null , "INDEXING, WORDS IN CACHE");
        chart.declareDimension(ymageChart.DIMENSION_RIGHT, rightscale, vspace * rightscale / (int)(maxbytes / 1024 / 1024), 0, "0000FF", "CCCCCC", "MEMORY/MEGABYTE");
        chart.declareDimension(ymageChart.DIMENSION_ANOT, anotscale, vspace * anotscale / maxppm, 0, "008800", null , "PPM [PAGES/MINUTE]");
        
        // draw chart
        long time;
        final long now = System.currentTimeMillis();
        long bytes;
        int x0 = 1, x1, y0 = 0, y1, ppm, words;
        serverProfiling.Event event;
        try {
            // draw memory
            Iterator<Event> i = serverProfiling.history("memory");
            while (i.hasNext()) {
                event = i.next();
                time = event.time - now;
                bytes = ((Long) event.payload).longValue();
                x1 = (int) (time/1000);
                y1 = (int) (bytes / 1024 / 1024);
                chart.setColor("AAAAFF");
                chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x1, y1, 2, null);
                chart.setColor("0000FF");
                if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_RIGHT, x0, y0, x1, y1);
                x0 = x1; y0 = y1;
            }
            
            // draw wordcache
            i = serverProfiling.history("wordcache");
            x0 = 1; y0 = 0;
            while (i.hasNext()) {
                event = i.next();
                time = event.time - now;
                words = (int) ((Long) event.payload).longValue();
                x1 = (int) (time/1000);
                y1 = words;
                chart.setColor("228822");
                chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x1, y1, 2, null);
                chart.setColor("008800");
                if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, x0, y0, x1, y1);
                x0 = x1; y0 = y1;
            }
            
            // draw ppm
            i = serverProfiling.history("ppm");
            x0 = 1; y0 = 0;
            while (i.hasNext()) {
                event = i.next();
                time = event.time - now;
                ppm = (int) ((Long) event.payload).longValue();
                x1 = (int) (time/1000);
                y1 = ppm;
                chart.setColor("AA8888");
                if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_ANOT, x0, y0, x1, y1);
                chart.setColor("AA2222");
                chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_ANOT, x1, y1, 2, ppm + " PPM");
                x0 = x1; y0 = y1;
            }
            
            bufferChart = chart;
        } catch (final ConcurrentModificationException cme) {
            chart = bufferChart;
        }
        
        return chart;
    }
    
    public static class searchEvent {
    	public String queryID, processName;
    	public long duration;
    	public int resultCount;
    	
    	public searchEvent(final String queryID, final String processName, final int resultCount, final long duration) {
    		this.queryID = queryID;
    		this.processName = processName;
    		this.resultCount = resultCount;
    		this.duration = duration;
    	}
    }
    
}
