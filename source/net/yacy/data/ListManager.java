// listManager.java
// -------------------------------------
// part of YACY
//
// (C) 2005, 2006 by Alexander Schier
// (C) 2007 by Bjoern 'Fuchs' Krombholz; fox.box@gmail.com
//
// last change: $LastChangedDate$ by $LastChangedBy$
// $LastChangedRevision$
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

package net.yacy.data;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.BlacklistFile;
import net.yacy.search.Switchboard;
import net.yacy.search.query.SearchEventCache;

// The Naming of the functions is a bit strange...

public class ListManager {

    private final static Pattern commaPattern = Pattern.compile(",");

    public static Switchboard switchboard = null;
    public static File listsPath = null;

    /**
     * Get ListSet from configuration file and return it as a unified Set.
     *
     * <b>Meaning of ListSet</b>: There are various "lists" in YaCy which are
     * actually disjunct (pairwise unequal) sets which themselves can be seperated
     * into different subsets. E.g., there can be more than one blacklist of a type.
     * A ListSet is the set of all those "lists" (subsets) of an equal type.
     *
     * @param setName name of the ListSet
     * @return a ListSet from configuration file
     */
    public static Set<String> getListSet(final String setName) {
        return string2set(switchboard.getConfig(setName, ""));
    }

    /**
     * Removes an element from a ListSet and updates the configuration file
     * accordingly. If the element doesn't exist, then nothing will be changed.
     *
     * @param setName name of the ListSet.
     * @param listName name of the element to remove from the ListSet.
     */
    public static void removeFromListSet(final String setName, final String listName) {
        final Set<String> listSet = getListSet(setName);

        if (!listSet.isEmpty()) {
            listSet.remove(listName);
            switchboard.setConfig(setName, collection2string(listSet));
        }
    }

    /**
     * Adds an element to an existing ListSet. If the ListSet doesn't exist yet,
     * a new one will be added. If the ListSet already contains an identical element,
     * then nothing happens.
     *
     * The new list will be written to the configuartion file.
     *
     * @param setName
     * @param newListName
     */
    public static void updateListSet(final String setName, final String newListName) {
        final Set<String> listSet = getListSet(setName);
        listSet.add(newListName);

        switchboard.setConfig(setName, collection2string(listSet));
    }

    /**
     * @param setName ListSet in which to search for an element.
     * @param listName the element to search for.
     * @return <code>true</code> if the ListSet "setName" contains an element
     *         "listName", <code>false</code> otherwise.
     */
    public static boolean listSetContains(final String setName, final String listName) {
        return getListSet(setName).contains(listName);
    }


//================general Lists==================

    public static String getListString(final String filename, final boolean withcomments) {
        return FileUtils.getListString(new File(listsPath ,filename), withcomments);
    }

//================Helper functions for collection conversion==================

    /**
     * Simple conversion of a Collection of Strings to a comma separated String.
     * If the implementing Collection subclass guaranties an order of its elements,
     * the substrings of the result will have the same order.
     *
     * @param col a Collection of Strings.
     * @return String with elements from set separated by comma.
     */
    public static String collection2string(final Collection<String> col){
        final StringBuilder str = new StringBuilder(col.size() * 40);

        if (col != null && !col.isEmpty()) {
            final Iterator<String> it = col.iterator();
            str.append(it.next());
            while(it.hasNext()) {
            	if (str.length() > 0) str.append(',');
            	str.append(it.next());
            }
        }

        return str.toString();
    }

    /**
     * @see listManager#string2vector(String)
     */
    public static ArrayList<String> string2arraylist(final String string){
        ArrayList<String> list;

        if (string != null && string.length() > 0) {
            list = new ArrayList<String>(Arrays.asList(commaPattern.split(string, 0)));
        } else {
            list = new ArrayList<String>();
        }

        return list;
    }

    /**
     * Simple conversion of a comma separated list to a unified Set.
     *
     * @param string list of comma separated Strings
     * @return resulting Set or empty Set if string is <code>null</code>
     */
    public static Set<String> string2set(final String string){
        HashSet<String> set;

        if (string != null) {
            set = new HashSet<String>(Arrays.asList(commaPattern.split(string, 0)));
        } else {
            set = new HashSet<String>();
        }

        return set;
    }

    /**
     * Simple conversion of a comma separated list to a Vector containing
     * the order of the substrings.
     *
     * @param string list of comma separated Strings
     * @return resulting Vector or empty Vector if string is <code>null</code>
     */
    public static Vector<String> string2vector(final String string){
        Vector<String> v;

        if (string != null) {
            v = new Vector<String>(Arrays.asList(commaPattern.split(string, 0)));
        } else {
            v = new Vector<String>();
        }

        return v;
    }

//=============Blacklist specific================

    /**
     * Load or reload all active Blacklists
     */
    public static void reloadBlacklists(){
        final List<BlacklistFile> blacklistFiles = new ArrayList<BlacklistFile>(BlacklistType.values().length);
        for (final BlacklistType supportedBlacklistType : BlacklistType.values()) {
            final BlacklistFile blFile = new BlacklistFile(
                    switchboard.getConfig(
                    supportedBlacklistType.toString() + ".BlackLists", switchboard.getConfig("BlackLists.DefaultList", "url.default.black")),
                    supportedBlacklistType);
            blacklistFiles.add(blFile);
        }

        Switchboard.urlBlacklist.loadList(
                blacklistFiles.toArray(new BlacklistFile[blacklistFiles.size()]),
                "/");
        SearchEventCache.cleanupEvents(true);

//       switchboard.urlBlacklist.clear();
//       if (f != "") switchboard.urlBlacklist.loadLists("black", f, "/");
    }
}
