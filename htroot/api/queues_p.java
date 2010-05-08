import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import de.anomic.crawler.NoticedURL;
import de.anomic.crawler.retrieval.Request;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class queues_p {
    
    public static final String STATE_RUNNING = "running";
    public static final String STATE_PAUSED = "paused";
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(final Date date) {
        if (date == null) return "";
        return dayFormatter.format(date);
    }
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        //wikiCode wikiTransformer = new wikiCode(switchboard);
        final serverObjects prop = new serverObjects();
        Segment segment = null;
        boolean html = post != null && post.containsKey("html");
        prop.setLocalized(html);
        if (post != null && post.containsKey("segment") && sb.verifyAuthentication(header, false)) {
            segment = sb.indexSegments.segment(post.get("segment"));
        }
        if (segment == null) segment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        prop.put("rejected", "0");
        //int showRejectedCount = 10;
        
        yacySeed initiator;
        
        // index size
        prop.putNum("urlpublictextSize", segment.urlMetadata().size());
        prop.putNum("rwipublictextSize", segment.termIndex().sizesMax());

        // loader queue
        prop.putNum("loaderSize", sb.crawlQueues.workerSize());        
        prop.putNum("loaderMax", sb.getConfigLong(SwitchboardConstants.CRAWLER_THREADS_ACTIVE_MAX, 10));
        if (sb.crawlQueues.workerSize() == 0) {
            prop.put("list-loader", "0");
        } else {
            final Request[] w = sb.crawlQueues.activeWorkerEntries();
            int count = 0;
            for (int i = 0; i < w.length; i++)  {
                if (w[i] == null) continue;
                prop.put("list-loader_"+count+"_profile", w[i].profileHandle());
                initiator = sb.peers.getConnected(new String(w[i].initiator()));
                prop.putHTML("list-loader_"+count+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                prop.put("list-loader_"+count+"_depth", w[i].depth());
                prop.putXML("list-loader_"+count+"_url", w[i].url().toString());
                count++;
            }
            prop.put("list-loader", count);
        }
        
        //local crawl queue
        prop.putNum("localCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL).getJobCount());
        prop.put("localCrawlState", sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL) ? STATE_PAUSED : STATE_RUNNING);
        int stackSize = sb.crawlQueues.noticeURL.stackSize(NoticedURL.STACK_TYPE_CORE);
        addNTable(sb, prop, "list-local", sb.crawlQueues.noticeURL.top(NoticedURL.STACK_TYPE_CORE, Math.min(10, stackSize)));

        //global crawl queue
        prop.putNum("limitCrawlSize", sb.crawlQueues.limitCrawlJobSize());
        prop.put("limitCrawlState", STATE_RUNNING);
        stackSize = sb.crawlQueues.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);

        //global crawl queue
        prop.putNum("remoteCrawlSize", sb.getThread(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL).getJobCount());
        prop.put("remoteCrawlState", sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_REMOTE_TRIGGERED_CRAWL) ? STATE_PAUSED : STATE_RUNNING);
        stackSize = sb.crawlQueues.noticeURL.stackSize(NoticedURL.STACK_TYPE_LIMIT);

        if (stackSize == 0) {
            prop.put("list-remote", "0");
        } else {
            addNTable(sb, prop, "list-remote", sb.crawlQueues.noticeURL.top(NoticedURL.STACK_TYPE_LIMIT, Math.min(10, stackSize)));
        }

        // return rewrite properties
        return prop;
    }
    
    
    public static final void addNTable(final Switchboard sb, final serverObjects prop, final String tableName, final ArrayList<Request> crawlerList) {

        int showNum = 0;
        Request urle;
        yacySeed initiator;
        for (int i = 0; i < crawlerList.size(); i++) {
            urle = crawlerList.get(i);
            if ((urle != null) && (urle.url() != null)) {
                initiator = sb.peers.getConnected(urle.initiator() == null ? "" : new String(urle.initiator()));
                prop.put(tableName + "_" + showNum + "_profile", urle.profileHandle());
                prop.put(tableName + "_" + showNum + "_initiator", ((initiator == null) ? "proxy" : initiator.getName()));
                prop.put(tableName + "_" + showNum + "_depth", urle.depth());
                prop.put(tableName + "_" + showNum + "_modified", daydate(urle.appdate()));
                prop.putXML(tableName + "_" + showNum + "_anchor", urle.name());
                prop.putXML(tableName + "_" + showNum + "_url", urle.url().toNormalform(false, true));
                prop.put(tableName + "_" + showNum + "_hash", urle.url().hash());
                showNum++;
            }
        }
        prop.put(tableName, showNum);

    }
}
