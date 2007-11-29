// ScreenSaver.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This File is contributed by Martin Thelian
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardQueue;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class ScreenSaver {
    
    /**
     * Generates a proxy-autoconfig-file (application/x-ns-proxy-autoconfig) 
     * See: <a href="http://wp.netscape.com/eng/mozilla/2.0/relnotes/demo/proxy-live.html">Proxy Auto-Config File Format</a> 
     * @param header the complete HTTP header of the request
     * @param post any arguments for this servlet, the request carried with (GET as well as POST)
     * @param env the serverSwitch object holding all runtime-data
     * @return the rewrite-properties for the template
     */
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {

        plasmaSwitchboard sb = (plasmaSwitchboard)env;
        boolean localCrawlStarted = false;
        boolean remoteTriggeredCrawlStarted = false;
        boolean globalCrawlTriggerStarted = false;
        try {
            InputStream input = (InputStream) header.get("INPUTSTREAM");
            OutputStream output = (OutputStream) header.get("OUTPUTSTREAM");
            
            String line = null;
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(input));
            PrintWriter outputWriter = new PrintWriter(output);
            while ((line = inputReader.readLine()) != null) {
                yacyCore.peerActions.updateMySeed();
                if (line.equals("")) {
                    continue;
                } else if (line.startsWith("PPM")) {                    
                    String currentPPM = yacyCore.seedDB.mySeed().get(yacySeed.ISPEED, "-1");
                    outputWriter.println(currentPPM);
                } else if (line.startsWith("LINKS")) {
                    String currentLinks = yacyCore.seedDB.mySeed().get(yacySeed.LCOUNT, "-1");
                    outputWriter.println(currentLinks);
                } else if (line.startsWith("WORDS")) {
                    String currentWords = yacyCore.seedDB.mySeed().get(yacySeed.ICOUNT, "-1");
                    outputWriter.println(currentWords);
                } else if (line.equals("CURRENTURL")) {
                    String currentURL = "";
                    ArrayList entryList = new ArrayList();
                    synchronized (sb.indexingTasksInProcess) {
                        if (sb.indexingTasksInProcess.size() > 0) {
                            entryList.addAll(sb.indexingTasksInProcess.values());
                        }
                    }
                    if (entryList.size() > 0) {
                        plasmaSwitchboardQueue.Entry pcentry = (plasmaSwitchboardQueue.Entry) entryList.get(0);
                        currentURL = pcentry.url().toString();
                    }
                    
                    outputWriter.println(currentURL);
                } else if (line.equals("CONTINUECRAWLING")) {
                    if (sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL)) {
                        localCrawlStarted = true;
                        sb.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
                    }
                    if (sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL)) {
                        remoteTriggeredCrawlStarted = true;
                        sb.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
                    }
                    if (sb.crawlJobIsPaused(plasmaSwitchboard.CRAWLJOB_REMOTE_CRAWL_LOADER)) {
                        globalCrawlTriggerStarted = true;
                        sb.continueCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_CRAWL_LOADER);
                    }                                     
                } else if (line.equals("EXIT")) {
                    outputWriter.println("OK");
                    outputWriter.flush();
                    return null;
                } else {
                    outputWriter.println("Unknown command");
                }
                outputWriter.flush();
            }    
            
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (localCrawlStarted) {
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_LOCAL_CRAWL);
            }
            if (remoteTriggeredCrawlStarted) {
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_TRIGGERED_CRAWL);
            }
            if (globalCrawlTriggerStarted) {
                sb.pauseCrawlJob(plasmaSwitchboard.CRAWLJOB_REMOTE_CRAWL_LOADER);
            }            
        }
    }
    
}
