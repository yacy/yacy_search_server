// IndexImportOAIPMH.java
// -------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 04.05.2009 on http://yacy.net
// Frankfurt, Germany
//
// $LastChangedDate: 2009-10-11 23:29:18 +0200 (So, 11 Okt 2009) $
// $LastChangedRevision: 6400 $
// $LastChangedBy: orbiter $
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

import java.net.MalformedURLException;

import net.yacy.document.importer.OAIPMHImporter;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexImportOAIPMH_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (OAIPMHImporter.job != null && OAIPMHImporter.job.isAlive()) {
            // one import is running, no option to insert anything
            prop.put("import", 1);
            prop.put("import_thread", "running");
            prop.put("import_source", OAIPMHImporter.job.source());
            prop.put("import_count", OAIPMHImporter.job.count());
            prop.put("import_speed", OAIPMHImporter.job.speed());
            prop.put("import_runningHours", (OAIPMHImporter.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (OAIPMHImporter.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (OAIPMHImporter.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (OAIPMHImporter.job.remainingTime() / 60) % 60);
        } else {
            prop.put("import", 0);
            if (post == null) {
                prop.put("import_status", 0);
            } else {
                if (post.containsKey("oaipmhurl")) {
                    String oaipmhurl = post.get("oaipmhurl");
                    DigestURI url = null;
                    try {
                        url = new DigestURI(oaipmhurl, null);
                        OAIPMHImporter.job = new OAIPMHImporter(sb.loader, url);
                        OAIPMHImporter.job.start();
                        prop.put("import", 0);
                        prop.put("import_thread", "started");
                        prop.put("import_source", OAIPMHImporter.job.source());
                        prop.put("import_count", 0);
                        prop.put("import_speed", 0);
                        prop.put("import_runningHours", 0);
                        prop.put("import_runningMinutes", 0);
                        prop.put("import_remainingHours", 0);
                        prop.put("import_remainingMinutes", 0);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        prop.put("import", 0);
                        prop.put("import_status", 1);
                        prop.put("import_status_message", e.getMessage());
                    }
                }
                return prop;
            }
        }
        return prop;
    }
}
