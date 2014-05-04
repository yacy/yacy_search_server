/**
 *  OAIPMHImporter
 *  Copyright 2009 by Michael Peter Christen
 *  First released 30.09.2009 at http://yacy.net
 *
 *  This is a part of YaCy, a peer-to-peer based web search engine
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;


// list records from oai-pmh like
// http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc

public class OAIPMHImporter extends Thread implements Importer, Comparable<OAIPMHImporter> {

    private static int importerCounter = Integer.MAX_VALUE;
    private static final Object N = new Object();

    public static ConcurrentHashMap<OAIPMHImporter, Object> startedJobs = new ConcurrentHashMap<OAIPMHImporter, Object>();
    public static ConcurrentHashMap<OAIPMHImporter, Object> runningJobs = new ConcurrentHashMap<OAIPMHImporter, Object>();
    public static ConcurrentHashMap<OAIPMHImporter, Object> finishedJobs = new ConcurrentHashMap<OAIPMHImporter, Object>();

    private final LoaderDispatcher loader;
    private DigestURL source;
    private int recordsCount, chunkCount, completeListSize;
    private final long startTime;
    private long finishTime;
    private final ResumptionToken resumptionToken;
    private String message;
    private final int serialNumber;
    private final ClientIdentification.Agent agent;

    public OAIPMHImporter(final LoaderDispatcher loader, final ClientIdentification.Agent agent, final DigestURL source) {
        this.agent = agent;
        this.serialNumber = importerCounter--;
        this.loader = loader;
        this.recordsCount = 0;
        this.chunkCount = 0;
        this.completeListSize = 0;
        this.startTime = System.currentTimeMillis();
        this.finishTime = 0;
        this.resumptionToken = null;
        this.message = "import initialized";
        // fix start url
        String url = ResumptionToken.truncatedURL(source);
        if (!url.endsWith("?")) url = url + "?";
        try {
            this.source = new DigestURL(url + "verb=ListRecords&metadataPrefix=oai_dc");
        } catch (final MalformedURLException e) {
            // this should never happen
            ConcurrentLog.logException(e);
        }
        startedJobs.put(this, N);
    }

    @Override
    public int count() {
        return this.recordsCount;
    }

    public int chunkCount() {
        return this.chunkCount;
    }

    @Override
    public String status() {
        return this.message;
    }

    public ResumptionToken getResumptionToken() {
        return this.resumptionToken;
    }

    public int getCompleteListSize() {
        return this.completeListSize;
    }

    @Override
    public long remainingTime() {
        return (this.isAlive()) ? Long.MAX_VALUE : 0; // we don't know
    }

    @Override
    public long runningTime() {
        return (this.isAlive()) ? System.currentTimeMillis() - this.startTime : this.finishTime - this.startTime;
    }

    @Override
    public String source() {
        return this.source.toNormalform(true);
    }

    @Override
    public int speed() {
        return (int) (1000L * (count()) / runningTime());
    }

    @Override
    public void run() {
        while (runningJobs.size() > 50) {
            try {Thread.sleep(10000 + 3000 * (System.currentTimeMillis() % 6));} catch (final InterruptedException e) {}
        }
        startedJobs.remove(this);
        runningJobs.put(this, N);
        this.message = "loading first part of records";
        while (true) {
            try {
                OAIPMHLoader oailoader = new OAIPMHLoader(this.loader, this.source, Switchboard.getSwitchboard().surrogatesInPath, this.agent);
                this.completeListSize = Math.max(this.completeListSize, oailoader.getResumptionToken().getCompleteListSize());
                this.chunkCount++;
                this.recordsCount += oailoader.getResumptionToken().getRecordCounter();
                this.source = oailoader.getResumptionToken().resumptionURL();
                if (this.source == null) {
                    this.message = "import terminated with source = null";
                    break;
                }
                this.message = "loading next resumption fragment, cursor = " + oailoader.getResumptionToken().getCursor();
            } catch (final IOException e) {
                this.message = e.getMessage();
                break;
            }
        }
        this.finishTime = System.currentTimeMillis();
        runningJobs.remove(this);
        finishedJobs.put(this, N);
    }


    // methods that are needed to put the object into a Hashtable or a Map:

    @Override
    public int hashCode() {
        return this.serialNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof OAIPMHImporter)) return false;
        OAIPMHImporter other = (OAIPMHImporter) obj;
        return this.compareTo(other) == 0;
    }

    // methods that are needed to put the object into a Tree:
    @Override
    public int compareTo(OAIPMHImporter o) {
        if (this.serialNumber > o.serialNumber) return 1;
        if (this.serialNumber < o.serialNumber) return -1;
        return 0;
    }

    public static final char hostReplacementChar = '_';
    public static final char filenameSeparationChar = '.';
    public static final String filenamePrefix = "oaipmh";

    /**
     * compute a host id
     * @param source
     * @return a string that is a key for the given host
     */
    public static final String hostID(DigestURL source) {
        String s = ResumptionToken.truncatedURL(source);
        if (s.endsWith("?")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("https://")) s = s.substring(8);
        if (s.startsWith("http://")) s = s.substring(7);
        return s.replace('.', hostReplacementChar).replace('/', hostReplacementChar).replace(':', hostReplacementChar);
    }

    /**
     * get a file name for a source. the file name contains a prefix that is used to identify
     * that source as part of the OAI-PMH import process and a host key to identify the source.
     * also included is a date stamp within the file name
     * @param source
     * @return a file name for the given source. It will be different for each call for same hosts because it contains a date stamp
     */
    public static final String filename4Source(DigestURL source) {
        return filenamePrefix + OAIPMHImporter.filenameSeparationChar +
               OAIPMHImporter.hostID(source) + OAIPMHImporter.filenameSeparationChar +
               GenericFormatter.SHORT_MILSEC_FORMATTER.format() + ".xml";
    }
}