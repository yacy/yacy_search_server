// listManager.java
// -------------------------------------
// part of YACY
// 
// (C) 2005, 2006 by Alexander Schier
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;

// The Naming of the functions is a bit strange...

public class listManager {
    public static plasmaSwitchboard switchboard;
    public static File listsPath;

//===============Listslists=====================
        // get an array of all Lists from a Config Property
        public static String[] getListslistArray(String Listname) {
            return switchboard.getConfig(Listname, "").split(",");
        }

        // removes a List from a Lists-List
        public static void removeListFromListslist(String ListName, String BlackList) {
            String[] Lists = getListslistArray(ListName);
            String temp = "";

            for (int i=0; i <= Lists.length -1; i++) {
                if (!Lists[i].equals(BlackList) && !Lists[i].equals("")) {
                    temp += Lists[i] + ",";
                }
            }
            if (temp.endsWith(",")) { //remove "," at end...
                temp = temp.substring(0, temp.length() -1);
            }
            if (temp.startsWith(",") ) { //remove "," at end...
                temp = temp.substring(1, temp.length());
            }

            switchboard.setConfig(ListName, temp);
        }

        // add a new List to a List-List
        public static void addListToListslist(String ListName, String newList) {
            String[] Lists = getListslistArray(ListName);
            String temp = "";

            for (int i = 0; i <= (Lists.length -1); i++) {
                temp += Lists[i] + ",";
            }
            temp += newList;
            switchboard.setConfig(ListName, temp);
        }

        // returns true, if the Lists-List contains the Listname
        public static boolean ListInListslist(String Listname, String BlackList) {
            String[] Lists =  getListslistArray(Listname);

            for (int u=0; u <= Lists.length -1; u++) {
                if (BlackList.equals(Lists[u])) {
                    return true;
                }
            }
            return false;
        }

//================generel Lists==================

