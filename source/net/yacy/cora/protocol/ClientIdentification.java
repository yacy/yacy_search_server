/**
 *  ClientIdentification
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 26.04.2011 at https://yacy.net
 *
 *  $LastChangedDate: 2011-04-21 23:59:56 +0200 (Do, 21 Apr 2011) $
 *  $LastChangedRevision: 7673 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package net.yacy.cora.protocol;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ClientIdentification {

    public static final int clientTimeoutInit = 30000;
    public static final int minimumLocalDeltaInit  =  10; // the minimum time difference between access of the same local domain
    public static final int minimumGlobalDeltaInit = 250; // the minimum time difference between access of the same global domain

    public static class Agent {
        private final String[] userAgents; // the name that is send in http request to identify the agent
        private final String[] robotIDs;   // the name that is used in robots.txt to identify the agent; might be null or empty to signal that no robots.txt must be loaded (should only be use for browser agents)
        private final int    minimumDelta; // the minimum delay between two accesses
        private final int    clientTimeout;

        public Agent(final String[] userAgents, final String[] robotIDs, final int minimumDelta, final int clientTimeout) {
            this.userAgents = userAgents;
            this.robotIDs = robotIDs;
            this.minimumDelta = minimumDelta;
            this.clientTimeout = clientTimeout;
        }

        public String userAgent() {
            return this.userAgents.length == 1 ? this.userAgents[0] : this.userAgents[random.nextInt(this.userAgents.length)];
        }

        public boolean isRobot() {
            return this.robotIDs != null && this.robotIDs.length > 0;
        }

        public String[] robotIDs() {
            return this.robotIDs == null || this.robotIDs.length == 0 ? new String[] {"Mozilla"} : this.robotIDs;
        }

        public int minimumDelta() {
            return this.minimumDelta;
        }

        public int clientTimeout() {
            return this.clientTimeout;
        }
    }

    private final static String[] browserAgents = new String[]{
        // see https://github.com/yacy/yacy_search_server/issues/727
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.3",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.3",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36"
    };
    private static final Random random = new Random(System.currentTimeMillis());
    private static Map<String, Agent> agents = new ConcurrentHashMap<>();
    public final static String yacyInternetCrawlerAgentName = "YaCy Internet (cautious)";
    public static Agent yacyInternetCrawlerAgent = null; // defined later in static
    public final static String yacyIntranetCrawlerAgentName = "YaCy Intranet (greedy)";
    public static Agent yacyIntranetCrawlerAgent = null; // defined later in static
    public final static String googleAgentName = "Googlebot";
    //public final static Agent googleAgentAgent = new Agent("Googlebot/2.1 (+http://www.google.com/bot.html)", new String[]{"Googlebot", "Googlebot-Mobile"}, minimumGlobalDeltaInit / 10, clientTimeoutInit);
    public final static Agent googleAgentAgent = new Agent(new String[]{"Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"}, new String[]{"Googlebot", "Googlebot-Mobile"}, minimumGlobalDeltaInit / 10, clientTimeoutInit); // see https://github.com/yacy/yacy_search_server/issues/727
    public final static String yacyProxyAgentName = "YaCyProxy";
    public final static Agent yacyProxyAgent = new Agent(new String[]{"yacy - this is a proxy access through YaCy from a browser, not a robot (the yacy bot user agent is 'yacybot')"}, new String[]{"yacy"}, minimumGlobalDeltaInit, clientTimeoutInit);
    public final static String customAgentName = "Custom Agent";
    public final static String browserAgentName = "Random Browser";
    public static Agent browserAgent;

    /**
     * provide system information (this is part of YaCy protocol)
     */
    public static final String yacySystem = System.getProperty("os.arch", "no-os-arch") + " " +
            System.getProperty("os.name", "no-os-name") + " " + System.getProperty("os.version", "no-os-version") +
            "; " + "java " + System.getProperty("java.version", "no-java-version") + "; " + generateLocation(); // keep this before the following static initialization block as this constant is used by generateYaCyBot()

    static {
        generateYaCyBot("new");
        browserAgent = new Agent(browserAgents, new String[0], minimumGlobalDeltaInit, clientTimeoutInit);
        agents.put(googleAgentName, googleAgentAgent);
        agents.put(browserAgentName, browserAgent);
        agents.put(yacyProxyAgentName, yacyProxyAgent);
    }

    /**
     * produce a YaCy user agent string
     * @param addinfo
     * @return
     */
    public static void generateYaCyBot(final String addinfo) {
        final String agentString = "yacybot (" + addinfo + "; " + yacySystem  + ") https://yacy.net/bot.html";
        yacyInternetCrawlerAgent = new Agent(new String[]{agentString}, new String[]{"yacybot"}, minimumGlobalDeltaInit, clientTimeoutInit);
        yacyIntranetCrawlerAgent = new Agent(new String[]{agentString}, new String[]{"yacybot"}, minimumLocalDeltaInit, clientTimeoutInit); // must have the same userAgent String as the web crawler because this is also used for snippets
        agents.put(yacyInternetCrawlerAgentName, yacyInternetCrawlerAgent);
        agents.put(yacyIntranetCrawlerAgentName, yacyIntranetCrawlerAgent);
    }

    public static void generateCustomBot(final String name, final String string, final int minimumdelta, final int clienttimeout) {
        if (name.toLowerCase().indexOf("yacy") >= 0 || string.toLowerCase().indexOf("yacy") >= 0) return; // don't allow 'yacy' in custom bot strings
        final String agentString = string.replace("$$SYSTEM$$", yacySystem.replace("java", "O"));
        agents.put(customAgentName, new Agent(new String[]{agentString}, new String[]{name}, minimumdelta, clienttimeout));
    }

    /**
     * get the default agent
     * @param newagent
     */
    public static Agent getAgent(final String agentName) {
        if (agentName == null || agentName.length() == 0) return yacyInternetCrawlerAgent;
        final Agent agent = agents.get(agentName);
        return agent == null ? yacyInternetCrawlerAgent : agent;
    }

    /**
     * generating the location string
     *
     * @return
     */
    public static String generateLocation() {
        String loc = System.getProperty("user.timezone", "nowhere");
        final int p = loc.indexOf('/');
        if (p > 0) {
            loc = loc.substring(0, p);
        }
        loc = loc + "/" + System.getProperty("user.language", "dumb");
        return loc;
    }

    /**
     * gets the location out of the user agent
     *
     * location must be after last ; and before first )
     *
     * @param userAgent in form "useragentinfo (some params; _location_) additional info"
     * @return
     */
    public static String parseLocationInUserAgent(final String userAgent) {
        final String location;

        final int firstOpenParenthesis = userAgent.indexOf('(');
        final int lastSemicolon = userAgent.lastIndexOf(';');
        final int firstClosedParenthesis = userAgent.indexOf(')');

        if (lastSemicolon < firstClosedParenthesis) {
            // ; Location )
            location = (firstClosedParenthesis > 0) ? userAgent.substring(lastSemicolon + 1, firstClosedParenthesis)
                    .trim() : userAgent.substring(lastSemicolon + 1).trim();
        } else {
            if (firstOpenParenthesis < userAgent.length()) {
                if (firstClosedParenthesis > firstOpenParenthesis) {
                    // ( Location )
                    location = userAgent.substring(firstOpenParenthesis + 1, firstClosedParenthesis).trim();
                } else {
                    // ( Location <end>
                    location = userAgent.substring(firstOpenParenthesis + 1).trim();
                }
            } else {
                location = "";
            }
        }

        return location;
    }
}
