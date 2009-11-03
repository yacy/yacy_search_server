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

import net.yacy.document.importer.OAIPMHImporter;
import net.yacy.document.importer.OAIPMHReader;
import net.yacy.document.importer.ResumptionToken;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.http.server.RequestHeader;
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
                    url = new DigestURI(oaipmhurl, null);
                    OAIPMHReader r = new OAIPMHReader(sb.loader, url, sb.surrogatesInPath, "oaipmh-one");
                    ResumptionToken rt = r.getResumptionToken();
                    prop.put("import-one", 1);
                    prop.put("import-one_count", (rt == null) ? "not available" : Integer.toString(rt.getRecordCounter()));
                    prop.put("import-one_source", r.source());
                    prop.put("import-one_rt", r.getResumptionToken().toString());
                    
                    // set next default url
                    try {
                        DigestURI nexturl = (rt == null) ? null : rt.resumptionURL(url);
                        if (rt != null) prop.put("defaulturl", (nexturl == null) ? "" : nexturl.toNormalform(true, false));
                    } catch (MalformedURLException e) {
                        prop.put("defaulturl", e.getMessage());
                    } catch (IOException e) {
                        // reached end of resumption
                        prop.put("defaulturl", e.getMessage());
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    prop.put("import-one", 2);
                    prop.put("import-one_error", e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    prop.put("import-one", 2);
                    prop.put("import-one_error", e.getMessage());
                }
            }
            
            if (post.containsKey("importroot")) {
                String oaipmhurl = post.get("urlstartall", "");
                DigestURI url = null;
                try {
                    url = new DigestURI(oaipmhurl, null);
                    OAIPMHImporter job = new OAIPMHImporter(sb.loader, url);
                    job.start();
                    prop.put("status", 1);
                    prop.put("optiongetlist", 1);
                    prop.put("iframetype", 1);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    prop.put("status", 2);
                    prop.put("status_message", e.getMessage());
                }
            }
            
            if (post.containsKey("getlist")) {
                prop.put("iframetype", 2);
            }
        }
        return prop;
    }
}
