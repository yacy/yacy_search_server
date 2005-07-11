// Blacklist_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 02.08.2004
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
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.http.httpdProxyHandler;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class Blacklist_p {

	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	listManager.switchboard = (plasmaSwitchboard) env;

	listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig("listManager.listsPath", "DATA/LISTS"));

	serverObjects prop = new serverObjects();
	String line;
	String HTMLout = "";
	String out = "";
	String removeItem = "removeme";
	int numItems=0;
	int i=0;
	
	String filenames[] = listManager.getListslistArray("proxyBlackLists");
	String filename = "";
	

	if(post != null && post.containsKey("blackLists")){ //Blacklist selected
		filename = (String)post.get("blackLists");
	}else if(post != null && post.containsKey("filename")){
		filename = (String)post.get("filename");
	}else if(filenames.length > 0){ //first BlackList
		filename = filenames[0];
	}else{ //No BlackList
		//No file
		filename = ""; //?
		System.out.println("DEBUG: No Blacklist found");
	}
	prop.put("status", 0);//nothing


	//List Management

	//Del list
	if( post != null && post.containsKey("dellistbutton") ){

		File BlackListFile = new File(listManager.listsPath, filename);
		BlackListFile.delete();

		//Remove from all BlackLists Lists
		listManager.removeListFromListslist("proxyBlackLists", filename);
		listManager.removeListFromListslist("proxyBlackListsActive", filename);
		listManager.removeListFromListslist("proxyBlackListsShared", filename);
		
		//reload Blacklists
		listManager.reloadBlacklists();

		filenames = listManager.getListslistArray("proxyBlackLists");
		if(filenames.length > 0){
			filename = filenames[0];
		}
	}//del list

	if( post != null && post.containsKey("newlistbutton") ){

		String newList = (String)post.get("newlist");
		if( !newList.endsWith(".black") ){
			newList += ".black";
		}

		filename = newList; //to select it in the returnes Document
		try{
			File newFile = new File(listManager.listsPath, newList);
			newFile.createNewFile();
		
			listManager.addListToListslist("proxyBlackLists", newList);
			listManager.addListToListslist("proxyBlackListsActive", newList);
			listManager.addListToListslist("proxyBlackListsShared", newList);

		}catch(IOException e){}

	}//newlist

	if( post != null && post.containsKey("activatelistbutton") ){
			
		if( listManager.ListInListslist("proxyBlackListsActive", filename) ){ 
			listManager.removeListFromListslist("proxyBlackListsActive", filename);
		}else{ //inactive list -> enable
			listManager.addListToListslist("proxyBlackListsActive", filename);
		}

		listManager.reloadBlacklists();
	}
	
	if( post != null && post.containsKey("sharelistbutton") ){
			
		if( listManager.ListInListslist("proxyBlackListsShared", filename) ){ 
			//Remove from shared BlackLists
			listManager.removeListFromListslist("proxyBlackListsShared", filename);
		}else{ //inactive list -> enable
			listManager.addListToListslist("proxyBlackListsShared", filename);
		}
	}
	//List Management End



	Vector list = listManager.getListArray(new File(listManager.listsPath, filename));
	//remove a Item?
	if( post != null && post.containsKey("delbutton") && post.containsKey("Itemlist") && !((String)post.get("Itemlist")).equals("") ){
		removeItem = (String)post.get("Itemlist");
	}

	//Read the List
	Iterator it = list.iterator();
	while(it.hasNext()){
	    line = (String) it.next();

		if(! (line.startsWith("#") || line.equals("") || line.equals(removeItem)) ){ //Not the item to remove
			prop.put("Itemlist_"+numItems+"_item", line);
			numItems++;
		}

		if(! line.equals(removeItem) ){
			out += line + serverCore.crlfString; //full list
		}else{
			prop.put("status", 1);//removed
			prop.put("status_item", line);
			if (listManager.switchboard.blackListURLs != null)
			    listManager.switchboard.blackListURLs.remove(line);
		}
	}
	prop.put("Itemlist", numItems);

	//Add a new Item
	if( post != null && post.containsKey("addbutton") && !((String)post.get("newItem")).equals("") ){
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
		
		out += newItem+"\n";
		
		prop.put("Itemlist_"+numItems+"_item", newItem);
		numItems++;
		prop.put("Itemlist", numItems);
		
		prop.put("status", 2);//added
		prop.put("status_item", newItem);//added

		//add to blacklist
		if (listManager.switchboard.blackListURLs != null)
                    listManager.switchboard.blackListURLs.put(newItem.substring(0, pos), newItem.substring(pos + 1));
	}
	listManager.writeList(new File(listManager.listsPath, filename), out);

	//List known hosts for BlackList retrieval
	yacySeed seed;
	if( yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0 ){ //no nullpointer error
	    Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null);
            i=0;
	    while (e.hasMoreElements()) {
		seed = (yacySeed) e.nextElement();
                if (seed != null) {
                    String Hash = seed.hash;
                    String Name = seed.get("Name", "nameless");
					prop.put("otherHosts_"+i+"_hash", Hash);
					prop.put("otherHosts_"+i+"_name", Name);
					i++;
                }
	    }
		prop.put("otherHosts", i);
	}else{
		//DEBUG: System.out.println("BlackList_p: yacy seed not loaded!");
	}
	String BlackLists[] = listManager.getListslistArray("proxyBlackLists");
	
	//List BlackLists
	for(i=0; i <= BlackLists.length -1;i++){
		prop.put("blackLists_"+i+"_name", BlackLists[i]);
		prop.put("blackLists_"+i+"_active", 0);
		prop.put("blackLists_"+i+"_shared", 0);
		prop.put("blackLists_"+i+"_selected", 0);
		if( BlackLists[i].equals(filename) ){ //current List
			prop.put("blackLists_"+i+"_selected", 1);
		}
		if( listManager.ListInListslist("proxyBlackListsActive", BlackLists[i]) ){
			prop.put("blackLists_"+i+"_active", 1);
		}
		if( listManager.ListInListslist("proxyBlackListsShared", BlackLists[i]) ){
			prop.put("blackLists_"+i+"_shared", 1);
		}
	}
	prop.put("blackLists", i);

	prop.put("filename", filename);
	return prop;
    }

}
