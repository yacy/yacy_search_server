// Steering.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
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

// You must compile this file with
// javac -classpath .:../Classes SettingsAck_p.java
// if the shell's current path is HTROOT

import java.io.File;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyRelease;

public class Steering {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch ss) {
        if (post == null || ss == null) { return new serverObjects(); }

        final Switchboard sb = (Switchboard) ss;
        final serverObjects prop = new serverObjects();
        prop.put("info", "0"); //no information submitted

        final String requestIP = post.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "127.0.0.1");
        
        // handle access rights
        if (!sb.verifyAuthentication(header, false)) {
            Log.logInfo("STEERING", "log-in attempt for steering from " + requestIP);
            prop.put("AUTHENTICATE", "admin log-in"); // force log-in
            return prop;
        }

        if (post.containsKey("shutdown")) {
            Log.logInfo("STEERING", "shutdown request from " + requestIP);
            sb.terminate(10, "shutdown request from Steering; ip = " + requestIP);
            prop.put("info", "3");
            
            return prop;
        }

        if (post.containsKey("restart")) {
            Log.logInfo("STEERING", "restart request from " + requestIP);
            yacyRelease.restart();
            prop.put("info", "4");
            
            return prop;
        }
        
        if (post.containsKey("update")) {
            Log.logInfo("STEERING", "update request from " + requestIP);
            final boolean devenvironment = new File(sb.getAppPath(), ".svn").exists();
            final String releaseFileName = post.get("releaseinstall", "");
            final File releaseFile = new File(sb.getDataPath(), "DATA/RELEASE/".replace("/", File.separator) + releaseFileName);
            if ((!devenvironment) && (releaseFileName.length() > 0) && (releaseFile.exists())) {
                yacyRelease.deployRelease(releaseFile);
            }
            prop.put("info", "5");
            prop.putHTML("info_release", releaseFileName);
            
            return prop;
        }
        return prop;
    }

}
