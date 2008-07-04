// ConfigSkins_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 29.12.2004
// extended by Michael Christen, 4.7.2008
//
// LICENSE
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.crawler.HTTPLoader;
import de.anomic.data.listManager;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyURL;

public class ConfigSkins_p {

	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        String skinPath = new File(env.getRootPath(), env.getConfig("skinPath", "DATA/SKINS")).toString();

        // Fallback
        prop.put("currentskin", "");
        prop.put("status", "0"); // nothing

        String[] skinFiles = listManager.getDirListing(skinPath);
        if (skinFiles == null) {
            return prop;
        }

        // if there are no skins, use the current style as default
        // normally only invoked at first start of YaCy
        if (skinFiles.length == 0) {
            try {
                serverFileUtils.copy(new File(env.getRootPath(), "htroot/env/style.css"), new File(skinPath, "default.css"));
                env.setConfig("currentSkin", "default");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (post != null) {
            if (post.containsKey("use_button") && post.get("skin") != null) {
                // change skin
                changeSkin(sb, skinPath, post.get("skin"));

            }
            if (post.containsKey("delete_button")) {
                // delete skin
                File skinfile = new File(skinPath, post.get("skin"));
                skinfile.delete();

            }
            if (post.containsKey("install_button")) {
                // load skin from URL
                String url = post.get("url");
                ArrayList<String> skinVector;
                try {
                    yacyURL u = new yacyURL(url, null);
                    httpHeader reqHeader = new httpHeader();
                    reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
                    skinVector = nxTools.strings(HttpClient.wget(u.toString(), reqHeader, 10000), "UTF-8");
                } catch (IOException e) {
                    prop.put("status", "1");// unable to get URL
                    prop.put("status_url", url);
                    return prop;
                }
                try {
                    Iterator<String> it = skinVector.iterator();
                    File skinFile = new File(skinPath, url.substring(url.lastIndexOf("/"), url.length()));
                    BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(skinFile)));

                    while (it.hasNext()) {
                        bw.write(it.next() + "\n");
                    }
                    bw.close();
                } catch (IOException e) {
                    prop.put("status", "2");// error saving the skin
                    return prop;
                }
                if (post.containsKey("use_skin") && (post.get("use_skin", "")).equals("on")) {
                    changeSkin(sb, skinPath, url.substring(url.lastIndexOf("/"), url.length()));
                }
            }
            if (post.containsKey("searchpage_button")) {
                sb.setConfig("promoteSearchPageGreeting", post.get("promoteSearchPageGreeting", ""));
                sb.setConfig("promoteSearchPageGreeting.homepage", post.get("promoteSearchPageGreeting.homepage", ""));
                sb.setConfig("promoteSearchPageGreeting.largeImage", post.get("promoteSearchPageGreeting.largeImage", ""));
                sb.setConfig("promoteSearchPageGreeting.smallImage", post.get("promoteSearchPageGreeting.smallImage", ""));
            }
            
        }

        // reread skins
        skinFiles = listManager.getDirListing(skinPath);
        int i;
        for (i = 0; i <= skinFiles.length - 1; i++) {
            if (skinFiles[i].endsWith(".css")) {
                prop.put("skinlist_" + i + "_file", skinFiles[i]);
                prop.put("skinlist_" + i + "_name", skinFiles[i].substring(0, skinFiles[i].length() - 4));
            }
        }
        prop.put("skinlist", i);

        prop.put("currentskin", env.getConfig("currentSkin", "default"));
        
        prop.put("promoteSearchPageGreeting", sb.getConfig("promoteSearchPageGreeting", ""));
        prop.put("promoteSearchPageGreeting.homepage", sb.getConfig("promoteSearchPageGreeting.homepage", ""));
        prop.put("promoteSearchPageGreeting.largeImage", sb.getConfig("promoteSearchPageGreeting.largeImage", ""));
        prop.put("promoteSearchPageGreeting.smallImage", sb.getConfig("promoteSearchPageGreeting.smallImage", ""));
        String myaddress = sb.webIndex.seedDB.mySeed().getPublicAddress();
        if (myaddress == null) myaddress = "localhost:" + sb.getConfig("port", "8080");
        prop.put("myaddress", myaddress);
        return prop;
    }

    private static boolean changeSkin(plasmaSwitchboard sb, String skinPath, String skin) {
        File htdocsDir = new File(sb.getConfigPath("htDocsPath", "DATA/HTDOCS"), "env");
        File styleFile = new File(htdocsDir, "style.css");
        File skinFile = new File(skinPath, skin);

        styleFile.getParentFile().mkdirs();
        try {
            serverFileUtils.copy(skinFile, styleFile);
            sb.setConfig("currentSkin", skin.substring(0, skin.length() - 4));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
