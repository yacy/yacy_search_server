// Network.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 16.02.2005
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

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class Network {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        boolean overview = (post == null) || (((String) post.get("page", "0")).equals("0"));
        
        String mySeedType = yacyCore.seedDB.mySeed.get("PeerType", "virgin");
        boolean iAmActive = (mySeedType.equals("senior")) || (mySeedType.equals("principal"));
        
        if (overview) {
            long accActLinks = yacyCore.seedDB.countActiveURL();
            long accActWords = yacyCore.seedDB.countActiveRWI();
            long accPassLinks = yacyCore.seedDB.countPassiveURL();
            long accPassWords = yacyCore.seedDB.countPassiveRWI();
            long accPotLinks = yacyCore.seedDB.countPotentialURL();
            long accPotWords = yacyCore.seedDB.countPotentialRWI();
            
            int conCount = yacyCore.seedDB.sizeConnected();
            int disconCount = yacyCore.seedDB.sizeDisconnected();
            int potCount = yacyCore.seedDB.sizePotential();
            
            boolean complete = ((post == null) ? false : post.get("links", "false").equals("true"));
            
            // create own peer info
            yacySeed seed = yacyCore.seedDB.mySeed;
            if (yacyCore.seedDB.mySeed != null){ //our Peer
                long links, words;
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
                prop.put("table_my-version", seed.get("Version", "-"));
                prop.put("table_my-uptime", seed.get("Uptime", "-"));
                prop.put("table_my-links", groupDigits(links));
                prop.put("table_my-words", groupDigits(words));
                prop.put("table_my-acceptcrawl", "" + (seed.getFlagAcceptRemoteCrawl() ? 1 : 0) );
                prop.put("table_my-acceptindex", "" + (seed.getFlagAcceptRemoteIndex() ? 1 : 0) );
                prop.put("table_my-sI", seed.get("sI", "-"));
                prop.put("table_my-sU", seed.get("sU", "-"));
                prop.put("table_my-rI", seed.get("rI", "-"));
                prop.put("table_my-rU", seed.get("rU", "-"));
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
            
            String comment = "";
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
        } else {
            // generate table
            int page = Integer.parseInt(post.get("page", "1"));
            int conCount = 0;
            int maxCount = 100;
            if (yacyCore.seedDB == null) {
                prop.put("table", 0);//no remote senior/principal proxies known"
            } else {
                int size = 0;
                switch (page) {
                    case 1 : size = yacyCore.seedDB.sizeConnected(); break;
                    case 2 : size = yacyCore.seedDB.sizeDisconnected(); break;
                    case 3 : size = yacyCore.seedDB.sizePotential(); break;
                }
                if (size == 0) {
                    prop.put("table", 0);//no remote senior/principal proxies known"
                } else {
                    // add temporary the own seed to the database
                    if (iAmActive) {
                        yacyCore.peerActions.updateMySeed();
                        yacyCore.seedDB.addConnected(yacyCore.seedDB.mySeed);
                    }
                    boolean dark = true;
                    yacySeed seed;
                    boolean complete = post.containsKey("ip");
                    Enumeration e = null;
                    switch (page) {
                        case 1 :
                            e = yacyCore.seedDB.seedsSortedConnected(post.get("order", "down").equals("up"), post.get("sort", "LCount"));
                            prop.put("table_total", yacyCore.seedDB.sizeConnected());
                            break;
                        case 2 :
                            e = yacyCore.seedDB.seedsSortedDisconnected(post.get("order", "up").equals("up"), post.get("sort", "LastSeen"));
                            prop.put("table_total", yacyCore.seedDB.sizeDisconnected());
                            break;
                        case 3 :
                            e = yacyCore.seedDB.seedsSortedPotential(post.get("order", "up").equals("up"), post.get("sort", "LastSeen"));
                            prop.put("table_total", yacyCore.seedDB.sizePotential());
                            break;
                    }
                    while ((e.hasMoreElements()) && (conCount < maxCount)) {
                        seed = (yacySeed) e.nextElement();
                        if (seed != null) {
                            if (conCount >= maxCount) break;
                            if (seed.hash.equals(yacyCore.seedDB.mySeed.hash)) {
                                prop.put("table_list_"+conCount+"_dark", 2);
                            } else {
                                prop.put("table_list_"+conCount+"_dark", ((dark) ? 1 : 0) ); dark=!dark;
                            }                            
                            long links, words;
                            try {
                                links = Long.parseLong(seed.get("LCount", "0"));
                                words = Long.parseLong(seed.get("ICount", "0"));
                            } catch (Exception exc) {links = 0; words = 0;}
                            prop.put("table_list_"+conCount+"_complete", ((complete)? 1 : 0) );
                            prop.put("table_list_"+conCount+"_hash", seed.hash);
                            String shortname = seed.get("Name", "deadlink");
                            if (shortname.length() > 20) shortname = shortname.substring(0, 20) + "...";
                            prop.put("table_list_"+conCount+"_shortname", shortname);
                            prop.put("table_list_"+conCount+"_fullname", seed.get("Name", "deadlink"));
                            if (complete) {
                                prop.put("table_list_"+conCount+"_complete", 1);
                                prop.put("table_list_"+conCount+"_complete_ip", seed.get("IP", "-") );
                                prop.put("table_list_"+conCount+"_complete_port", seed.get("Port", "-") );
                                prop.put("table_list_"+conCount+"_complete_hash", seed.hash);
                            }else{
                                prop.put("table_list_"+conCount+"_complete", 0);
                            }
                            if (seed.isJunior()) {
                                prop.put("table_list_"+conCount+"_type", 0);
                            } else if(seed.isSenior()){
                                prop.put("table_list_"+conCount+"_type", 1);
                            } else if(seed.isPrincipal()) {
                                prop.put("table_list_"+conCount+"_type", 2);
                                prop.put("table_list_"+conCount+"_type_url", seed.get("seedURL", "http://nowhere/") );
                            }
                            prop.put("table_list_"+conCount+"_version", seed.get("Version", "-"));
                            prop.put("table_list_"+conCount+"_contact", (seed.getFlagDirectConnect() ? 1 : 0) );
                            prop.put("table_list_"+conCount+"_lastSeen", lastSeen(seed.get("LastSeen", "-")) );
                            prop.put("table_list_"+conCount+"_uptime", seed.get("Uptime", "-") );
                            prop.put("table_list_"+conCount+"_links", groupDigits(links));
                            prop.put("table_list_"+conCount+"_words", groupDigits(words));
                            prop.put("table_list_"+conCount+"_acceptcrawl", (seed.getFlagAcceptRemoteCrawl() ? 1 : 0) );
                            prop.put("table_list_"+conCount+"_acceptindex", (seed.getFlagAcceptRemoteIndex() ? 1 : 0) );
                            prop.put("table_list_"+conCount+"_sI", seed.get("sI", "-"));
                            prop.put("table_list_"+conCount+"_sU", seed.get("sU", "-"));
                            prop.put("table_list_"+conCount+"_rI", seed.get("rI", "-"));
                            prop.put("table_list_"+conCount+"_rU", seed.get("rU", "-"));
                            prop.put("table_list_"+conCount+"_seeds", seed.get("SCount", "-"));
                            prop.put("table_list_"+conCount+"_connects", seed.get("CCount", "-"));
                            conCount++;
                        }//seed != null
                    }//while
                    if (iAmActive) yacyCore.seedDB.removeMySeed();
                    prop.put("table_list", conCount);
                    prop.put("table", 1);
                    prop.put("table_num", conCount);
                    //prop.put("table_total", (maxCount > conCount) ? conCount : maxCount);
                    prop.put("table_complete", ((complete)? 1 : 0) );
                }
            }
            prop.put("page", page);
            prop.put("table_page", page);
            switch (page) {
                case 1 : prop.put("table_peertype", "senior/principal"); break;
                case 2 : prop.put("table_peertype", "senior/principal"); break;
                case 3 : prop.put("table_peertype", "junior"); break;
            }
        }
        // return rewrite properties
        return prop;
    }
    
    private static String lastSeen(String date) {
        long l = 0;
        if (date.length() == 0)
            l = 999;
        else
            try {
                l = (yacyCore.universalTime() - yacyCore.shortFormatter.parse(date).getTime()) / 1000 / 60;
            } catch (java.text.ParseException e) {
                l = 999;
            }
        if (l == 999) return "-"; else return "" + l;
    }
    
    private static String groupDigits(long Number) {
        String s = "" + Number;
        String t = "";
        for (int i = 0; i < s.length(); i++)  t = s.charAt(s.length() - i - 1) + (((i % 3) == 0) ? "," : "") + t;
        return t.substring(0, t.length() - 1);
    }
}
