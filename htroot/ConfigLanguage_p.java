//Language_p.java 
//-----------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

//This File is contributed by Alexander Schier
//last change: 25.05.2005

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../Classes Blacklist_p.java
//if the shell's current path is HTROOT

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.crawler.HTTPLoader;
import de.anomic.data.listManager;
import de.anomic.data.translator;
import de.anomic.http.HttpClient;
import de.anomic.http.httpRequestHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyURL;


public class ConfigLanguage_p {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        //listManager.switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        final String langPath = env.getConfigPath("locale.work", "DATA/LOCALE/locales").getAbsolutePath();

        //Fallback
        //prop.put("currentlang", ""); //is done by Translationtemplate
        prop.put("status", "0");//nothing

        String[] langFiles = listManager.getDirListing(langPath);
        if(langFiles == null){
            return prop;
        }

        if (post != null){
            //change language
            if(post.containsKey("use_button") && post.get("language") != null){
                translator.changeLang(env, langPath, post.get("language"));

                //delete language file
            }else if(post.containsKey("delete")){
                final File langfile= new File(langPath, post.get("language"));
                langfile.delete();

                //load language file from URL
            } else if (post.containsKey("url")){
                final String url = post.get("url");
                ArrayList<String> langVector;
                try{
                    final yacyURL u = new yacyURL(url, null);
                    final httpRequestHeader reqHeader = new httpRequestHeader();
                    reqHeader.put(httpRequestHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
                    langVector = nxTools.strings(HttpClient.wget(u.toString(), reqHeader, 10000), "UTF-8");
                }catch(final IOException e){
                    prop.put("status", "1");//unable to get url
                    prop.put("status_url", url);
                    return prop;
                }
                try{
                    final Iterator<String> it = langVector.iterator();
                    final File langFile = new File(langPath, url.substring(url.lastIndexOf("/"), url.length()));
                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(langFile)));

                    while (it.hasNext()) {
                        bw.write(it.next() + "\n");
                    }
                    bw.close();
                }catch(final IOException e){
                    prop.put("status", "2");//error saving the language file
                    return prop;
                }
                if(post.containsKey("use_lang") && (post.get("use_lang")).equals("on")){
                    translator.changeLang(env, langPath, url.substring(url.lastIndexOf("/"), url.length()));
                }
            }
        }

        //reread language files
        langFiles = listManager.getDirListing(langPath);
        int i;
        final HashMap<String, String> langNames = translator.langMap(env);
        String langKey, langName;

        //virtual entry
        prop.put("langlist_0_file", "default");
        prop.put("langlist_0_name", ((langNames.get("default") == null) ? "default" : (String) langNames.get("default")));
        prop.put("langlist_0_selected", "selected=\"selected\"");

        for(i=0;i<= langFiles.length-1 ;i++){
            if(langFiles[i].endsWith(".lng")){
                //+1 because of the virtual entry "default" at top
                langKey = langFiles[i].substring(0, langFiles[i].length() -4);
                langName = langNames.get(langKey);
                prop.put("langlist_"+(i+1)+"_file", langFiles[i]);
                prop.put("langlist_"+(i+1)+"_name", ((langName == null) ? langKey : langName));
                if(env.getConfig("locale.language", "default").equals(langKey)) {
                    prop.put("langlist_"+(i+1)+"_selected", "selected=\"selected\"");
                    prop.put("langlist_0_selected", " "); // reset Default
                } else {
                    prop.put("langlist_"+(i+1)+"_selected", " ");
                }
            }
        }
        prop.put("langlist", (i+1));

        //is done by Translationtemplate
        //langName = (String) langNames.get(env.getConfig("locale.language", "default"));
        //prop.put("currentlang", ((langName == null) ? "default" : langName));
        return prop;
    }
}
