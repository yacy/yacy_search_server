//sharedBlacklist_p.java
//-----------------------
//part of the AnomicHTTPProxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

//This File is contributed by Alexander Schier

//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../Classes Blacklist_p.java
//if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.ListManager;
import net.yacy.data.list.ListAccumulator;
import net.yacy.data.list.XMLBlacklistImporter;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.Seed;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import org.xml.sax.SAXException;


public class sharedBlacklist_p {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_ENTRIES_ADDED = 1;
    public static final int STATUS_FILE_ERROR = 2;
    public static final int STATUS_PEER_UNKNOWN = 3;
    public static final int STATUS_URL_PROBLEM = 4;
    public static final int STATUS_WRONG_INVOCATION = 5;
    public static final int STATUS_PARSE_ERROR = 6;

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // get the name of the destination blacklist
        String selectedBlacklistName = "";
        if( post != null && post.containsKey("currentBlacklist") ){
            selectedBlacklistName = post.get("currentBlacklist");
        }else{
            selectedBlacklistName = "shared.black";
        }

        prop.putHTML("currentBlacklist", selectedBlacklistName);
        prop.putHTML("page_target", selectedBlacklistName);

        if (post != null) {

            // loading all blacklist files located in the directory
            final List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);

            // List BlackLists
            int blacklistCount = 0;

            if (dirlist != null) {
                for (final String element : dirlist) {
                    prop.putXML("page_blackLists_" + blacklistCount + "_name", element);
                    blacklistCount++;
                }
            }
            prop.put("page_blackLists", blacklistCount);

