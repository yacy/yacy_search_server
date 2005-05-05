// Skins_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 29.12.2004
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;

import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Skins_p {

	private static boolean copyFile(File from, File to){
			if(from == null || to == null){
			return false;
		}
		try{
			serverFileUtils.copy(from, to);
			return true;
		}catch(IOException e){
			return false;
		}
	}

	private static boolean changeSkin(serverSwitch env, String skinPath, String skin){
		File styleFile = new File(env.getRootPath(), "htroot/env/style.css");
		File skinFile  = new File(skinPath, skin);

		if(copyFile(skinFile, styleFile)){
			env.setConfig("currentSkin", skin.substring(0,skin.length()-4));
			return true;
		}
		return false;
	}

	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	//listManager.switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	String skinPath = new File(env.getRootPath(), env.getConfig("skinPath", "DATA/SKINS")).toString();

	//Fallback
	prop.put("currentskin", "");
	prop.put("status", 0);//nothing
	
	String[] skinFiles = listManager.getDirListing(skinPath);
	if(skinFiles == null){
		return prop;
	}
	
	//if there are no skins, use the current style as default
	//normally only invoked at first start of YACY
	if(skinFiles.length == 0){
		copyFile(new File(env.getRootPath(), "htroot/env/style.css"), new File(skinPath, "default.css"));
		env.setConfig("currentSkin", "default");
	}
	
	if (post != null){
		//change skin
		if(post.containsKey("use_button")){
			changeSkin(env, skinPath, (String)post.get("skin"));
			
		//delete skin
		}else if(post.containsKey("delete")){
			File skinfile= new File(skinPath, (String)post.get("skin"));
			skinfile.delete();

		//load Skin from URL
		} else if (post.containsKey("url")){
			String url = (String)post.get("url");
			Vector skinVector;
			try{
				skinVector = httpc.wget(new URL(url), 6000, null, null, switchboard.remoteProxyHost, switchboard.remoteProxyPort);
			}catch(IOException e){
				prop.put("status", 1);//unable to get url
				prop.put("status_url", url);
				return prop;
			}
			try{
				Iterator it = skinVector.iterator();
				File skinFile = new File(skinPath, url.substring(url.lastIndexOf("/"), url.length()));
				BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(skinFile)));

				while(it.hasNext()){
					bw.write(it.next() + "\n");
				}
				bw.close();
			}catch(IOException e){
				prop.put("status", 2);//error saving the skin
				return prop;
			}
			if(post.containsKey("use_skin") && ((String)post.get("use_skin")).equals("on")){
				changeSkin(env, skinPath, url.substring(url.lastIndexOf("/"), url.length()));
			}
		}
	}

	
	//reread Skins
	skinFiles = listManager.getDirListing(skinPath);
	int i;
	for(i=0;i<= skinFiles.length-1;i++){
		if(skinFiles[i].endsWith(".css")){
			prop.put("skinlist_"+i+"_file", skinFiles[i]);
			prop.put("skinlist_"+i+"_name", skinFiles[i].substring(0, skinFiles[i].length() -4));
		}
	}
	prop.put("skinlist", i);

	prop.put("currentskin", env.getConfig("currentSkin", "default"));
	return prop;
    }

}
