//ConfigLanguage_p.java
//-----------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004

//This File is contributed by Alexander Schier
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.Translator;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class ConfigLanguage_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final serverObjects prop = new serverObjects();
        Switchboard sb = (Switchboard) env;
        final File langPath = new File(sb.getAppPath("locale.source", "locales").getAbsolutePath());

        //Fallback
        //prop.put("currentlang", ""); //is done by Translationtemplate
        prop.put("status", "0");//nothing

        List<String> langFiles = Translator.langFiles(langPath);
        if (langFiles == null) {
            return prop;
        }

        if (post != null) {
            final String selectedLanguage = post.get("language");

            // store this call as api call
            ((Switchboard) env).tables.recordAPICall(post, "ConfigLanguage_p.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "language settings: " + selectedLanguage);

            //change language
            if (post.containsKey("use_button") && selectedLanguage != null){
                /* Only change language if filename is contained in list of filesnames
                 * read from the language directory. This is very important to prevent
                 * directory traversal attacks!
                 */
                if (langFiles.contains(selectedLanguage) || selectedLanguage.startsWith("default")) {
                    Translator.changeLang(env, langPath, selectedLanguage);
                }

                //delete language file
            } else if (post.containsKey("delete")) {

                /* Only delete file if filename is contained in list of filesnames
                 * read from the language directory. This is very important to prevent
                 * directory traversal attacks!
                 */
                if (langFiles.contains(selectedLanguage)) {
                    final File langfile= new File(langPath, selectedLanguage);
                    FileUtils.deletedelete(langfile);
                }

                //load language file from URL
            } else if (post.containsKey("url")){
                final String url = post.get("url");
                Iterator<String> it;
                try {
                    final DigestURL u = new DigestURL(url);
                    it = FileUtils.strings(u.get(ClientIdentification.yacyInternetCrawlerAgent, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "")));
                } catch(final IOException e) {
                    prop.put("status", "1");//unable to get url
                    prop.put("status_url", url);
                    return prop;
                }
                try {
                    final File langFile = new File(langPath, url.substring(url.lastIndexOf('/'), url.length()));
                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(langFile)));

                    while (it.hasNext()) {
                        bw.write(it.next() + "\n");
                    }
                    bw.close();
                } catch(final IOException e) {
                    prop.put("status", "2");//error saving the language file
                    return prop;
                }
                if (post.containsKey("use_lang") && "on".equals(post.get("use_lang"))) {
                    Translator.changeLang(env, langPath, url.substring(url.lastIndexOf('/'), url.length()));
                }
            }
        }

        //re-read language files
        langFiles = Translator.langFiles(langPath);
        Collections.sort(langFiles);
        final Map<String, String> langNames = Translator.langMap(env);

        //virtual entry
        prop.put("langlist_0_file", "default");
        prop.put("langlist_0_name", ((langNames.get("default") == null) ? "default" : langNames.get("default")));
        prop.put("langlist_0_selected", "selected=\"selected\"");

        int count = 0;
        for (final String langFile : langFiles) {
            //+1 because of the virtual entry "default" at top
            final String langKey = langFile.substring(0, langFile.length() -4);
            final String langName = langNames.get(langKey);
            prop.put("langlist_" + (count + 1) + "_file", langFile);
            prop.put("langlist_" + (count + 1) + "_name", ((langName == null) ? langKey : langName));

            if(env.getConfig("locale.language", "default").equals(langKey)) {
                prop.put("langlist_" + (count + 1) + "_selected", "selected=\"selected\"");
                prop.put("langlist_0_selected", " "); // reset Default
            } else {
                prop.put("langlist_" + (count + 1) + "_selected", " ");
            }
            count++;
        }
        prop.put("langlist", (count + 1));

        //is done by Translationtemplate
        //langName = (String) langNames.get(env.getConfig("locale.language", "default"));
        //prop.put("currentlang", ((langName == null) ? "default" : langName));
        return prop;
    }
}
