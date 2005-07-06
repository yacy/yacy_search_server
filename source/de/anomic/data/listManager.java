// listManager.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: 09.08.2004
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
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import de.anomic.http.httpdProxyHandler;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;

//The Naming of the functions is a bit strange...

public class listManager {
	public static plasmaSwitchboard switchboard;
	public static File listsPath;

//===============Listslists=====================
        //get an array of all Lists from a Config Property
        public static String[] getListslistArray(String Listname){
                return switchboard.getConfig(Listname, "").split(",");
        }

        //removes a List from a Lists-List
        public static void removeListFromListslist(String ListName, String BlackList){
                String Lists[] = getListslistArray(ListName);
                String temp = "";

                for(int i=0;i <= Lists.length -1;i++){
                        if( !Lists[i].equals(BlackList) && !Lists[i].equals("") ){
                                temp += Lists[i] + ",";
                        }
                }
                if( temp.endsWith(",") ){ //remove "," at end...
                        temp = temp.substring(0, temp.length() -1);
                }
                if( temp.startsWith(",") ){ //remove "," at end...
                        temp = temp.substring(1, temp.length() );
                }

                switchboard.setConfig(ListName, temp);
        }

        //add a new List to a List-List
        public static void addListToListslist(String ListName, String newList){
                String Lists[] = getListslistArray(ListName);
                String temp = "";

                for(int i = 0;i <= (Lists.length -1); i++){
                        temp += Lists[i] + ",";
                }
                temp += newList;
                switchboard.setConfig(ListName, temp);
        }

        //returns true, if the Lists-List contains the Listname
        public static boolean ListInListslist(String Listname, String BlackList){
                String Lists[] =  getListslistArray(Listname);

                for(int u=0;u <= Lists.length -1;u++){
                        if( BlackList.equals(Lists[u]) ){
                                return true;
                        }
                }
                return false;
        }

//================generel Lists==================

	//Gets a Array of all lines(Items) of a (list)file
	public static Vector getListArray(File listFile){
		String line;
		Vector list = new Vector();
		int count = 0;
        BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile)));

			while( (line = br.readLine()) != null){
				list.add(line);
				count++;
			}
			br.close();
		}catch(IOException e){
			//list is empty
		} finally {
            if (br!=null)try{br.close();}catch(Exception e) {}
        }
		return list; 
	}

	//Writes the Liststring to a file
	public static boolean writeList(File listFile, String out){
        BufferedWriter bw = null;
		try{
		    bw = new BufferedWriter(new PrintWriter(new FileWriter(listFile)));
			bw.write(out);
			bw.close();
			return true;
		}catch(IOException e){
			return false;
		} finally {
            if (bw!=null)try{bw.close();}catch(Exception e){}
        }
	}

	//overloaded function to write an array
	public static boolean writeList(File listFile, String[] list){
		String out = "";
		for(int i=0;i <= list.length; i++){
			out += list[i] + serverCore.crlfString;
		}
		return writeList(listFile, out); //(File, String)
	}

	public static String getListString(String filename, boolean withcomments){
		String temp = "";
		String line = "";
        BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(listsPath ,filename))));
			//Read the List
			while((line = br.readLine()) != null){
				if( (!line.startsWith("#") || withcomments) || (!line.equals("")) ){
					temp += line + serverCore.crlfString;
				}
			}
			br.close();
		} catch(IOException e){            
        } finally {
            if (br!=null)try{br.close();}catch(Exception e){}
        }
        
		return temp;
	}

	//get a Directory Listing as a String Array
	public static String[] getDirListing(String dirname){
		String[] fileListString;
		File[] fileList;
		File dir = new File(dirname);

		if(dir != null){
			if(!dir.exists()){
				dir.mkdir();
			}
			fileList = dir.listFiles();
			fileListString = new String[fileList.length];
			for(int i=0;i<= fileList.length-1;i++){
				fileListString[i]=fileList[i].getName();
			}
			return fileListString;
		}
		return null;
	}

	/**
	 * Returns a List of all dirs and subdirs as File Objects
	 *
	 * Warning: untested
	 */
	public static Vector getDirsRecursive(File dir){
		File[] dirList = dir.listFiles();
		Vector resultList = new Vector();
		Vector recursive;
		Iterator it;
		for(int i=0;i<dirList.length;i++){
			if(dirList[i].isDirectory()){
				resultList.add(dirList[i]);
				recursive = getDirsRecursive(dirList[i]);
				it=recursive.iterator();
				while(it.hasNext()){
					resultList.add(it.next());
				}
			}
		}
		return resultList;
	}


//=============Blacklist specific================
	
	//load all active Blacklists in the Proxy
	public static void reloadBlacklists(){
                String f = switchboard.getConfig("proxyBlackListsActive", "");
                if (f != ""){
			httpdProxyHandler.blackListURLs = httpdProxyHandler.loadBlacklist("black", f, "/");
		}else{
			httpdProxyHandler.blackListURLs = new TreeMap();
		}
        }


}
