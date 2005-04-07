// sharedBlacklist_p.java 
// -----------------------
// part of the AnomicHTTPProxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 04.07.2004
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

// you must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.util.*;
import java.io.*;
import java.net.*;
import de.anomic.tools.*;
import de.anomic.server.*;
import de.anomic.yacy.*;
import de.anomic.net.*;
import de.anomic.http.*;
import de.anomic.plasma.*;

public class sharedBlacklist_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	File listsPath = new File(switchboard.getRootPath(), env.getConfig("listsPath", "DATA/LISTS"));
	String filename = "";
 	String line = "";
	String out = "";
	String HTMLout = "";
	HashSet Blacklist = new HashSet();
	Vector otherBlacklist = new Vector();
	String status = "";
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
	}
	try{
		//Read the List
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(listsPath, filename))));
		while((line = br.readLine()) != null){
			if(! (line.startsWith("#") || line.equals("")) ){
				Blacklist.add(line);
				out += line + serverCore.crlfString;
			}
		}
		br.close();
	}catch(IOException e){}

	if( post != null && post.containsKey("hash") ){ //Step 1: retrieve the Items
		Hash = (String) post.get("hash");
		status = "Proxy \"" + Name + "\" not found"; //will later be resetted
		
		yacySeed seed;
		if( yacyCore.seedDB != null ){ //no nullpointer error..
		    Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null);
		    while (e.hasMoreElements()) {
			seed = (yacySeed) e.nextElement();
			if (seed != null && seed.hash.equals(Hash) ) {
			    IP = seed.get("IP", "127.0.0.1"); 
			    Port = seed.get("Port", "8080");
			    Name = (String) seed.get("Name", "<" + IP + ":" + Port + ">");
			    status = "";
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
                    otherBlacklist = httpc.wget(new URL(address), 6000, null, null, switchboard.remoteProxyHost, switchboard.remoteProxyPort); //get List
                } catch (Exception e) {}
                
		//Make HTML-Optionlist with retrieved items
		for(i = 0; i <= (otherBlacklist.size() -1); i++){
			String tmp = (String) otherBlacklist.get(i);
			if( !Blacklist.contains(tmp) && (!tmp.equals("")) ){
				//newBlacklist.add(tmp);
				count++;
				HTMLout += "<tr><td>" + Name + "</td><td>" + tmp + "</td><td><input type=\"checkbox\" name=\""+ count +"\" value=\"" + tmp + "\"></td></tr>\n";
			}
		}
		//write the list
		try{
			BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(listsPath, filename))));
			bw.write(out);
			bw.close();
		}catch(IOException e){}

	}else if( post != null && post.containsKey("url") ){
		//load from URL
		address = (String)post.get("url");
		status = "URL \"" + address + "\" not found or empty List"; //will later be resetted
		//Name = "&nbsp;"; //No Name
		Name = address;
                
                try {
                    otherBlacklist = httpc.wget(new URL(address), 6000, null, null, switchboard.remoteProxyHost, switchboard.remoteProxyPort); //get List
                } catch (Exception e) {}
		status = ""; //TODO: check if the wget failed...

                //Make HTML-Optionlist with retrieved items
                for(i = 0; i <= (otherBlacklist.size() -1); i++){
                        String tmp = (String) otherBlacklist.get(i);
                        if( !Blacklist.contains(tmp) && (!tmp.equals("")) && (!tmp.startsWith("#")) ){ //This List may contain comments.
                                //newBlacklist.add(tmp);
                                count++;
                                HTMLout += "<tr><td>" + Name + "</td><td>" + tmp + "</td><td><input type=\"checkbox\" name=\""+ count +"\" value=\"" + tmp + "\"></td></tr>\n";
                        }
                }

	}else if( post != null && post.containsKey("file") ){

		try{
			//Read the List
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream( (String)post.get("file") )));
			while((line = br.readLine()) != null){
				if(! (line.startsWith("#") || line.equals("")) ){
					otherBlacklist.add(line);
				}
			}
			br.close();
		}catch(IOException e){
			status = "File Error! Wrong Path?";
		}
		status = "";
		Name = (String)post.get("file");

                //Make HTML-Optionlist with retrieved items
                for(i = 0; i <= (otherBlacklist.size() -1); i++){
                        String tmp = (String) otherBlacklist.get(i);
                        if( !Blacklist.contains(tmp) && (!tmp.equals("")) && (!tmp.startsWith("#")) ){ //This List may contain comments.
                                //newBlacklist.add(tmp);
                                count++;
                                HTMLout += "<tr><td>" + Name + "</td><td>" + tmp + "</td><td><input type=\"checkbox\" name=\""+ count +"\" value=\"" + tmp + "\"></td></tr>\n";
                        }
                }

	}else if( post != null && post.containsKey("add") ){ //Step 2: Add the Items
		num = Integer.parseInt( (String)post.get("num") );
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
				status += "<b>"+newItem+"</b> was added to the Blacklist<br>\n";
				if (httpdProxyHandler.blackListURLs != null)
				    httpdProxyHandler.blackListURLs.put(newItem.substring(0, pos), newItem.substring(pos + 1));

				//write the list
				try{
					BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(listsPath, filename))));
					bw.write(out);
                	                bw.close();
				}catch(IOException e){}

			}else{
			}
		}
	}else{
		status = "Wrong Invocation! Please invoke with sharedBlacklist.html?name=PeerName";
	}
	
	prop.put("filename", filename);
	prop.put("status", status);
	prop.put("table",HTMLout);
	prop.put("name", Name);
	prop.put("num", String.valueOf(count));
	return prop;
    }

}
