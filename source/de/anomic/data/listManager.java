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

package de.anomic.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import de.anomic.index.indexAbstractReferenceBlacklist;
import de.anomic.index.indexReferenceBlacklist.blacklistFile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;

// The Naming of the functions is a bit strange...

public class listManager {
    public static plasmaSwitchboard switchboard = null;
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
        
        if (listSet.size() > 0) {
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
        final Set<String> Lists =  getListSet(setName);

        return Lists.contains(listName);
    }


//================general Lists==================

    /**
     * Read lines of a file into an ArrayList.
     * 
     * @param listFile the file
     * @return the resulting array as an ArrayList
     */
    public static ArrayList<String> getListArray(final File listFile){
        String line;
        final ArrayList<String> list = new ArrayList<String>();
        int count = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile),"UTF-8"));

            while((line = br.readLine()) != null){
                list.add(line);
                count++;
            }
            br.close();
        } catch(final IOException e) {
            // list is empty
        } finally {
            if (br!=null) try { br.close(); } catch (final Exception e) {}
        }
        return list;
    }

    /**
     * Write a String to a file (used for string representation of lists).
     * 
     * @param listFile the file to write to
     * @param out the String to write
     * @return returns <code>true</code> if successful, <code>false</code> otherwise
     */
    public static boolean writeList(final File listFile, final String out) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new PrintWriter(new FileWriter(listFile)));
            bw.write(out);
            bw.close();
            return true;
        } catch(final IOException e) {
            return false;
        } finally {
            if (bw!=null) try { bw.close(); } catch (final Exception e) {}
        }
    }

    /**
     * Write elements of an Array of Strings to a file (one element per line).
     *  
     * @param listFile the file to write to
     * @param list the Array to write
     * @return returns <code>true</code> if successful, <code>false</code> otherwise
     */
    public static boolean writeList(final File listFile, final String[] list){
        final StringBuffer out = new StringBuffer();
        for(int i=0;i < list.length; i++){
            out
            .append(list[i])
            .append(serverCore.CRLF_STRING);
        }
        return writeList(listFile, new String(out)); //(File, String)
    }

    // same as below
    public static String getListString(final String filename, final boolean withcomments) {        
        final File listFile = new File(listsPath ,filename);
        return getListString(listFile, withcomments);
    }

    /**
     * Read lines of a text file into a String, optionally ignoring comments.
     * 
     * @param listFile the File to read from.
     * @param withcomments If <code>false</code> ignore lines starting with '#'.
     * @return String representation of the file content.
     */
    public static String getListString(final File listFile, final boolean withcomments){
        final StringBuffer temp = new StringBuffer();
        
        BufferedReader br = null;        
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile)));
            temp.ensureCapacity((int) listFile.length());
            
            // Read the List
            String line = "";
            while ((line = br.readLine()) != null) {
                if ((!line.startsWith("#") || withcomments) || !line.equals("")) {
                    //temp += line + serverCore.CRLF_STRING;
                    temp.append(line)
                        .append(serverCore.CRLF_STRING);
                }
            }
            br.close();
        } catch (final IOException e) {
        } finally {
            if (br!=null) try { br.close(); } catch (final Exception e) {}
        }

        return new String(temp);
    }

    // get a Directory Listing as a String Array
    public static List<String> getDirListing(final String dirname){
        final File dir = new File(dirname);
        return getDirListing(dir);
    }
    
    /**
     * Read content of a directory into a String array of file names.
     * 
     * @param dir The directory to get the file listing from. If it doesn't exist yet,
     * it will be created.
     * @return array of file names
     */
    public static List<String> getDirListing(final File dir){
        List<String> ret = new LinkedList();
        File[] fileList;

        if (dir != null ) {
            if (!dir.exists()) {
                dir.mkdir();
            }
            fileList = dir.listFiles();
            for (int i=0; i<= fileList.length-1; i++) {
                ret.add(fileList[i].getName());
            }
            return ret;
        }
        return null;
    }    

    // same as below
    public static ArrayList<File> getDirsRecursive(final File dir, final String notdir){
        return getDirsRecursive(dir, notdir, true);
    }
    
    /**
     * Returns a List of all dirs and subdirs as File Objects
     *
     * Warning: untested
     */
    public static ArrayList<File> getDirsRecursive(final File dir, final String notdir, final boolean excludeDotfiles){
        final File[] dirList = dir.listFiles();
        final ArrayList<File> resultList = new ArrayList<File>();
        ArrayList<File> recursive;
        Iterator<File> iter;
        for (int i=0;i<dirList.length;i++) {
            if (dirList[i].isDirectory() && (!excludeDotfiles || !dirList[i].getName().startsWith(".")) && !dirList[i].getName().equals(notdir)) {
                resultList.add(dirList[i]);
                recursive = getDirsRecursive(dirList[i], notdir, excludeDotfiles);
                iter=recursive.iterator();
                while (iter.hasNext()) {
                    resultList.add(iter.next());
                }
            }
        }
        return resultList;
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
        final StringBuffer str = new StringBuffer();
        
        if (col != null && (col.size() > 0)) {
            final Iterator<String> it = col.iterator();
            str.append(it.next());
            while(it.hasNext()) {
                str.append(",").append(it.next());
            }
        }
        
        return str.toString();
    }

    /**
     * @see listManager#string2vector(String)
     */
    public static ArrayList<String> string2arraylist(final String string){
        ArrayList<String> l;

        if (string != null && string.length() > 0) {
            l = new ArrayList<String>(Arrays.asList(string.split(",")));
        } else {
            l = new ArrayList<String>();
        }

        return l;
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
            set = new HashSet<String>(Arrays.asList(string.split(",")));
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
            v = new Vector<String>(Arrays.asList(string.split(",")));
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
        final String supportedBlacklistTypesStr = indexAbstractReferenceBlacklist.BLACKLIST_TYPES_STRING;
        final String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(",");
        
        final ArrayList<blacklistFile> blacklistFiles = new ArrayList<blacklistFile>(supportedBlacklistTypes.length);
        for (int i=0; i < supportedBlacklistTypes.length; i++) {
            final blacklistFile blFile = new blacklistFile(
                    switchboard.getConfig(
                    supportedBlacklistTypes[i] + ".BlackLists", switchboard.getConfig("BlackLists.DefaultList", "url.default.black")),
                    supportedBlacklistTypes[i]);
            blacklistFiles.add(blFile);
        }
        
        plasmaSwitchboard.urlBlacklist.clear();
        plasmaSwitchboard.urlBlacklist.loadList(
                blacklistFiles.toArray(new blacklistFile[blacklistFiles.size()]),
                "/");

//       switchboard.urlBlacklist.clear();
//       if (f != "") switchboard.urlBlacklist.loadLists("black", f, "/");
    }
}
