// LogStatistic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 16.01.2007
//
// This File is contributed by Franz Brausze
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.util.HashSet;
import java.util.Hashtable;

import net.yacy.kelondro.logging.LogParser;

import de.anomic.http.server.RequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class LogStatistics_p {
    
    private static final String RESULTS = "results_";
    
    @SuppressWarnings({ "unchecked", "boxing" })
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        final serverObjects prop = new serverObjects();

        Hashtable<String, Object> r = null;
        boolean displaySubmenu = false;
        
        prop.put("submenu", displaySubmenu ? "1" : "0");
        
        if (r == null) {
            prop.put("results", "0");
            return prop;
        }
        prop.put("results", "1");
        String[] t;
        float l;
        prop.putNum(RESULTS + LogParser.DHT_DISTANCE_AVERAGE, (Long) r.get(LogParser.DHT_DISTANCE_AVERAGE));
        prop.putNum(RESULTS + LogParser.DHT_DISTANCE_MAX, (Long) r.get(LogParser.DHT_DISTANCE_MAX));
        prop.putNum(RESULTS + LogParser.DHT_DISTANCE_MIN, (Long) r.get(LogParser.DHT_DISTANCE_MIN));
        prop.put(RESULTS + LogParser.DHT_REJECTED, (Integer) r.get(LogParser.DHT_REJECTED));
        prop.put(RESULTS + LogParser.DHT_SELECTED, (Integer) r.get(LogParser.DHT_SELECTED));
        prop.put(RESULTS + LogParser.DHT_SENT_FAILED, (Integer) r.get(LogParser.DHT_SENT_FAILED));
        t = transformMem(((Long)r.get(LogParser.DHT_TRAFFIC_SENT)).longValue());
        prop.put(RESULTS + LogParser.DHT_TRAFFIC_SENT, t[0]);
        prop.put(RESULTS + LogParser.DHT_TRAFFIC_SENT + "Unit", t[1]);
        prop.put(RESULTS + LogParser.DHT_URLS_SENT, (Integer) r.get(LogParser.DHT_URLS_SENT));
        prop.put(RESULTS + LogParser.DHT_WORDS_SELECTED, (Integer) r.get(LogParser.DHT_WORDS_SELECTED));
        t = transformTime(((Integer)r.get(LogParser.DHT_WORDS_SELECTED_TIME)).longValue() * 1000L);
        prop.put(RESULTS + LogParser.DHT_WORDS_SELECTED_TIME, t[0]);
        prop.put(RESULTS + LogParser.DHT_WORDS_SELECTED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.ERROR_CHILD_TWICE_LEFT, (Integer) r.get(LogParser.ERROR_CHILD_TWICE_LEFT));
        prop.put(RESULTS + LogParser.ERROR_CHILD_TWICE_RIGHT, (Integer) r.get(LogParser.ERROR_CHILD_TWICE_RIGHT));
        prop.put(RESULTS + LogParser.ERROR_MALFORMED_URL, (Integer) r.get(LogParser.ERROR_MALFORMED_URL));
        prop.put(RESULTS + LogParser.INDEXED_ANCHORS, (Integer) r.get(LogParser.INDEXED_ANCHORS));
//        t = transformTime(((Integer)r.get(LogParser.INDEXED_INDEX_TIME)).longValue());
//        prop.put(RESULTS + LogParser.INDEXED_INDEX_TIME, t[0]);
//        prop.put(RESULTS + LogParser.INDEXED_INDEX_TIME + "Unit", t[1]);
//        t = transformTime(((Integer)r.get(LogParser.INDEXED_PARSE_TIME)).longValue());
//        prop.put(RESULTS + LogParser.INDEXED_PARSE_TIME, t[0]);
//        prop.put(RESULTS + LogParser.INDEXED_PARSE_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.INDEXED_SITES, (Integer) r.get(LogParser.INDEXED_SITES));
        t = transformMem(((Integer)r.get(LogParser.INDEXED_SITES_SIZE)).longValue());
        prop.put(RESULTS + LogParser.INDEXED_SITES_SIZE, t[0]);
        prop.put(RESULTS + LogParser.INDEXED_SITES_SIZE + "Unit", t[1]);
//        t = transformTime(((Integer)r.get(LogParser.INDEXED_STACK_TIME)).longValue());
//        prop.put(RESULTS + LogParser.INDEXED_STACK_TIME, t[0]);
//        prop.put(RESULTS + LogParser.INDEXED_STACK_TIME + "Unit", t[1]);
//        t = transformTime(((Integer)r.get(LogParser.INDEXED_STORE_TIME)).longValue());
//        prop.put(RESULTS + LogParser.INDEXED_STORE_TIME, t[0]);
//        prop.put(RESULTS + LogParser.INDEXED_STORE_TIME + "Unit", t[1]);
        t = transformTime(((Integer)r.get(LogParser.INDEXED_LINKSTORE_TIME)).longValue());
        prop.put(RESULTS + LogParser.INDEXED_LINKSTORE_TIME, t[0]);
        prop.put(RESULTS + LogParser.INDEXED_LINKSTORE_TIME + "Unit", t[1]);
        t = transformTime(((Integer)r.get(LogParser.INDEXED_INDEXSTORE_TIME)).longValue());
        prop.put(RESULTS + LogParser.INDEXED_INDEXSTORE_TIME, t[0]);
        prop.put(RESULTS + LogParser.INDEXED_INDEXSTORE_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.INDEXED_WORDS, (Integer) r.get(LogParser.INDEXED_WORDS));
        prop.put(RESULTS + LogParser.PEERS_BUSY, (Integer) r.get(LogParser.PEERS_BUSY));
        prop.put(RESULTS + LogParser.PEERS_TOO_LESS, (Integer) r.get(LogParser.PEERS_TOO_LESS));
        prop.put(RESULTS + LogParser.RANKING_DIST, (Integer) r.get(LogParser.RANKING_DIST));
        prop.put(RESULTS + LogParser.RANKING_DIST_FAILED, (Integer) r.get(LogParser.RANKING_DIST_FAILED));
        t = transformTime(((Integer)r.get(LogParser.RANKING_DIST_TIME)).longValue());
        prop.put(RESULTS + LogParser.RANKING_DIST_TIME, t[0]);
        prop.put(RESULTS + LogParser.RANKING_DIST_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.RWIS_BLOCKED, (Integer) r.get(LogParser.RWIS_BLOCKED));
        prop.put(RESULTS + LogParser.RWIS_RECEIVED, (Integer) r.get(LogParser.RWIS_RECEIVED));
        t = transformTime(((Long)r.get(LogParser.RWIS_RECEIVED_TIME)).longValue());
        prop.put(RESULTS + LogParser.RWIS_RECEIVED_TIME, t[0]);
        prop.put(RESULTS + LogParser.RWIS_RECEIVED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.URLS_BLOCKED, (Integer) r.get(LogParser.URLS_BLOCKED));
        prop.put(RESULTS + LogParser.URLS_RECEIVED, (Integer) r.get(LogParser.URLS_RECEIVED));
        t = transformTime(((Long)r.get(LogParser.URLS_RECEIVED_TIME)).longValue());
        prop.put(RESULTS + LogParser.URLS_RECEIVED_TIME, t[0]);
        prop.put(RESULTS + LogParser.URLS_RECEIVED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.URLS_REQUESTED, (Integer) r.get(LogParser.URLS_REQUESTED));
        prop.put(RESULTS + LogParser.WORDS_RECEIVED, (Integer) r.get(LogParser.WORDS_RECEIVED));
        l = ((Long)r.get(LogParser.TOTAL_PARSER_TIME)).floatValue();
        t = transformTime((long)l);
        prop.put(RESULTS + LogParser.TOTAL_PARSER_TIME, t[0]);
        prop.put(RESULTS + LogParser.TOTAL_PARSER_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParser.TOTAL_PARSER_RUNS, (Integer) r.get(LogParser.TOTAL_PARSER_RUNS));
        if ((l /= 1000) == 0) {
            prop.put(RESULTS + "avgExists", "0");
        } else {
            prop.put(RESULTS + "avgExists", "1");
            prop.put(RESULTS + "avgExists_avgParserRunsPerMinute", (int) (((Integer) r.get(LogParser.TOTAL_PARSER_RUNS)).floatValue() / l)); 
        }
        
        String[] names = ((HashSet<String>) r.get(LogParser.DHT_REJECTED_PEERS_NAME)).toArray(new String[1]);
        String[] hashes = ((HashSet<String>) r.get(LogParser.DHT_REJECTED_PEERS_HASH)).toArray(new String[1]);
        int i;
        for (i = 0; i<names.length && i<hashes.length; i++) {
            prop.putHTML(RESULTS + "useDHTRejectPeers_DHTRejectPeers_" + i + "_name", names[i]);
            prop.putHTML(RESULTS + "useDHTRejectPeers_DHTRejectPeers_" + i + "_hash", hashes[i]);
        }
        prop.put(RESULTS + "DHTRejectPeers", i);
        prop.put(RESULTS + "useDHTRejectPeers", (i > 0) ? "1" : "0");
        prop.put(RESULTS + "useDHTRejectPeers_DHTRejectPeers", i);
        
        names = ((HashSet<String>)r.get(LogParser.DHT_SENT_PEERS_NAME)).toArray(new String[1]);
        hashes = ((HashSet<String>)r.get(LogParser.DHT_SENT_PEERS_HASH)).toArray(new String[1]);
        for (i = 0; i<names.length && i<hashes.length; i++) {
            prop.putHTML(RESULTS + "useDHTPeers_DHTPeers_" + i + "_name", names[i]);
            prop.putHTML(RESULTS + "useDHTPeers_DHTPeers_" + i + "_hash", hashes[i]);
        }
        prop.put(RESULTS + "DHTPeers", i);
        prop.put(RESULTS + "useDHTPeers", (i > 0) ? "1" : "0");
        prop.put(RESULTS + "useDHTPeers_DHTPeers", i);
        
        return prop;
    }
    
    private static final String MILLISECONDS = "ms";
    private static final String SECONDS = "sec";
    private static final String MINUTES = "min";
    private static final String HOURS = "h";
    private static final String DAYS = "days";
    
    private static final String[] units = new String[] { "Bytes", "KiloBytes", "MegaBytes", "GigaBytes" };
    
    private static String[] transformTime(long timems) {
        if (timems > 10000) timems /= 1000; else return new String[] { Long.toString(timems), MILLISECONDS };
        if (timems > 180) timems /= 60; else return new String[] { Long.toString(timems), SECONDS };
        if (timems > 600) timems /= 60; else return new String[] { Long.toString(timems), MINUTES };
        if (timems > 240) timems /= 24; else return new String[] { Long.toString(timems), HOURS };
        return new String[] { Long.toString(timems), DAYS };
    }
    
    private static String[] transformMem(long mem) {
        int i;
        for (i=0; i<units.length && mem >= 10240; i++)
            mem /= 1024;
        return new String[] { Long.toString(mem), units[i] };
    }
}
