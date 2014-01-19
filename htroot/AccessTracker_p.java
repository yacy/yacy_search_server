// AccessTracker_p.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2007 on http://www.yacy.net
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

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryParams;
import net.yacy.server.serverAccessTracker;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.serverAccessTracker.Track;

public class AccessTracker_p {

    private static Collection<Track> listclone (final Collection<Track> m) {
        final Collection<Track> accessClone = new LinkedBlockingQueue<Track>();
        try {
            accessClone.addAll(m);
        } catch (final ConcurrentModificationException e) {}
        return accessClone;
    }

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.setLocalized(!(header.get(HeaderFramework.CONNECTION_PROP_PATH)).endsWith(".xml"));
        int page = 0;
        if (post != null) {
            page = post.getInt("page", 0);
        }
        prop.put("page", page);

        final int maxCount = 1000;
        boolean dark = true;
        if (page == 0) {
            final Iterator<String> i = serverAccessTracker.accessHosts();
            String host;
            int entCount = 0;
            try {
                while ((entCount < maxCount) && (i.hasNext())) {
                    host = i.next();
                    prop.putHTML("page_list_" + entCount + "_host", host);
                    prop.putNum("page_list_" + entCount + "_countSecond", serverAccessTracker.latestAccessCount(host, 1000));
                    prop.putNum("page_list_" + entCount + "_countMinute", serverAccessTracker.latestAccessCount(host, 1000 * 60));
                    prop.putNum("page_list_" + entCount + "_count10Minutes", serverAccessTracker.latestAccessCount(host, 1000 * 60 * 10));
                    prop.putNum("page_list_" + entCount + "_countHour", serverAccessTracker.latestAccessCount(host, 1000 * 60 * 60));
                    entCount++;
                }
            } catch (final ConcurrentModificationException e) {
                // we don't want to synchronize this
                ConcurrentLog.logException(e);
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);

            entCount = 0;
            prop.put("page_bflist", entCount);
        } else if (page == 1) {
            String host = (post == null) ? "" : post.get("host", "");
            int entCount = 0;
            Collection<Track> access;
            Track entry;
            if (host.length() > 0) {
                access = serverAccessTracker.accessTrack(host);
                if (access != null) {
                    try {
                        final Iterator<Track> ii = listclone(access).iterator();
                        while (ii.hasNext()) {
                            entry = ii.next();
                            prop.putHTML("page_list_" + entCount + "_host", host);
                            prop.put("page_list_" + entCount + "_date", GenericFormatter.SIMPLE_FORMATTER.format(new Date(entry.getTime())));
                            prop.putHTML("page_list_" + entCount + "_path", entry.getPath());
                            entCount++;
                        }
                    } catch (final ConcurrentModificationException e) {
                        // we don't want to synchronize this
                        ConcurrentLog.logException(e);
                    }
                }
            } else {
                try {
                    final Iterator<String> i = serverAccessTracker.accessHosts();
                    while ((entCount < maxCount) && (i.hasNext())) {
                        host = i.next();
                        access = serverAccessTracker.accessTrack(host);
                        final Iterator<Track> ii = listclone(access).iterator();
                        while (ii.hasNext()) {
                                entry = ii.next();
                                prop.putHTML("page_list_" + entCount + "_host", host);
                                prop.put("page_list_" + entCount + "_date", GenericFormatter.SIMPLE_FORMATTER.format(new Date(entry.getTime())));
                                prop.putHTML("page_list_" + entCount + "_path", entry.getPath());
                                entCount++;
                        }
                    }
                } catch (final ConcurrentModificationException e) {
                    // we don't want to synchronize this
                    ConcurrentLog.logException(e);
                }
            }
            prop.put("page_list", entCount);
            prop.put("page_num", entCount);
        } else if ((page == 2) || (page == 4)) {
            final Iterator<QueryParams> ai = (page == 2) ? AccessTracker.get(AccessTracker.Location.local) : AccessTracker.get(AccessTracker.Location.remote);
            QueryParams query;
            long qcountSum = 0;
            long rcountSum = 0;
            long tcountSum = 0;
            long rcount = 0;
            long utimeSum = 0;
            long stimeSum = 0;
            long rtimeSum = 0;
            long utimeSum1 = 0;
            long stimeSum1 = 0;
            long rtimeSum1 = 0;
            int m = 0;

            while (ai.hasNext()) {
                try {
                    query = ai.next();
                } catch (final ConcurrentModificationException e) {
                    // we don't want to synchronize this
                    ConcurrentLog.logException(e);
                    break;
                }
                // put values in template
                prop.put("page_list_" + m + "_dark", ((dark) ? 1 : 0) );
                dark =! dark;
                prop.putHTML("page_list_" + m + "_host", query.clienthost);
                prop.put("page_list_" + m + "_date", GenericFormatter.SIMPLE_FORMATTER.format(new Date(query.starttime)));
                prop.put("page_list_" + m + "_timestamp", query.starttime);
                if (page == 2) {
                    // local search
                    prop.putNum("page_list_" + m + "_offset", query.offset);
                    prop.putHTML("page_list_" + m + "_querystring", query.getQueryGoal().getQueryString(false));
                } else {
                    // remote search
                    prop.putHTML("page_list_" + m + "_peername", (query.remotepeer == null) ? "<unknown>" : query.remotepeer.getName());
                    prop.put("page_list_" + m + "_queryhashes", QueryParams.anonymizedQueryHashes(query.getQueryGoal().getIncludeHashes()));
                }
                prop.putNum("page_list_" + m + "_querycount", query.itemsPerPage);
                prop.putNum("page_list_" + m + "_transmitcount", query.transmitcount);
                prop.putNum("page_list_" + m + "_resultcount", 0 /*query.getResultCount()*/);
                prop.putNum("page_list_" + m + "_urltime", query.urlretrievaltime);
                prop.putNum("page_list_" + m + "_snippettime", query.snippetcomputationtime);
                prop.putNum("page_list_" + m + "_resulttime", query.searchtime);
                prop.putHTML("page_list_" + m + "_userAgent", query.userAgent);
                qcountSum += query.itemsPerPage;
                rcountSum += 0; //query.getResultCount();
                tcountSum += query.transmitcount;
                utimeSum += query.urlretrievaltime;
                stimeSum += query.snippetcomputationtime;
                rtimeSum += query.searchtime;
                
                if (query.transmitcount > 0){
                    rcount++;
                /*  utimeSum1 += query.urlretrievaltime;
                    stimeSum1 += query.snippetcomputationtime; */
                    rtimeSum1 += query.searchtime;
                }                
                m++;
            }
            prop.put("page_list", m);
            prop.put("page_num", m);
            prop.put("page_resultcount", rcount);

            // Put -1 instead of NaN as result for empty search list and return the safe HTML blank char for table output
            if (m == 0) {
                m = -1;
                // return empty values to not break the table view
                prop.put("page_list", 1);
                prop.put("page_list_0_dark", 1 );
                prop.put("page_list_0_host", "");
                prop.put("page_list_0_date", "");
                prop.put("page_list_0_timestamp", "");
                if (page == 2) {
                    // local search
                    prop.putNum("page_list_0_offset", "");
                    prop.put("page_list_0_querystring", "");
                } else {
                    // remote search
                    prop.put("page_list_0_peername", "");
                    prop.put("page_list_0_queryhashes", "");
                }
                prop.putNum("page_list_0_querycount", "");
                prop.putNum("page_list_0_transmitcount", "");
                prop.putNum("page_list_0_resultcount", "");
                prop.putNum("page_list_0_urltime", "");
                prop.putNum("page_list_0_snippettime", "");
                prop.putNum("page_list_0_resulttime", "");
                prop.put("page_list_0_userAgent", "");
            }
            if (rcount == 0) rcount = -1;
            prop.putNum("page_querycount_avg", (double) qcountSum / m);
            prop.putNum("page_resultcount_avg", (double) rcountSum / m);
            prop.putNum("page_urltime_avg", (double) utimeSum / m);
            prop.putNum("page_snippettime_avg", (double) stimeSum / m);
            prop.putNum("page_resulttime_avg", (double) rtimeSum / m);
            prop.putNum("page_transmitcount_avg", (double) tcountSum / rcount);
            prop.putNum("page_resultcount_avg1", (double) rcountSum / rcount);
            prop.putNum("page_urltime_avg1", (double) utimeSum1 / rcount);
            prop.putNum("page_snippettime_avg1", (double) stimeSum1 / rcount);
            prop.putNum("page_resulttime_avg1", (double) rtimeSum1 / rcount);
            prop.putNum("page_total", (page == 2) ? AccessTracker.size(AccessTracker.Location.local) : AccessTracker.size(AccessTracker.Location.remote));
        } else if ((page == 3) || (page == 5)) {
            final Iterator<Entry<String, TreeSet<Long>>> i = (page == 3) ? sb.localSearchTracker.entrySet().iterator() : sb.remoteSearchTracker.entrySet().iterator();
            String host;
            TreeSet<Long> handles;
            int m = 0;
            int qphSum = 0;
            Map.Entry<String, TreeSet<Long>> entry;
            try {
            while ((m < maxCount) && (i.hasNext())) {
                entry = i.next();
                host = entry.getKey();
                handles = entry.getValue();

                int dateCount = 0;
                final Iterator<Long> ii = handles.iterator();
                while (ii.hasNext()) {
                    final Long timestamp = ii.next();
                    prop.put("page_list_" + m + "_dates_" + dateCount + "_date", GenericFormatter.SIMPLE_FORMATTER.format(new Date(timestamp.longValue())));
                    prop.put("page_list_" + m + "_dates_" + dateCount + "_timestamp", timestamp.toString());
                    dateCount++;
                }
                prop.put("page_list_" + m + "_dates", dateCount);
                final int qph = handles.tailSet(Long.valueOf(System.currentTimeMillis() - 1000 * 60 * 60)).size();
                qphSum += qph;
                prop.put("page_list_" + m + "_qph", qph);

                prop.put("page_list_" + m + "_dark", ((dark) ? 1 : 0) ); dark =! dark;
                prop.putHTML("page_list_" + m + "_host", host);
                if (page == 5) {
                    final Seed remotepeer = sb.peers.lookupByIP(Domains.dnsResolve(host), -1, true, true, true);
                    prop.putHTML("page_list_" + m + "_peername", (remotepeer == null) ? "UNKNOWN" : remotepeer.getName());
                }
                prop.putNum("page_list_" + m + "_count", handles.size());

                // next
                m++;
            }
            } catch (final ConcurrentModificationException e) {
                // we don't want to synchronize this
                ConcurrentLog.logException(e);
            }
            // return empty values to not break the table view if no results can be listed
            if (m==0) {
                prop.put("page_list", 1);
                prop.put("page_list_0_dates_0_date", "");
                prop.put("page_list_0_dates", 1);
                prop.putNum("page_list_0_qph", "");
                prop.put("page_list_0_dark", 1 );
                prop.put("page_list_0_peername", "");
                prop.put("page_list_0_host", "");
                prop.putNum("page_list_0_count", "");
            } else {
                prop.put("page_list", m);
            }
            prop.putNum("page_num", m);
            prop.putNum("page_total", (page == 3) ? AccessTracker.size(AccessTracker.Location.local) : AccessTracker.size(AccessTracker.Location.remote));
            prop.putNum("page_qph_sum", qphSum);
        }
        // return rewrite properties
        return prop;
    }

}
