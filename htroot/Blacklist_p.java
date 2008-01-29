// Blacklist_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
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
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.urlPattern.abstractURLPattern;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacyURL;

public class Blacklist_p {
	private final static String DISABLED         = "disabled_";
    private final static String BLACKLIST        = "blackLists_";
    private final static String BLACKLIST_SHARED = "BlackLists.Shared";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {

        // initialize the list manager
        listManager.switchboard = (plasmaSwitchboard) env;
        listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        
        // getting the list of supported blacklist types
        String supportedBlacklistTypesStr = abstractURLPattern.BLACKLIST_TYPES_STRING;
        String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(",");        
        
        String blacklistToUse = null;
        serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", plasmaSwitchboard.urlBlacklist.getEngineInfo());
        
        // do all post operations
        if (post != null) {
            
            if(post.containsKey("testList")) {
            	prop.put("testlist", "1");
            	String urlstring = post.get("testurl", "");
            	if(!urlstring.startsWith("http://")) urlstring = "http://"+urlstring;
                yacyURL testurl = null;
				try {
					testurl = new yacyURL(urlstring, null);
				} catch (MalformedURLException e) { }
				if(testurl != null) {
					prop.putHTML("testlist_url",testurl.toString());
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER, testurl))
						prop.put("testlist_listedincrawler", "1");
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_DHT, testurl))
						prop.put("testlist_listedindht", "1");
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_NEWS, testurl))
						prop.put("testlist_listedinnews", "1");
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_PROXY, testurl))
						prop.put("testlist_listedinproxy", "1");
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_SEARCH, testurl))
						prop.put("testlist_listedinsearch", "1");
					if(plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_SURFTIPS, testurl))
						prop.put("testlist_listedinsurftips", "1");
				}
				else prop.put("testlist_url","not valid");
            }
        	if (post.containsKey("selectList")) {
                blacklistToUse = (String)post.get("selectedListName"); 
                if (blacklistToUse != null && blacklistToUse.length() == 0) blacklistToUse = null;
            }
            if (post.containsKey("createNewList")) {
                /* ===========================================================
                 * Creation of a new blacklist
                 * =========================================================== */
                
                blacklistToUse = (String)post.get("newListName");
                if (blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION","");
                    return prop;
                }   
                
                if (!blacklistToUse.endsWith(".black")) blacklistToUse += ".black";

                try {
                    final File newFile = new File(listManager.listsPath, blacklistToUse);
                    newFile.createNewFile();
                    
                    // share the newly created blacklist
                    listManager.updateListSet(BLACKLIST_SHARED, blacklistToUse);
                    
                    // activate it for all known blacklist types
                    for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                        listManager.updateListSet(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse);
                    }                                 
                } catch (IOException e) {/* */}
                
            } else if (post.containsKey("deleteList")) {
                /* ===========================================================
                 * Delete a blacklist
                 * =========================================================== */                
                
                blacklistToUse = (String)post.get("selectedListName");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION","");
                    return prop;
                }                   
                
                File BlackListFile = new File(listManager.listsPath, blacklistToUse);
                BlackListFile.delete();

                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    listManager.removeFromListSet(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse);
                }                
                
                // remove it from the shared list
                listManager.removeFromListSet(BLACKLIST_SHARED, blacklistToUse);
                blacklistToUse = null;
                
                // reload Blacklists
                listManager.reloadBlacklists();

            } else if (post.containsKey("activateList")) {

                /* ===========================================================
                 * Activate/Deactivate a blacklist
                 * =========================================================== */                   
                
                blacklistToUse = (String)post.get("selectedListName");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }                   
                
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {                    
                    if (post.containsKey("activateList4" + supportedBlacklistTypes[blTypes])) {
                        listManager.updateListSet(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse);                        
                    } else {
                        listManager.removeFromListSet(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse);                        
                    }                    
                }                     

                listManager.reloadBlacklists();                
                
            } else if (post.containsKey("shareList")) {

                /* ===========================================================
                 * Share a blacklist
                 * =========================================================== */                   
                
                blacklistToUse = (String)post.get("selectedListName");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }                   
                
                if (listManager.listSetContains(BLACKLIST_SHARED, blacklistToUse)) { 
                    // Remove from shared BlackLists
                    listManager.removeFromListSet(BLACKLIST_SHARED, blacklistToUse);
                } else { // inactive list -> enable
                    listManager.updateListSet(BLACKLIST_SHARED, blacklistToUse);
                }
            } else if (post.containsKey("deleteBlacklistEntry")) {
                
                /* ===========================================================
                 * Delete a blacklist entry
                 * =========================================================== */
                
                // get the current selected blacklist name
                blacklistToUse = (String)post.get("currentBlacklist");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }
                
                // get the entry that should be deleted
                String oldEntry = (String)post.get("selectedEntry");
                if (oldEntry == null || oldEntry.trim().length() == 0) {
                    prop.put("LOCATION", header.get("PATH") + "?selectList=&selectedListName=" + blacklistToUse);
                    return prop;
                }
                
                // load blacklist data from file
                ArrayList<String> list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));
                
                // delete the old entry from file
                if (list != null) {
                    for (int i=0; i < list.size(); i++) {
                        if (((String)list.get(i)).equals(oldEntry)) {
                            list.remove(i);
                            break;
                        }
                    }
                    listManager.writeList(new File(listManager.listsPath, blacklistToUse), (String[])list.toArray(new String[list.size()]));
                }
                
                // remove the entry from the running blacklist engine
                int pos = oldEntry.indexOf("/");
                if (pos < 0) {
                    // add default empty path pattern
                    pos = oldEntry.length();
                    oldEntry = oldEntry + "/.*";
                }
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse)) {
                        plasmaSwitchboard.urlBlacklist.remove(supportedBlacklistTypes[blTypes],oldEntry.substring(0, pos), oldEntry.substring(pos + 1));
                    }
                }
                
            } else if (post.containsKey("addBlacklistEntry")) {
                
                /* ===========================================================
                 * Add a new blacklist entry
                 * =========================================================== */
                
                blacklistToUse = (String)post.get("currentBlacklist");   
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }
                
                String newEntry = (String)post.get("newEntry");
                if (newEntry.trim().length() == 0) {
                    prop.put("LOCATION", header.get("PATH") + "?selectList=&selectedListName=" + blacklistToUse);
                    return prop;
                }
                
                // TODO: ignore empty entries
                
                if (newEntry.startsWith("http://") ){
                    newEntry = newEntry.substring(7);
                }

                int pos = newEntry.indexOf("/");
                if (pos < 0) {
                    // add default empty path pattern
                    pos = newEntry.length();
                    newEntry = newEntry + "/.*";
                }

                // append the line to the file
                PrintWriter pw = null;
                try {
                    pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklistToUse), true));
                    pw.println(newEntry);
                    pw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (pw != null) try { pw.close(); } catch (Exception e){ /* */}
                }

                // add to blacklist
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse)) {
                        plasmaSwitchboard.urlBlacklist.add(supportedBlacklistTypes[blTypes],newEntry.substring(0, pos), newEntry.substring(pos + 1));
                    }
                }
            } else if (post.containsKey("moveBlacklistEntry")) {

                /* ===========================================================
                 * First add entry to blacklist item moves to
                 * =========================================================== */

                blacklistToUse = (String)post.get("targetBlacklist");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }

                String entry = (String)post.get("selectedEntry");
                if (entry == null || entry.trim().length() == 0) {
                    prop.put("LOCATION",header.get("PATH") + "?selectList=&selectedListName=" + blacklistToUse);
                    return prop;
                }

                // TODO: ignore empty entries

                if (entry.startsWith("http://") ){
                    entry = entry.substring(7);
                }

                int pos = entry.indexOf("/");
                if (pos < 0) {
                    // add default empty path pattern
                    pos = entry.length();
                    entry = entry + "/.*";
                }

                // append the line to the file
                PrintWriter pw = null;
                try {
                    pw = new PrintWriter(new FileWriter(new File(listManager.listsPath, blacklistToUse), true));
                    pw.println(entry);
                    pw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (pw != null) try { pw.close(); } catch (Exception e){ /* */}
                }

                // add to blacklist
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse)) {
                        plasmaSwitchboard.urlBlacklist.add(supportedBlacklistTypes[blTypes],entry.substring(0, pos), entry.substring(pos + 1));
                    }
                }

                /* ===========================================================
                 * Then delete item from blacklist it is moved away from
                 * =========================================================== */

                // get the current selected blacklist name
                blacklistToUse = (String)post.get("currentBlacklist");
                if (blacklistToUse == null || blacklistToUse.trim().length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }

                // load blacklist data from file
                ArrayList<String> list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));

                // delete the old entry from file
                if (list != null) {
                    for (int i=0; i < list.size(); i++) {
                        if (((String)list.get(i)).equals(entry)) {
                            list.remove(i);
                            break;
                        }
                    }
                    listManager.writeList(new File(listManager.listsPath, blacklistToUse), (String[])list.toArray(new String[list.size()]));
                }

                // remove the entry from the running blacklist engine
                pos = entry.indexOf("/");
                if (pos < 0) {
                    // add default empty path pattern
                    pos = entry.length();
                    entry = entry + "/.*";
                }
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",blacklistToUse)) {
                        plasmaSwitchboard.urlBlacklist.remove(supportedBlacklistTypes[blTypes],entry.substring(0, pos), entry.substring(pos + 1));
                    }
                }



            }

        }

        // loading all blacklist files located in the directory
        String[] dirlist = listManager.getDirListing(listManager.listsPath);
        
        // if we have not chosen a blacklist until yet we use the first file
        if (blacklistToUse == null && dirlist != null && dirlist.length > 0) {
            blacklistToUse = dirlist[0];
        }
        

        // Read the blacklist items from file
        if (blacklistToUse != null) {
            int entryCount = 0;
            final ArrayList<String> list = listManager.getListArray(new File(listManager.listsPath, blacklistToUse));
            
            // sort them
            String[] sortedlist = new String[list.size()];
            Arrays.sort(list.toArray(sortedlist));
            
            // display them
            for (int j=0;j<sortedlist.length;++j){
                String nextEntry = sortedlist[j];
                
                if (nextEntry.length() == 0) continue;
                if (nextEntry.startsWith("#")) continue;
    
                prop.put(DISABLED + "Itemlist_" + entryCount + "_item", nextEntry);
                entryCount++;
            }
        	prop.put(DISABLED + "Itemlist", entryCount);


	        // List known hosts for BlackList retrieval
	        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) { // no nullpointer error
	            int peerCount = 0;
	            try {
	                TreeMap<String, String> hostList = new TreeMap<String, String>();
	                final Iterator<yacySeed> e = yacyCore.seedDB.seedsConnected(true, false, null, (float) 0.0);
	                while (e.hasNext()) {
	                    yacySeed seed = (yacySeed) e.next();
	                    if (seed != null) hostList.put(seed.get(yacySeed.NAME, "nameless"),seed.hash);
	                }
	
	                String peername;
	                while ((peername = hostList.firstKey()) != null) {
	                    final String Hash = hostList.get(peername);
	                    prop.put(DISABLED + "otherHosts_" + peerCount + "_hash", Hash);
	                    prop.putHTML(DISABLED + "otherHosts_" + peerCount + "_name", peername, true);
	                    hostList.remove(peername);
	                    peerCount++;
	                }
	            } catch (Exception e) {/* */}
	            prop.put(DISABLED + "otherHosts", peerCount);
	        }
        }
        
        // List BlackLists
        int blacklistCount = 0;
        if (dirlist != null) {
            for (int i = 0; i <= dirlist.length - 1; i++) {
                prop.putHTML(DISABLED + BLACKLIST + blacklistCount + "_name", dirlist[i], true);
                prop.put(DISABLED + BLACKLIST + blacklistCount + "_selected", "0");

                if (dirlist[i].equals(blacklistToUse)) { //current List
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_selected", "1");

                    for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                        prop.putHTML(DISABLED + "currentActiveFor_" + blTypes + "_blTypeName",supportedBlacklistTypes[blTypes], true);
                        prop.put(DISABLED + "currentActiveFor_" + blTypes + "_checked",
                                listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",dirlist[i]) ? "0" : "1");
                    }
                    prop.put(DISABLED + "currentActiveFor", supportedBlacklistTypes.length);

                }
                
                if (listManager.listSetContains(BLACKLIST_SHARED, dirlist[i])) {
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_shared", "1");
                } else {
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_shared", "0");
                }

                int activeCount = 0;
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (listManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists",dirlist[i])) {
                        prop.putHTML(DISABLED + BLACKLIST + blacklistCount + "_active_" + activeCount + "_blTypeName", supportedBlacklistTypes[blTypes]);
                        activeCount++;
                    }                
                }          
                prop.put(DISABLED + BLACKLIST + blacklistCount + "_active", activeCount);
                blacklistCount++;
            }
        }
        prop.put(DISABLED + "blackLists", blacklistCount);
        
        prop.putHTML(DISABLED + "currentBlacklist", (blacklistToUse==null) ? "" : blacklistToUse, true);
        prop.put("disabled", (blacklistToUse == null) ? "1" : "0");
        return prop;
    }

}
