

import de.anomic.http.httpRequestHeader;
import de.anomic.http.httpdByteCountInputStream;
import de.anomic.http.httpdByteCountOutputStream;
import de.anomic.kelondro.kelondroMemory;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProcessor;
import de.anomic.server.serverSwitch;

public class status_p {
    
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        if (post == null || !post.containsKey("html"))
            prop.setLocalized(false);
        prop.put("rejected", "0");
        sb.updateMySeed();
        final int  cacheSize = sb.webIndex.dhtCacheSize();
        final long cacheMaxSize = sb.getConfigLong(plasmaSwitchboardConstants.WORDCACHE_MAX_COUNT, 10000);
        prop.putNum("ppm", sb.currentPPM());
        prop.putNum("qpm", sb.webIndex.seedDB.mySeed().getQPM());
        prop.putNum("wordCacheSize", sb.webIndex.dhtCacheSize());
        prop.putNum("wordCacheSize", cacheSize);
        prop.putNum("wordCacheMaxSize", cacheMaxSize);
        prop.put("wordCacheCount", cacheSize);
        prop.put("wordCacheMaxCount", cacheMaxSize);

		//
		// memory usage and system attributes
        prop.putNum("freeMemory", kelondroMemory.free());
        prop.putNum("totalMemory", kelondroMemory.total());
        prop.putNum("maxMemory", kelondroMemory.max());
        prop.putNum("processors", serverProcessor.availableCPU);

		// proxy traffic
		prop.put("trafficIn", httpdByteCountInputStream.getGlobalCount());
		prop.put("trafficProxy", httpdByteCountOutputStream.getAccountCount("PROXY"));
		prop.put("trafficCrawler", httpdByteCountInputStream.getAccountCount("CRAWLER"));

        // return rewrite properties
        return prop;
    }
    
}
