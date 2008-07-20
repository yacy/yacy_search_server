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

import de.anomic.crawler.HTTPLoader;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;

public class Network {

    private static final String STR_TABLE_LIST = "table_list_";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> switchboard) {
        plasmaSwitchboard sb = (plasmaSwitchboard) switchboard;
        final long start = System.currentTimeMillis();
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.setLocalized(!(header.get("PATH")).endsWith(".xml"));
        prop.putHTML("page_networkTitle", sb.getConfig("network.unit.description", "unspecified"));
        prop.putHTML("page_networkName", sb.getConfig("network.unit.name", "unspecified"));
        final boolean overview = (post == null) || (post.get("page", "0").equals("0"));

        final String mySeedType = sb.webIndex.seedDB.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
        final boolean iAmActive = (mySeedType.equals(yacySeed.PEERTYPE_SENIOR) || mySeedType.equals(yacySeed.PEERTYPE_PRINCIPAL));

        if (overview) {
            long accActLinks = sb.webIndex.seedDB.countActiveURL();
            long accActWords = sb.webIndex.seedDB.countActiveRWI();
            final long accPassLinks = sb.webIndex.seedDB.countPassiveURL();
            final long accPassWords = sb.webIndex.seedDB.countPassiveRWI();
            long accPotLinks = sb.webIndex.seedDB.countPotentialURL();
            long accPotWords = sb.webIndex.seedDB.countPotentialRWI();

            int conCount = sb.webIndex.seedDB.sizeConnected();
            final int disconCount = sb.webIndex.seedDB.sizeDisconnected();
            int potCount = sb.webIndex.seedDB.sizePotential();

//          final boolean complete = ((post == null) ? false : post.get("links", "false").equals("true"));
            final long otherppm = sb.webIndex.seedDB.countActivePPM();
            final double otherqpm = sb.webIndex.seedDB.countActiveQPM();
            long myppm = 0;
            double myqph = 0d;

            // create own peer info
            yacySeed seed = sb.webIndex.seedDB.mySeed();
            if (sb.webIndex.seedDB.mySeed() != null){ //our Peer
                // update seed info
                sb.updateMySeed();
                
                long LCount;
                long ICount;
                long RCount;
                try {
                    LCount = Long.parseLong(seed.get(yacySeed.LCOUNT, "0"));
                    ICount = Long.parseLong(seed.get(yacySeed.ICOUNT, "0"));
                    RCount = Long.parseLong(seed.get(yacySeed.RCOUNT, "0"));
                } catch (Exception e) {LCount = 0; ICount = 0; RCount = 0;}

                // my-info
                prop.putHTML("table_my-name", seed.get(yacySeed.NAME, "-") );
                prop.put("table_my-hash", seed.hash );
                if (sb.webIndex.seedDB.mySeed().isVirgin()) {
                    prop.put("table_my-info", 0);
                } else if(sb.webIndex.seedDB.mySeed().isJunior()) {
                    prop.put("table_my-info", 1);
                    accPotLinks += LCount;
                    accPotWords += ICount;
                } else if(sb.webIndex.seedDB.mySeed().isSenior()) {
                    prop.put("table_my-info", 2);
                    accActLinks += LCount;
                    accActWords += ICount;
                } else if(sb.webIndex.seedDB.mySeed().isPrincipal()) {
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
                prop.put("table_my-uptime", serverDate.formatInterval(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, ""))));
                prop.putNum("table_my-LCount", LCount);
                prop.putNum("table_my-ICount", ICount);
                prop.putNum("table_my-RCount", RCount);
                prop.putNum("table_my-sI", Long.parseLong(seed.get(yacySeed.INDEX_OUT, "0")));
                prop.putNum("table_my-sU", Long.parseLong(seed.get(yacySeed.URL_OUT, "0")));
                prop.putNum("table_my-rI", Long.parseLong(seed.get(yacySeed.INDEX_IN, "0")));
                prop.putNum("table_my-rU", Long.parseLong(seed.get(yacySeed.URL_IN, "0")));
                prop.putNum("table_my-ppm", myppm);
                prop.putNum("table_my-qph", Math.round(100d * myqph) / 100d);
                prop.putNum("table_my-totalppm", sb.totalPPM);
                prop.putNum("table_my-totalqph", Math.round(6000d * sb.totalQPM) / 100d);
                prop.putNum("table_my-seeds", Long.parseLong(seed.get(yacySeed.SCOUNT, "0")));
                prop.putNum("table_my-connects", Double.parseDouble(seed.get(yacySeed.CCOUNT, "0")));
                prop.put("table_my-url", seed.get(yacySeed.SEEDLIST, ""));
                
                // generating the location string
                prop.putHTML("table_my-location", HttpClient.generateLocation());
            }

            // overall results: Network statistics
            if (iAmActive) conCount++; else if (mySeedType.equals(yacySeed.PEERTYPE_JUNIOR)) potCount++;
            prop.putNum("table_active-count", conCount);
            prop.putNum("table_active-links", accActLinks);
            prop.putNum("table_active-words", accActWords);
            prop.putNum("table_passive-count", disconCount);
            prop.putNum("table_passive-links", accPassLinks);
            prop.putNum("table_passive-words", accPassWords);
            prop.putNum("table_potential-count", potCount);
            prop.putNum("table_potential-links", accPotLinks);
            prop.putNum("table_potential-words", accPotWords);
            prop.putNum("table_all-count", conCount + disconCount + potCount);
            prop.putNum("table_all-links", accActLinks + accPassLinks + accPotLinks);
            prop.putNum("table_all-words", accActWords + accPassWords + accPotWords);

            prop.putNum("table_gppm", otherppm + ((iAmActive) ? myppm : 0));
            prop.putNum("table_gqph", Math.round(6000d * otherqpm + 100d * ((iAmActive) ? myqph : 0d)) / 100d);

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
        } else if (post != null && Integer.parseInt(post.get("page", "1")) == 4) {
            prop.put("table", 4); // triggers overview
            prop.put("page", 4);          

            if (post.containsKey("addPeer")) {

                // AUTHENTICATE
                if (!header.containsKey(httpHeader.AUTHORIZATION)) {
                    prop.putHTML("AUTHENTICATE","log-in");
                    return prop;
                }

                final HashMap<String, String> map = new HashMap<String, String>();
                map.put(yacySeed.IP, post.get("peerIP"));
                map.put(yacySeed.PORT, post.get("peerPort"));
                yacySeed peer = new yacySeed(post.get("peerHash"),map);

                sb.updateMySeed();
                final int added = yacyClient.publishMySeed(sb.webIndex.seedDB.mySeed(), sb.webIndex.peerActions, peer.getPublicAddress(), peer.hash);

                if (added <= 0) {
                    prop.put("table_comment",1);
                    prop.putHTML("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getPublicAddress());
                } else {
                    peer = sb.webIndex.seedDB.getConnected(peer.hash);
                    if (peer == null) {
                        prop.put("table_comment",1);
                        prop.put("table_comment_status","publish: disconnected peer 'UNKNOWN/" + post.get("peerHash") + "' from UNKNOWN");
                    } else {
                        prop.put("table_comment",2);
                        prop.putHTML("table_comment_status","publish: handshaked " + peer.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + peer.getName() + "' at " + peer.getPublicAddress());
                        prop.putHTML("table_comment_details",peer.toString());
                    }
                }

                prop.put("table_peerHash",post.get("peerHash"));
                prop.put("table_peerIP",post.get("peerIP"));
                prop.put("table_peerPort",post.get("peerPort"));                
            } else {
                prop.put("table_peerHash","");
                prop.put("table_peerIP","");
                prop.put("table_peerPort","");

                prop.put("table_comment",0);
            }
        } else {
            // generate table
            final int page = (post == null ? 1 : Integer.parseInt(post.get("page", "1")));
            final int maxCount = (post == null ? 300 : Integer.parseInt(post.get("maxCount", "300")));
            int conCount = 0;            
            if (sb.webIndex.seedDB == null) {
                prop.put("table", 0);//no remote senior/principal proxies known"
            } else {
                int size = 0;
                switch (page) {
                    case 1 : size = sb.webIndex.seedDB.sizeConnected(); break;
                    case 2 : size = sb.webIndex.seedDB.sizeDisconnected(); break;
                    case 3 : size = sb.webIndex.seedDB.sizePotential(); break;
                    default: break;
                }
                if (size == 0) {
                    prop.put("table", 0);//no remote senior/principal proxies known"
                } else {
                    // add temporary the own seed to the database
                    if (iAmActive) {
                        sb.updateMySeed();
                        sb.webIndex.seedDB.addConnected(sb.webIndex.seedDB.mySeed());
                    }

                    // find updated Information using YaCyNews
                    final HashSet<String> updatedProfile = new HashSet<String>();
                    final HashMap<String, Map<String, String>> updatedWiki = new HashMap<String, Map<String, String>>();
                    final HashMap<String, Map<String, String>> updatedBlog = new HashMap<String, Map<String, String>>();
                    final HashMap<String, String> isCrawling = new HashMap<String, String>();
                    yacyNewsRecord record;
                    Iterator<yacyNewsRecord> recordIterator = sb.webIndex.newsPool.recordIterator(yacyNewsPool.INCOMING_DB, true);
                    while (recordIterator.hasNext()) {
                        record = recordIterator.next();
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
                    final boolean complete = (post != null && post.containsKey("ip"));
                    Iterator<yacySeed> e = null;
                    final boolean order = (post != null && post.get("order", "down").equals("up"));
                    final String sort = (post == null ? null : post.get("sort", null));
                    switch (page) {
                        case 1 : e = sb.webIndex.seedDB.seedsSortedConnected(order, (sort == null ? yacySeed.LCOUNT : sort)); break;
                        case 2 : e = sb.webIndex.seedDB.seedsSortedDisconnected(order, (sort == null ? yacySeed.LASTSEEN : sort)); break;
                        case 3 : e = sb.webIndex.seedDB.seedsSortedPotential(order, (sort == null ? yacySeed.LASTSEEN : sort)); break;
                        default: break;
                    }
                    String startURL;
                    Map<String, String> wikiMap;
                    Map<String, String> blogMap;
                    String userAgent, location;
                    int PPM;
                    double QPM;
                    Pattern peerSearchPattern = null;
                    prop.put("regexerror", 0);
                    prop.put("regexerror_wrongregex", (String)null);
                    if (post != null && post.containsKey("search")) {
                        try {
                            peerSearchPattern = Pattern.compile(post.get("match", ""), Pattern.CASE_INSENSITIVE);
                        } catch (PatternSyntaxException pse){
                            prop.put("regexerror", 1);
                            prop.putHTML("regexerror_wrongregex", pse.getPattern());
                        }
                    }
                    if(e != null) {
                    while (e.hasNext() && conCount < maxCount) {
                        seed = e.next();
                        if (seed != null) {
                            if((post != null && post.containsKey("search"))  && peerSearchPattern != null /*(wrongregex == null)*/) {
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
                            if (seed.hash.equals(sb.webIndex.seedDB.mySeed().hash)) {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", 2);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_dark", ((dark) ? 1 : 0) ); dark=!dark;
                            }
                            if (updatedProfile.contains(seed.hash)) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedProfile", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_updatedProfile_hash", seed.hash);
                            }
                            if ((wikiMap = updatedWiki.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki", 0);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki", 1);
                                prop.putHTML(STR_TABLE_LIST + conCount + "_updatedWiki_page", wikiMap.get("page"));
                                prop.put(STR_TABLE_LIST + conCount + "_updatedWiki_address", seed.getPublicAddress());
                            }
                            if ((blogMap = updatedBlog.get(seed.hash)) == null) {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog", 0);
                            } else {
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog", 1);
                                prop.putHTML(STR_TABLE_LIST + conCount + "_updatedBlog_page", blogMap.get("page"));
                                prop.putHTML(STR_TABLE_LIST + conCount + "_updatedBlog_subject", blogMap.get("subject"));
                                prop.put(STR_TABLE_LIST + conCount + "_updatedBlog_address", seed.getPublicAddress());
                            }
                            PPM = seed.getPPM();
                            QPM = seed.getQPM();
                            if (((startURL = isCrawling.get(seed.hash)) != null) && (PPM >= 4)) {
                                prop.put(STR_TABLE_LIST + conCount + "_isCrawling", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_isCrawling_page", startURL);
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_hash", seed.hash);
                            String shortname = seed.get(yacySeed.NAME, "deadlink");
                            if (shortname.length() > 20) {
                                shortname = shortname.substring(0, 20) + "..."; 
                            }
                            prop.putHTML(STR_TABLE_LIST + conCount + "_shortname", shortname);
                            prop.putHTML(STR_TABLE_LIST + conCount + "_fullname", seed.get(yacySeed.NAME, "deadlink"));
                            userAgent = null;
                            if (seed.hash.equals(sb.webIndex.seedDB.mySeed().hash)) {
                                userAgent = HTTPLoader.yacyUserAgent;
                                location = HttpClient.generateLocation();
                            } else {
                               userAgent = sb.webIndex.peerActions.getUserAgent(seed.getIP());
                               location = parseLocationInUserAgent(userAgent);
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_location", location);
                            if (complete) {
                                prop.put(STR_TABLE_LIST + conCount + "_complete", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_ip", seed.getIP() );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_port", seed.get(yacySeed.PORT, "-") );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_hash", seed.hash);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_age", seed.getAge());
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_CRWCnt", Long.parseLong(seed.get(yacySeed.CRWCNT, "0")));
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_CRTCnt", Long.parseLong(seed.get(yacySeed.CRTCNT, "0")));
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_seeds", Long.parseLong(seed.get(yacySeed.SCOUNT, "0")));
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_connects", Double.parseDouble(seed.get(yacySeed.CCOUNT, "0")));
                                prop.putHTML(STR_TABLE_LIST + conCount + "_complete_userAgent", userAgent);
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
                            prop.putHTML(STR_TABLE_LIST + conCount + "_type_url", seed.get(yacySeed.SEEDLIST, "http://nowhere/"));

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
                                prop.putHTML(STR_TABLE_LIST + conCount + "_dhtreceive_peertags", ((peertags == null) || (peertags.length() == 0)) ? "no tags given" : ("tags = " + peertags));
                            }
                            prop.putHTML(STR_TABLE_LIST + conCount + "_version", yacyVersion.combined2prettyVersion(seed.get(yacySeed.VERSION, "0.1"), shortname));
                            prop.putNum(STR_TABLE_LIST + conCount + "_lastSeen", /*seed.getLastSeenString() + " " +*/ lastseen);
                            prop.put(STR_TABLE_LIST + conCount + "_utc", seed.get(yacySeed.UTC, "-"));
                            prop.putHTML(STR_TABLE_LIST + conCount + "_uptime", serverDate.formatInterval(60000 * Long.parseLong(seed.get(yacySeed.UPTIME, "0"))));
                            prop.putNum(STR_TABLE_LIST + conCount + "_LCount", seed.getLong(yacySeed.LCOUNT, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_ICount", seed.getLong(yacySeed.ICOUNT, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_RCount", seed.getLong(yacySeed.RCOUNT, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_sI", seed.getLong(yacySeed.INDEX_OUT, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_sU", seed.getLong(yacySeed.URL_OUT, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_rI", seed.getLong(yacySeed.INDEX_IN, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_rU", seed.getLong(yacySeed.URL_IN, 0));
                            prop.putNum(STR_TABLE_LIST + conCount + "_ppm", PPM);
                            prop.putNum(STR_TABLE_LIST + conCount + "_qph", Math.round(6000d * QPM) / 100d);
                            conCount++;
                        } // seed != null
                    } // while
                    }
                    if (iAmActive) { sb.webIndex.seedDB.removeMySeed(); }
                    prop.putNum("table_list", conCount);
                    prop.put("table", 1);
                    prop.putNum("table_num", conCount);
                    prop.putNum("table_total", ((page == 1) && (iAmActive)) ? (size + 1) : size );
                    prop.put("table_complete", ((complete)? 1 : 0) );                    
                }
            }
            prop.put("page", page);
            prop.put("table_page", page);
            prop.putHTML("table_searchpattern", (post == null ? "" : post.get("match", "")));
            switch (page) {
                case 1 : prop.putHTML("table_peertype", "senior/principal"); break;
                case 2 : prop.putHTML("table_peertype", "senior/principal"); break;
                case 3 : prop.putHTML("table_peertype", yacySeed.PEERTYPE_JUNIOR); break;
                default: break;
            }
        }
        
        prop.putNum("table_rt", System.currentTimeMillis() - start);

        // return rewrite properties
        return prop;
    }

    /**
     * gets the location out of the user agent
     * 
     * location must be after last ; and before first )
     * 
     * @param userAgent in form "useragentinfo (some params; _location_) additional info"
     * @return
     */
    private static String parseLocationInUserAgent(final String userAgent) {
        final String location;

        final int firstOpenParenthesis = userAgent.indexOf('(');
        final int lastSemicolon = userAgent.lastIndexOf(';');
        final int firstClosedParenthesis = userAgent.indexOf(')');

        if (lastSemicolon > 0) {
            // ; Location )
            location = (firstClosedParenthesis > 0) ? userAgent.substring(lastSemicolon + 1, firstClosedParenthesis)
                    .trim() : userAgent.substring(lastSemicolon + 1).trim();
        } else {
            if (firstOpenParenthesis > 0) {
                if (firstClosedParenthesis > 0) {
                    // ( Location )
                    location = userAgent.substring(firstOpenParenthesis + 1, firstClosedParenthesis).trim();
                } else {
                    // ( Location <end>
                    location = userAgent.substring(firstOpenParenthesis + 1).trim();
                }
            } else {
                location = "";
            }
        }

        return location;
    }
}