// Network.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
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

// You must compile this file with
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverDate;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyVersion;

public class Network {

    private static final String STR_TABLE_LIST = "table_list_";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        long start = System.currentTimeMillis();
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final boolean overview = (post == null) || (post.get("page", "0").equals("0"));

        final String mySeedType = yacyCore.seedDB.mySeed.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
        final boolean iAmActive = (mySeedType.equals(yacySeed.PEERTYPE_SENIOR) || mySeedType.equals(yacySeed.PEERTYPE_PRINCIPAL));

        if (overview) {
            long accActLinks = yacyCore.seedDB.countActiveURL();
            long accActWords = yacyCore.seedDB.countActiveRWI();
            final long accPassLinks = yacyCore.seedDB.countPassiveURL();
            final long accPassWords = yacyCore.seedDB.countPassiveRWI();
            long accPotLinks = yacyCore.seedDB.countPotentialURL();
            long accPotWords = yacyCore.seedDB.countPotentialRWI();

            int conCount = yacyCore.seedDB.sizeConnected();
            final int disconCount = yacyCore.seedDB.sizeDisconnected();
            int potCount = yacyCore.seedDB.sizePotential();

//          final boolean complete = ((post == null) ? false : post.get("links", "false").equals("true"));
            final long otherppm = yacyCore.seedDB.countActivePPM();
            long myppm = 0;

            // create own peer info
            yacySeed seed = yacyCore.seedDB.mySeed;
            if (yacyCore.seedDB.mySeed != null){ //our Peer
                long links;
                long words;
                try {
                    links = Long.parseLong(seed.get(yacySeed.LCOUNT, "0"));
                    words = Long.parseLong(seed.get(yacySeed.ICOUNT, "0"));
                } catch (Exception e) {links = 0; words = 0;}

                // my-info
                prop.put("table_my-name", seed.get(yacySeed.NAME, "-") );
                if (yacyCore.seedDB.mySeed.isVirgin()) {
                    prop.put("table_my-info", 0);
                } else if(yacyCore.seedDB.mySeed.isJunior()) {
                    prop.put("table_my-info", 1);
                    accPotLinks += links;
                    accPotWords += words;
                } else if(yacyCore.seedDB.mySeed.isSenior()) {
                    prop.put("table_my-info", 2);
                    accActLinks += links;
                    accActWords += words;
                } else if(yacyCore.seedDB.mySeed.isPrincipal()) {
                    prop.put("table_my-info", 3);
                    accActLinks += links;
                    accActWords += words;
                }
                prop.put("table_my-acceptcrawl", seed.getFlagAcceptRemoteCrawl() ? 1 : 0);
                prop.put("table_my-dhtreceive", seed.getFlagAcceptRemoteIndex() ? 1 : 0);


                myppm = seed.getPPM();
                prop.put("table_my-version", seed.get(yacySeed.VERSION, "-"));
                prop.put("table_my-utc", seed.get(yacySeed.UTC, "-"));
                prop.put("table_my-uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, ""))));
                prop.put("table_my-links", groupDigits(Long.toString(links)));
                prop.put("table_my-words", groupDigits(Long.toString(words)));
                prop.put("table_my-sI", groupDigits(seed.get(yacySeed.INDEX_OUT, "0")));
                prop.put("table_my-sU", groupDigits(seed.get(yacySeed.URL_OUT, "0")));
                prop.put("table_my-rI", groupDigits(seed.get(yacySeed.INDEX_IN, "0")));
                prop.put("table_my-rU", groupDigits(seed.get(yacySeed.URL_IN, "0")));
                prop.put("table_my-ppm", myppm);
                prop.put("table_my-seeds", seed.get(yacySeed.SCOUNT, "-"));
                prop.put("table_my-connects", groupDigits(seed.get(yacySeed.CCOUNT, "0")));
            }

            // overall results: Network statistics
            if (iAmActive) conCount++; else if (mySeedType.equals(yacySeed.PEERTYPE_JUNIOR)) potCount++;
            prop.put("table_active-count", conCount);
            prop.put("table_active-links", groupDigits(accActLinks));
            prop.put("table_active-words", groupDigits(accActWords));
            prop.put("table_passive-count", disconCount);
            prop.put("table_passive-links", groupDigits(accPassLinks));
            prop.put("table_passive-words", groupDigits(accPassWords));
            prop.put("table_potential-count", potCount);
            prop.put("table_potential-links", groupDigits(accPotLinks));
            prop.put("table_potential-words", groupDigits(accPotWords));
            prop.put("table_all-count", (conCount + disconCount + potCount));
            prop.put("table_all-links", groupDigits(accActLinks + accPassLinks + accPotLinks));
            prop.put("table_all-words", groupDigits(accActWords + accPassWords + accPotWords));

            prop.put("table_gppm", otherppm + ((iAmActive) ? myppm : 0));

//          String comment = "";
            prop.put("table_comment", 0);
            if (conCount == 0) {
                if (Integer.parseInt(sb.getConfig("onlineMode", "1")) == 2) {
                    prop.put("table_comment", 1);//in onlinemode, but not online
                } else {
                    prop.put("table_comment", 2);//not in online mode, and not online
                }
            }
            prop.put("table", 2); // triggers overview
            prop.put("page", 0);
        } else if (Integer.parseInt(post.get("page", "1")) == 4) {
            prop.put("table", 4); // triggers overview
            prop.put("page", 4);          

            if (post.containsKey("addPeer")) {

                // AUTHENTICATE
                if (!header.containsKey(httpHeader.AUTHORIZATION)) {
                    prop.put("AUTHENTICATE","log-in");
                    return prop;
                }

                final HashMap map = new HashMap();
                map.put(yacySeed.IP,post.get("peerIP"));
                map.put(yacySeed.PORT,post.get("peerPort"));
                yacySeed peer = new yacySeed((String) post.get("peerHash"),map);

                yacyCore.peerActions.updateMySeed();
                final int added = yacyClient.publishMySeed(peer.getAddress(), peer.hash);

                if (added <= 0) {
                    prop.put("table_comment",1);
                    prop.put("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getAddress());
                } else {
                    peer = yacyCore.seedDB.getConnected(peer.hash);
                    if (peer == null) {
                        prop.put("table_comment",1);
                        prop.put("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getAddress());                     
                    } else {
                        prop.put("table_comment",2);
                        prop.put("table_comment_status","publish: handshaked " + peer.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + peer.getName() + "' at " + peer.getAddress());
                        prop.put("table_comment_details",peer.toString());
                    }
                }

                prop.put("table_peerHash",(String) post.get("peerHash"));
                prop.put("table_peerIP",(String)post.get("peerIP"));
                prop.put("table_peerPort",(String) post.get("peerPort"));                
            } else {
                prop.put("table_peerHash","");
                prop.put("table_peerIP","");
                prop.put("table_peerPort","");

                prop.put("table_comment",0);
            }
        } else {
            // generate table
            final int page = Integer.parseInt(post.get("page", "1"));
            final int maxCount = 300;
            int conCount = 0;            
            if (yacyCore.seedDB == null) {
                prop.put("table", 0);//no remote senior/principal proxies known"
            } else {
                int size = 0;
                switch (page) {
                    case 1 : size = yacyCore.seedDB.sizeConnected(); break;
                    case 2 : size = yacyCore.seedDB.sizeDisconnected(); break;
                    case 3 : size = yacyCore.seedDB.sizePotential(); break;
                    default: break;
                }
                if (size == 0) {
                    prop.put("table", 0);//no remote senior/principal proxies known"
                } else {
                    // add temporary the own seed to the database
                    if (iAmActive) {
                        yacyCore.peerActions.updateMySeed();
                        yacyCore.seedDB.addConnected(yacyCore.seedDB.mySeed);
                    }

                    // find updated Information using YaCyNews
                    final HashSet updatedProfile = new HashSet();
                    final HashMap updatedWiki = new HashMap();
                    final HashMap isCrawling = new HashMap();
                    int availableNews = yacyCore.newsPool.size(yacyNewsPool.INCOMING_DB);
                    if (availableNews > 300) { availableNews = 300; }
                    yacyNewsRecord record;
                    try {
                        for (int c = availableNews - 1; c >= 0; c--) {
                            record = yacyCore.newsPool.get(yacyNewsPool.INCOMING_DB, c);
                            if (record == null) {
                                break;
                            } else if (record.category().equals("prfleupd")) {
                                updatedProfile.add(record.originator());
                            } else if (record.category().equals("wiki_upd")) {
                                updatedWiki.put(record.originator(), record.attributes().get("page"));
                            } else if (record.category().equals("crwlstrt")) {
                                isCrawling.put(record.originator(), record.attributes().get("startURL"));
                            }
                        }
                    } catch (IOException e) {}

                    boolean dark = true;
                    yacySeed seed;
                    final boolean complete = post.containsKey("ip");
                    Enumeration e = null;
                    switch (page) {
                        case 1 : e = yacyCore.seedDB.seedsSortedConnected(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LCOUNT)); break;
                        case 2 : e = yacyCore.seedDB.seedsSortedDisconnected(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LASTSEEN)); break;
                        case 3 : e = yacyCore.seedDB.seedsSortedPotential(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LASTSEEN)); break;
                        default: break;
                    }
                    int p;
                    String startURL;
                    String wikiPage;
                    String userAgent, location;
                    final StringBuffer alert = new StringBuffer();
                    int PPM;
                    while (e.hasMoreElements() && conCount < maxCount) {
                        seed = (yacySeed) e.nextElement();
                        if (seed != null) {
                            if (conCount >= maxCount) { break; }
                            if (seed.hash.equals(yacyCore.seedDB.mySeed.hash)) {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", 2);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                            }
                            alert.setLength(0);
                            if (updatedProfile.contains(seed.hash)) {
                                alert.append("<a href=\"ViewProfile.html?hash=").append(seed.hash).append("\"><img border=\"0\" src=\"/env/grafics/profile.gif\" align=\"bottom\"></a>");
                            }
                            if ((wikiPage = (String) updatedWiki.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWikiPage", "");
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWikiPage", "?page=" + wikiPage);
                                alert.append("<a href=\"http://").append(seed.get(yacySeed.NAME, "deadlink")).append(".yacy/Wiki.html?page=").append(wikiPage).append("\"><img border=\"0\" src=\"/env/grafics/wiki.gif\" align=\"bottom\"></a>");
                            }
                            try {
                                PPM = Integer.parseInt(seed.get(yacySeed.ISPEED, "-"));
                            } catch (NumberFormatException ee) {
                                PPM = 0;
                            }
                            if (((startURL = (String) isCrawling.get(seed.hash)) != null) && (PPM >= 10)) {
                                alert.append("<a href=\"").append(startURL).append("\"><img border=\"0\" src=\"/env/grafics/crawl.gif\" align=\"bottom\"></a>");
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_alert", alert.toString());
                            prop.put(STR_TABLE_LIST + conCount + "_hash", seed.hash);
                            String shortname = seed.get(yacySeed.NAME, "deadlink");
                            if (shortname.length() > 20) {
                                shortname = shortname.substring(0, 20) + "..."; 
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_shortname", shortname);
                            prop.put(STR_TABLE_LIST + conCount + "_fullname", seed.get(yacySeed.NAME, "deadlink"));
                            userAgent = yacyCore.peerActions.getUserAgent(seed.getIP());
                            p = userAgent.lastIndexOf(';');
                            location = (p > 0) ? userAgent.substring(p + 1, userAgent.length() - 1).trim(): "";
                            prop.put(STR_TABLE_LIST + conCount + "_location", location);
                            if (complete) {
                                prop.put(STR_TABLE_LIST + conCount + "_complete", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_ip", seed.get(yacySeed.IP, "-") );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_port", seed.get(yacySeed.PORT, "-") );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_hash", seed.hash);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_age", seed.getAge());
                                prop.put(STR_TABLE_LIST + conCount + "_complete_CRWCnt", seed.get(yacySeed.CRWCNT, "0"));
                                prop.put(STR_TABLE_LIST + conCount + "_complete_CRTCnt", seed.get(yacySeed.CRTCNT, "0"));
                                prop.put(STR_TABLE_LIST + conCount + "_complete_seeds", seed.get(yacySeed.SCOUNT, "-"));
                                prop.put(STR_TABLE_LIST + conCount + "_complete_connects", groupDigits(seed.get(yacySeed.CCOUNT, "0")));
                                prop.put(STR_TABLE_LIST + conCount + "_complete_userAgent", userAgent);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_complete", 0);
                            }


                            if (seed.isJunior()) {
                                prop.put(STR_TABLE_LIST + conCount + "_type", 0);
                            } else if(seed.isSenior()){
                                prop.put(STR_TABLE_LIST + conCount + "_type", 1);
                            } else if(seed.isPrincipal()) {
                                prop.put(STR_TABLE_LIST + conCount + "_type", 2);
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_type_url", seed.get("seedURL", "http://nowhere/"));

                            long lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenTime()) / 1000 / 60);
                            if (page == 2 || lastseen > 1440) { // Passive Peers should be passive, also Peers without contact greater than an day
                                // principal/senior/junior: red/red=offline
                                prop.put(STR_TABLE_LIST + conCount + "_type_direct", 2);
                            } else {
                                // principal/senior: green/green=direct or yellow/yellow=passive
                                // junior: red/green=direct or red/yellow=passive
                                prop.put(STR_TABLE_LIST + conCount + "_type_direct", seed.getFlagDirectConnect() ? 1 : 0);
                            }
                            
                            if (page == 1) {
                                prop.put(STR_TABLE_LIST + conCount + "_acceptcrawl", seed.getFlagAcceptRemoteCrawl() ? 1 : 0); // green=on or red=off 
                                prop.put(STR_TABLE_LIST + conCount + "_dhtreceive", seed.getFlagAcceptRemoteIndex() ? 1 : 0);  // green=on or red=off
                                prop.put(STR_TABLE_LIST + conCount + "_rankingreceive", (seed.getVersion() >= yacyVersion.YACY_ACCEPTS_RANKING_TRANSMISSION) ? 1 : 0);
                            } else { // Passive, Potential Peers
                                if (seed.getFlagAcceptRemoteCrawl()) {
                                    prop.put(STR_TABLE_LIST + conCount + "_acceptcrawl", 2); // red/green: offline, was on
                                } else {
                                    prop.put(STR_TABLE_LIST + conCount + "_acceptcrawl", 0); // red/red; offline was off
                                }
                                if (seed.getFlagAcceptRemoteIndex()) {
                                    prop.put(STR_TABLE_LIST + conCount + "_dhtreceive", 2);  // red/green: offline, was on
                                } else {
                                    prop.put(STR_TABLE_LIST + conCount + "_dhtreceive", 0);  // red/red; offline was off
                                }
                                if (seed.getVersion() >= yacyVersion.YACY_ACCEPTS_RANKING_TRANSMISSION) {
                                    prop.put(STR_TABLE_LIST + conCount + "_rankingreceive", 1);
                                } else {
                                    prop.put(STR_TABLE_LIST + conCount + "_rankingreceive", 0);
                                }
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_version", yacy.combinedVersionString2PrettyString(seed.get(yacySeed.VERSION, "0.1")));
                            prop.put(STR_TABLE_LIST + conCount + "_lastSeen", lastseen);
                            prop.put(STR_TABLE_LIST + conCount + "_utc", seed.get(yacySeed.UTC, "-"));
                            prop.put(STR_TABLE_LIST + conCount + "_uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, "0"))));
                            prop.put(STR_TABLE_LIST + conCount + "_links", groupDigits(seed.get(yacySeed.LCOUNT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_words", groupDigits(seed.get(yacySeed.ICOUNT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_sI", groupDigits(seed.get(yacySeed.INDEX_OUT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_sU", groupDigits(seed.get(yacySeed.URL_OUT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_rI", groupDigits(seed.get(yacySeed.INDEX_IN, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_rU", groupDigits(seed.get(yacySeed.URL_IN, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_ppm", PPM);
                            conCount++;
                        } // seed != null
                    } // while
                    if (iAmActive) { yacyCore.seedDB.removeMySeed(); }
                    prop.put("table_list", conCount);
                    prop.put("table", 1);
                    prop.put("table_num", conCount);
                    prop.put("table_total", (maxCount > conCount) ? conCount : maxCount);
                    prop.put("table_complete", ((complete)? 1 : 0) );
                }
            }
            prop.put("page", page);
            prop.put("table_page", page);
            switch (page) {
                case 1 : prop.put("table_peertype", "senior/principal"); break;
                case 2 : prop.put("table_peertype", "senior/principal"); break;
                case 3 : prop.put("table_peertype", yacySeed.PEERTYPE_JUNIOR); break;
                default: break;
            }
        }
        prop.put("table_rt", System.currentTimeMillis() - start);
        // return rewrite properties
        return prop;
    }

    private static String groupDigits(String sValue) {
        long lValue;
        try {
            if (sValue.endsWith(".0")) { sValue = sValue.substring(0, sValue.length() - 2); } // for Connects per hour, why float ?
            lValue = Long.parseLong(sValue);
        } catch (Exception e) {lValue = 0;}
        if (lValue == 0) { return "-"; }
        sValue = Long.toString(lValue);
        String rValue = "";
        for (int i = 0; i < sValue.length(); i++) { rValue = sValue.charAt(sValue.length() - i - 1) + (((i % 3) == 0) ? "." : "") + rValue; }
        return rValue.substring(0, rValue.length() - 1);
    }

    private static String groupDigits(long Number) {
        final String s = Long.toString(Number);
        String t = "";
        for (int i = 0; i < s.length(); i++) t = s.charAt(s.length() - i - 1) + (((i % 3) == 0) ? "." : "") + t;
        return t.substring(0, t.length() - 1);
    }

}