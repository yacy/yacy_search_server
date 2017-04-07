// IndexImportWarc_p.java
// -------------------------
// (c) 2017 by reger24; https://github.com/reger24
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

import java.io.File;
import java.io.FileNotFoundException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.WarcImporter;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexImportWarc_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (WarcImporter.job != null && WarcImporter.job.isAlive()) {
            // one import is running, no option to insert anything
            prop.put("import", 1);
            prop.put("import_thread", "running");
            prop.put("import_warcfile", WarcImporter.job.source());
            prop.put("import_count", WarcImporter.job.count());
            prop.put("import_speed", WarcImporter.job.speed());
            prop.put("import_runningHours", (WarcImporter.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (WarcImporter.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (WarcImporter.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (WarcImporter.job.remainingTime() / 60) % 60);
        } else {
            prop.put("import", 0);
            if (post != null) {
                if (post.containsKey("file")) {
                    String file = post.get("file");
                    final File sourcefile = new File(file);
                    if (sourcefile.exists()) {
                        try {
                            WarcImporter wi = new WarcImporter(sourcefile);
                            wi.start();
                            prop.put("import_thread", "started");
                        } catch (FileNotFoundException ex) {
                            prop.put("import_thread", "Error: file not found [" + file + "]");
                        }
                        prop.put("import_warcfile", file);
                    } else {
                        prop.put("import_warcfile", "");
                        prop.put("import_thread", "Error: file not found [" + file + "]");
                    }
                    prop.put("import", 1);
                    prop.put("import_count", 0);
                    prop.put("import_speed", 0);
                    prop.put("import_runningHours", 0);
                    prop.put("import_runningMinutes", 0);
                    prop.put("import_remainingHours", 0);
                    prop.put("import_remainingMinutes", 0);
                }
            }
        }
        return prop;
    }
}
