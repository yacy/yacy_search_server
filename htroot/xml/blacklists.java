// /xml/blacklists.java
// -------------------------------
// based on /xml/blacklists_p.java (C) 2006 Alexander Schier, changes by Marc Nause
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
import java.util.List;

import de.anomic.data.listManager;
import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class blacklists {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final serverObjects prop = new serverObjects();
        
        listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));
        final List<String> dirlist = listManager.getDirListing(listManager.listsPath);
        int blacklistCount=0;
        
        List<String> list;
        int count;
        if (dirlist != null) {
            for (String element : dirlist) {
                prop.putXML("lists_" + blacklistCount + "_name", element);
         
                if (listManager.listSetContains("BlackLists.Shared", element)) {

                    list = listManager.getListArray(new File(listManager.listsPath, element));

                    count=0;
                    for (int j=0;j<list.size();++j){
                        final String nextEntry = list.get(j);

                        if (nextEntry.length() == 0) continue;
                        if (nextEntry.startsWith("#")) continue;

                        prop.putXML("lists_" + blacklistCount + "_items_" + count + "_item", nextEntry);
                        count++;
                    }
                    prop.put("lists_" + blacklistCount + "_items", count);
                    blacklistCount++;
                }
            }
        }
        prop.put("lists", blacklistCount);

        // return rewrite properties
        return prop;
    }
    
}
