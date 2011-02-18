// IndexImportOAIPMH.java
// -------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 30.10.2009 on http://yacy.net
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.OAIPMHImporter;
import net.yacy.document.importer.OAIPMHLoader;
import net.yacy.document.importer.ResumptionToken;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.data.WorkTables;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class IndexImportOAIPMH_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        prop.put("import-one", 0);
        prop.put("status", 0);
        prop.put("defaulturl", "");
        int jobcount = OAIPMHImporter.runningJobs.size() + OAIPMHImporter.startedJobs.size() + OAIPMHImporter.finishedJobs.size();
        prop.put("iframetype", (jobcount == 0) ? 2 : 1);
        prop.put("optiongetlist", (jobcount == 0) ? 0 : 1);
        if (post != null) {
            if (post.containsKey("urlstartone")) {
                String oaipmhurl = post.get("urlstartone");
                if (oaipmhurl.indexOf("?") < 0) oaipmhurl = oaipmhurl + "?verb=ListRecords&metadataPrefix=oai_dc";
                DigestURI url = null;
                try {
                    url = new DigestURI(oaipmhurl);
                    OAIPMHLoader r = new OAIPMHLoader(sb.loader, url, sb.surrogatesInPath, "oaipmh-one");
                    ResumptionToken rt = r.getResumptionToken();
                    prop.put("import-one", 1);
                    prop.put("import-one_count", (rt == null) ? "not available" : Integer.toString(rt.getRecordCounter()));
                    prop.put("import-one_source", r.source());
                    prop.put("import-one_rt", r.getResumptionToken().toString());
                    
                    // set next default url
                    try {
                        DigestURI nexturl = (rt == null) ? null : rt.resumptionURL();
                        if (rt != null) prop.put("defaulturl", (nexturl == null) ? "" : nexturl.toNormalform(true, false));
                    } catch (MalformedURLException e) {
                        prop.put("defaulturl", e.getMessage());
                    } catch (IOException e) {
                        // reached end of resumption
                        prop.put("defaulturl", e.getMessage());
                    }
                } catch (MalformedURLException e) {
                    Log.logException(e);
                    prop.put("import-one", 2);
                    prop.put("import-one_error", e.getMessage());
                } catch (IOException e) {
                    Log.logException(e);
                    prop.put("import-one", 2);
                    prop.put("import-one_error", e.getMessage());
                }
            }
            
            if (post.get("urlstart", "").length() > 0) {
                String oaipmhurl = post.get("urlstart", "");
                sb.tables.recordAPICall(post, "IndexImportOAIPMH_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "OAI-PMH import for " + oaipmhurl);
                DigestURI url = null;
                try {
                    url = new DigestURI(oaipmhurl);
                    OAIPMHImporter job = new OAIPMHImporter(sb.loader, url);
                    job.start();
                    prop.put("status", 1);
                    prop.put("optiongetlist", 1);
                    prop.put("iframetype", 1);
                } catch (MalformedURLException e) {
                    Log.logException(e);
                    prop.put("status", 2);
                    prop.put("status_message", e.getMessage());
                }
            }
            
            
            if (post.get("loadrows", "").length() > 0) {
                // create a time-ordered list of events to execute
                Set<String> sources = new TreeSet<String>();
                for (final Map.Entry<String, String> entry: post.entrySet()) {
                    if (entry.getValue().startsWith("mark_")) {
                        sources.add(entry.getValue().substring(5));
                    }
                }
                prop.put("status", 1);
                prop.put("optiongetlist", 1);
                prop.put("iframetype", 1);
                
                // prepare the set for random read from it (to protect the servers at the beginning of the list)
                List<String> sourceList = new ArrayList<String>(sources.size());
                for (String oaipmhurl: sources) sourceList.add(oaipmhurl);
                Random r = new Random(System.currentTimeMillis());
                
                // start jobs for the sources
                DigestURI url = null;
                while (sourceList.size() > 0) {
                    String oaipmhurl = sourceList.remove(r.nextInt(sourceList.size()));
                    try {
                        url = new DigestURI(oaipmhurl);
                        OAIPMHImporter job = new OAIPMHImporter(sb.loader, url);
                        job.start();
                    } catch (MalformedURLException e) {
                        Log.logException(e);
                    }
                }
            }
            
            if (post.containsKey("getlist")) {
                prop.put("iframetype", 2);
            }
        }
        return prop;
    }
}
