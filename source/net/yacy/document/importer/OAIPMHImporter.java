// OAIPMHImporter
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.09.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
// $LastChangedBy: low012 $
//
// LICENSE
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

package net.yacy.document.importer;

import java.io.IOException;
import java.net.MalformedURLException;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.search.Switchboard;


// get one server with
// http://roar.eprints.org/index.php?action=csv
// list records from oai-pmh like
// http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc


public class OAIPMHImporter extends Thread implements Importer {

    public static OAIPMHImporter job; // if started from a servlet, this object is used to store the thread
    
    private LoaderDispatcher loader;
    private DigestURI source;
    private int count;
    private long startTime;
    private ResumptionToken resumptionToken;
    private String message;
    
    public OAIPMHImporter(LoaderDispatcher loader, DigestURI source) {
        this.loader = loader;
        this.count = 0;
        this.startTime = System.currentTimeMillis();
        this.resumptionToken = null;
        this.message = "import initialized";
        // fix start url
        String url = ResumptionToken.truncatedURL(source);
        if (!url.endsWith("?")) url = url + "?";
        try {
            this.source = new DigestURI(url + "verb=ListRecords&metadataPrefix=oai_dc", null);
        } catch (MalformedURLException e) {
            // this should never happen
            e.printStackTrace();
        }
    }

    public int count() {
        return this.count;
    }
    
    public String status() {
        return this.message;
    }
    
    public ResumptionToken getResumptionToken() {
        return this.resumptionToken;
    }

    public long remainingTime() {
        return Long.MAX_VALUE; // we don't know
    }

    public long runningTime() {
        return System.currentTimeMillis() - this.startTime;
    }

    public String source() {
        return source.toNormalform(true, false);
    }

    public int speed() {
        return (int) (1000L * ((long) count()) / runningTime());
    }
    
    public void run() {
        this.message = "loading first part of records";
        while (true) {
            try {
                OAIPMHReader reader = new OAIPMHReader(this.loader, this.source, Switchboard.getSwitchboard().surrogatesInPath, "oaipmh");
                this.source = reader.getResumptionToken().resumptionURL(this.source);
                if (this.source == null) {
                    this.message = "import terminated with source = null";
                    break;
                }
                this.message = "loading next resumption fragment, cursor = " + reader.getResumptionToken().getCursor();
            } catch (IOException e) {
                this.message = e.getMessage();
                break;
            }
        }
    }
}