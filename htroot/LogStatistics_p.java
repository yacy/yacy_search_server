// LogStatistic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 16.01.2007
//
// This File is contributed by Franz Brausze
//
// $LastChangedDate: 2007-01-17 12:00:00 +0100 (Di, 17 Jan 2007) $
// $LastChangedRevision: 3216 $
// $LastChangedBy: karlchenofhell $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

import java.util.HashSet;
import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.Logger;

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.LogalizerHandler;
import de.anomic.server.logging.logParsers.LogParserPLASMA;

public class LogStatistics_p {
    
    private static final String RESULTS = "results_";
    
    @SuppressWarnings({ "unchecked", "boxing" })
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        
        final serverObjects prop = new serverObjects();
        Logger logger = Logger.getLogger("");
        
        
        
        Handler[] handlers = logger.getHandlers();
        Hashtable<String, Object> r = null;
        boolean displaySubmenu = false;
        for (int i=0; i<handlers.length; i++) {
            if (handlers[i] instanceof LogalizerHandler) {
                displaySubmenu = true;
                LogalizerHandler h = ((LogalizerHandler)handlers[i]);
                r = h.getParserResults(h.getParser(0));
                break;
            }
        }
        
        prop.put("submenu", displaySubmenu ? "1" : "0");
        
        if (r == null) {
            prop.put("results", "0");
            return prop;
        }
        prop.put("results", "1");
        String[] t;
        float l;
        prop.put(RESULTS + LogParserPLASMA.DHT_DISTANCE_AVERAGE, (Double) r.get(LogParserPLASMA.DHT_DISTANCE_AVERAGE));
        prop.put(RESULTS + LogParserPLASMA.DHT_DISTANCE_MAX, (String) r.get(LogParserPLASMA.DHT_DISTANCE_MAX));
        prop.put(RESULTS + LogParserPLASMA.DHT_DISTANCE_MIN, (String) r.get(LogParserPLASMA.DHT_DISTANCE_MIN));
        prop.put(RESULTS + LogParserPLASMA.DHT_REJECTED, (String) r.get(LogParserPLASMA.DHT_REJECTED));
        prop.put(RESULTS + LogParserPLASMA.DHT_SELECTED, (String) r.get(LogParserPLASMA.DHT_SELECTED));
        prop.put(RESULTS + LogParserPLASMA.DHT_SENT_FAILED, (String) r.get(LogParserPLASMA.DHT_SENT_FAILED));
        t = transformMem(((Long)r.get(LogParserPLASMA.DHT_TRAFFIC_SENT)).longValue());
        prop.put(RESULTS + LogParserPLASMA.DHT_TRAFFIC_SENT, t[0]);
        prop.put(RESULTS + LogParserPLASMA.DHT_TRAFFIC_SENT + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.DHT_URLS_SENT, (String) r.get(LogParserPLASMA.DHT_URLS_SENT));
        prop.put(RESULTS + LogParserPLASMA.DHT_WORDS_SELECTED, (String) r.get(LogParserPLASMA.DHT_WORDS_SELECTED));
        t = transformTime(((Integer)r.get(LogParserPLASMA.DHT_WORDS_SELECTED_TIME)).longValue() * 1000L);
        prop.put(RESULTS + LogParserPLASMA.DHT_WORDS_SELECTED_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.DHT_WORDS_SELECTED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.ERROR_CHILD_TWICE_LEFT, (String) r.get(LogParserPLASMA.ERROR_CHILD_TWICE_LEFT));
        prop.put(RESULTS + LogParserPLASMA.ERROR_CHILD_TWICE_RIGHT, (String) r.get(LogParserPLASMA.ERROR_CHILD_TWICE_RIGHT));
        prop.put(RESULTS + LogParserPLASMA.ERROR_MALFORMED_URL, (String) r.get(LogParserPLASMA.ERROR_MALFORMED_URL));
        prop.put(RESULTS + LogParserPLASMA.INDEXED_ANCHORS, (String) r.get(LogParserPLASMA.INDEXED_ANCHORS));
        t = transformTime(((Integer)r.get(LogParserPLASMA.INDEXED_INDEX_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.INDEXED_INDEX_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_INDEX_TIME + "Unit", t[1]);
        t = transformTime(((Integer)r.get(LogParserPLASMA.INDEXED_PARSE_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.INDEXED_PARSE_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_PARSE_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_SITES, (String) r.get(LogParserPLASMA.INDEXED_SITES));
        t = transformMem(((Integer)r.get(LogParserPLASMA.INDEXED_SITES_SIZE)).longValue());
        prop.put(RESULTS + LogParserPLASMA.INDEXED_SITES_SIZE, t[0]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_SITES_SIZE + "Unit", t[1]);
        t = transformTime(((Integer)r.get(LogParserPLASMA.INDEXED_STACK_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.INDEXED_STACK_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_STACK_TIME + "Unit", t[1]);
        t = transformTime(((Integer)r.get(LogParserPLASMA.INDEXED_STORE_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.INDEXED_STORE_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_STORE_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.INDEXED_WORDS, (String) r.get(LogParserPLASMA.INDEXED_WORDS));
        prop.put(RESULTS + LogParserPLASMA.PEERS_BUSY, (String) r.get(LogParserPLASMA.PEERS_BUSY));
        prop.put(RESULTS + LogParserPLASMA.PEERS_TOO_LESS, (String) r.get(LogParserPLASMA.PEERS_TOO_LESS));
        prop.put(RESULTS + LogParserPLASMA.RANKING_DIST, (String) r.get(LogParserPLASMA.RANKING_DIST));
        prop.put(RESULTS + LogParserPLASMA.RANKING_DIST_FAILED, (String) r.get(LogParserPLASMA.RANKING_DIST_FAILED));
        t = transformTime(((Integer)r.get(LogParserPLASMA.RANKING_DIST_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.RANKING_DIST_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.RANKING_DIST_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.RWIS_BLOCKED, (String) r.get(LogParserPLASMA.RWIS_BLOCKED));
        prop.put(RESULTS + LogParserPLASMA.RWIS_RECEIVED, (String) r.get(LogParserPLASMA.RWIS_RECEIVED));
        t = transformTime(((Long)r.get(LogParserPLASMA.RWIS_RECEIVED_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.RWIS_RECEIVED_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.RWIS_RECEIVED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.URLS_BLOCKED, (String) r.get(LogParserPLASMA.URLS_BLOCKED));
        prop.put(RESULTS + LogParserPLASMA.URLS_RECEIVED, (String) r.get(LogParserPLASMA.URLS_RECEIVED));
        t = transformTime(((Long)r.get(LogParserPLASMA.URLS_RECEIVED_TIME)).longValue());
        prop.put(RESULTS + LogParserPLASMA.URLS_RECEIVED_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.URLS_RECEIVED_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.URLS_REQUESTED, (String) r.get(LogParserPLASMA.URLS_REQUESTED));
        prop.put(RESULTS + LogParserPLASMA.WORDS_RECEIVED, (String) r.get(LogParserPLASMA.WORDS_RECEIVED));
        l = ((Long)r.get(LogParserPLASMA.TOTAL_PARSER_TIME)).floatValue();
        t = transformTime((long)l);
        prop.put(RESULTS + LogParserPLASMA.TOTAL_PARSER_TIME, t[0]);
        prop.put(RESULTS + LogParserPLASMA.TOTAL_PARSER_TIME + "Unit", t[1]);
        prop.put(RESULTS + LogParserPLASMA.TOTAL_PARSER_RUNS, (String) r.get(LogParserPLASMA.TOTAL_PARSER_RUNS));
        if ((l /= 1000) == 0) {
            prop.put(RESULTS + "avgExists", "0");
        } else {
            prop.put(RESULTS + "avgExists", "1");
            prop.put(RESULTS + "avgExists_avgParserRunsPerMinute", (int) (((Integer) r.get(LogParserPLASMA.TOTAL_PARSER_RUNS)).floatValue() / l)); 
        }
        
        String[] names = (String[]) ((HashSet<String>) r.get(LogParserPLASMA.DHT_REJECTED_PEERS_NAME)).toArray();
        String[] hashes = (String[]) ((HashSet<String>) r.get(LogParserPLASMA.DHT_REJECTED_PEERS_HASH)).toArray();
        int i = 0;
        for (; i<names.length && i<hashes.length; i++) {
            prop.put(RESULTS + "useDHTRejectPeers_DHTRejectPeers_" + i + "_name", names[i]);
            prop.put(RESULTS + "useDHTRejectPeers_DHTRejectPeers_" + i + "_hash", hashes[i]);
        }
        prop.put(RESULTS + "DHTRejectPeers", i);
        prop.put(RESULTS + "useDHTRejectPeers", (i > 0) ? "1" : "0");
        prop.put(RESULTS + "useDHTRejectPeers_DHTRejectPeers", i);
        
        names = (String[]) ((HashSet<String>)r.get(LogParserPLASMA.DHT_SENT_PEERS_NAME)).toArray();
        hashes = (String[]) ((HashSet<String>)r.get(LogParserPLASMA.DHT_SENT_PEERS_HASH)).toArray();
        i = 0;
        for (; i<names.length && i<hashes.length; i++) {
            prop.put(RESULTS + "useDHTPeers_DHTPeers_" + i + "_name", names[i]);
            prop.put(RESULTS + "useDHTPeers_DHTPeers_" + i + "_hash", hashes[i]);
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
