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
import java.util.List;
import java.util.Set;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.OAIListFriendsLoader;
import net.yacy.document.importer.OAIPMHImporter;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexImportOAIPMHList_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        prop.put("refresh", 0);
        prop.put("import", 0);
        prop.put("source", 0);

        if (post != null && post.containsKey("source")) {
            ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
            final Set<String> oaiRoots = new OAIListFriendsLoader().getListFriends(sb.loader, agent).keySet();

            boolean dark = false;
            int count = 0;
            for (final String root: oaiRoots) {
                prop.put("source_table_" + count + "_dark", (dark) ? "1" : "0");
                prop.put("source_table_" + count + "_count", count);
                prop.put("source_table_" + count + "_source", CharacterCoding.unicode2html(root, true));
                prop.put("source_table_" + count + "_loadurl", "<a href=\"IndexImportOAIPMH_p.html?urlstart=" + CharacterCoding.unicode2html(root, true) + "\" target=\"_top\">" + CharacterCoding.unicode2html(root, true) + "</a>");
                dark = !dark;
                count++;
            }
            prop.put("source_table", count);
            prop.put("source_num", count);
            prop.put("source", 1);
        }

        if (post != null && post.containsKey("import")) {
            final List<OAIPMHImporter> jobs = new ArrayList<OAIPMHImporter>();
            for (OAIPMHImporter job: OAIPMHImporter.runningJobs.keySet()) jobs.add(job);
            for (OAIPMHImporter job: OAIPMHImporter.startedJobs.keySet()) jobs.add(job);
            for (OAIPMHImporter job: OAIPMHImporter.finishedJobs.keySet()) jobs.add(job);

            boolean dark = false;
            int count = 0;
            for (final OAIPMHImporter job: jobs) {
                prop.put("import_table_" + count + "_dark", (dark) ? "1" : "0");
                prop.put("import_table_" + count + "_thread", (job.isAlive()) ? "<img src=\"env/grafics/loading.gif\" alt=\"running\" />" : "finished");
                prop.putXML("import_table_" + count + "_source", job.source());
                prop.put("import_table_" + count + "_chunkCount", job.chunkCount());
                prop.put("import_table_" + count + "_recordsCount", job.count());
                prop.put("import_table_" + count + "_completeListSize", job.getCompleteListSize());
                prop.put("import_table_" + count + "_speed", job.speed());
                dark = !dark;
                count++;
            }
            prop.put("import_table", count);
            prop.put("import", 1);
            prop.put("refresh", 1);
        }
        return prop;
    }

}
