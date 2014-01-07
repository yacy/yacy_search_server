// ConfigAppearance_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 29.12.2004
// extended by Michael Christen, 4.7.2008
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import com.google.common.io.Files;


public class ConfigAppearance_p {

    private final static String SKIN_FILENAME_FILTER = "^.*\\.css$";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        final String skinPath = new File(env.getDataPath(), env.getConfig("skinPath", SwitchboardConstants.SKINS_PATH_DEFAULT)).toString();

        // Fallback
        prop.put("currentskin", "");
        prop.put("status", "0"); // nothing

        List<String> skinFiles = FileUtils.getDirListing(skinPath, SKIN_FILENAME_FILTER);
        if (skinFiles == null) {
            return prop;
        }

        if (post != null) {
            String selectedSkin = post.get("skin");

            if (post.containsKey("use_button") && selectedSkin != null) {
                /* Only change skin if filename is contained in list of filesnames
                 * read from the skin directory. This is very important to prevent
                 * directory traversal attacks!
                 */
                if (skinFiles.contains(selectedSkin)) {
                    changeSkin(sb, skinPath, selectedSkin);
                }

            }

            if (post.containsKey("delete_button")) {

                /* Only delete file if filename is contained in list of filesname
                 * read from the skin directory. This is very important to prevent
                 * directory traversal attacks!
                 */
                if (skinFiles.contains(selectedSkin)) {
                    final File skinfile = new File(skinPath, selectedSkin);
                    FileUtils.deletedelete(skinfile);
                }
            }

            if (post.containsKey("install_button")) {
                // load skin from URL
                final String url = post.get("url");

                final Iterator<String> it;
                try {
                    final DigestURL u = new DigestURL(url);
                    it = FileUtils.strings(u.get(ClientIdentification.yacyInternetCrawlerAgent, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "")));
                } catch (final IOException e) {
                    prop.put("status", "1");// unable to get URL
                    prop.put("status_url", url);
                    return prop;
                }
                try {
                    final File skinFile = new File(skinPath, url.substring(url.lastIndexOf('/'), url.length()));
                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(skinFile)));

                    while (it.hasNext()) {
                        bw.write(it.next() + "\n");
                    }

                    bw.close();
                } catch (final IOException e) {
                    prop.put("status", "2");// error saving the skin
                    return prop;
                }
                if (post.containsKey("use_skin") && "on".equals(post.get("use_skin", ""))) {
                    changeSkin(sb, skinPath, url.substring(url.lastIndexOf('/'), url.length()));
                }
            }

            if (post.containsKey("set_colors")) {
                if (skinFiles.contains(selectedSkin)) {
                    changeSkin(sb, skinPath, selectedSkin);
                }
                for (final Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getKey().startsWith("color_")) {
                        env.setConfig(entry.getKey(), "#" + entry.getValue());
                    }
                }
            }
        }

        // reread skins
        skinFiles = FileUtils.getDirListing(skinPath, SKIN_FILENAME_FILTER);
        Collections.sort(skinFiles);
        int count = 0;
        for (final String skinFile : skinFiles) {
            if (skinFile.endsWith(".css")) {
                prop.put("skinlist_" + count + "_file", skinFile);
                prop.put("skinlist_" + count + "_name", skinFile.substring(0, skinFile.length() - 4));
                count++;
            }
        }
        prop.put("skinlist", count);
        prop.putHTML("currentskin", env.getConfig("currentSkin", "default"));

        // write colors from generic skin
        Iterator<String> i = env.configKeys();
        while (i.hasNext()) {
            final String key = i.next();
            if (key.startsWith("color_")) prop.put(key, env.getConfig(key, "#000000").substring(1));
        }
        return prop;
    }

    private static boolean changeSkin(final Switchboard sb, final String skinPath, final String skin) {
        final File htdocsDir = new File(sb.getDataPath("htDocsPath", "DATA/HTDOCS"), "env");
        final File styleFile = new File(htdocsDir, "style.css");
        final File skinFile = new File(skinPath, skin);

        styleFile.getParentFile().mkdirs();
        try {
            Files.copy(skinFile, styleFile);
            sb.setConfig("currentSkin", skin.substring(0, skin.length() - 4));
            return true;
        } catch (final IOException e) {
            return false;
        }
    }
}
