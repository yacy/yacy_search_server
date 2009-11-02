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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;

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
        prop.put("import-all", 0);
        prop.put("import-all_status", 0);
        prop.put("defaulturl", "");
        
        OAIPMHImporter job = null;
        try {
            job = OAIPMHImporter.runningJobs.first();
        } catch (NoSuchElementException e0) {
            try {
                job = OAIPMHImporter.startedJobs.first();
            } catch (NoSuchElementException e1) {
                try {
                    job = OAIPMHImporter.finishedJobs.first();
                } catch (NoSuchElementException e2) {}
            }
        }
        if (job != null) {
            // one import is running, no option to insert anything
            prop.put("import-all", 1);
            prop.put("import-all_thread", (job.isAlive()) ? "running" : "finished");
            prop.put("import-all_source", job.source());
            prop.put("import-all_chunkCount", job.chunkCount());
            prop.put("import-all_recordsCount", job.count());
            prop.put("import-all_speed", job.speed());
            return prop;
        }
        
        if (post != null) {
            if (post.containsKey("urlstartone")) {
                String oaipmhurl = post.get("urlstartone");
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
            
            if (post.containsKey("urlstartall")) {
                String oaipmhurl = post.get("urlstartall");
                DigestURI url = null;
                try {
                    url = new DigestURI(oaipmhurl, null);
                    job = new OAIPMHImporter(sb.loader, url);
                    job.start();
                    prop.put("import-all", 1);
                    prop.put("import-all_thread", "started");
                    prop.put("import-all_source", job.source());
                    prop.put("import-all_chunkCount", 0);
                    prop.put("import-all_recordsCount", 0);
                    prop.put("import-all_speed", 0);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    prop.put("import-all", 0);
                    prop.put("import-all_status", 1);
                    prop.put("import-all_status_message", e.getMessage());
                }
            }
        }
        return prop;
    }
}
