// timeline.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-02-10 01:06:59 +0100 (Di, 10 Feb 2009) $
// $LastChangedRevision: 5586 $
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

package net.yacy.htroot.api;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.util.CommonPattern;
import net.yacy.search.EventTracker;
import net.yacy.search.EventTracker.Event;
import net.yacy.search.query.AccessTracker;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class timeline_p {

    // example:
    // http://localhost:8090/api/timeline_p.xml?from=20140601000000&to=20140629000000&data=queries&head=2&period=6h

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;

        // get type of data to be listed in the timeline
        final int maxeventsperperiod = post.getInt("head", 1); // the maximum number of events per period
        final String period = post.get("period", ""); // must be an integer with a character c at the end, c = Y|M|d|h|m|s
        long periodlength = 0;
        if (period.length() > 0) {
            final char c = period.charAt(period.length() - 1);
            final long p = Long.parseLong(period.substring(0, period.length() - 1));
                 if (c == 's') periodlength = p * AbstractFormatter.secondMillis;
            else if (c == 'm') periodlength = p * AbstractFormatter.minuteMillis;
            else if (c == 'h') periodlength = p * AbstractFormatter.hourMillis;
            else if (c == 'd') periodlength = p * AbstractFormatter.dayMillis;
            else if (c == 'M') periodlength = p * AbstractFormatter.monthAverageMillis;
            else if (c == 'Y' || c == 'y') periodlength = p * AbstractFormatter.normalyearMillis;
            else periodlength = 0;
        }
        final String[] data = CommonPattern.COMMA.split(post.get("data", ""));  // a string of word hashes that shall be searched and combined
        final Map<String, List<EventTracker.Event>> proc = new HashMap<>();
        for (final String s: data) if (s.length() > 0) proc.put(s, null);

        // get a time period
        Date fromDate = new Date(0);
        Date toDate = new Date();
        try {fromDate = GenericFormatter.SHORT_SECOND_FORMATTER.parse(post.get("from", GenericFormatter.SHORT_SECOND_FORMATTER.format(fromDate)), 0).getTime();} catch (final ParseException e) {}
        try {toDate = GenericFormatter.SHORT_SECOND_FORMATTER.parse(post.get("to", GenericFormatter.SHORT_SECOND_FORMATTER.format(toDate)), 0).getTime();} catch (final ParseException e) {}

        // get latest dump;
        AccessTracker.dumpLog();

        // fill proc with events from the given data and time period
        if (proc.containsKey("queries")) {
            final List<EventTracker.Event> events = AccessTracker.readLog(AccessTracker.getDumpFile(), fromDate, toDate);
            proc.put("queries", events);
        }

        // mix all events into one event list
        final TreeMap<String, EventTracker.Event> eax = new TreeMap<>();
        for (final List<EventTracker.Event> events: proc.values()) if (events != null) {
            for (final EventTracker.Event event: events) eax.put(event.getFormattedDate(), event);
        }
        proc.clear(); // we don't need that here any more
        List<EventTracker.Event> ea = new ArrayList<>();
        for (final Event event: eax.values()) ea.add(event);

        if (periodlength > 0 && ea.size() > 0) {
            // create a statistical analysis; step by chunks of periodlength entries
            Event firstEvent = ea.iterator().next();
            long startDate = fromDate.getTime();
            //TreeMap<Date, EventTracker.Event>
            final OrderedScoreMap<String> accumulation = new OrderedScoreMap<>(null);
            final List<EventTracker.Event> eap = new ArrayList<>();
            String limit = GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date(startDate + periodlength));
            for (final Event event: ea) {
                if (event.getFormattedDate().compareTo(limit) >= 0) {
                    // write accumulation of the score map into eap
                    stats(accumulation, eap, startDate, periodlength, maxeventsperperiod, firstEvent.type);
                    firstEvent = event;
                    startDate += periodlength;
                    limit = GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date(startDate + periodlength));
                }
                accumulation.inc(event.payload.toString());
            }
            stats(accumulation, eap, startDate, periodlength, maxeventsperperiod, firstEvent.type);

            // overwrite the old table for out
            ea = eap;
        }

        // create a list of these events
        int count = 0;
        for (final Event event: ea) {
            prop.put("event_" + count + "_time", event.getFormattedDate());
            prop.put("event_" + count + "_isPeriod", event.duration == 0 ? 0 : 1);
            prop.put("event_" + count + "_isPeriod_duration", event.duration);
            prop.put("event_" + count + "_isPeriod_count", event.count);
            prop.putHTML("event_" + count + "_type", event.type);
            prop.putXML("event_" + count + "_description", event.payload.toString());
            count++;
        }
        prop.put("event", count);
        prop.put("count", count);
        return prop;
    }

    private static void stats(final OrderedScoreMap<String> accumulation, final List<EventTracker.Event> eap, final long startDate, final long periodlength, final int head, final String type) {
        // write accumulation of the score map into eap
        final Iterator<String> si = accumulation.keys(false);
        int c = 0;
        while (si.hasNext() && c++ < head) {
            final String key = si.next();
            eap.add(new Event(startDate, periodlength, type, key, accumulation.get(key)));
        }
        accumulation.clear();
    }

}
