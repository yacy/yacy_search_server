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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.yFormatter;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;

public class Network {

    private static final String STR_TABLE_LIST = "table_list_";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch switchboard) {
        boolean isXML = ((String)header.get("PATH")).endsWith(".xml");
        plasmaSwitchboard sb = (plasmaSwitchboard) switchboard;
        final long start = System.currentTimeMillis();
        if (isXML) {
            yFormatter.setLocale("none");
        }
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.put("page_networkTitle", sb.getConfig("network.unit.description", "unspecified"));
        prop.put("page_networkName", sb.getConfig("network.unit.name", "unspecified"));
        final boolean overview = (post == null) || (post.get("page", "0").equals("0"));

        final String mySeedType = yacyCore.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
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
            final double otherqpm = yacyCore.seedDB.countActiveQPM();
            long myppm = 0;
            double myqph = 0d;

            // create own peer info
            yacySeed seed = yacyCore.seedDB.mySeed();
            if (yacyCore.seedDB.mySeed() != null){ //our Peer
                // update seed info
                yacyCore.peerActions.updateMySeed();
                
                long LCount;
                long ICount;
                long RCount;
                try {
                    LCount = Long.parseLong(seed.get(yacySeed.LCOUNT, "0"));
                    ICount = Long.parseLong(seed.get(yacySeed.ICOUNT, "0"));
                    RCount = Long.parseLong(seed.get(yacySeed.RCOUNT, "0"));
                } catch (Exception e) {LCount = 0; ICount = 0; RCount = 0;}

                // my-info
                prop.put("table_my-name", seed.get(yacySeed.NAME, "-") );
                prop.put("table_my-hash", seed.hash );
                if (yacyCore.seedDB.mySeed().isVirgin()) {
                    prop.put("table_my-info", 0);
                } else if(yacyCore.seedDB.mySeed().isJunior()) {
                    prop.put("table_my-info", 1);
                    accPotLinks += LCount;
                    accPotWords += ICount;
                } else if(yacyCore.seedDB.mySeed().isSenior()) {
                    prop.put("table_my-info", 2);
                    accActLinks += LCount;
                    accActWords += ICount;
                } else if(yacyCore.seedDB.mySeed().isPrincipal()) {
                    prop.put("table_my-info", 3);
                    accActLinks += LCount;
                    accActWords += ICount;
                }
                prop.put("table_my-acceptcrawl", seed.getFlagAcceptRemoteCrawl() ? 1 : 0);
                prop.put("table_my-dhtreceive", seed.getFlagAcceptRemoteIndex() ? 1 : 0);
                prop.put("table_my-rankingreceive", seed.getFlagAcceptCitationReference() ? 1 : 0);


                myppm = seed.getPPM();
                myqph = 60d * seed.getQPM();
                prop.put("table_my-version", seed.get(yacySeed.VERSION, "-"));
                prop.put("table_my-utc", seed.get(yacySeed.UTC, "-"));
                prop.put("table_my-uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, ""))));
                prop.put("table_my-LCount", yFormatter.number(LCount));
                prop.put("table_my-ICount", yFormatter.number(ICount));
                prop.put("table_my-RCount", yFormatter.number(RCount));
                prop.put("table_my-sI", yFormatter.number(seed.get(yacySeed.INDEX_OUT, "0")));
                prop.put("table_my-sU", yFormatter.number(seed.get(yacySeed.URL_OUT, "0")));
                prop.put("table_my-rI", yFormatter.number(seed.get(yacySeed.INDEX_IN, "0")));
                prop.put("table_my-rU", yFormatter.number(seed.get(yacySeed.URL_IN, "0")));
                prop.put("table_my-ppm", myppm);
                prop.put("table_my-qph", yFormatter.number(Math.round(100d * myqph) / 100d));
                prop.put("table_my-totalppm", yFormatter.number(sb.totalPPM));
                prop.put("table_my-totalqph", yFormatter.number(Math.round(6000d * sb.totalQPM) / 100d));
                prop.put("table_my-seeds", yFormatter.number(seed.get(yacySeed.SCOUNT, "-")));
                prop.put("table_my-connects", yFormatter.number(seed.get(yacySeed.CCOUNT, "0")));
                prop.put("table_my-url", seed.get("seedURL", ""));
                
                // generating the location string
                String location = httpc.userAgent;
                int p = location.lastIndexOf(';');
                location = (p > 0) ? location.substring(p + 1, location.length() - 1).trim(): "";
                prop.put("table_my-location", location);
            }

            // overall results: Network statistics
            if (iAmActive) conCount++; else if (mySeedType.equals(yacySeed.PEERTYPE_JUNIOR)) potCount++;
            prop.put("table_active-count", yFormatter.number(conCount));
            prop.put("table_active-links", yFormatter.number(accActLinks));
            prop.put("table_active-words", yFormatter.number(accActWords));
            prop.put("table_passive-count", yFormatter.number(disconCount));
            prop.put("table_passive-links", yFormatter.number(accPassLinks));
            prop.put("table_passive-words", yFormatter.number(accPassWords));
            prop.put("table_potential-count", yFormatter.number(potCount));
            prop.put("table_potential-links", yFormatter.number(accPotLinks));
            prop.put("table_potential-words", yFormatter.number(accPotWords));
            prop.put("table_all-count", yFormatter.number(conCount + disconCount + potCount));
            prop.put("table_all-links", yFormatter.number(accActLinks + accPassLinks + accPotLinks));
            prop.put("table_all-words", yFormatter.number(accActWords + accPassWords + accPotWords));

            prop.put("table_gppm", otherppm + ((iAmActive) ? myppm : 0));
            prop.put("table_gqph", yFormatter.number(Math.round(6000d * otherqpm + 100d * ((iAmActive) ? myqph : 0d)) / 100d));

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
                final int added = yacyClient.publishMySeed(peer.getPublicAddress(), peer.hash);

                if (added <= 0) {
                    prop.put("table_comment",1);
                    prop.put("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getPublicAddress());
                } else {
                    peer = yacyCore.seedDB.getConnected(peer.hash);
                    if (peer == null) {
                        prop.put("table_comment",1);
                        prop.put("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getPublicAddress());                     
                    } else {
                        prop.put("table_comment",2);
                        prop.put("table_comment_status","publish: handshaked " + peer.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + peer.getName() + "' at " + peer.getPublicAddress());
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
            final int maxCount = Integer.parseInt(post.get("maxCount", "300"));
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
                        yacyCore.seedDB.addConnected(yacyCore.seedDB.mySeed());
                    }

                    // find updated Information using YaCyNews
                    final HashSet updatedProfile = new HashSet();
                    final HashMap updatedWiki = new HashMap();
                    final HashMap updatedBlog = new HashMap();
                    final HashMap isCrawling = new HashMap();
                    yacyNewsRecord record;
                    Iterator recordIterator = yacyCore.newsPool.recordIterator(yacyNewsPool.INCOMING_DB, true);
                    while (recordIterator.hasNext()) {
                        record = (yacyNewsRecord) recordIterator.next();
                        if (record == null) {
                            continue;
                        } else if (record.category().equals(yacyNewsPool.CATEGORY_PROFILE_UPDATE)) {
                            updatedProfile.add(record.originator());
                        } else if (record.category().equals(yacyNewsPool.CATEGORY_WIKI_UPDATE)) {
                            updatedWiki.put(record.originator(), record.attributes());
                        } else if (record.category().equals(yacyNewsPool.CATEGORY_BLOG_ADD)) {
                            updatedBlog.put(record.originator(), record.attributes());
                        } else if (record.category().equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                            isCrawling.put(record.originator(), record.attributes().get("startURL"));
                        }
                    }

                    boolean dark = true;
                    yacySeed seed;
                    final boolean complete = post.containsKey("ip");
                    Iterator e = null;
                    switch (page) {
                        case 1 : e = yacyCore.seedDB.seedsSortedConnected(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LCOUNT)); break;
                        case 2 : e = yacyCore.seedDB.seedsSortedDisconnected(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LASTSEEN)); break;
                        case 3 : e = yacyCore.seedDB.seedsSortedPotential(post.get("order", "down").equals("up"), post.get("sort", yacySeed.LASTSEEN)); break;
                        default: break;
                    }
                    int p;
                    String startURL;
                    Map wikiMap;
                    Map blogMap;
                    String userAgent, location;
                    int PPM;
                    double QPM;
                    Pattern peerSearchPattern = null;
                    String wrongregex = null;
                    prop.put("regexerror", 0);
                    prop.put("regexerror_wrongregex", (String)null);
                    if(post.containsKey("search")) {
                        try {
                            peerSearchPattern = Pattern.compile(post.get("match", ""), Pattern.CASE_INSENSITIVE);
                        } catch (PatternSyntaxException pse){
                            wrongregex = pse.getPattern();
                            prop.put("regexerror", 1);
                            prop.put("regexerror_wrongregex", wrongregex);
                        }
                    }
                    while (e.hasNext() && conCount < maxCount) {
                        seed = (yacySeed) e.next();
                        if (seed != null) {
                            if((post.containsKey("search"))  && (wrongregex == null)) {
                                boolean abort = true;
                                Matcher m = peerSearchPattern.matcher (seed.getName());
                                if (m.find ()) {
                                    abort = false;
                                }
                                m = peerSearchPattern.matcher (seed.hash);
                                if (m.find ()) {
                                    abort = false;
                                }
                                if (abort) continue;
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_updatedProfile", 0);
                            prop.put(STR_TABLE_LIST + conCount + "_updatedWikiPage", 0);
                            prop.put(STR_TABLE_LIST + conCount + "_updatedBlog", 0);
                            prop.put(STR_TABLE_LIST + conCount + "_isCrawling", 0);
                            if (conCount >= maxCount) { break; }
                            if (seed.hash.equals(yacyCore.seedDB.mySeed().hash)) {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", 2);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                            }
                            if (updatedProfile.contains(seed.hash)) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedProfile", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_updatedProfile_hash", seed.hash);
                            }
                            if ((wikiMap = (Map) updatedWiki.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki", 0);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki_page", (String) wikiMap.get("page"));
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki_address", seed.getPublicAddress());
                            }
                            if ((blogMap = (Map) updatedBlog.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog", 0);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog_page", (String) blogMap.get("page"));
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog_subject", (String) blogMap.get("subject"));
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog_address", seed.getPublicAddress());
                            }
                            PPM = seed.getPPM();
                            QPM = seed.getQPM();
                            if (((startURL = (String) isCrawling.get(seed.hash)) != null) && (PPM >= 4)) {
                                prop.put(STR_TABLE_LIST + conCount + "_isCrawling", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_isCrawling_page", startURL);
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_hash", seed.hash);
                            String shortname = seed.get(yacySeed.NAME, "deadlink");
                            if (shortname.length() > 20) {
                                shortname = shortname.substring(0, 20) + "..."; 
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_shortname", shortname);
                            prop.put(STR_TABLE_LIST + conCount + "_fullname", seed.get(yacySeed.NAME, "deadlink"));
                            userAgent = null;
                            if (seed.hash.equals(yacyCore.seedDB.mySeed().hash)) {
                               userAgent = httpc.userAgent;
                            } else {
                               userAgent = yacyCore.peerActions.getUserAgent(seed.getIP());
                            }
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
                                prop.put(STR_TABLE_LIST + conCount + "_complete_connects", yFormatter.number(seed.get(yacySeed.CCOUNT, "0")));
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

                            final long lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
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
                                
                                if (seed.getVersion() >= yacyVersion.YACY_ACCEPTS_RANKING_TRANSMISSION &&
                                    seed.getFlagAcceptCitationReference()) {
                                    prop.put(STR_TABLE_LIST + conCount + "_rankingreceive", 1);
                                } else {
                                    prop.put(STR_TABLE_LIST + conCount + "_rankingreceive", 0);
                                }
                            }
                            if (seed.getFlagAcceptRemoteIndex()) {
                                prop.put(STR_TABLE_LIST + conCount + "_dhtreceive_peertags", "");
                            } else {
                                String peertags = serverCodings.set2string(seed.getPeerTags(), ",", false);
                                prop.put(STR_TABLE_LIST + conCount + "_dhtreceive_peertags", ((peertags == null) || (peertags.length() == 0)) ? "no tags given" : ("tags = " + peertags));
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_version", yacyVersion.combined2prettyVersion(seed.get(yacySeed.VERSION, "0.1"), shortname));
                            prop.put(STR_TABLE_LIST + conCount + "_lastSeen", /*seed.getLastSeenString() + " " +*/ lastseen);
                            prop.put(STR_TABLE_LIST + conCount + "_utc", seed.get(yacySeed.UTC, "-"));
                            prop.put(STR_TABLE_LIST + conCount + "_uptime", serverDate.intervalToString(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, "0"))));
                            prop.put(STR_TABLE_LIST + conCount + "_LCount", yFormatter.number(seed.get(yacySeed.LCOUNT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_ICount", yFormatter.number(seed.get(yacySeed.ICOUNT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_RCount", yFormatter.number(seed.get(yacySeed.RCOUNT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_sI", yFormatter.number(seed.get(yacySeed.INDEX_OUT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_sU", yFormatter.number(seed.get(yacySeed.URL_OUT, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_rI", yFormatter.number(seed.get(yacySeed.INDEX_IN, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_rU", yFormatter.number(seed.get(yacySeed.URL_IN, "0")));
                            prop.put(STR_TABLE_LIST + conCount + "_ppm", PPM);
                            prop.put(STR_TABLE_LIST + conCount + "_qph", yFormatter.number(Math.round(6000d * QPM) / 100d));
                            conCount++;
                        } // seed != null
                    } // while
                    if (iAmActive) { yacyCore.seedDB.removeMySeed(); }
                    prop.put("table_list", conCount);
                    prop.put("table", 1);
                    prop.put("table_num", conCount);
                    prop.put("table_total", ((page == 1) && (iAmActive)) ? (size + 1) : size );
                    prop.put("table_complete", ((complete)? 1 : 0) );                    
                }
            }
            prop.put("page", page);
            prop.put("table_page", page);
            prop.put("table_searchpattern", post.get("match", ""));
            switch (page) {
                case 1 : prop.put("table_peertype", "senior/principal"); break;
                case 2 : prop.put("table_peertype", "senior/principal"); break;
                case 3 : prop.put("table_peertype", yacySeed.PEERTYPE_JUNIOR); break;
                default: break;
            }
        }
        
        prop.put("table_rt", System.currentTimeMillis() - start);
        if (isXML) {
            // restore locale for formatter
            yFormatter.setLocale(sb.getConfig("locale.lang", "default"));
        }
        // return rewrite properties
        return prop;
    }
}