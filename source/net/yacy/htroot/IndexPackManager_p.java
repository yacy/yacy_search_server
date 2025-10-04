// IndexPackManager_p.java
// -----------------------
// (C) 2025 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.htroot;

import java.io.File;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexPackManager_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {
            final String mvfrom = post.get("mvfrom", "");
            final String mvto = post.get("mvto", "");
            final String delete = post.get("delete", "0");
            final String file = post.get("file", "");

            if (!mvfrom.isEmpty() && !mvto.isEmpty() && !file.isEmpty()) {
                // move as requested
                if ("loaded".equals(mvfrom) && "hold".equals(mvto)) {
                    new File(sb.packsLoadedPath, file).renameTo(new File(sb.packsHoldPath, file));
                }
                if ("hold".equals(mvfrom) && "load".equals(mvto)) {
                    new File(sb.packsHoldPath, file).renameTo(new File(sb.packsLoadPath, file));
                }
            }
            if (delete.equals("1") && !file.isEmpty()) {
                // delete as requested, files to delete can only be in hold
                new File(sb.packsHoldPath, file).delete();
            }
        }

        // set default values
        prop.put("reload", 0);


        // show Pack folder contents
        int i = 0; boolean dark = true;
        for (final String file: sb.packsInHold()) {
            prop.put("packs-hold_" + i + "_file", file);
            prop.put("packs-hold_" + i + "_size", new File(sb.packsHoldPath, file).length() / 1024);
            prop.put("packs-hold_" + i + "_process",
                "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?mvfrom=hold&mvto=load&file=" + file + "\" role=\"button\">load</a>&nbsp;" +
                "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?delete=1&file=" + file + "\" role=\"button\">delete</a>"
            );
            prop.put("packs_hold" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-hold_0_file", "&lt;no packs in hold&gt;");
            prop.put("packs-hold_0_size", "");
            prop.put("packs-hold_" + i + "_process", "");
            prop.put("packs-hold_0_dark", true);
            i = 1;
        }
        prop.put("packs-hold", i);
        i = 0; dark = true;
        for (final String file: sb.packsInLoad()) {
            prop.put("packs-load_" + i + "_file", file);
            prop.put("packs-load_" + i + "_size", new File(sb.packsLoadPath, file).length() / 1024);
            prop.put("packs-load_" + i + "_process", "");
            prop.put("packs-load_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-load_0_file", "&lt;no packs in load&gt;");
            prop.put("packs-load_0_size", "");
            prop.put("packs-load_0_process", "");
            prop.put("packs-load_0_dark", true);
            i = 1;
        } else {
            // while packs are loaded, we reload the interface to refresh the pack list constantly
            prop.put("reload", 1);
        }
        prop.put("packs-load", i);
        i = 0; dark = true;
        for (final String file: sb.packsInLoaded()) {
            prop.put("packs-loaded_" + i + "_file", file);
            prop.put("packs-loaded_" + i + "_size", new File(sb.packsLoadedPath, file).length() / 1024);
            prop.put("packs-loaded_" + i + "_process", "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?mvfrom=loaded&mvto=hold&file=" + file + "\" role=\"button\">to: hold</a>");
            prop.put("packs-loaded_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-loaded_0_file", "&lt;no packs in loaded&gt;");
            prop.put("packs-loaded_0_size", "");
            prop.put("packs-loaded_0_process", "");
            prop.put("packs-loaded_0_dark", true);
            i = 1;
        }
        prop.put("packs-loaded", i);

        // return rewrite properties
        return prop;
    }

}