// XmlBlacklistImporter.java
// -------------------------------------
// part of YACY
//
// (C) 2009 by Marc Nause
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

package net.yacy.data.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class is used to store content and properties of several blacklists.
 */
public class ListAccumulator {

    private final Map<String,Integer> names = new HashMap<String,Integer>();
    private final List<List<String>> entries = new ArrayList<List<String>>();
    private final List<Map<String,String>> properties = new ArrayList<Map<String,String>>();
    private int listCount = 0;
    private List<String> currentEntries;
    private Map<String,String> currentProperties;

    /**
     * Adds a new list if a list by that name does not exist yet.
     * @param name The name of the list to be added.
     * @return True if the new list has been added, else false (if list by name exists already).
     */
    public boolean addList(final String name) {
        boolean ret = false;
        if (!names.containsKey(name)) {
            names.put(name, listCount);
            entries.add(new LinkedList<String>());
            properties.add(new HashMap<String,String>());

            currentEntries = entries.get(listCount);
            currentProperties = properties.get(listCount);

            listCount++;
            ret = true;
        }
        return ret;
    }

    /**
     * Adds a new entry to a list identified by a given name.
     * @param key The name of the list the entry is to be added to.
     * @param entry The new entry.
     * @return True if the entry has been added, else false (if list does not exists).
     */
    public boolean addEntry(final String list, final String entry) {
        boolean ret = false;
        if (names.containsKey(list)) {
            entries.get(names.get(list)).add(entry);
            ret = true;
        }
        return ret;
    }

    /**
     * Adds a new entry to the list which has been added as the latest.
     * @param entry The new entry.
     * @return True if the entry has been added, else false (if no list has been added yet).
     */
    public boolean addEntryToCurrent(final String entry) {
        boolean ret = false;
        if (currentEntries != null) {
            currentEntries.add(entry);
            ret = true;
        }
        return ret;
    }

    /**
     * Adds a new property to a list identified by a given name.
     * @param list The name of the list.
     * @param property The name of the property.
     * @param value The value of the property.
     * @return True if the property has been added, else false (if list does not exists).
     */
    public boolean addProperty(final String list, final String property, final String value) {
        boolean ret = false;
        if (names.containsKey(list)) {
            properties.get(names.get(list)).put(property, value);
            ret = true;
        }
        return ret;
    }

    /**
     * Adds a new property to the list which has been added as the latest.
     * @param list The name of the list.
     * @param property The name of the property.
     * @param value The value of the property.
     * @return True if the property has been added, else false (if no list has been added yet).
     */
    public boolean addPropertyToCurrent(final String property, final String value) {
        boolean ret = false;
        if (currentProperties != null) {
            currentProperties.put(property, value);
            ret = true;
        }
        return ret;
    }

    /**
     * Returns a {@link List} which contains all the {@link List Lists} of entries.
     * @return list of lists.
     */
    public List<List<String>> getEntryLists() {
        return entries;
    }

    /**
     * Returns a {@link List} which contains all the {@link Maps} of entries.
     * @return
     */
    public List<Map<String,String>> getPropertyMaps() {
        return properties;
    }
}