// ProfilingGraph.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.12.2007 on http://yacy.net
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

package net.yacy.peers.graphics;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import net.yacy.search.EventTracker;
import net.yacy.search.EventTracker.Event;
import net.yacy.search.query.SearchEventType;
import net.yacy.visualization.ChartPlotter;
import net.yacy.visualization.RasterPlotter;

public class ProfilingGraph {

    private static ChartPlotter bufferChart = null;
    public static long maxTime = 600000L;

    public static long maxPayload(final EventTracker.EClass eventname, final long min) {
    	final Iterator<Event> list = EventTracker.getHistory(eventname);
    	if (list == null) return min;
        long max = min, l;
        synchronized (list) {
            EventTracker.Event event;
            while (list.hasNext()) {
                event = list.next();
                l = ((Long) event.payload).longValue();
                if (l > max) max = l;
            }
        }
        return max;
    }

    /**
     * 
     * @param width width of the graph in pixels
     * @param height height of the graph in pixels
     * @param subline the eventual graph subtitle. May be null.
     * @param maxTime maximum time range
     * @param timeUnit the time unit for the time range. When null, default to seconds.
     * @param showMemory draw memory usage when true
     * @param showPeers draw peer ping when true
     * @return a RasterPlotter instance drawn from the event tracker
     */
    public static RasterPlotter performanceGraph(final int width, final int height, final String subline, final int maxTime, final TimeUnit timeUnit, final boolean showMemory, final boolean showPeers) {
        // find maximum values for automatic graph dimension adoption
        final int maxppm = (int) maxPayload(EventTracker.EClass.PPM, 25);
        final int maxwords = (int) maxPayload(EventTracker.EClass.WORDCACHE, 12000);
        final long maxbytes = maxPayload(EventTracker.EClass.MEMORY, 110 * 1024 * 1024);
        final int maxmbytes = (int)(maxbytes / 1024 / 1024);

        // declare graph and set dimensions
        final int leftborder = 30;
        final int rightborder = 30;
        final int topborder = 20;
        final int bottomborder = 20;
        final int leftscale = (maxwords > 150000) ? maxwords / 150000 * 20000 : 10000;
        final int rightscale = showMemory ? ((maxmbytes > 1500) ? maxmbytes / 1500 * 200 : 100) : Math.max(100, maxppm / 100 * 100);
        final int anotscale = 1000;
        final int bottomscale = Math.max(1, maxTime / 10);
        final int vspace = height - topborder - bottomborder;
        final int hspace = width - leftborder - rightborder;
        ChartPlotter chart = new ChartPlotter(width, height, 0xFFFFFFl, 0x000000l, 0xAAAAAAl, leftborder, rightborder, topborder, bottomborder, "YACY PEER PERFORMANCE: MAIN MEMORY, WORD CACHE AND PAGES/MINUTE (PPM)", subline);
		chart.declareDimension(ChartPlotter.DIMENSION_BOTTOM, bottomscale, hspace / (maxTime / bottomscale), -maxTime,
				0x000000l, 0xCCCCCCl, "TIME/" + timeUnit != null ? timeUnit.toString() : TimeUnit.SECONDS.toString());
        chart.declareDimension(ChartPlotter.DIMENSION_LEFT, leftscale, (int)((long)vspace * (long)leftscale / maxwords), 0, 0x008800l, null , "WORDS IN INDEXING CACHE");
        if (showMemory) {
            chart.declareDimension(ChartPlotter.DIMENSION_RIGHT, rightscale, (int)((long)vspace * (long)rightscale / maxmbytes), 0, 0x0000FFl, 0xCCCCCCl, "MEMORY/MEGABYTE");
        } else {
            chart.declareDimension(ChartPlotter.DIMENSION_RIGHT, rightscale, (int)((long)vspace * (long)rightscale / Math.max(1, maxppm)), 0, 0xFF0000l, 0xCCCCCCl, "INDEXING SPEED/PAGES PER MINUTE");
        }
        chart.declareDimension(ChartPlotter.DIMENSION_ANOT0, anotscale, (int)((long)vspace * (long)anotscale / maxppm), 0, 0x008800l, null , "PPM [PAGES/MINUTE]");
        chart.declareDimension(ChartPlotter.DIMENSION_ANOT1, vspace / 6, vspace / 6, 0, 0x888800l, null , "URL");
        chart.declareDimension(ChartPlotter.DIMENSION_ANOT2, 1, 1, 0, 0x888800l, null , "PING");

        // draw chart
        long time;
        final long now = System.currentTimeMillis();
        long bytes;
        float x0, x1;
        int y0, y1;
        try {
            // draw urls
            /*
            Iterator<Event> i = serverProfiling.history("indexed");
            x0 = 1; y0 = 0;
            while (i.hasNext()) {
                event = i.next();
                time = event.time - now;
                x1 = (int) (time/1000);
                y1 = ppm;
                chart.setColor("AA8888");
                if (x0 < 0) chart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_ANOT0, x0, y0, x1, y1);
                chart.setColor("AA2222");
                chart.chartDot(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_ANOT0, x1, y1, 2, ((String) event.payload), 315);
                x0 = x1; y0 = y1;
            }
            */
            Iterator<Event> events;
            // draw memory
            if (showMemory) {
                events = EventTracker.getHistory(EventTracker.EClass.MEMORY);
                x0 = 1.0f; y0 = 0;
                if (events != null) {
                    EventTracker.Event event;
                    while (events.hasNext()) {
                        event = events.next();
                        time = event.getTime() - now;
                        bytes = ((Long) event.payload).longValue();
                        x1 = timeUnit.convert(time, TimeUnit.MILLISECONDS);
                        y1 = (int) (bytes / 1024 / 1024);
//  the dots don't        chart.setColor(0xAAAAFF);
//  very nice             chart.chartDot(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_RIGHT, x1, y1, 2, null, 0);
                        chart.setColor(0x0000FF);
                        if (x0 < 0) chart.chartLine(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_RIGHT, x0, y0, x1, y1);
                        x0 = x1; y0 = y1;
                    }
                }
            }

            // draw wordcache
            events = EventTracker.getHistory(EventTracker.EClass.WORDCACHE);
            x0 = 1.0f; y0 = 0;
            if (events != null) {
                EventTracker.Event event;
                int words;
                while (events.hasNext()) {
                    event = events.next();
                    time = event.getTime() - now;
                    words = (int) ((Long) event.payload).longValue();
                    x1 = timeUnit.convert(time, TimeUnit.MILLISECONDS);
                    y1 = words;
                    chart.setColor(0x228822);
                    chart.chartDot(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_LEFT, x1, y1, 2, null, 315);
                    chart.setColor(0x008800);
                    if (x0 < 0) chart.chartLine(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_LEFT, x0, y0, x1, y1);
                    x0 = x1; y0 = y1;
                }
            }

            // draw ppm
            events = EventTracker.getHistory(EventTracker.EClass.PPM);
            x0 = 1.0f; y0 = 0;
            if (events != null) {
                EventTracker.Event event;
                int ppm;
                while (events.hasNext()) {
                    event = events.next();
                    time = event.getTime() - now;
                    ppm = (int) ((Long) event.payload).longValue();
                    x1 = timeUnit.convert(time, TimeUnit.MILLISECONDS);
                    y1 = ppm;
                    chart.setColor(0xAA8888);
                    if (x0 < 0) chart.chartLine(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_ANOT0, x0, y0, x1, y1);
                    chart.setColor(0xAA2222);
                    chart.chartDot(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_ANOT0, x1, y1, 2, ppm + " PPM", 0);
                    x0 = x1; y0 = y1;
                }
            }

            // draw peer ping
            if (showPeers) {
                events = EventTracker.getHistory(EventTracker.EClass.PEERPING);
                x0 = 1.0f; y0 = 0;
                if (events != null) {
                    EventTracker.Event event;
                    EventPing ping;
                    String pingPeer;
                    while (events.hasNext()) {
                        event = events.next();
                        time = event.getTime() - now;
                        ping = (EventPing) event.payload;
                        x1 = timeUnit.convert(time, TimeUnit.MILLISECONDS);
                        y1 = Math.abs((ping.outgoing ? ping.toPeer : ping.fromPeer).hashCode()) % vspace;
                        pingPeer = ping.outgoing ? "-> " + ping.toPeer.toUpperCase() : "<- " + ping.fromPeer.toUpperCase();
                        chart.setColor(0x9999AA);
                        chart.chartDot(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_ANOT2, x1, y1, 2, pingPeer + (ping.newPeers > 0 ? "(+" + ping.newPeers + ")" : ""), 0);
                        x0 = x1; y0 = y1;
                    }
                }
            }

            bufferChart = chart;
        } catch (final ConcurrentModificationException cme) {
            chart = bufferChart;
        }

        return chart;
    }

    public static class EventSearch {
        public SearchEventType processName;
        public String comment;
    	public String queryID;
    	public long duration;
    	public int resultCount;

    	public EventSearch(final String queryID, final SearchEventType processName, final String comment, final int resultCount, final long duration) {
    		this.queryID = queryID;
    		this.processName = processName;
    		this.comment = comment;
    		this.resultCount = resultCount;
    		this.duration = duration;
    	}
    }

    public static class EventDHT {
        public String fromPeer, toPeer;
        public boolean outgoing;
        public int totalReferences, newReferences;

        public EventDHT(final String fromPeer, final String toPeer, final boolean outgoing, final int totalReferences, final int newReferences) {
            this.fromPeer = fromPeer;
            this.toPeer = toPeer;
            this.outgoing = outgoing;
            this.totalReferences = totalReferences;
            this.newReferences = newReferences;
        }
    }

    public static class EventPing {

        public String fromPeer, toPeer;
        public boolean outgoing;
        public int newPeers;

        public EventPing(final String fromPeer, final String toPeer, final boolean outgoing, final int newPeers) {
            this.fromPeer = fromPeer;
            this.toPeer = toPeer;
            this.outgoing = outgoing;
            this.newPeers = newPeers;
        }
    }
}
