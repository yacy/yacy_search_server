// IndexImportMediawiki.java
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.MediawikiImporter;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Import of MediaWiki dump files in the local index.
 */
public class IndexImportMediawiki_p {

	/**
	 * Run conditions :
	 * - no MediaWiki import thread is running : allow to start a new import by filling the "file" parameter
	 * - the MediaWiki import thread is running : returns monitoring information.
	 * @param header servlet request header
	 * @param post request parameters. Supported keys :
	 *            <ul>
	 *            <li>file : a dump file path on this YaCy server local file system</li>
	 *            </ul>
	 * @param env server environment
	 * @return the servlet answer object
	 */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (MediawikiImporter.job != null && MediawikiImporter.job.isAlive()) {
            // one import is running, no option to insert anything
            prop.put("import", 1);
            prop.put("import_thread", "running");
            prop.put("import_dump", MediawikiImporter.job.source());
            prop.put("import_count", MediawikiImporter.job.count());
            prop.put("import_speed", MediawikiImporter.job.speed());
            prop.put("import_runningHours", (MediawikiImporter.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (MediawikiImporter.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (MediawikiImporter.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (MediawikiImporter.job.remainingTime() / 60) % 60);
        } else {
            prop.put("import", 0);
            if (post == null) {
                prop.put("import_status", 0);
            } else {
                if (post.containsKey("file")) {
                    String file = post.get("file");
                    if (file.startsWith("file://")) file = file.substring(7);
                    if (file.startsWith("http")) {
                    	prop.put("import_status", 1);
                    } else {
                        final File sourcefile = new File(file);
                        if (!sourcefile.exists()) {
                        	prop.put("import_status", 2);
                            prop.put("import_status_sourceFile", sourcefile.getAbsolutePath());
                        } else if(!sourcefile.canRead()) {
                        	prop.put("import_status", 3);
                            prop.put("import_status_sourceFile", sourcefile.getAbsolutePath());
                        } else if(sourcefile.isDirectory()) {
                        	prop.put("import_status", 4);
                        	prop.put("import_status_sourceFile", sourcefile.getAbsolutePath());
                        } else {
                            MediawikiImporter.job = new MediawikiImporter(sourcefile, sb.surrogatesInPath);
                            MediawikiImporter.job.start();
                            prop.put("import_dump", MediawikiImporter.job.source());
                            prop.put("import_thread", "started");
                            prop.put("import", 1);
                        }
                    }
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
