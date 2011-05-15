//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.CrawlSwitchboard;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class WatchWebStructure_p {
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        String color_text    = "888888";
        String color_back    = "FFFFFF";
        String color_dot     = "11BB11";
        String color_line    = "222222";
        String color_lineend = "333333";
        
        int width = 1024;
        int height = 576;
        int depth = 3;
        int nodes = 500; // maximum number of host nodes that are painted
        int time = -1;
        String host = "auto";
        String besthost;
        
        if (post != null) {
            width         = post.getInt("width", 1024);
            height        = post.getInt("height", 576);
            depth         = post.getInt("depth", 3);
            nodes         = post.getInt("nodes", width * height * 100 / 1024 / 576);
            time          = post.getInt("time", -1);
            host          = post.get("host", "auto");
            color_text    = post.get("colortext",    color_text);
            color_back    = post.get("colorback",    color_back);
            color_dot     = post.get("colordot",     color_dot);
            color_line    = post.get("colorline",    color_line);
            color_lineend = post.get("colorlineend", color_lineend);
        }
        
        if (host.equals("auto")) {
        	// try to find the host from the crawl profiles
        	CrawlProfile e;
            for (byte[] handle: sb.crawler.getActive()) {
                e = sb.crawler.getActive(handle);
                if (e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_PROXY) ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_REMOTE) ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT)  ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT) ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA) ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA) ||
                    e.name().equals(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE))
                   continue;
                host = e.name();
                break; // take the first one
            }
        }
        
        // find start point
        if (host == null ||
            host.length() == 0 ||
            host.equals("auto") ||
            sb.webStructure.referencesCount(DigestURI.hosthash6(host)) == 0) {
            // find domain with most references
            besthost = sb.webStructure.hostWithMaxReferences();
        } else {
            besthost = host;
        }
        
        prop.putHTML("host", host);
        prop.putHTML("besthost", besthost);
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

        prop.put("colortext",    color_text);
        prop.put("colorback",    color_back);
        prop.put("colordot",     color_dot);
        prop.put("colorline",    color_line);
        prop.put("colorlineend", color_lineend);
        return prop;
    }
}
