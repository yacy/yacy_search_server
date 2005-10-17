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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class Blacklist_p {
    private final static String BLACKLIST        = "blackLists_";
    private final static String BLACKLIST_ALL    = "proxyBlackLists";
    private final static String BLACKLIST_ACTIVE = "proxyBlackListsActive";
    private final static String BLACKLIST_SHARED = "proxyBlackListsShared";

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        listManager.switchboard = (plasmaSwitchboard) env;
        listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        final serverObjects prop = new serverObjects();
        String line;
//      String HTMLout = "";

        String removeItem = "removeme";
        int numItems = 0;
        int i; // need below

        String[] filenames = listManager.getListslistArray(BLACKLIST_ALL);
        String filename = "";

        if (post != null) {
            if (post.containsKey("blackLists")) { // Blacklist selected
                filename = (String)post.get("blackLists");
            } else if (post.containsKey("filename")) {
                filename = (String)post.get("filename");
            } else if (filenames.length > 0){ // first BlackList
                filename = filenames[0];
//          } else { //No BlackList
//              System.out.println("DEBUG: No Blacklist found");
            }
            prop.put("status", 0); // nothing

            // del list
            if (post.containsKey("dellistbutton")) {
                final File BlackListFile = new File(listManager.listsPath, filename);
                BlackListFile.delete();

                // remove from all BlackLists Lists
                listManager.removeListFromListslist(BLACKLIST_ALL, filename);
                listManager.removeListFromListslist(BLACKLIST_ACTIVE, filename);
                listManager.removeListFromListslist(BLACKLIST_SHARED, filename);

                // reload Blacklists
                listManager.reloadBlacklists();
                filenames = listManager.getListslistArray(BLACKLIST_ALL);
                if (filenames.length > 0) {
                    filename = filenames[0];
                }

            // new list
            } else if (post.containsKey("newlistbutton")) {
                String newList = (String)post.get("newlist");
                if (!newList.endsWith(".black")) {
                    newList += ".black";
                }
                filename = newList; //to select it in the returnes Document
                try {
                    final File newFile = new File(listManager.listsPath, newList);
                    newFile.createNewFile();
                    listManager.addListToListslist(BLACKLIST_ALL, newList);
                    listManager.addListToListslist(BLACKLIST_ACTIVE, newList);
                    listManager.addListToListslist(BLACKLIST_SHARED, newList);
                } catch (IOException e) {}


            } else if (post.containsKey("activatelistbutton")) {
                if( listManager.ListInListslist(BLACKLIST_ACTIVE, filename) ){ 
                    listManager.removeListFromListslist(BLACKLIST_ACTIVE, filename);
                } else { // inactive list -> enable
                    listManager.addListToListslist(BLACKLIST_ACTIVE, filename);
                }
                listManager.reloadBlacklists();

            } else if (post.containsKey("sharelistbutton")) {
                if (listManager.ListInListslist(BLACKLIST_SHARED, filename)) { 
                    // Remove from shared BlackLists
                    listManager.removeListFromListslist(BLACKLIST_SHARED, filename);
                } else { // inactive list -> enable
                    listManager.addListToListslist(BLACKLIST_SHARED, filename);
                }
            } // List Management End

            // remove a Item?
            if (post.containsKey("delbutton") &&
                post.containsKey("Itemlist")  &&
              !((String)post.get("Itemlist")).equals("") ) {
                removeItem = (String)post.get("Itemlist");
            }
        } // post != null   

        // Read the List
        final ArrayList list = listManager.getListArray(new File(listManager.listsPath, filename));
        final StringBuffer out = new StringBuffer(list.size() * 64);
        final Iterator iter = list.iterator();
        while (iter.hasNext()){
            line = (String) iter.next();

            if (!(line.length() == 0 || line.charAt(0) == '#' || line.equals(removeItem))) { //Not the item to remove
                prop.put("Itemlist_" + numItems + "_item", line);
                numItems++;
            }

            if (line.equals(removeItem)) {
                prop.put("status", 1);//removed
                prop.put("status_item", line);
//              if (listManager.switchboard.urlBlacklist != null) {
//                  listManager.switchboard.urlBlacklist.remove(line);
                if (plasmaSwitchboard.urlBlacklist != null) {
                    plasmaSwitchboard.urlBlacklist.remove(line);
                }
            } else {
                out.append(line).append(serverCore.crlfString); //full list
            }
        }
        prop.put("Itemlist", numItems);

        // Add a new Item
        if (post != null && post.containsKey("addbutton") && !((String)post.get("newItem")).equals("")) {
            String newItem = (String)post.get("newItem");

            //clean http://
            if ( newItem.startsWith("http://") ){
                newItem = newItem.substring(7);
            }

            //append "/.*"
            int pos = newItem.indexOf("/");
            if (pos < 0) {
                // add default empty path pattern
                pos = newItem.length();
                newItem = newItem + "/.*";
            }

            out.append(newItem).append(serverCore.crlfString);

            prop.put("Itemlist_"+numItems+"_item", newItem);
            numItems++;
            prop.put("Itemlist", numItems);

            prop.put("status", 2);//added
            prop.put("status_item", newItem);//added

            // add to blacklist
//          if (listManager.switchboard.urlBlacklist != null)
//              listManager.switchboard.urlBlacklist.add(newItem.substring(0, pos), newItem.substring(pos + 1));
            if (plasmaSwitchboard.urlBlacklist != null) { 
                plasmaSwitchboard.urlBlacklist.add(newItem.substring(0, pos), newItem.substring(pos + 1));
            }
        }
        listManager.writeList(new File(listManager.listsPath, filename), out.toString());

        // List known hosts for BlackList retrieval
        yacySeed seed;
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) { // no nullpointer error
            final Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null);
            i = 0;
            while (e.hasMoreElements()) {
                seed = (yacySeed) e.nextElement();
                if (seed != null) {
                    final String Hash = seed.hash;
                    final String Name = seed.get(yacySeed.NAME, "nameless");
                    prop.put("otherHosts_" + i + "_hash", Hash);
                    prop.put("otherHosts_" + i + "_name", Name);
                    i++;
                }
            }
            prop.put("otherHosts", i);
//      } else {
//          System.out.println("BlackList_p: yacy seed not loaded!"); // DEBUG: 
        }

        // List BlackLists
        final String[] BlackLists = listManager.getListslistArray(BLACKLIST_ALL);
        for (i = 0; i <= BlackLists.length - 1; i++) {
            prop.put(BLACKLIST + i + "_name", BlackLists[i]);
            prop.put(BLACKLIST + i + "_active", 0);
            prop.put(BLACKLIST + i + "_shared", 0);
            prop.put(BLACKLIST + i + "_selected", 0);
            if (BlackLists[i].equals(filename)) { //current List
                prop.put(BLACKLIST + i + "_selected", 1);
            }
            if (listManager.ListInListslist(BLACKLIST_ACTIVE, BlackLists[i])) {
                prop.put(BLACKLIST + i + "_active", 1);
            }
            if (listManager.ListInListslist(BLACKLIST_SHARED, BlackLists[i])) {
                prop.put(BLACKLIST + i + "_shared", 1);
            }
        }
        prop.put("blackLists", i);
        prop.put("filename", filename);
        return prop;
    }

}
