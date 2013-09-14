//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
//

import java.util.Iterator;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class WatchWebStructure_p {
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        String color_text    = "888888";
        String color_back    = "FFFFFF";
        String color_dot0    = "1111BB";
        String color_dota    = "11BB11";
        String color_line    = "222222";
        String color_lineend = "333333";

        int width = 1280;
        int height = 720;
        int depth = 3;
        int nodes = 300; // maximum number of host nodes that are painted
        int time = -1;
        String host = "auto";
        String besthost;

        if (post != null) {
            width         = post.getInt("width", 1280);
            height        = post.getInt("height", 720);
            depth         = post.getInt("depth", 3);
            nodes         = post.getInt("nodes", width * height * 300 / width / height);
            time          = post.getInt("time", -1);
            host          = post.get("host", "auto");
            color_text    = post.get("colortext",    color_text);
            color_back    = post.get("colorback",    color_back);
            color_dot0    = post.get("colordot0",    color_dot0);
            color_dota    = post.get("colordota",    color_dota);
            color_line    = post.get("colorline",    color_line);
            color_lineend = post.get("colorlineend", color_lineend);
        }

        if (host.equals("auto")) {
        	// try to find the host from the crawl profiles
        	CrawlProfile e;
            for (final byte[] handle: sb.crawler.getActive()) {
                e = sb.crawler.getActive(handle);
                if (CrawlSwitchboard.DEFAULT_PROFILES.contains(e.name())) continue;
                host = e.name();
                break; // take the first one
            }
        }

        // fix start point if a "www."-prefix would be better
        if (host != null && !host.startsWith("www")) {
            if (sb.webStructure.referencesCount(DigestURL.hosthash6("www." + host)) > sb.webStructure.referencesCount(DigestURL.hosthash6(host))) {
                host = "www." + host;
            }
        }
        
        if (post != null && post.containsKey("hosts")) {
            int maxcount = 200;
            ReversibleScoreMap<String> score = sb.webStructure.hostReferenceScore();
            int c = 0;
            Iterator<String> i = score.keys(false);
            String h;
            while (i.hasNext() && c < maxcount) {
                h = i.next();
                prop.put("hosts_list_" + c + "_host", h);
                prop.put("hosts_list_" + c + "_count", score.get(h));
                c++;
            }
            prop.put("hosts_list", c);
            prop.put("hosts", 1);
        }

        // find start point
        if (host == null ||
            host.isEmpty() ||
            host.equals("auto")
            // || sb.webStructure.referencesCount(DigestURI.hosthash6(host)) == 0
            ) {
            // find domain with most references
            besthost = sb.webStructure.hostWithMaxReferences();
            if (besthost == null) besthost = host;
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
        prop.put("colordot0",    color_dot0);
        prop.put("colordota",    color_dota);
        prop.put("colorline",    color_line);
        prop.put("colorlineend", color_lineend);
        return prop;
    }
}
