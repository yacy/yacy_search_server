//sharedBlacklist_p.java 
//-----------------------
//part of the AnomicHTTPProxy
//(C) by Michael Peter Christen; mc@anomic.de
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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import de.anomic.crawler.HTTPLoader;
import de.anomic.data.listManager;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.index.indexAbstractReferenceBlacklist;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class sharedBlacklist_p {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_ENTRIES_ADDED = 1;
    public static final int STATUS_FILE_ERROR = 2;
    public static final int STATUS_PEER_UNKNOWN = 3;
    public static final int STATUS_URL_PROBLEM = 4;
    public static final int STATUS_WRONG_INVOCATION = 5;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();

        // getting the name of the destination blacklist
        String selectedBlacklistName = "";
        if( post != null && post.containsKey("currentBlacklist") ){
            selectedBlacklistName = post.get("currentBlacklist");
        }else{
            selectedBlacklistName = "shared.black";
        }
        
        prop.put("currentBlacklist", selectedBlacklistName);
        prop.put("page_target", selectedBlacklistName);
        
        if (post != null) {
            ArrayList<String> otherBlacklist = null;
            
            if (post.containsKey("hash")) {
                /* ======================================================
                 * Import blacklist from other peer 
                 * ====================================================== */
                
                // getting the source peer hash
                String Hash = post.get("hash");
                
                // generate the download URL
                String downloadURL = null;
                if( sb.webIndex.seedDB != null ){ //no nullpointer error..
                    yacySeed seed = sb.webIndex.seedDB.getConnected(Hash); 
                    if (seed != null) {
                        String IP = seed.getIP(); 
                        String Port = seed.get(yacySeed.PORT, "8080");
                        String peerName = seed.get(yacySeed.NAME, "<" + IP + ":" + Port + ">");
                        prop.putHTML("page_source", peerName);

                        downloadURL = "http://" + IP + ":" + Port + "/yacy/list.html?col=black";
                    } else {
                        prop.put("status", STATUS_PEER_UNKNOWN);//YaCy-Peer not found
                        prop.put("page", "1");
                    }
                } else {
                    prop.put("status", STATUS_PEER_UNKNOWN);//YaCy-Peer not found
                    prop.put("page", "1");
                }
                
                if (downloadURL != null) {
                    // download the blacklist
                    try {
                        httpHeader reqHeader = new httpHeader();
                        reqHeader.put(httpHeader.PRAGMA,"no-cache");
                        reqHeader.put(httpHeader.CACHE_CONTROL,"no-cache");
                        reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
                        
                        // get List
                        yacyURL u = new yacyURL(downloadURL, null);
                        otherBlacklist = nxTools.strings(HttpClient.wget(u.toString(), reqHeader, 1000), "UTF-8"); 
                    } catch (Exception e) {
                        prop.put("status", STATUS_PEER_UNKNOWN);
                        prop.put("page", "1");
                    }
                }
            } else if (post.containsKey("url")) {
                /* ======================================================
                 * Download the blacklist from URL
                 * ====================================================== */
                
                String downloadURL = post.get("url");
                prop.putHTML("page_source", downloadURL);

                try {
                    yacyURL u = new yacyURL(downloadURL, null);
                    httpHeader reqHeader = new httpHeader();
                    reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
                    otherBlacklist = nxTools.strings(HttpClient.wget(u.toString(), reqHeader, 10000), "UTF-8"); //get List
                } catch (Exception e) {
                    prop.put("status", STATUS_URL_PROBLEM);
                    prop.putHTML("status_address",downloadURL);
                    prop.put("page", "1");
                }
            } else if (post.containsKey("file")) {
                /* ======================================================
                 * Import the blacklist from file
                 * ====================================================== */
                String sourceFileName = post.get("file");
                prop.put("page_source", sourceFileName);
                
                File sourceFile = new File(listManager.listsPath, sourceFileName);
                if (!sourceFile.exists() || !sourceFile.canRead() || !sourceFile.isFile()) {
                    prop.put("status", STATUS_FILE_ERROR);
                    prop.put("page", "1");
                } else {
                    otherBlacklist = listManager.getListArray(sourceFile);
                }
                
            } else if (post.containsKey("add")) {
                /* ======================================================
                 * Add loaded items into blacklist file
                 * ====================================================== */
                
                prop.put("page", "1"); //result page
                prop.put("status", STATUS_ENTRIES_ADDED); //list of added Entries
                
                int count = 0;//couter of added entries
                PrintWriter pw = null;
                try {
                    // open the blacklist file
                    pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, selectedBlacklistName), true));
                    
                    // loop through the received entry list
                    int num = Integer.parseInt( post.get("num") );
                    for(int i=0;i < num; i++){ 
                        if( post.containsKey("item" + i) ){
                            String newItem = post.get("item" + i);
                            
                            //This should not be needed...
                            if ( newItem.startsWith("http://") ){
                                newItem = newItem.substring(7);
                            }
                            
                            // separate the newItem into host and path
                            int pos = newItem.indexOf("/");
                            if (pos < 0) {
                                // add default empty path pattern
                                pos = newItem.length();
                                newItem = newItem + "/.*";
                            }
                            
                            // append the item to the file
                            pw.println(newItem);

                            count++;
                            if (plasmaSwitchboard.urlBlacklist != null) {
                                String supportedBlacklistTypesStr = indexAbstractReferenceBlacklist.BLACKLIST_TYPES_STRING;
                                String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(",");  

                                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",selectedBlacklistName)) {
                                        plasmaSwitchboard.urlBlacklist.add(supportedBlacklistTypes[blTypes],newItem.substring(0, pos), newItem.substring(pos + 1));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    prop.put("status", "1");
                    prop.putHTML("status_error", e.getLocalizedMessage());
                } finally {
                    if (pw != null) try { pw.close(); } catch (Exception e){ /* */}
                }
                
                prop.put("LOCATION","Blacklist_p.html?selectedListName=" + selectedBlacklistName + "&selectList=");
                return prop;
            }
            
            // generate the html list
            if (otherBlacklist != null) {
                // loading the current blacklist content
                HashSet<String> Blacklist = new HashSet<String>(listManager.getListArray(new File(listManager.listsPath, selectedBlacklistName)));
                
                // sort the loaded blacklist
                String[] sortedlist = otherBlacklist.toArray(new String[otherBlacklist.size()]);
                Arrays.sort(sortedlist);
                
                int count = 0;
                for(int i = 0; i < sortedlist.length; i++){
                    String tmp = sortedlist[i];
                    if( !Blacklist.contains(tmp) && (!tmp.equals("")) ){
                        //newBlacklist.add(tmp);
                        prop.put("page_urllist_" + count + "_dark", count % 2 == 0 ? "0" : "1");
                        prop.put("page_urllist_" + count + "_url", tmp);
                        prop.put("page_urllist_" + count + "_count", count);
                        count++;
                    }
                }
                prop.put("page_urllist", (count));
                prop.put("num", count);
                prop.put("page", "0");
            }
                
                
        } else {
            prop.put("page", "1");
            prop.put("status", "5");//Wrong Invokation
        }
        return prop;
    }
}
