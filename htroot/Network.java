// Network.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.MapTools;

//import de.anomic.http.client.Client;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyNewsDB;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyPeerActions;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyVersion;
import java.util.concurrent.ConcurrentMap;

public class Network {

    private static final String STR_TABLE_LIST = "table_list_";

    public static serverObjects respond(final RequestHeader requestHeader, final serverObjects post, final serverSwitch switchboard) {
        final Switchboard sb = (Switchboard) switchboard;
        final long start = System.currentTimeMillis();
        
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        prop.put("menu", post == null ? 2 : (post.get("menu", "").equals("embed")) ? 0 : (post.get("menu","").equals("simple")) ? 1 : 2);
        if (sb.peers.mySeed() != null) prop.put("menu_newpeer_peerhash", sb.peers.mySeed().hash);
        
        prop.setLocalized(!(requestHeader.get(HeaderFramework.CONNECTION_PROP_PATH)).endsWith(".xml"));
        prop.putHTML("page_networkTitle", sb.getConfig("network.unit.description", "unspecified"));
        prop.putHTML("page_networkName", sb.getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified"));
        final boolean overview = (post == null) || (post.get("page", "0").equals("0"));

        final String mySeedType = sb.peers.mySeed().get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);
        final boolean iAmActive = (mySeedType.equals(yacySeed.PEERTYPE_SENIOR) || mySeedType.equals(yacySeed.PEERTYPE_PRINCIPAL));

        if (overview) {
            long accActLinks = sb.peers.countActiveURL();
            long accActWords = sb.peers.countActiveRWI();
            final long accPassLinks = sb.peers.countPassiveURL();
            final long accPassWords = sb.peers.countPassiveRWI();
            long accPotLinks = sb.peers.countPotentialURL();
            long accPotWords = sb.peers.countPotentialRWI();

            int conCount = sb.peers.sizeConnected();
            final int disconCount = sb.peers.sizeDisconnected();
            int potCount = sb.peers.sizePotential();

//          final boolean complete = ((post == null) ? false : post.get("links", "false").equals("true"));
            final long otherppm = sb.peers.countActivePPM();
            final double otherqpm = sb.peers.countActiveQPM();
            long myppm = 0;
            double myqph = 0d;

            // create own peer info
            final yacySeed seed = sb.peers.mySeed();
            if (sb.peers.mySeed() != null){ //our Peer
                // update seed info
                sb.updateMySeed();
                
                final long LCount = seed.getLinkCount();
                final long ICount = seed.getWordCount();
                final long RCount = seed.getLong(yacySeed.RCOUNT, 0L);

                // my-info
                prop.putHTML("table_my-name", seed.get(yacySeed.NAME, "-") );
                prop.put("table_my-hash", seed.hash );
                if (sb.peers.mySeed().isVirgin()) {
                    prop.put("table_my-info", 0);
                } else if(sb.peers.mySeed().isJunior()) {
                    prop.put("table_my-info", 1);
                    accPotLinks += LCount;
                    accPotWords += ICount;
                } else if(sb.peers.mySeed().isSenior()) {
                    prop.put("table_my-info", 2);
                    accActLinks += LCount;
                    accActWords += ICount;
                } else if(sb.peers.mySeed().isPrincipal()) {
                    prop.put("table_my-info", 3);
                    accActLinks += LCount;
                    accActWords += ICount;
                }
                prop.put("table_my-acceptcrawl", seed.getFlagAcceptRemoteCrawl() ? 1 : 0);
                prop.put("table_my-dhtreceive", seed.getFlagAcceptRemoteIndex() ? 1 : 0);


                myppm = sb.currentPPM();
                myqph = 60d * sb.averageQPM();
                prop.put("table_my-version", seed.get(yacySeed.VERSION, "-"));
                prop.put("table_my-utc", seed.get(yacySeed.UTC, "-"));
                prop.put("table_my-uptime", yacyPeerActions.formatInterval(60000 * seed.getLong(yacySeed.UPTIME, 0)));
                prop.putNum("table_my-LCount", LCount);
                prop.putNum("table_my-ICount", ICount);
                prop.putNum("table_my-RCount", RCount);
                prop.putNum("table_my-sI", seed.getLong(yacySeed.INDEX_OUT, 0L));
                prop.putNum("table_my-sU", seed.getLong(yacySeed.URL_OUT, 0L));
                prop.putNum("table_my-rI", seed.getLong(yacySeed.INDEX_IN, 0L));
                prop.putNum("table_my-rU", seed.getLong(yacySeed.URL_IN, 0L));
                prop.putNum("table_my-ppm", myppm);
                prop.putNum("table_my-qph", Math.round(100d * myqph) / 100d);
                prop.putNum("table_my-qph-publocal", Math.round(6000d * sb.averageQPMPublicLocal()) / 100d);
                prop.putNum("table_my-qph-pubremote", Math.round(6000d * sb.averageQPMGlobal()) / 100d);
                prop.putNum("table_my-seeds", seed.getLong(yacySeed.SCOUNT, 0L));
                prop.putNum("table_my-connects", seed.getFloat(yacySeed.CCOUNT, 0F));
                prop.put("table_my-url", seed.get(yacySeed.SEEDLISTURL, ""));
                
                // generating the location string
                prop.putHTML("table_my-location", ClientIdentification.generateLocation());
            }

            // overall results: Network statistics
            if (iAmActive) conCount++; else if (mySeedType.equals(yacySeed.PEERTYPE_JUNIOR)) potCount++;
            int activeLastMonth = sb.peers.sizeActiveSince(30 * 1440);
            int activeLastWeek = sb.peers.sizeActiveSince(7 * 1440);
            int activeLastDay = sb.peers.sizeActiveSince(1440);
            int activeSwitch =
                (activeLastDay <= conCount) ? 0 :
                (activeLastWeek <= activeLastDay) ? 1 :
                (activeLastMonth <= activeLastWeek) ? 2 : 3;
            prop.putNum("table_active-switch", activeSwitch);
            prop.putNum("table_active-switch_last-month", activeLastMonth);
            prop.putNum("table_active-switch_last-week", activeLastWeek);
            prop.putNum("table_active-switch_last-day", activeLastDay);
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
            prop.put("table", 2); // triggers overview
            prop.put("page", 0);
        } else if (post != null && post.getInt("page", 1) == 4) {
            prop.put("table", 4); // triggers overview
            prop.put("page", 4);
            
            if (sb.peers.mySeed() != null) {
	            prop.put("table_my-hash", sb.peers.mySeed().hash );
	            prop.put("table_my-ip", sb.peers.mySeed().getIP() );
	            prop.put("table_my-port", sb.peers.mySeed().getPort() );
            }

            if (post.containsKey("addPeer")) {

                // AUTHENTICATE
                if (!requestHeader.containsKey(RequestHeader.AUTHORIZATION)) {
                    prop.putHTML("AUTHENTICATE","log-in");
                    return prop;
                }

                final ConcurrentMap<String, String> map = new ConcurrentHashMap<String, String>();
                map.put(yacySeed.IP, post.get("peerIP"));
                map.put(yacySeed.PORT, post.get("peerPort"));
                yacySeed peer = new yacySeed(post.get("peerHash"), map);

                sb.updateMySeed();
                final int added = yacyClient.hello(sb.peers.mySeed(), sb.peers.peerActions, peer.getPublicAddress(), peer.hash);

                if (added <= 0) {
                    prop.put("table_comment",1);
                    prop.putHTML("table_comment_status","publish: disconnected peer '" + peer.getName() + "/" + post.get("peerHash") + "' from " + peer.getPublicAddress());
                } else {
                    peer = sb.peers.getConnected(peer.hash);
                    if (peer == null) {
                        prop.put("table_comment",1);
                        prop.putHTML("table_comment_status","publish: disconnected peer 'UNKNOWN/" + post.get("peerHash") + "' from UNKNOWN");
                    } else {
                        prop.put("table_comment",2);
                        prop.putHTML("table_comment_status","publish: handshaked " + peer.get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR) + " peer '" + peer.getName() + "' at " + peer.getPublicAddress());
                        prop.putHTML("table_comment_details",peer.toString());
                    }
                }

                prop.putHTML("table_peerHash",post.get("peerHash"));
                prop.putHTML("table_peerIP",post.get("peerIP"));
                prop.putHTML("table_peerPort",post.get("peerPort"));                
            } else {
                prop.put("table_peerHash","");
                prop.put("table_peerIP","");
                prop.put("table_peerPort","");

                prop.put("table_comment",0);
            }
        } else {
            // generate table
            final int page = (post == null ? 1 : post.getInt("page", 1));
            final int maxCount = (post == null ? 300 : post.getInt("maxCount", 300));
            int conCount = 0;            
            if (sb.peers == null) {
                prop.put("table", 0);//no remote senior/principal proxies known"
            } else {
                int size = 0;
                switch (page) {
                    case 1 : size = sb.peers.sizeConnected(); break;
                    case 2 : size = sb.peers.sizeDisconnected(); break;
                    case 3 : size = sb.peers.sizePotential(); break;
                    default: break;
                }
                if (size == 0) {
                    prop.put("table", 0);//no remote senior/principal proxies known"
                } else {
                    // add temporary the own seed to the database
                    if (iAmActive) {
                        sb.updateMySeed();
                        sb.peers.addConnected(sb.peers.mySeed());
                    }

                    // find updated Information using YaCyNews
                    final HashSet<String> updatedProfile = new HashSet<String>();
                    final HashMap<String, Map<String, String>> updatedWiki = new HashMap<String, Map<String, String>>();
                    final HashMap<String, Map<String, String>> updatedBlog = new HashMap<String, Map<String, String>>();
                    final HashMap<String, String> isCrawling = new HashMap<String, String>();
                    yacyNewsDB.Record record;
                    final Iterator<yacyNewsDB.Record> recordIterator = sb.peers.newsPool.recordIterator(yacyNewsPool.INCOMING_DB, true);
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
                        case 1 : e = sb.peers.seedsSortedConnected(order, (sort == null ? yacySeed.LCOUNT : sort)); break;
                        case 2 : e = sb.peers.seedsSortedDisconnected(order, (sort == null ? yacySeed.LASTSEEN : sort)); break;
                        case 3 : e = sb.peers.seedsSortedPotential(order, (sort == null ? yacySeed.LASTSEEN : sort)); break;
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
                        } catch (final PatternSyntaxException pse){
                            prop.put("regexerror", 1);
                            prop.putHTML("regexerror_wrongregex", pse.getPattern());
                        }
                    }
                    if(e != null) {
                    while (e.hasNext() && conCount < maxCount) {
                        seed = e.next();
                        assert seed != null;
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
                            if (sb.peers != null && sb.peers.mySeed() != null && seed.hash != null && seed.hash.equals(sb.peers.mySeed().hash)) {
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
                            if (seed.hash != null && seed.hash.equals(sb.peers.mySeed().hash)) {
                                userAgent = ClientIdentification.getUserAgent();
                                location = ClientIdentification.generateLocation();
                            } else {
                               userAgent = sb.peers.peerActions.getUserAgent(seed.getIP());
                               location = ClientIdentification.parseLocationInUserAgent(userAgent);
                            }
                            prop.put(STR_TABLE_LIST + conCount + "_location", location);
                            if (complete) {
                                prop.put(STR_TABLE_LIST + conCount + "_complete", 1);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_ip", seed.getIP() );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_port", seed.get(yacySeed.PORT, "-") );
                                prop.put(STR_TABLE_LIST + conCount + "_complete_hash", seed.hash);
                                prop.put(STR_TABLE_LIST + conCount + "_complete_age", seed.getAge());
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_seeds", seed.getLong(yacySeed.SCOUNT, 0L));
                                prop.putNum(STR_TABLE_LIST + conCount + "_complete_connects", seed.getFloat(yacySeed.CCOUNT, 0F));
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
                            prop.putHTML(STR_TABLE_LIST + conCount + "_type_url", seed.get(yacySeed.SEEDLISTURL, "http://nowhere/"));

                            final long lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
                            if (page == 1 && lastseen > 720) {
                            	continue;
                            }
                            if (page == 2 || (page == 1 && lastseen > 360)) { // Passive Peers should be passive, also Peers without contact greater than 6 hours
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
                            }
                            if (seed.getFlagAcceptRemoteIndex()) {
                                prop.put(STR_TABLE_LIST + conCount + "_dhtreceive_peertags", "");
                            } else {
                                final String peertags = MapTools.set2string(seed.getPeerTags(), ",", false);
                                prop.putHTML(STR_TABLE_LIST + conCount + "_dhtreceive_peertags", ((peertags == null) || (peertags.length() == 0)) ? "no tags given" : ("tags = " + peertags));
                            }
                            prop.putHTML(STR_TABLE_LIST + conCount + "_version", yacyVersion.combined2prettyVersion(seed.get(yacySeed.VERSION, "0.1"), shortname));
                            prop.putNum(STR_TABLE_LIST + conCount + "_lastSeen", /*seed.getLastSeenString() + " " +*/ lastseen);
                            prop.put(STR_TABLE_LIST + conCount + "_utc", seed.get(yacySeed.UTC, "-"));
                            prop.putHTML(STR_TABLE_LIST + conCount + "_uptime", yacyPeerActions.formatInterval(60000 * seed.getLong(yacySeed.UPTIME, 0)));
                            prop.putNum(STR_TABLE_LIST + conCount + "_LCount", seed.getLinkCount());
                            prop.putNum(STR_TABLE_LIST + conCount + "_ICount", seed.getWordCount());
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
                    if (iAmActive) { sb.peers.removeMySeed(); }
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

}