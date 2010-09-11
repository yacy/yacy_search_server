

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.io.ByteCount;
//import net.yacy.kelondro.io.ByteCountInputStream;
//import net.yacy.kelondro.io.ByteCountOutputStream;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class status_p {
    
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        Segment segment = null;
        boolean html = post != null && post.containsKey("html");
        prop.setLocalized(html);
        if (post != null && post.containsKey("segment") && sb.verifyAuthentication(header, false)) {
            segment = sb.indexSegments.segment(post.get("segment"));
        }
        if (segment == null) segment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        
        prop.put("rejected", "0");
        sb.updateMySeed();
        final int cacheMaxSize = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 10000);
        prop.putNum("ppm", sb.currentPPM());
        prop.putNum("qpm", sb.peers.mySeed().getQPM());
        prop.putNum("wordCacheSize", segment.termIndex().getBufferSize());
        prop.putNum("wordCacheMaxSize", cacheMaxSize);
		
        // crawl queues
        prop.putNum("localCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount());
        prop.putNum("limitCrawlSize", sb.crawlQueues.limitCrawlJobSize());
        prop.putNum("remoteCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL).getJobCount());
        prop.putNum("loaderSize", sb.crawlQueues.workerSize());        
        prop.putNum("loaderMax", sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10));
        
		// memory usage and system attributes
        prop.putNum("freeMemory", MemoryControl.free());
        prop.putNum("totalMemory", MemoryControl.total());
        prop.putNum("maxMemory", MemoryControl.maxMemory);
        prop.putNum("processors", WorkflowProcessor.availableCPU);

		// proxy traffic
		prop.put("trafficIn", ByteCount.getGlobalCount());
		prop.put("trafficProxy", ByteCount.getAccountCount(ByteCount.PROXY));
		prop.put("trafficCrawler", ByteCount.getAccountCount(ByteCount.CRAWLER));

        // return rewrite properties
        return prop;
    }
    
}
