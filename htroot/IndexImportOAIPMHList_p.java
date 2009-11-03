// IndexImportOAIPMHList.java
// -------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 03.11.2009 on http://yacy.net
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

import java.util.ArrayList;
import java.util.Set;

import net.yacy.document.importer.OAIPMHImporter;

import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexImportOAIPMHList_p {

    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        prop.put("refresh", 0);
        prop.put("import", 0);
        prop.put("source", 0);
        
        if (post != null && post.containsKey("source")) {
            Set<String> oaiRoots = OAIPMHImporter.getOAIServer(sb.loader);
            
            boolean dark = false;
            int cnt = 0;
            for (String root: oaiRoots) {
                prop.put("source_table_" + cnt + "_dark", (dark) ? "1" : "0");
                prop.put("source_table_" + cnt + "_source", root);
                dark = !dark;
                cnt++;
            }
            prop.put("source_table", cnt);
            prop.put("source", 1);
        }
        
        if (post != null && post.containsKey("import")) {
            ArrayList<OAIPMHImporter> jobs = new ArrayList<OAIPMHImporter>();
            for (OAIPMHImporter job: OAIPMHImporter.runningJobs) jobs.add(job);
            for (OAIPMHImporter job: OAIPMHImporter.startedJobs) jobs.add(job);
            for (OAIPMHImporter job: OAIPMHImporter.finishedJobs) jobs.add(job);
            
            boolean dark = false;
            int cnt = 0;
            for (OAIPMHImporter job: jobs) {
                prop.put("import_table_" + cnt + "_dark", (dark) ? "1" : "0");
                prop.put("import_table_" + cnt + "_thread", (job.isAlive()) ? "<img src=\"/env/grafics/crawl.gif\" alt=\"running\" />" : "finished");
                prop.put("import_table_" + cnt + "_source", job.source());
                prop.put("import_table_" + cnt + "_chunkCount", job.chunkCount());
                prop.put("import_table_" + cnt + "_recordsCount", job.count());
                prop.put("import_table_" + cnt + "_speed", job.speed());
                dark = !dark;
                cnt++;
            }
            prop.put("import_table", cnt);
            prop.put("import", 1);
            prop.put("refresh", 1);
        }
        return prop;
    }

}