    // Gets a Array of all lines(Items) of a (list)file
    public static ArrayList getListArray(File listFile){
        String line;
        ArrayList list = new ArrayList();
        int count = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile)));

            while((line = br.readLine()) != null){
                list.add(line);
                count++;
            }
            br.close();
        } catch(IOException e) {
            // list is empty
        } finally {
            if (br!=null) try { br.close(); } catch (Exception e) {}
        }
        return list;
    }

    // Writes the Liststring to a file
    public static boolean writeList(File listFile, String out) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new PrintWriter(new FileWriter(listFile)));
            bw.write(out);
            bw.close();
            return true;
        } catch(IOException e) {
            return false;
        } finally {
            if (bw!=null) try { bw.close(); } catch (Exception e) {}
        }
    }

    // overloaded function to write an array
    public static boolean writeList(File listFile, String[] list){
        StringBuffer out = new StringBuffer();
        for(int i=0;i < list.length; i++){
            out
            .append(list[i])
            .append(serverCore.crlfString);
        }
        return writeList(listFile, out.toString()); //(File, String)
    }

    public static String getListString(String filename, boolean withcomments){
        StringBuffer temp = new StringBuffer();
        
        BufferedReader br = null;        
        try{
            File listFile;
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile = new File(listsPath ,filename))));
            temp.ensureCapacity((int) listFile.length());
            
            // Read the List
            String line = "";
            while ((line = br.readLine()) != null) {
                if ((!line.startsWith("#") || withcomments) || !line.equals("")) {
                    //temp += line + serverCore.crlfString;
                    temp.append(line)
                        .append(serverCore.crlfString);
                }
            }
            br.close();
        } catch (IOException e) {
        } finally {
            if (br!=null) try { br.close(); } catch (Exception e) {}
        }

        return temp.toString();
    }

    // get a Directory Listing as a String Array
    public static String[] getDirListing(String dirname){
        String[] fileListString;
        File[] fileList;
        final File dir = new File(dirname);
        return getDirListing(dir);
    }
    
    public static String[] getDirListing(File dir){
        String[] fileListString;
        File[] fileList;

        if (dir != null ) {
            if (!dir.exists()) {
                dir.mkdir();
            }
            fileList = dir.listFiles();
            fileListString = new String[fileList.length];
            for (int i=0; i<= fileList.length-1; i++) {
                fileListString[i]=fileList[i].getName();
            }
            return fileListString;
        }
        return null;
    }    

    public static ArrayList getDirsRecursive(File dir, String notdir){
        return getDirsRecursive(dir, notdir, true);
    }
    /**
     * Returns a List of all dirs and subdirs as File Objects
     *
     * Warning: untested
     */
    public static ArrayList getDirsRecursive(File dir, String notdir, boolean excludeDotfiles){
        final File[] dirList = dir.listFiles();
        final ArrayList resultList = new ArrayList();
        ArrayList recursive;
        Iterator iter;
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
    public static String arraylist2string(ArrayList list){
        Iterator it=list.iterator();
        String ret="";
        if(it.hasNext()){
            ret=(String) it.next();
            while(it.hasNext()){
                ret+=","+(String)it.next();
            }
        }
        return ret;
    }
    public static ArrayList string2arraylist(String string){
        ArrayList ret=new ArrayList();
        String[] hashes=string.split(",");
        if(string.indexOf(",") > -1){
            for(int i=0;i<hashes.length;i++){
                ret.add(hashes[i]);
            }
        }else{
            ret = new ArrayList();
            if(!string.equals("")){
                ret.add(string);
            }
        }
        return ret;
    }
    public static String vector2string(Vector vector){
        Iterator it=vector.iterator();
        String ret="";
        if(it.hasNext()){
            ret=(String) it.next();
            while(it.hasNext()){
                ret+=","+(String)it.next();
            }
        }
        return ret;
    }

    public static Vector string2vector(String string){
        Vector ret=new Vector();
        String[] hashes=string.split(",");
        if(string.indexOf(",") > -1){
            for(int i=0;i<hashes.length;i++){
                ret.add(hashes[i]);
            }
        }else{
            ret = new Vector();
            if(!string.equals("")){
            	ret.add(string);
            }
        }
        return ret;
    }
    public static String hashset2string(HashSet hashset){
        StringBuffer ret=new StringBuffer();
        if(hashset!=null){
            Iterator it=hashset.iterator();
            if(it.hasNext()){
                ret.append((String)it.next());
                while(it.hasNext()){
                    ret.append(",").append((String)it.next());
                }
            }
        }
        return ret.toString();
    }
    public static HashSet string2hashset(String string){
        HashSet ret=new HashSet();
        String[] hashes=string.split(",");
        if(string.indexOf(",") > -1){
            for(int i=0;i<hashes.length;i++){
                ret.add(hashes[i]);
            }
        }else{
            ret = new HashSet();
            if(!string.equals("")){
                ret.add(string);
            }
        }
        return ret;
    }


//=============Blacklist specific================

    // load all active Blacklists in the Proxy
    public static void reloadBlacklists(){
        String supportedBlacklistTypesStr = switchboard.getConfig("BlackLists.types", "");
        String[] supportedBlacklistTypes = supportedBlacklistTypesStr.split(",");
        
        ArrayList blacklistFiles = new ArrayList(supportedBlacklistTypes.length);
        for (int i=0; i < supportedBlacklistTypes.length; i++) {
            String[] blacklistFile = new String[]{
                    supportedBlacklistTypes[i],
                    switchboard.getConfig(supportedBlacklistTypes[i] + ".BlackLists", "")
            };
            blacklistFiles.add(blacklistFile);
        }
        
        de.anomic.plasma.plasmaSwitchboard.urlBlacklist.clear();
        de.anomic.plasma.plasmaSwitchboard.urlBlacklist.loadList((String[][])blacklistFiles.toArray(new String[blacklistFiles.size()][]), "/");

//       switchboard.urlBlacklist.clear();
//       if (f != "") switchboard.urlBlacklist.loadLists("black", f, "/");
    }
}
