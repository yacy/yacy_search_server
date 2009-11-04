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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.document.parser.csvParser;

import de.anomic.crawler.CrawlProfile;
import de.anomic.search.Switchboard;


// get one server with
// http://roar.eprints.org/index.php?action=csv
// or
// http://www.openarchives.org/Register/BrowseSites
// or
// http://www.openarchives.org/Register/ListFriends
//
// list records from oai-pmh like
// http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc


public class OAIPMHImporter extends Thread implements Importer, Comparable<OAIPMHImporter> {

    private static int importerCounter = Integer.MAX_VALUE;
    
    public static TreeSet<OAIPMHImporter> startedJobs = new TreeSet<OAIPMHImporter>();
    public static TreeSet<OAIPMHImporter> runningJobs = new TreeSet<OAIPMHImporter>();
    public static TreeSet<OAIPMHImporter> finishedJobs = new TreeSet<OAIPMHImporter>();
    
    private LoaderDispatcher loader;
    private DigestURI source;
    private int recordsCount, chunkCount;
    private long startTime, finishTime;
    private ResumptionToken resumptionToken;
    private String message;
    private int serialNumber;
    
    public OAIPMHImporter(LoaderDispatcher loader, DigestURI source) {
        this.serialNumber = importerCounter--;
        this.loader = loader;
        this.recordsCount = 0;
        this.chunkCount = 0;
        this.startTime = System.currentTimeMillis();
        this.finishTime = 0;
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
        startedJobs.add(this);
    }

    public int count() {
        return this.recordsCount;
    }
    
    public int chunkCount() {
        return this.chunkCount;
    }
    
    public String status() {
        return this.message;
    }
    
    public ResumptionToken getResumptionToken() {
        return this.resumptionToken;
    }

    public long remainingTime() {
        return (this.isAlive()) ? Long.MAX_VALUE : 0; // we don't know
    }

    public long runningTime() {
        return (this.isAlive()) ? System.currentTimeMillis() - this.startTime : this.finishTime - this.startTime;
    }

    public String source() {
        return source.toNormalform(true, false);
    }

    public int speed() {
        return (int) (1000L * ((long) count()) / runningTime());
    }
    
    public void run() {
        while (runningJobs.size() > 10) {
            try {Thread.sleep(1000 + 1000 * System.currentTimeMillis() % 6);} catch (InterruptedException e) {}
        }
        startedJobs.remove(this);
        runningJobs.add(this);
        this.message = "loading first part of records";
        while (true) {
            try {
                OAIPMHReader reader = new OAIPMHReader(this.loader, this.source, Switchboard.getSwitchboard().surrogatesInPath, "oaipmh");
                this.chunkCount++;
                this.recordsCount += reader.getResumptionToken().getRecordCounter();
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
        this.finishTime = System.currentTimeMillis();
        runningJobs.remove(this);
        finishedJobs.add(this);
    }
    
    
    // methods that are needed to put the object into a Hashtable or a Map:
    
    public int hashCode() {
        return this.serialNumber;
    }
    
    public boolean equals(OAIPMHImporter o) {
        return this.compareTo(o) == 0;
    }

    // methods that are needed to put the object into a Tree:
    public int compareTo(OAIPMHImporter o) {
        if (this.serialNumber > o.serialNumber) return 1;
        if (this.serialNumber < o.serialNumber) return -1;
        return 0;
    }
    
    public static Set<String> getOAIServer(LoaderDispatcher loader) {
        TreeSet<String> list = new TreeSet<String>();

        // read roar
        File roar = new File(Switchboard.getSwitchboard().dictionariesPath, "harvesting/roar.csv");
        DigestURI roarSource;
        try {
            roarSource = new DigestURI("http://roar.eprints.org/index.php?action=csv", null);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            roarSource = null;
        }
        if (!roar.exists()) try {
            // load the file from the net
            loader.load(roarSource, CrawlProfile.CACHE_STRATEGY_NOCACHE, roar);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (roar.exists()) {
            csvParser parser = new csvParser();
            try {
                List<String[]> table = parser.getTable(roarSource, "", "UTF-8", new FileInputStream(roar));
                for (String[] row: table) {
                    if (row.length > 2 && (row[2].startsWith("http://") || row[2].startsWith("https://"))) {
                        list.add(row[2]);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        
        return list;
    }
    
}