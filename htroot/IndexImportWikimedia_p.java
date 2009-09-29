// IndexImportWikimedia.java
// -------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 04.05.2009 on http://yacy.net
// Frankfurt, Germany
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

import java.io.File;
import java.net.MalformedURLException;

import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.mediawikiIndex;

public class IndexImportWikimedia_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (mediawikiIndex.job != null && mediawikiIndex.job.isAlive()) {
            // one import is running, no option to insert anything
            prop.put("import", 1);
            prop.put("import_thread", "running");
            prop.put("import_dump", mediawikiIndex.job.sourcefile.getName());
            prop.put("import_count", mediawikiIndex.job.count);
            prop.put("import_speed", mediawikiIndex.job.speed());
            prop.put("import_runningHours", (mediawikiIndex.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (mediawikiIndex.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (mediawikiIndex.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (mediawikiIndex.job.remainingTime() / 60) % 60);
        } else {
            prop.put("import", 0);
            if (post == null) {
                prop.put("import_status", 0);
            } else {
                if (post.containsKey("file")) {
                    File sourcefile = new File(post.get("file"));
                    String name = sourcefile.getName(); // i.e. dewiki-20090311-pages-articles.xml.bz2
                    if (!name.endsWith("pages-articles.xml.bz2")) {
                        prop.put("import", 0);
                        prop.put("import_status", 1);
                        prop.put("import_status_message", "file name must end with 'pages-articles.xml.bz2'");
                        return prop;
                    }
                    String lang = name.substring(0, 2);
                    try {
                        mediawikiIndex.job = new mediawikiIndex(sourcefile, sb.surrogatesInPath, "http://" + lang + ".wikipedia.org/wiki/");
                        mediawikiIndex.job.start();
                        prop.put("import", 1);
                        prop.put("import_thread", "started");
                        prop.put("import_dump", mediawikiIndex.job.sourcefile.getName());
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