            Iterator<String> otherBlacklist = null;
            ListAccumulator otherBlacklists = null;
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));

            if (post.containsKey("hash")) {
                /* ======================================================
                 * Import blacklist from other peer
                 * ====================================================== */

                // get the source peer hash
                final String hash = post.get("hash");

                // generate the download URL
                String downloadURLOld = null;
                if( sb.peers != null ){ //no nullpointer error..
                    final Seed seed = sb.peers.getConnected(hash);
                    if (seed != null) {
                        final String IP = seed.getIP();
                        final String Port = seed.get(Seed.PORT, "8090");
                        final String peerName = seed.get(Seed.NAME, "<" + IP + ":" + Port + ">");
                        prop.putHTML("page_source", peerName);
                        downloadURLOld = "http://" + IP + ":" + Port + "/yacy/list.html?col=black";
                    } else {
                        prop.put("status", STATUS_PEER_UNKNOWN);//YaCy-Peer not found
                        prop.putHTML("status_name", hash);
                        prop.put("page", "1");
                    }
                } else {
                    prop.put("status", STATUS_PEER_UNKNOWN);//YaCy-Peer not found
                    prop.putHTML("status_name", hash);
                    prop.put("page", "1");
                }

                if (downloadURLOld != null) {
                    // download the blacklist
                    try {
                        // get List
                        final DigestURL u = new DigestURL(downloadURLOld);

                        otherBlacklist = FileUtils.strings(u.get(agent, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "")));
                    } catch (final Exception e) {
                        prop.put("status", STATUS_PEER_UNKNOWN);
                        prop.putHTML("status_name", hash);
                        prop.put("page", "1");
                    }
                }
            } else if (post.containsKey("url")) {
                /* ======================================================
                 * Download the blacklist from URL
                 * ====================================================== */

                final String downloadURL = post.get("url");
                prop.putHTML("page_source", downloadURL);

                try {
                    final DigestURL u = new DigestURL(downloadURL);
                    otherBlacklist = FileUtils.strings(u.get(agent, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "")));
                } catch (final Exception e) {
                    prop.put("status", STATUS_URL_PROBLEM);
                    prop.putHTML("status_address",downloadURL);
                    prop.put("page", "1");
                }
            } else if (post.containsKey("file")) {

                if (post.containsKey("type") && post.get("type").equalsIgnoreCase("xml")) {
                    /* ======================================================
                     * Import the blacklist from XML file
                     * ====================================================== */
                    final String sourceFileName = post.get("file");
                    prop.putHTML("page_source", sourceFileName);

                    final String fileString = post.get("file$file");

                    if (fileString != null) {
                        try {
                            otherBlacklists = new XMLBlacklistImporter().parse(new StringReader(fileString));
                        } catch (final IOException ex) {
                            prop.put("status", STATUS_FILE_ERROR);
                        } catch (final SAXException ex) {
                            prop.put("status", STATUS_PARSE_ERROR);
                        }
                    }
                } else {
                    /* ======================================================
                     * Import the blacklist from text file
                     * ====================================================== */
                    final String sourceFileName = post.get("file");
                    prop.putHTML("page_source", sourceFileName);

                    final String fileString = post.get("file$file");

                    if (fileString != null) {
                        otherBlacklist = FileUtils.strings(UTF8.getBytes(fileString));
                    }
                }
            } else if (post.containsKey("add")) {
                /* ======================================================
                 * Add loaded items into blacklist file
                 * ====================================================== */

                prop.put("page", "1"); //result page
                prop.put("status", STATUS_ENTRIES_ADDED); //list of added Entries

                try {
                    // loop through the received entry list
                    final int num = post.getInt("num", 0);
                    for(int i = 0; i < num; i++){
                        if( post.containsKey("item" + i) ){
                            String newItem = post.get("item" + i);

                            //This should not be needed...
                            if ( newItem.startsWith("http://") ){
                                newItem = newItem.substring(7);
                            }

                            // separate the newItem into host and path
                            int pos = newItem.indexOf('/',0);
                            if (pos < 0) {
                                // add default empty path pattern
                                pos = newItem.length();
                                newItem = newItem + "/.*";
                            }

                            if (Switchboard.urlBlacklist != null) {
                                for (final BlacklistType supportedBlacklistType : BlacklistType.values()) {
                                    if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists",selectedBlacklistName)) {
                                        Switchboard.urlBlacklist.add(supportedBlacklistType,selectedBlacklistName,newItem.substring(0, pos), newItem.substring(pos + 1));
                                    }
                                }
                                SearchEventCache.cleanupEvents(true);
                            }
                        }
                    }
                } catch (final Exception e) {
                    prop.put("status", "1");
                    prop.putHTML("status_error", e.getLocalizedMessage());
                }

                /* unable to use prop.putHTML() or prop.putXML() here because they
                 * turn the ampersand into &amp; which renders the parameters
                 * useless (at least when using Opera 9.53, haven't tested other browsers)
                 */
                prop.put(serverObjects.ACTION_LOCATION,"Blacklist_p.html?selectedListName=" + CharacterCoding.unicode2html(selectedBlacklistName, true) + "&selectList=select");
                return prop;
            }

            // generate the html list
            if (otherBlacklist != null) {
                // loading the current blacklist content
                final Set<String> Blacklist = new HashSet<String>(FileUtils.getListArray(new File(ListManager.listsPath, selectedBlacklistName)));

                int count = 0;
                while (otherBlacklist.hasNext()) {
                    final String tmp = otherBlacklist.next();
                    if( !Blacklist.contains(tmp) && (!tmp.equals("")) ){
                        //newBlacklist.add(tmp);
                        prop.put("page_urllist_" + count + "_dark", count % 2 == 0 ? "0" : "1");
                        prop.putHTML("page_urllist_" + count + "_url", tmp);
                        prop.put("page_urllist_" + count + "_count", count);
                        count++;
                    }
                }
                prop.put("page_urllist", (count));
                prop.put("num", count);
                prop.put("page", "0");

            } else if (otherBlacklists != null) {
                final List<List<String>> entries = otherBlacklists.getEntryLists();
                //List<Map<String,String>> properties = otherBlacklists.getPropertyMaps();
                int count = 0;

                for(final List<String> list : entries) {

                    // sort the loaded blacklist
                    final String[] sortedlist = list.toArray(new String[list.size()]);
                    Arrays.sort(sortedlist);

                    for (final String element : sortedlist) {
                        final String tmp = element;
                        if(!tmp.equals("")){
                            //newBlacklist.add(tmp);
                            prop.put("page_urllist_" + count + "_dark", count % 2 == 0 ? "0" : "1");
                            prop.putHTML("page_urllist_" + count + "_url", tmp);
                            prop.put("page_urllist_" + count + "_count", count);
                            count++;
                        }
                    }

                }

                prop.put("page_urllist", (count));
                prop.put("num", count);
                prop.put("page", "0");

            }

        } else {
            prop.put("page", "1");
            prop.put("status", "5");//Wrong Invocation
        }
        return prop;
    }
}
