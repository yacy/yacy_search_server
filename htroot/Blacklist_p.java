// Blacklist_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

// You must compile this file with
// javac -classpath .:../classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;

import de.anomic.data.WorkTables;
import de.anomic.data.ListManager;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Blacklist_p {
    private final static String EDIT             = "edit_";
    private final static String DISABLED         = "disabled_";
    private final static String BLACKLIST        = "blackLists_";
    private final static String BLACKLIST_MOVE   = "blackListsMove_";
    private final static String BLACKLIST_SHARED = "BlackLists.Shared";
    

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        
        // initialize the list manager
        ListManager.switchboard = (Switchboard) env;
        ListManager.listsPath = new File(ListManager.switchboard.getDataPath(),ListManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        
        // get the list of supported blacklist types
        final String supportedBlacklistTypesStr = Blacklist.BLACKLIST_TYPES_STRING;
        final String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(",");
        
        // load all blacklist files located in the directory
        List<String> dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
        
        String blacklistToUse = null;
        final serverObjects prop = new serverObjects();
        prop.putHTML("blacklistEngine", Switchboard.urlBlacklist.getEngineInfo());

        // do all post operations
        if (post != null) {
           
            final String action = post.get("action", "");
            
            if(post.containsKey("testList")) {
            	prop.put("testlist", "1");
            	String urlstring = post.get("testurl", "");
            	if (!urlstring.startsWith("http://") &&
                        !urlstring.startsWith("https://") &&
                        !urlstring.startsWith("ftp://") &&
                        !urlstring.startsWith("smb://") &&
                        !urlstring.startsWith("file://")) {
                    urlstring = "http://"+urlstring;
                }
                DigestURI testurl;
                try {
                    testurl = new DigestURI(urlstring);
                } catch (final MalformedURLException e) {
                    testurl = null;
                }
                if(testurl != null) {
                    prop.putHTML("testlist_url",testurl.toString());
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, testurl)) {
                            prop.put("testlist_listedincrawler", "1");
                    }
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_DHT, testurl)) {
                            prop.put("testlist_listedindht", "1");
                    }
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_NEWS, testurl)) {
                            prop.put("testlist_listedinnews", "1");
                    }
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_PROXY, testurl)) {
                            prop.put("testlist_listedinproxy", "1");
                    }
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_SEARCH, testurl)) {
                            prop.put("testlist_listedinsearch", "1");
                    }
                    if (Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_SURFTIPS, testurl)) {
                            prop.put("testlist_listedinsurftips", "1");
                    }
                } else {
                    prop.put("testlist_url","not valid");
                }
            }
            if (post.containsKey("selectList")) {
                blacklistToUse = post.get("selectedListName");
                if (blacklistToUse != null && blacklistToUse.length() == 0) {
                    blacklistToUse = null;
                }
            }
            if (post.containsKey("createNewList")) {
                /* ===========================================================
                 * Creation of a new blacklist
                 * =========================================================== */
                
                blacklistToUse = post.get("newListName", "").trim();
                if (blacklistToUse.length() == 0) {
                    prop.put("LOCATION","");
                    return prop;
                }   
                   
                // Check if blacklist name only consists of "legal" characters.
                // This is mainly done to prevent files from being written to other directories
                // than the LISTS directory.
                if (!blacklistToUse.matches("^[\\p{L}\\d\\+\\-_]+[\\p{L}\\d\\+\\-_.]*(\\.black){0,1}$")) {
                    prop.put("error", 1);
                    prop.putHTML("error_name", blacklistToUse);
                    blacklistToUse = null;
                } else {
                
                    if (!blacklistToUse.endsWith(".black")) {
                        blacklistToUse += ".black";
                    }

                    if (!dirlist.contains(blacklistToUse)) {
                        try {
                            final File newFile = new File(ListManager.listsPath, blacklistToUse);
                            newFile.createNewFile();

                            // share the newly created blacklist
                            ListManager.updateListSet(BLACKLIST_SHARED, blacklistToUse);

                            // activate it for all known blacklist types
                            for (final String supportedBlacklistType : supportedBlacklistTypes) {
                                ListManager.updateListSet(supportedBlacklistType + ".BlackLists", blacklistToUse);
                            }                            
                        } catch (final IOException e) {/* */}
                    } else {
                        prop.put("error", 2);
                        prop.putHTML("error_name", blacklistToUse);
                        blacklistToUse = null;
                    }
                    
                    // reload Blacklists
                    dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);
                }
                
            } else if (post.containsKey("deleteList")) {
                /* ===========================================================
                 * Delete a blacklist
                 * =========================================================== */                
                
                blacklistToUse = post.get("selectedListName");
                if (blacklistToUse == null || blacklistToUse.length() == 0) {
                    prop.put("LOCATION","");
                    return prop;
                }                   
                
                final File blackListFile = new File(ListManager.listsPath, blacklistToUse);
                if(!blackListFile.delete()) {
                    Log.logWarning("Blacklist", "file "+ blackListFile +" could not be deleted!");
                }

                for (final String supportedBlacklistType : supportedBlacklistTypes) {
                    ListManager.removeFromListSet(supportedBlacklistType + ".BlackLists",blacklistToUse);
                }                
                
                // remove it from the shared list
                ListManager.removeFromListSet(BLACKLIST_SHARED, blacklistToUse);
                blacklistToUse = null;
                
                // reload Blacklists
                dirlist = FileUtils.getDirListing(ListManager.listsPath, Blacklist.BLACKLIST_FILENAME_FILTER);

            } else if (post.containsKey("activateList")) {

                /* ===========================================================
                 * Activate/Deactivate a blacklist
                 * =========================================================== */                   
                
                blacklistToUse = post.get("selectedListName", "").trim();
                if (blacklistToUse == null || blacklistToUse.length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }                   
                
                for (final String supportedBlacklistType : supportedBlacklistTypes) {
                    if (post.containsKey("activateList4" + supportedBlacklistType)) {
                        ListManager.updateListSet(supportedBlacklistType + ".BlackLists",blacklistToUse);
                    } else {
                        ListManager.removeFromListSet(supportedBlacklistType + ".BlackLists",blacklistToUse);
                    }                    
                }                     

                ListManager.reloadBlacklists();
                
            } else if (post.containsKey("shareList")) {

                /* ===========================================================
                 * Share a blacklist
                 * =========================================================== */                   
                
                blacklistToUse = post.get("selectedListName", "").trim();
                if (blacklistToUse == null || blacklistToUse.length() == 0) {
                    prop.put("LOCATION", "");
                    return prop;
                }                   
                
                if (ListManager.listSetContains(BLACKLIST_SHARED, blacklistToUse)) {
                    // Remove from shared BlackLists
                    ListManager.removeFromListSet(BLACKLIST_SHARED, blacklistToUse);
                } else { // inactive list -> enable
                    ListManager.updateListSet(BLACKLIST_SHARED, blacklistToUse);
                }
            } else if ("deleteBlacklistEntry".equals(action)) {
                
                /* ===========================================================
                 * Delete an entry from a blacklist
                 * =========================================================== */
                
                blacklistToUse = post.get("currentBlacklist", "").trim();
                
                final String[] selectedBlacklistEntries = post.getAll("selectedEntry.*");
                
                if (selectedBlacklistEntries.length > 0) {
                    String temp = null;
                    for (final String selectedBlacklistEntry : selectedBlacklistEntries) {
                        if ((temp = deleteBlacklistEntry(blacklistToUse, selectedBlacklistEntry, header, supportedBlacklistTypes)) != null) {
                            prop.put("LOCATION", temp);
                            return prop;
                        }
                    }
                }
                ListManager.reloadBlacklists();

            } else if (post.containsKey("addBlacklistEntry")) {
                
                /* ===========================================================
                 * Add new entry to blacklist
                 * =========================================================== */

                blacklistToUse = post.get("currentBlacklist", "").trim();
                final String blentry = post.get("newEntry", "").trim();
                
                // store this call as api call
                ListManager.switchboard.tables.recordAPICall(post, "Blacklist_p.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "add to blacklist: " + blentry);
                
                final String temp = addBlacklistEntry(blacklistToUse, blentry, header, supportedBlacklistTypes);
                if (temp != null) {
                    prop.put("LOCATION", temp);
                    return prop;
                }
                ListManager.reloadBlacklists();
                
            } else if ("moveBlacklistEntry".equals(action)) {
                
                /* ===========================================================
                 * Move an entry from one blacklist to another
                 * =========================================================== */
                
                blacklistToUse = post.get("currentBlacklist", "").trim();
                final String targetBlacklist = post.get("targetBlacklist");
                
                final String[] selectedBlacklistEntries = post.getAll("selectedEntry.*");
                
                if (selectedBlacklistEntries != null &&
                        selectedBlacklistEntries.length > 0 &&
                        targetBlacklist != null &&
                        blacklistToUse != null &&
                        !targetBlacklist.equals(blacklistToUse)) {
                    String temp;
                    for (final String selectedBlacklistEntry : selectedBlacklistEntries) {
                        if ((temp = addBlacklistEntry(targetBlacklist, selectedBlacklistEntry, header, supportedBlacklistTypes)) != null) {
                            prop.put("LOCATION", temp);
                            return prop;
                        }

                        if ((temp = deleteBlacklistEntry(blacklistToUse, selectedBlacklistEntry, header, supportedBlacklistTypes)) != null) {
                            prop.put("LOCATION", temp);
                            return prop;
                            
                        }
                    }
                }
                ListManager.reloadBlacklists();

            } else if ("editBlacklistEntry".equals(action)) {
                
                /* ===========================================================
                 * Edit entry of a blacklist
                 * =========================================================== */
                
                blacklistToUse = post.get("currentBlacklist", "").trim();
                
                final String[] editedBlacklistEntries = post.getAll("editedBlacklistEntry.*");
        
                // if edited entry has been posted, save changes
                if (editedBlacklistEntries.length > 0) {
      
                    final String[] selectedBlacklistEntries = post.getAll("selectedBlacklistEntry.*");
                    
                    if (selectedBlacklistEntries.length != editedBlacklistEntries.length) {
                        prop.put("LOCATION", "");
                        return prop;
                    }
                    
                    String temp = null;

                    for (int i = 0; i < selectedBlacklistEntries.length; i++) {

                        if (!selectedBlacklistEntries[i].equals(editedBlacklistEntries[i])) {

                            if ((temp = deleteBlacklistEntry(blacklistToUse, selectedBlacklistEntries[i], header, supportedBlacklistTypes)) != null) {
                                prop.put("LOCATION", temp);
                                return prop;
                            }

                            if ((temp = addBlacklistEntry(blacklistToUse, editedBlacklistEntries[i], header, supportedBlacklistTypes)) != null) {
                                prop.put("LOCATION", temp);
                                return prop;
                            }
                        }
                    }
                    ListManager.reloadBlacklists();
                    prop.putHTML(DISABLED + EDIT + "currentBlacklist", blacklistToUse);
                    
                // else return entry to be edited
                } else {
                    final String[] selectedEntries = post.getAll("selectedEntry.*");
                    if (selectedEntries != null && selectedEntries.length > 0 && blacklistToUse != null) {
                        for (int i = 0; i < selectedEntries.length; i++) {
                            prop.putHTML(DISABLED + EDIT + "editList_" + i + "_item", selectedEntries[i]);
                            prop.put(DISABLED + EDIT + "editList_" + i + "_count", i);
                        }
                        prop.putHTML(DISABLED + EDIT + "currentBlacklist", blacklistToUse);
                        prop.put(DISABLED + "edit", "1");   
                        prop.put(DISABLED + EDIT + "editList", selectedEntries.length);
                    }
                }
            } else if ("selectRange".equals(action)) {
                blacklistToUse = post.get("currentBlacklist");
            }

        }

        // if we have not chosen a blacklist until yet we use the first file
        if (blacklistToUse == null && dirlist != null && !dirlist.isEmpty()) {
            blacklistToUse = dirlist.get(0);
        }

        // Read the blacklist items from file
        if (blacklistToUse != null) {
            int entryCount = 0;
            final List<String> list = FileUtils.getListArray(new File(ListManager.listsPath, blacklistToUse));
            
            // sort them
            final String[] sortedlist = new String[list.size()];
            Arrays.sort(list.toArray(sortedlist));
            
            // display them
            boolean dark = true;
            int offset = 0;
            int size = 50;
            int to = 50;
            if (post != null) {
                offset = post.getInt("offset", 0);
                size = post.getInt("size", 50);
                to = offset + size;
            }
            if (offset > sortedlist.length || offset < 0) {
                offset = 0;
            }
            if (to > sortedlist.length || size < 1) {
                to = sortedlist.length;
            }

            for (int j = offset; j < to; ++j){
                final String nextEntry = sortedlist[j];
                
                if (nextEntry.length() == 0) continue;
                if (nextEntry.charAt(0) == '#') continue;
                prop.put(DISABLED + EDIT + "Itemlist_" + entryCount + "_dark", dark ? "1" : "0");
                dark = !dark;
                prop.putHTML(DISABLED + EDIT + "Itemlist_" + entryCount + "_item", nextEntry);
                prop.put(DISABLED + EDIT + "Itemlist_" + entryCount + "_count", entryCount);
                entryCount++;
            }
            prop.put(DISABLED + EDIT + "Itemlist", entryCount);

            // create selection of sublist
            entryCount = 0;
            int end = -1;
            int start = -1;
            if (sortedlist.length > 0) {
                while (end < sortedlist.length) {
                    if (size > 0) {
                        start = entryCount * size;
                        end = (entryCount + 1) * size;
                    } else {
                        start = 0;
                        end = sortedlist.length;
                    }
                    prop.put(DISABLED + EDIT + "subListOffset_" + entryCount + "_value", start);
                    prop.put(DISABLED + EDIT + "subListOffset_" + entryCount + "_fvalue", start + 1);
                    if (end > sortedlist.length) {
                        end = sortedlist.length;
                    }
                    prop.put(DISABLED + EDIT + "subListOffset_" + entryCount + "_tvalue", end);
                    if (start == offset) {
                        prop.put(DISABLED + EDIT + "subListOffset_" + entryCount + "_selected", 1);
                    }
                    entryCount++;
                }
            } else {
                prop.put(DISABLED + EDIT + "subListOffset_0_value", 0);
                prop.put(DISABLED + EDIT + "subListOffset_0_fvalue", 0);
                prop.put(DISABLED + EDIT + "subListOffset_0_tvalue", 0);
                entryCount++;
            }
            prop.put(DISABLED + EDIT + "subListOffset", entryCount);

            // create selection of list size
            int[] sizes = {10,25,50,100,250,-1};
            for (int i = 0; i < sizes.length; i++) {
                prop.put(DISABLED + EDIT + "subListSize_" + i + "_value", sizes[i]);
                if (sizes[i] == -1) {
                    prop.put(DISABLED + EDIT + "subListSize_" + i + "_text", "all");
                } else {
                    prop.put(DISABLED + EDIT + "subListSize_" + i + "_text", sizes[i]);
                }
                if (sizes[i] == size) {
                    prop.put(DISABLED + EDIT + "subListSize_" + i + "_selected", 1);
                }
            }
            prop.put(DISABLED + EDIT + "subListSize", sizes.length);
        }
        
        // List BlackLists
        int blacklistCount = 0;
        int blacklistMoveCount = 0;
        if (dirlist != null) {

            for (String element : dirlist) {
                prop.putXML(DISABLED + BLACKLIST + blacklistCount + "_name", element);
                prop.put(DISABLED + BLACKLIST + blacklistCount + "_selected", "0");

                if (element.equals(blacklistToUse)) { //current List
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_selected", "1");

                    for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                        prop.putXML(DISABLED + "currentActiveFor_" + blTypes + "_blTypeName",supportedBlacklistTypes[blTypes]);
                        prop.put(DISABLED + "currentActiveFor_" + blTypes + "_checked",
                                ListManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists", element) ? "0" : "1");
                    }
                    prop.put(DISABLED + "currentActiveFor", supportedBlacklistTypes.length);

                } else {
                    prop.putXML(DISABLED + EDIT + BLACKLIST_MOVE + blacklistMoveCount + "_name", element);
                    blacklistMoveCount++;
                }
                
                if (ListManager.listSetContains(BLACKLIST_SHARED, element)) {
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_shared", "1");
                } else {
                    prop.put(DISABLED + BLACKLIST + blacklistCount + "_shared", "0");
                }

                int activeCount = 0;
                for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
                    if (ListManager.listSetContains(supportedBlacklistTypes[blTypes] + ".BlackLists", element)) {
                        prop.putHTML(DISABLED + BLACKLIST + blacklistCount + "_active_" + activeCount + "_blTypeName", supportedBlacklistTypes[blTypes]);
                        activeCount++;
                    }                
                }          
                prop.put(DISABLED + BLACKLIST + blacklistCount + "_active", activeCount);
                blacklistCount++;
            }
        }
        prop.put(DISABLED + "blackLists", blacklistCount);
        prop.put(DISABLED + EDIT + "blackListsMove", blacklistMoveCount);
        
        prop.putXML(DISABLED + "currentBlacklist", (blacklistToUse==null) ? "" : blacklistToUse);
        prop.putXML(DISABLED + EDIT + "currentBlacklist", (blacklistToUse==null) ? "" : blacklistToUse);
        prop.put("disabled", (blacklistToUse == null) ? "1" : "0");
        return prop;
    }
    

    
    /**
     * This method adds a new entry to the chosen blacklist.
     * @param blacklistToUse the name of the blacklist the entry is to be added to
     * @param newEntry the entry that is to be added
     * @param header
     * @param supportedBlacklistTypes
     * @return null if no error occured, else a String to put into LOCATION
     */
    private static String addBlacklistEntry(
            final String blacklistToUse,
            String newEntry,
            final RequestHeader header,
            final String[] supportedBlacklistTypes) {

        if (blacklistToUse == null || blacklistToUse.length() == 0) {
            return "";
        }

        if (newEntry == null || newEntry.length() == 0) {
            return header.get(HeaderFramework.CONNECTION_PROP_PATH) + "?selectList=&selectedListName=" + blacklistToUse;
        }

        addBlacklistEntry(ListManager.listsPath, blacklistToUse, newEntry, supportedBlacklistTypes);
        SearchEventCache.cleanupEvents(true);
        return null;
    }
    

    /**
     * This method deletes a blacklist entry.
     * @param blacklistToUse the name of the blacklist the entry is to be deleted from
     * @param oldEntry the entry that is to be deleted
     * @param header
     * @param supportedBlacklistTypes
     * @return null if no error occured, else a String to put into LOCATION
     */
    private static String deleteBlacklistEntry(
            final String blacklistToUse,
            String oldEntry, 
            final RequestHeader header,
            final String[] supportedBlacklistTypes) {

        if (blacklistToUse == null || blacklistToUse.length() == 0) {
            return "";
        }

        if (oldEntry == null || oldEntry.length() == 0) {
            return header.get(HeaderFramework.CONNECTION_PROP_PATH) + "?selectList=&selectedListName=" + blacklistToUse;
        }

        deleteBlacklistEntry(ListManager.listsPath, blacklistToUse, oldEntry, supportedBlacklistTypes);
        SearchEventCache.cleanupEvents(true);
        return null;
    }

    /**
     * This method deletes a blacklist entry.
     * @param blacklistToUse the name of the blacklist the entry is to be deleted from
     * @param oldEntry the entry that is to be deleted
     * @param supportedBlacklistTypes
     */
    private static void deleteBlacklistEntry(
            final File listsPath,
            final String blacklistToUse,
            String oldEntry, 
            final String[] supportedBlacklistTypes) {

        // load blacklist data from file
        final List<String> list = FileUtils.getListArray(new File(listsPath, blacklistToUse));

        // delete the old entry from file
        if (list != null) {
            for (final String entry : list) {
                if (entry.equals(oldEntry)) {
                    list.remove(entry);
                    break;
                }
            }
            FileUtils.writeList(new File(listsPath, blacklistToUse), list.toArray(new String[list.size()]));
        }

        // remove the entry from the running blacklist engine
        int pos = oldEntry.indexOf('/');
        if (pos < 0) {
            // add default empty path pattern
            pos = oldEntry.length();
            oldEntry = oldEntry + "/.*";
        }
        for (final String supportedBlacklistType : supportedBlacklistTypes) {
            if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists",blacklistToUse)) {
                Switchboard.urlBlacklist.remove(supportedBlacklistType,oldEntry.substring(0, pos), oldEntry.substring(pos + 1));
            }
        }
    }


    
    /**
     * This method adds a new entry to the chosen blacklist.
     * @param blacklistToUse the name of the blacklist the entry is to be added to
     * @param newEntry the entry that is to be added
     * @param supportedBlacklistTypes
     */
    private static void addBlacklistEntry(
            final File listsPath,
            final String blacklistToUse,
            String newEntry,
            final String[] supportedBlacklistTypes) {

        // TODO: ignore empty entries

        if (newEntry.startsWith("http://") ){
            newEntry = newEntry.substring(7);
        } else if (newEntry.startsWith("https://")) {
            newEntry = newEntry.substring(8);
        }

        int pos = newEntry.indexOf('/');
        if (pos < 0) {
            // add default empty path pattern
            pos = newEntry.length();
            newEntry = newEntry + "/.*";
        }

        if (!Blacklist.blacklistFileContains(listsPath, blacklistToUse, newEntry)) {
            // append the line to the file
            PrintWriter pw = null;
            try {
                pw = new PrintWriter(new FileWriter(new File(listsPath, blacklistToUse), true));
                pw.println(newEntry);
                pw.close();
            } catch (final IOException e) {
                Log.logException(e);
            } finally {
                if (pw != null) {
                    try {
                        pw.close();
                    } catch (final Exception e) {
                        Log.logWarning("Blacklist", "could not close stream to " + blacklistToUse + "! " + e.getMessage());
                    }

                }
            }

            // add to blacklist
            for (final String supportedBlacklistType : supportedBlacklistTypes) {
                if (ListManager.listSetContains(supportedBlacklistType + ".BlackLists", blacklistToUse)) {
                    Switchboard.urlBlacklist.add(supportedBlacklistType, newEntry.substring(0, pos), newEntry.substring(pos + 1));
                }
            }
        }
    }
}
