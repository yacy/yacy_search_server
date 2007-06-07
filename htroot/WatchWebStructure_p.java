
import java.util.Iterator;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaCrawlProfile.entry;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class WatchWebStructure_p {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        
        int width = 768;
        int height = 576;
        int depth = 3;
        int nodes = 100; // maximum number of host nodes that are painted
        String host = "auto";
        
        if (post != null) {
            width = post.getInt("width", 768);
            height = post.getInt("height", 576);
            depth = post.getInt("depth", 3);
            nodes = post.getInt("nodes", width * height * 100 / 768 / 576);
            host = post.get("host", "auto");
        }
        
        if (host.equals("auto")) {
        	// try to find the host from the crawl profiles
        	Iterator it = sb.profiles.profiles(true);
            entry e;
            while (it.hasNext()) {
                e = (entry)it.next();
                if (e.name().equals(plasmaSwitchboard.CRAWL_PROFILE_PROXY) ||
                    e.name().equals(plasmaSwitchboard.CRAWL_PROFILE_REMOTE) ||
                    e.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_TEXT) ||
                    e.name().equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_MEDIA))
                   continue;
                host = e.name();
                break; // take the first one
            }
        }
        
        prop.put("host", host);
        prop.put("depth", depth);
        prop.put("depthi", Math.min(8, depth + 1));
        prop.put("depthd", Math.max(0, depth - 1));
        prop.put("width", width);
        prop.put("height", height);
        prop.put("nodes", nodes);
        
        return prop;
    }
}
