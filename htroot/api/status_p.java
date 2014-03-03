// status_p
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 18.12.2006 on http://www.anomic.de
// this file was created using the an implementation from IndexCreate_p.java, published 02.12.2004
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.io.IOException;

import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.Memory;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.io.ByteCount;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class status_p {

    public static final String STATE_RUNNING = "running";
    public static final String STATE_PAUSED = "paused";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final boolean html = post != null && post.containsKey("html");
        prop.setLocalized(html);
        Segment segment = sb.index;
        Fulltext fulltext = segment.fulltext();

        prop.put("rejected", "0");
        sb.updateMySeed();
        final int cacheMaxSize = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 10000);
        prop.put("ppm", Switchboard.currentPPM()); // we don't format the ppm here because that will cause that the progress bar shows nothing if the number is > 999
        prop.putNum("qpm", sb.peers.mySeed().getQPM());
        prop.putNum("wordCacheSize", segment.RWIBufferCount());
        prop.putNum("wordCacheMaxSize", cacheMaxSize);
        
		// memory usage and system attributes
        prop.putNum("usedMemory", MemoryControl.used());
        prop.putNum("freeMemory", MemoryControl.free());
        prop.putNum("totalMemory", MemoryControl.total());
        prop.putNum("maxMemory", MemoryControl.maxMemory());
        prop.putNum("usedDisk", sb.observer.getSizeOfDataPath(true));
        prop.putNum("freeDisk", sb.observer.getUsableSpace());
        prop.putNum("processors", WorkflowProcessor.availableCPU);
        prop.putNum("load", Memory.load());
        
		// proxy traffic
		prop.put("trafficIn", ByteCount.getGlobalCount());
		prop.put("trafficProxy", ByteCount.getAccountCount(ByteCount.PROXY));
		prop.put("trafficCrawler", ByteCount.getAccountCount(ByteCount.CRAWLER));

        // index size
        prop.putNum("urlpublictextSize", fulltext.collectionSize());
        prop.putNum("urlpublictextSegmentCount", fulltext.getDefaultConnector().getSegmentCount());
        prop.putNum("webgraphSize", fulltext.useWebgraph() ? fulltext.webgraphSize() : 0);
        prop.putNum("webgraphSegmentCount", fulltext.useWebgraph() ? fulltext.getWebgraphConnector().getSegmentCount() : 0);
        prop.putNum("citationSize", segment.citationCount());
        prop.putNum("citationSegmentCount", segment.citationSegmentCount());
        prop.putNum("rwipublictextSize", segment.RWICount());
        prop.putNum("rwipublictextSegmentCount", segment.RWISegmentCount());

        // loader queue
        prop.putNum("loaderSize", sb.crawlQueues.activeWorkerEntries().size());
        prop.putNum("loaderMax", sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10));

        //local crawl queue
        prop.putNum("localCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount());
        prop.put("localCrawlState", sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL) ? STATE_PAUSED : STATE_RUNNING);

        //global crawl queue
        prop.putNum("limitCrawlSize", sb.crawlQueues.limitCrawlJobSize());
        prop.put("limitCrawlState", STATE_RUNNING);

        //remote crawl queue
        prop.putNum("remoteCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL).getJobCount());
        prop.put("remoteCrawlState", sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL) ? STATE_PAUSED : STATE_RUNNING);

        //noload crawl queue
        prop.putNum("noloadCrawlSize", sb.crawlQueues.noloadCrawlJobSize());
        prop.put("noloadCrawlState", STATE_RUNNING);

        // generate crawl profile table
        int count = 0;
        final int domlistlength = (post == null) ? 160 : post.getInt("domlistlength", 160);
        CrawlProfile profile;
        // put active crawls into list
        String hosts = "";
        for (final byte[] h: sb.crawler.getActive()) {
            profile = sb.crawler.getActive(h);
            if (CrawlSwitchboard.DEFAULT_PROFILES.contains(profile.name())) continue;
            profile.putProfileEntry("crawlProfiles_list_", prop, true, false, count, domlistlength);
            RowHandleSet urlhashes = sb.crawler.getURLHashes(h);
            prop.put("crawlProfiles_list_" + count + "_count", urlhashes == null ? "unknown" : Integer.toString(urlhashes.size()));
            if (profile.urlMustMatchPattern() == CrawlProfile.MATCH_ALL_PATTERN) {
                hosts = hosts + "," + profile.name();
            }
            count++;
        }
        prop.put("crawlProfiles_list", count);
        prop.put("crawlProfiles_count", count);
        prop.put("crawlProfiles", count == 0 ? 0 : 1);

        prop.put("postprocessingRunning", Switchboard.postprocessingRunning ? 1 : 0);
        
        boolean processCollection =  sb.index.fulltext().getDefaultConfiguration().contains(CollectionSchema.process_sxt) && (sb.index.connectedCitation() || sb.index.fulltext().useWebgraph());
        boolean processWebgraph =  sb.index.fulltext().getWebgraphConfiguration().contains(WebgraphSchema.process_sxt) && sb.index.fulltext().useWebgraph();

        long collectionTimeSinceStart = processCollection && Switchboard.postprocessingRunning ? System.currentTimeMillis() - Switchboard.postprocessingStartTime[0] : 0;
        long webgraphTimeSinceStart = processWebgraph && Switchboard.postprocessingRunning ? System.currentTimeMillis() - Switchboard.postprocessingStartTime[1] : 0;

        long collectionRemainingCount = 0;
        if (processCollection) try {collectionRemainingCount = sb.index.fulltext().getDefaultConnector().getCountByQuery(CollectionSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);} catch (IOException e) {}
        long collectionCountSinceStart = Switchboard.postprocessingRunning ? Switchboard.postprocessingCount[0] - collectionRemainingCount : 0;
        int collectionSpeed = collectionTimeSinceStart == 0 ? 0 : (int) (60000 * collectionCountSinceStart / collectionTimeSinceStart); // pages per minute
        long collectionRemainingTime = collectionSpeed == 0 ? 0 : 60000 * collectionRemainingCount / collectionSpeed; // millis
        int collectionRemainingTimeMinutes = (int) (collectionRemainingTime / 60000);
        int collectionRemainingTimeSeconds = (int) ((collectionRemainingTime - (collectionRemainingTimeMinutes * 60000)) / 1000);

        long webgraphRemainingCount = 0;
        if (processWebgraph) try {webgraphRemainingCount = sb.index.fulltext().getWebgraphConnector().getCountByQuery(WebgraphSchema.process_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);} catch (IOException e) {}
        long webgraphCountSinceStart = Switchboard.postprocessingRunning ? Switchboard.postprocessingCount[1] - webgraphRemainingCount : 0;
        int webgraphSpeed = webgraphTimeSinceStart == 0 ? 0 : (int) (60000 * webgraphCountSinceStart / webgraphTimeSinceStart); // pages per minute
        long webgraphRemainingTime = webgraphSpeed == 0 ? 0 : 60000 * webgraphRemainingCount / webgraphSpeed; // millis
        int webgraphRemainingTimeMinutes = (int) (webgraphRemainingTime / 60000);
        int webgraphRemainingTimeSeconds = (int) ((webgraphRemainingTime - (webgraphRemainingTimeMinutes * 60000)) / 1000);

        prop.put("postprocessingCollectionRemainingCount", collectionRemainingCount);
        prop.put("postprocessingWebgraphRemainingCount", webgraphRemainingCount);
        prop.put("postprocessingRunning_activity", collectionTimeSinceStart > 0 ? "collection" : "webgraph");
        prop.put("postprocessingSpeed", collectionTimeSinceStart > 0 ? collectionSpeed : webgraphSpeed);
        prop.put("postprocessingElapsedTime", collectionTimeSinceStart > 0 ? collectionTimeSinceStart : webgraphTimeSinceStart);
        prop.put("postprocessingRemainingTime", collectionTimeSinceStart > 0 ? collectionRemainingTime : webgraphRemainingTime);
        prop.put("postprocessingRemainingTimeMinutes", collectionTimeSinceStart > 0 ? collectionRemainingTimeMinutes : webgraphRemainingTimeMinutes);
        prop.put("postprocessingRemainingTimeSeconds", collectionTimeSinceStart > 0 ? collectionRemainingTimeSeconds : webgraphRemainingTimeSeconds);
        
        // return rewrite properties
        return prop;
    }

}
