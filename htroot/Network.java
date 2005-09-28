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

public class Network {

    private static final String STR_TABLE_LIST = "table_list_";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final boolean overview = (post == null) || (((String) post.get("page", "0")).equals("0"));

        final String mySeedType = yacyCore.seedDB.mySeed.get("PeerType", "virgin");
        final boolean iAmActive = (mySeedType.equals("senior")) || (mySeedType.equals("principal"));

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
                    links = Long.parseLong(seed.get("LCount", "0"));
                    words = Long.parseLong(seed.get("ICount", "0"));
                } catch (Exception e) {links = 0; words = 0;}

                prop.put("table_my-name", seed.get("Name", "-") );
                if (yacyCore.seedDB.mySeed.isVirgin()) {
                    prop.put("table_my-type", 0);
                } else if(yacyCore.seedDB.mySeed.isJunior()) {
                    prop.put("table_my-type", 1);
                    accPotLinks += links;
                    accPotWords += words;
                } else if(yacyCore.seedDB.mySeed.isSenior()) {
                    prop.put("table_my-type", 2);
                    accActLinks += links;
                    accActWords += words;
                } else if(yacyCore.seedDB.mySeed.isPrincipal()) {
                    prop.put("table_my-type", 3);
                    accActLinks += links;
                    accActWords += words;
                }
                myppm = seed.getPPM();
                prop.put("table_my-version", seed.get("Version", "-"));
                prop.put("table_my-utc", seed.get("UTC", "-"));
                prop.put("table_my-uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get("Uptime", ""))));
                prop.put("table_my-links", groupDigits(links));
                prop.put("table_my-words", groupDigits(words));
                prop.put("table_my-acceptcrawl", Integer.toString(seed.getFlagAcceptRemoteCrawl() ? 1 : 0) );
                prop.put("table_my-acceptindex", Integer.toString(seed.getFlagAcceptRemoteIndex() ? 1 : 0) );
                prop.put("table_my-sI", seed.get("sI", "-"));
                prop.put("table_my-sU", seed.get("sU", "-"));
                prop.put("table_my-rI", seed.get("rI", "-"));
                prop.put("table_my-rU", seed.get("rU", "-"));
                prop.put("table_my-ppm", myppm);
                prop.put("table_my-seeds", seed.get("SCount", "-"));
                prop.put("table_my-connects", seed.get("CCount", "-"));
            }

            // overall results: Network statistics
            if (iAmActive) conCount++; else if (mySeedType.equals("junior")) potCount++;
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
                map.put("IP",(String) post.get("peerIP"));
                map.put("Port",(String) post.get("peerPort"));
                yacySeed peer = new yacySeed((String) post.get("peerHash"),map);

                final int added = yacyClient.publishMySeed(peer.getAddress(), peer.hash);

                if (added < 0) {
                    prop.put("table_comment",1);
                    prop.put("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getAddress());
                } else {
                    peer = yacyCore.seedDB.getConnected(peer.hash);
                    prop.put("table_comment",2);
                    prop.put("table_comment_status","publish: handshaked " + peer.get("PeerType", "senior") + " peer '" + peer.getName() + "' at " + peer.getAddress());
                    prop.put("table_comment_details",peer.toString());
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
                        case 1 : e = yacyCore.seedDB.seedsSortedConnected(post.get("order", "down").equals("up"), post.get("sort", "LCount")); break;
                        case 2 : e = yacyCore.seedDB.seedsSortedDisconnected(post.get("order", "up").equals("up"), post.get("sort", "LastSeen")); break;
                        case 3 : e = yacyCore.seedDB.seedsSortedPotential(post.get("order", "up").equals("up"), post.get("sort", "LastSeen")); break;
                        default: break;
                    }
                    String startURL;
                    String wikiPage;
                    final StringBuffer alert = new StringBuffer();
                    int PPM;
                    while ((e.hasMoreElements()) && (conCount < maxCount)) {
                        seed = (yacySeed) e.nextElement();
                        if (seed != null) {
                            if (conCount >= maxCount) { break; }
                            if (seed.hash.equals(yacyCore.seedDB.mySeed.hash)) {
                                prop.put(STR_TABLE_LIST+conCount+"_dark", 2);
                            } else {
                                prop.put(STR_TABLE_LIST+conCount+"_dark", ((dark) ? 1 : 0) ); dark=!dark;
                            }
                            alert.setLength(0);
                            if (updatedProfile.contains(seed.hash)) {
                                alert.append("<a href=\"ViewProfile.html?hash=").append(seed.hash).append("\"><img border=\"0\" src=\"/env/grafics/profile.gif\" align=\"bottom\"></a>");
                            }
                            if ((wikiPage = (String) updatedWiki.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST+conCount+"_updatedWikiPage", "");
                            } else {
                                prop.put(STR_TABLE_LIST+conCount+"_updatedWikiPage", "?page=" + wikiPage);
                                alert.append("<a href=\"http://").append(seed.get("Name", "deadlink")).append(".yacy/Wiki.html?page=").append(wikiPage).append("\"><img border=\"0\" src=\"/env/grafics/wiki.gif\" align=\"bottom\"></a>");
                            }
                            try {
                                PPM = Integer.parseInt(seed.get("ISpeed", "-"));
                            } catch (NumberFormatException ee) {
                                PPM = 0;
                            }
                            if (((startURL = (String) isCrawling.get(seed.hash)) != null) && (PPM >= 10)) {
                                alert.append("<a href=\"").append(startURL).append("\"><img border=\"0\" src=\"/env/grafics/crawl.gif\" align=\"bottom\"></a>");
                            }
                            prop.put(STR_TABLE_LIST+conCount+"_alert", alert.toString());
                            long links;
                            long words;
                            try {
                                links = Long.parseLong(seed.get("LCount", "0"));
                                words = Long.parseLong(seed.get("ICount", "0"));
                            } catch (Exception exc) {links = 0; words = 0;}
                            prop.put(STR_TABLE_LIST+conCount+"_hash", seed.hash);
                            String shortname = seed.get("Name", "deadlink");
                            if (shortname.length() > 20) {
                                shortname = shortname.substring(0, 20) + "..."; 
                            }
                            prop.put(STR_TABLE_LIST+conCount+"_shortname", shortname);
                            prop.put(STR_TABLE_LIST+conCount+"_fullname", seed.get("Name", "deadlink"));
                            if (complete) {
                                prop.put(STR_TABLE_LIST+conCount+"_complete", 1);
                                prop.put(STR_TABLE_LIST+conCount+"_complete_ip", seed.get("IP", "-") );
                                prop.put(STR_TABLE_LIST+conCount+"_complete_port", seed.get("Port", "-") );
                                prop.put(STR_TABLE_LIST+conCount+"_complete_hash", seed.hash);
                                prop.put(STR_TABLE_LIST+conCount+"_complete_age", seed.getAge());
                            }else{
                                prop.put(STR_TABLE_LIST+conCount+"_complete", 0);
                            }
                            if (seed.isJunior()) {
                                prop.put(STR_TABLE_LIST+conCount+"_type", 0);
                            } else if(seed.isSenior()){
                                prop.put(STR_TABLE_LIST+conCount+"_type", 1);
                            } else if(seed.isPrincipal()) {
                                prop.put(STR_TABLE_LIST+conCount+"_type", 2);
                                prop.put(STR_TABLE_LIST+conCount+"_type_url", seed.get("seedURL", "http://nowhere/") );
                            }
                            prop.put(STR_TABLE_LIST+conCount+"_version", yacy.combinedVersionString2PrettyString(seed.get("Version", "0.1")));
                            prop.put(STR_TABLE_LIST+conCount+"_contact", (seed.getFlagDirectConnect() ? 1 : 0));
                            prop.put(STR_TABLE_LIST+conCount+"_lastSeen", (System.currentTimeMillis() - seed.getLastSeenTime()) / 1000 / 60);
                            prop.put(STR_TABLE_LIST+conCount+"_utc", seed.get("UTC", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get("Uptime", "0"))));
                            prop.put(STR_TABLE_LIST+conCount+"_links", groupDigits(links));
                            prop.put(STR_TABLE_LIST+conCount+"_words", groupDigits(words));
                            prop.put(STR_TABLE_LIST+conCount+"_acceptcrawl", (seed.getFlagAcceptRemoteCrawl() ? 1 : 0) );
                            prop.put(STR_TABLE_LIST+conCount+"_acceptindex", (seed.getFlagAcceptRemoteIndex() ? 1 : 0) );
                            prop.put(STR_TABLE_LIST+conCount+"_sI", seed.get("sI", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_sU", seed.get("sU", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_rI", seed.get("rI", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_rU", seed.get("rU", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_ppm", PPM);
                            prop.put(STR_TABLE_LIST+conCount+"_seeds", seed.get("SCount", "-"));
                            prop.put(STR_TABLE_LIST+conCount+"_connects", seed.get("CCount", "-"));
                            conCount++;
                        }//seed != null
                    }//while
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
                case 3 : prop.put("table_peertype", "junior"); break;
                default: break;
            }
        }
        // return rewrite properties
        return prop;
    }

    
    private static String groupDigits(long Number) {
        final String s = Long.toString(Number);
        String t = "";
        for (int i = 0; i < s.length(); i++)  t = s.charAt(s.length() - i - 1) + (((i % 3) == 0) ? "," : "") + t;
        return t.substring(0, t.length() - 1);
    }

}
