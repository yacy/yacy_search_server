
import java.util.Iterator;

import de.anomic.crawler.CrawlProfile.entry;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class WatchWebStructure_p {
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        
        int width = 768;
        int height = 576;
        int depth = 3;
        int nodes = 500; // maximum number of host nodes that are painted
        int time = -1;
        String host = "auto";
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            depth = post.getInt("depth", 3);
            nodes = post.getInt("nodes", width * height * 100 / 768 / 576);
            time = post.getInt("time", -1);
            host = post.get("host", "auto");
        }
        
        if (host.equals("auto")) {
        	// try to find the host from the crawl profiles
        	final Iterator<entry> it = sb.webIndex.profilesActiveCrawls.profiles(true);
            entry e;
            while (it.hasNext()) {
                e = it.next();
                if (e.name().equals(plasmaWordIndex.CRAWL_PROFILE_PROXY) ||
                    e.name().equals(plasmaWordIndex.CRAWL_PROFILE_REMOTE) ||
                    e.name().equals(plasmaWordIndex.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT)  ||
                    e.name().equals(plasmaWordIndex.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT) ||
                    e.name().equals(plasmaWordIndex.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA) ||
                    e.name().equals(plasmaWordIndex.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA))
                   continue;
                host = e.name();
                break; // take the first one
            }
        }
        
        prop.put("host", host);
        prop.put("depth", depth);
        prop.put("depthi", Math.min(8, depth + 1));
        prop.put("depthd", Math.max(0, depth - 1));
        prop.put("nodes", nodes);
        prop.put("nodesi", Math.min(1000, nodes + 100));
        prop.put("nodesd", Math.max(100, nodes - 100));
        prop.put("time", time);
        prop.put("timei", (time > 9000) ? -1 : ((time < 0) ? -1 : Math.min(9999, time + 1000)));
        prop.put("timed", (time < 0) ? 9000 : Math.max(1000, time - 1000));
        prop.put("width", width);
        prop.put("height", height);
        
        return prop;
    }
}
