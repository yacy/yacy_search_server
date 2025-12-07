package net.yacy.htroot;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.http.servlets.RAGProxyServlet;

public class AIShield_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {
            final boolean allowNonLocal = post.containsKey("ai.shield.allow-nonlocalhost");
            final boolean showChatLink = post.containsKey("ai.shield.show-chat-link");
            final boolean limitAll = post.containsKey("ai.shield.limit-all");
            
            final String perMinute = post.get("ai.shield.rate.per-minute", sb.getConfig("ai.shield.rate.per-minute", "1")).trim();
            final String perHour = post.get("ai.shield.rate.per-hour", sb.getConfig("ai.shield.rate.per-hour", "12")).trim();
            final String perDay = post.get("ai.shield.rate.per-day", sb.getConfig("ai.shield.rate.per-day", "30")).trim();
            
            final String allPerMinute = post.get("ai.shield.all.per-minute", sb.getConfig("ai.shield.all.per-minute", "1")).trim();
            final String allPerHour = post.get("ai.shield.all.per-hour", sb.getConfig("ai.shield.all.per-hour", "12")).trim();
            final String allPerDay = post.get("ai.shield.all.per-day", sb.getConfig("ai.shield.all.per-day", "30")).trim();

            sb.setConfig("ai.shield.allow-nonlocalhost", allowNonLocal);
            sb.setConfig("ai.shield.show-chat-link", showChatLink);
            sb.setConfig("ai.shield.limit-all", limitAll);
            sb.setConfig("ai.shield.rate.per-minute", perMinute);
            sb.setConfig("ai.shield.rate.per-hour", perHour);
            sb.setConfig("ai.shield.rate.per-day", perDay);
            sb.setConfig("ai.shield.all.per-minute", allPerMinute);
            sb.setConfig("ai.shield.all.per-hour", allPerHour);
            sb.setConfig("ai.shield.all.per-day", allPerDay);
            // mark shield definition to show quest completion
            sb.setConfig("ai.shield.definition", allowNonLocal ? "enabled" : "");
        }

        // mark page as visited for gamification/quest tracking
        sb.setConfig("ui.AIShield_p.visited", "true");

        prop.put("ai.shield.allow-nonlocalhost", sb.getConfigBool("ai.shield.allow-nonlocalhost", false) ? "1" : "0");
        prop.put("ai.shield.show-chat-link", sb.getConfigBool("ai.shield.show-chat-link", false) ? "1" : "0");
        prop.put("ai.shield.limit-all", sb.getConfigBool("ai.shield.limit-all", false) ? "1" : "0");
        
        prop.put("ai.shield.rate.per-minute", sb.getConfig("ai.shield.rate.per-minute", "1"));
        prop.put("ai.shield.rate.per-hour", sb.getConfig("ai.shield.rate.per-hour", "12"));
        prop.put("ai.shield.rate.per-day", sb.getConfig("ai.shield.rate.per-day", "30"));
        
        prop.put("ai.shield.all.per-minute", sb.getConfig("ai.shield.all.per-minute", "1"));
        prop.put("ai.shield.all.per-hour", sb.getConfig("ai.shield.all.per-hour", "12"));
        prop.put("ai.shield.all.per-day", sb.getConfig("ai.shield.all.per-day", "30"));

        // telemetry: counts across all IPs
        final long now = System.currentTimeMillis();
        RAGProxyServlet.pruneOldEntries(now);
        prop.putNum("telemetry.per-minute", RAGProxyServlet.countAccess(null, RAGProxyServlet.ONE_MINUTE_MS, now));
        prop.putNum("telemetry.per-hour", RAGProxyServlet.countAccess(null, RAGProxyServlet.ONE_HOUR_MS, now));
        prop.putNum("telemetry.per-day", RAGProxyServlet.countAccess(null, RAGProxyServlet.ONE_DAY_MS, now));

        return prop;
    }
}
