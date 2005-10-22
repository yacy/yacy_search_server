// sharedBlacklist_p.java 
// -----------------------
// part of the AnomicHTTPProxy
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
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class sharedBlacklist_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	File listsPath = new File(switchboard.getRootPath(), env.getConfig("listsPath", "DATA/LISTS"));
	String filename = "";
 	String line = "";
	String out = "";
	HashSet Blacklist = new HashSet();
	ArrayList otherBlacklist = new ArrayList();
	int num = 0;
	int i = 0; //loop-var
	int count = 0;
	String IP = "127.0.0.1"; //should be replaced later
	String Port = "8080"; //aua!
	String Name = "";
	String Hash = "";
	String address = "";

	if( post != null && post.containsKey("filename") ){
		filename = (String)post.get("filename");
	}else{
		filename = "shared.black";
	}
    
    BufferedReader br = null;
	try{
		//Read the List 
		br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(listsPath, filename))));
		while((line = br.readLine()) != null){
			if(! (line.startsWith("#") || line.equals("")) ){
				Blacklist.add(line);
				out += line + serverCore.crlfString;
			}
		}
		br.close();
	}catch(IOException e){}
    finally {
        if (br!=null) try{br.close(); br=null;}catch(Exception e){}
    }

	prop.put("page", 0); //checkbox list
	if( post != null && post.containsKey("hash") ){ //Step 1: retrieve the Items
		Hash = (String) post.get("hash");
		prop.put("status", 3);//YaCy-Peer not found
		prop.put("status_name", Name);
		
		yacySeed seed;
		if( yacyCore.seedDB != null ){ //no nullpointer error..
		    Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null);
		    while (e.hasMoreElements()) {
			seed = (yacySeed) e.nextElement();
			if (seed != null && seed.hash.equals(Hash) ) {
			    IP = seed.get(yacySeed.IP, "127.0.0.1"); 
			    Port = seed.get(yacySeed.PORT, "8080");
			    Name = (String) seed.get(yacySeed.NAME, "<" + IP + ":" + Port + ">");
				prop.put("status", 0);//nothing
			}else{
			    //status = "No Seed found"; //wrong? The Name not known?
			}
		    }
		}
		//DEBUG
		//IP = "217.234.127.107";
		//Port = "8080";
		//Name = "RootServer";

		//Make Adresse
		address = "http://" + IP + ":" + Port + "/yacy/list.html?col=black";
                try {
                    otherBlacklist = httpc.wget(new URL(address), 6000, null, null, switchboard.remoteProxyConfig); //get List
                } catch (Exception e) {}
                
		//Make HTML-Optionlist with retrieved items
		for(i = 0; i <= (otherBlacklist.size() -1); i++){
			String tmp = (String) otherBlacklist.get(i);
			if( !Blacklist.contains(tmp) && (!tmp.equals("")) ){
				//newBlacklist.add(tmp);
				count++;
				prop.put("page_urllist_"+(count-1)+"_name", Name);
				prop.put("page_urllist_"+(count-1)+"_url", tmp);
				prop.put("page_urllist_"+(count-1)+"_count", count);
			}
		}
		prop.put("page_urllist", (count));
		//write the list
		try{
			BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(listsPath, filename))));
			bw.write(out);
			bw.close();
		}catch(IOException e){}

	}else if( post != null && post.containsKey("url") ){
		//load from URL
		address = (String)post.get("url");
		prop.put("status", 4);
		prop.put("status_address", address);
		//Name = "&nbsp;"; //No Name
		Name = address;
                
                try {
                    otherBlacklist = httpc.wget(new URL(address), 6000, null, null, switchboard.remoteProxyConfig); //get List
                } catch (Exception e) {}
		prop.put("status", 0); //TODO: check if the wget failed...

                //Make HTML-Optionlist with retrieved items
                for(i = 0; i <= (otherBlacklist.size() -1); i++){
                        String tmp = (String) otherBlacklist.get(i);
                        if( !Blacklist.contains(tmp) && (!tmp.equals("")) && (!tmp.startsWith("#")) ){ //This List may contain comments.
                                //newBlacklist.add(tmp);
                                count++;
								prop.put("page_urllist_"+(count-1)+"_name", Name);
								prop.put("page_urllist_"+(count-1)+"_url", tmp);
								prop.put("page_urllist_"+(count-1)+"_count", count);
                        }
                }
				prop.put("page_urllist", (count));

	}else if( post != null && post.containsKey("file") ){

		try{
			//Read the List
			br = new BufferedReader(new InputStreamReader(new FileInputStream( (String)post.get("file") )));
			while((line = br.readLine()) != null){
				if(! (line.startsWith("#") || line.equals("")) ){
					otherBlacklist.add(line);
				}
			}
			br.close();
		}catch(IOException e){
			prop.put("status", 2); //File Error
		} finally {
            if (br != null) try {br.close(); br = null; } catch (Exception e){}
        }
		Name = (String)post.get("file");

                //Make HTML-Optionlist with retrieved items
                for(i = 0; i <= (otherBlacklist.size() -1); i++){
                        String tmp = (String) otherBlacklist.get(i);
                        if( !Blacklist.contains(tmp) && (!tmp.equals("")) && (!tmp.startsWith("#")) ){ //This List may contain comments.
                                //newBlacklist.add(tmp);
                                count++;
								prop.put("page_urllist_"+(count-1)+"_name", Name);
								prop.put("page_urllist_"+(count-1)+"_url", tmp);
								prop.put("page_urllist_"+(count-1)+"_count", count);
                        }
                }
				prop.put("page_urllist", (count));

	}else if( post != null && post.containsKey("add") ){ //Step 2: Add the Items
		prop.put("page", 1); //result page
		num = Integer.parseInt( (String)post.get("num") );
		prop.put("status", 1); //list of added Entries
		count = 0;//couter of added entries
		for(i=1;i <= num; i++){ //count/num starts with 1!
			if( post.containsKey( String.valueOf(i) ) ){
				String newItem = (String)post.get( String.valueOf(i) );
				//This should not be needed...
				if ( newItem.startsWith("http://") ){
					newItem = newItem.substring(7);
				}
				// separate the newItem into host and path
				int pos = newItem.indexOf("/");
				if (pos < 0) {
				    // add default empty path pattern
				    pos = newItem.length();
				    newItem = newItem + "/.*";
				}
				out += newItem+"\n";
				prop.put("status_list_"+count+"_entry", newItem);
				count++;
				if (switchboard.urlBlacklist != null)
				    switchboard.urlBlacklist.add(newItem.substring(0, pos), newItem.substring(pos + 1));

				//write the list
				try{
					BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(listsPath, filename))));
					bw.write(out);
                	                bw.close();
				}catch(IOException e){}

			}else{
			}
		}
		prop.put("status_list", count);
	}else{
		prop.put("status", 5);//Wrong Invokation
	}
	
	prop.put("filename", filename);
	prop.put("page_name", Name);
	prop.put("num", String.valueOf(count));
	return prop;
    }

}
