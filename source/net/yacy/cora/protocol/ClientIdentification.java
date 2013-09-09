/**
 *  ClientIdentification
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 26.04.2011 at http://yacy.net
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

    public static final int clientTimeoutInit = 10000;
    public static final int minimumLocalDeltaInit  =  10; // the minimum time difference between access of the same local domain
    public static final int minimumGlobalDeltaInit = 500; // the minimum time difference between access of the same global domain
    
    public static class Agent {
        public final String userAgent;    // the name that is send in http request to identify the agent
        public final String[] robotIDs;     // the name that is used in robots.txt to identify the agent
        public final int    minimumDelta; // the minimum delay between two accesses
        public final int    clientTimeout;
        public Agent(final String userAgent, final String[] robotIDs, final int minimumDelta, final int clientTimeout) {
            this.userAgent = userAgent;
            this.robotIDs = robotIDs;
            this.minimumDelta = minimumDelta;
            this.clientTimeout = clientTimeout;
        }
    }
    
    private final static String[] browserAgents = new String[]{ // fake browser user agents are NOT AVAILABLE IN P2P OPERATION, only on special customer configurations (commercial users demanded this, I personally think this is inadvisable)
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:22.0) Gecko/20100101 Firefox/22.0",
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.72 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_4) AppleWebKit/536.30.1 (KHTML, like Gecko) Version/6.0.5 Safari/536.30.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.71 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36",
        "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36",
        "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; WOW64; Trident/6.0)",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:22.0) Gecko/20100101 Firefox/22.0",
        "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:23.0) Gecko/20100101 Firefox/23.0",
        "Mozilla/5.0 (Windows NT 5.1; rv:22.0) Gecko/20100101 Firefox/22.0",
        "Mozilla/5.0 (Windows NT 6.1; rv:22.0) Gecko/20100101 Firefox/22.0",
        "Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.72 Safari/537.36",
        "Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:21.0) Gecko/20100101 Firefox/21.0"
        };
    private static final Random random = new Random(System.currentTimeMillis());
    private static Map<String, Agent> agents = new ConcurrentHashMap<String, Agent>();
    public final static String yacyInternetCrawlerAgentName = "YaCy Internet (cautious)";
    public static Agent yacyInternetCrawlerAgent = null; // defined later in static
    public final static String yacyIntranetCrawlerAgentName = "YaCy Intranet (greedy)";
    public static Agent yacyIntranetCrawlerAgent = null; // defined later in static
    public final static String googleAgentName = "Googlebot";
    public final static Agent googleAgentAgent = new Agent("Googlebot/2.1 (+http://www.google.com/bot.html)", new String[]{"Googlebot", "Googlebot-Mobile"}, minimumGlobalDeltaInit / 2, clientTimeoutInit);
    public final static String browserAgentName = "Random Browser";
    public final static Agent browserAgent = new Agent(browserAgents[random.nextInt(browserAgents.length)], new String[]{"Mozilla"}, minimumLocalDeltaInit, clientTimeoutInit);
    public final static String yacyProxyAgentName = "YaCyProxy";
    public final static Agent yacyProxyAgent = new Agent("yacy - this is a proxy access through YaCy from a browser, not a robot (the yacy bot user agent is 'yacybot')", new String[]{"yacy"}, minimumGlobalDeltaInit, clientTimeoutInit);

    static {
        generateYaCyBot("new");
        agents.put(googleAgentName, googleAgentAgent);
        agents.put(browserAgentName, browserAgent);
        agents.put(yacyProxyAgentName, yacyProxyAgent);
    }
    
    /**
     * provide system information (this is part of YaCy protocol)
     */
    public static final String yacySystem = System.getProperty("os.arch", "no-os-arch") + " " +
            System.getProperty("os.name", "no-os-name") + " " + System.getProperty("os.version", "no-os-version") +
            "; " + "java " + System.getProperty("java.version", "no-java-version") + "; " + generateLocation();

    /**
     * produce a YaCy user agent string
     * @param addinfo
     * @return
     */
    public static void generateYaCyBot(String addinfo) {
        String agentString = "yacybot (" + addinfo + "; " + yacySystem  + ") http://yacy.net/bot.html";
        yacyInternetCrawlerAgent = new Agent(agentString, new String[]{"yacybot"}, minimumGlobalDeltaInit, clientTimeoutInit);
        yacyIntranetCrawlerAgent = new Agent(agentString, new String[]{"yacybot"}, minimumLocalDeltaInit, clientTimeoutInit); // must have the same userAgent String as the web crawler because this is also used for snippets
        agents.put(yacyInternetCrawlerAgentName, yacyInternetCrawlerAgent);
        agents.put(yacyIntranetCrawlerAgentName, yacyIntranetCrawlerAgent);
    }

    /**
     * get the default agent
     * @param newagent
     */
    public static Agent getAgent(String agentName) {
        if (agentName == null || agentName.length() == 0) return yacyInternetCrawlerAgent;
        Agent agent = agents.get(agentName);
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
