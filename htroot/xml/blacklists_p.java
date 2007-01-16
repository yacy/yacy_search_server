// /xml/blacklists_p.java
// -------------------------------
// (C) 2006 Alexander Schier
// part of YaCy
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
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  US


package xml;
import java.io.File;
import java.util.ArrayList;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class blacklists_p {
    
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        
        listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        String[] dirlist = listManager.getDirListing(listManager.listsPath);
        int blacklistCount=0;
        
        ArrayList list;
        int count;
        if (dirlist != null) {
            for (int i = 0; i <= dirlist.length - 1; i++) {
                prop.put("lists_" + blacklistCount + "_name", dirlist[i]);
         
                if (listManager.ListInListslist("BlackLists.Shared", dirlist[i])) {
                    prop.put("lists_" + blacklistCount + "_shared", 1);
                } else {
                    prop.put("lists_" + blacklistCount + "_shared", 0);
                }
                if (listManager.ListInListslist("dht" + ".BlackLists",dirlist[i])) {
                    prop.put("lists_" + blacklistCount + "_dht", 1);
                } else {
                    prop.put("lists_" + blacklistCount + "_dht", 0);
                }
                if (listManager.ListInListslist("crawler" + ".BlackLists",dirlist[i])) {
                    prop.put("lists_" + blacklistCount + "_crawler", 1);
                } else {
                    prop.put("lists_" + blacklistCount + "_crawler", 0);
                }
                if (listManager.ListInListslist("proxy" + ".BlackLists",dirlist[i])) {
                    prop.put("lists_" + blacklistCount + "_proxy", 1);
                } else {
                    prop.put("lists_" + blacklistCount + "_proxy", 0);
                }
                if (listManager.ListInListslist("search" + ".BlackLists",dirlist[i])) {
                    prop.put("lists_" + blacklistCount + "_search", 1);
                } else {
                    prop.put("lists_" + blacklistCount + "_search", 0);
                }
                list = listManager.getListArray(new File(listManager.listsPath, dirlist[i]));
                
                count=0;
                for (int j=0;j<list.size();++j){
                    String nextEntry = (String)list.get(j);
                    
                    if (nextEntry.length() == 0) continue;
                    if (nextEntry.startsWith("#")) continue;
        
                    prop.putSafeXML("lists_" + blacklistCount + "_items_" + count + "_item", nextEntry);
                    count++;
                }
                prop.put("lists_" + blacklistCount + "_items", count);
                blacklistCount++;
            }
        }
        prop.put("lists", blacklistCount);
        
        
        // return rewrite properties
        return prop;
    }
    
}



