/*
  robotsParser.java
  -------------------------------------
  part of YACY

  (C) 2005, 2006 by Alexander Schier
                    Martin Thelian

  last change: $LastChangedDate$LastChangedBy: orbiter $
  Revision: $LastChangedRevision$

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General public License for more details.

  You should have received a copy of the GNU General private License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

   extended to return structured objects instead of a Object[] and
   extended to return a Allow-List by Michael Christen, 21.07.2008
   extended to allow multiple user agents given by definition and
   returning the used user agent my Michael Christen 3.4.2011
*/

package net.yacy.crawler.robots;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.UTF8;

/*
 * A class for Parsing robots.txt files.
 * It only parses the Deny Part, yet.
 *
 * Robots RFC
 * http://www.robotstxt.org/wc/norobots-rfc.html
 *
 * TODO:
 *      - On the request attempt resulted in temporary failure a robot
 *      should defer visits to the site until such time as the resource
 *      can be retrieved.
 *
 *      - Extended Standard for Robot Exclusion
 *        See: http://www.conman.org/people/spc/robots2.html
 *
 *      - Robot Exclusion Standard Revisited
 *        See: http://www.kollar.com/robots.html
 */

public final class RobotsTxtParser {

    private static final Pattern patternTab = Pattern.compile("\t");

	private static final String ROBOTS_USER_AGENT = "User-agent:".toUpperCase();
    private static final String ROBOTS_DISALLOW = "Disallow:".toUpperCase();
    private static final String ROBOTS_ALLOW = "Allow:".toUpperCase();
    private static final String ROBOTS_COMMENT = "#";
    private static final String ROBOTS_SITEMAP = "Sitemap:".toUpperCase();
    private static final String ROBOTS_CRAWL_DELAY = "Crawl-delay:".toUpperCase();

    private final ArrayList<String> allowList;
    private final ArrayList<String> denyList;
    private       ArrayList<String> sitemaps;
    private       long crawlDelayMillis;
    private final String[] myNames; // a list of own name lists
    private       String agentName; // the name of the agent that was used to return the result

    protected RobotsTxtParser(final String[] myNames) {
        this.allowList = new ArrayList<String>(0);
        this.denyList = new ArrayList<String>(0);
        this.sitemaps = new ArrayList<String>(0);
        this.crawlDelayMillis = 0;
        this.myNames = myNames;
        this.agentName = null;
    }

    protected RobotsTxtParser(final String[] myNames, final byte[] robotsTxt) {
        this(myNames);
        if (robotsTxt != null && robotsTxt.length != 0) {
            final ByteArrayInputStream bin = new ByteArrayInputStream(robotsTxt);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
            parse(reader);
        }
    }

    private void parse(final BufferedReader reader) {
        final ArrayList<String> deny4AllAgents = new ArrayList<String>();
        final ArrayList<String> deny4ThisAgents = new ArrayList<String>();
        final ArrayList<String> allow4AllAgents = new ArrayList<String>();
        final ArrayList<String> allow4ThisAgents = new ArrayList<String>();

        int pos;
        String line = null, lineUpper = null;
        boolean isRule4AllAgents = false,
                isRule4ThisAgents = false,
                rule4ThisAgentsFound = false,
                inBlock = false;

        try {
            lineparser: while ((line = reader.readLine()) != null) {
                // replacing all tabs with spaces
                line = patternTab.matcher(line).replaceAll(" ").trim();
                lineUpper = line.toUpperCase();

                // parse empty line
                if (line.isEmpty()) {
                    // we have reached the end of the rule block
                    continue lineparser;
                }

                // parse comment
                if (line.startsWith(ROBOTS_COMMENT)) {
                    // we can ignore this. Just a comment line
                    continue lineparser;
                }

                // parse sitemap; if there are several sitemaps then take the first url
                // TODO: support for multiple sitemaps
                if (lineUpper.startsWith(ROBOTS_SITEMAP)) {
                    pos = line.indexOf(' ');
                    if (pos != -1) {
                        this.sitemaps.add(line.substring(pos).trim());
                    }
                    continue lineparser;
                }

                // parse user agent
                if (lineUpper.startsWith(ROBOTS_USER_AGENT)) {

                    if (inBlock) {
                        // we have detected the start of a new block
                        inBlock = false;
                        isRule4AllAgents = false;
                        isRule4ThisAgents = false;
                        this.crawlDelayMillis = 0; // each block has a separate delay
                    }

                    // cutting off comments at the line end
                    pos = line.indexOf(ROBOTS_COMMENT);
                    if (pos != -1) line = line.substring(0,pos).trim();

                    // getting out the robots name
                    pos = line.indexOf(' ');
                    if (pos != -1) {
                        final String userAgent = line.substring(pos).trim();
                        isRule4AllAgents |= userAgent.equals("*");
                        for (final String agent: this.myNames) {
                            if (userAgent.toLowerCase().equals(agent.toLowerCase())) {
                                this.agentName = agent;
                                isRule4ThisAgents = true;
                                break;
                            }
                        }
                        if (isRule4ThisAgents) rule4ThisAgentsFound = true;
                    }
                    continue lineparser;
                }

                // parse crawl delay
                if (lineUpper.startsWith(ROBOTS_CRAWL_DELAY)) {
                    inBlock = true;
                	if (isRule4ThisAgents || isRule4AllAgents) {
                		pos = line.indexOf(' ');
                		if (pos != -1) {
                			try {
                				// the crawl delay can be a float number and means number of seconds
                				this.crawlDelayMillis = (long) (1000.0 * Float.parseFloat(line.substring(pos).trim()));
                			} catch (final NumberFormatException e) {
                				// invalid crawling delay
                			}
                		}
                	}
                	continue lineparser;
                }

                // parse disallow
                if (lineUpper.startsWith(ROBOTS_DISALLOW) || lineUpper.startsWith(ROBOTS_ALLOW)) {
                    inBlock = true;
                    final boolean isDisallowRule = lineUpper.startsWith(ROBOTS_DISALLOW);

                    if (isRule4ThisAgents || isRule4AllAgents) {
                        // cutting off comments at the line end
                        pos = line.indexOf(ROBOTS_COMMENT);
                        if (pos != -1) line = line.substring(0,pos).trim();

                        // cut off tailing *
                        if (line.endsWith("*")) line = line.substring(0,line.length()-1);

                        // parse the path
                        pos = line.indexOf(' ');
                        if (pos >= 0) {
                            // getting the path
                            String path = line.substring(pos).trim();

                            // unencoding all special charsx
                            try {
                                path = UTF8.decodeURL(path);
                            } catch (final Exception e) {
                                /*
                                 * url decoding failed. E.g. because of
                                 * "Incomplete trailing escape (%) pattern"
                                 */
                            }

                            // escaping all occurences of ; because this char is used as special char in the Robots DB
                            path = RobotsTxt.ROBOTS_DB_PATH_SEPARATOR_MATCHER.matcher(path).replaceAll("%3B");

                            // adding it to the pathlist
                            if (isDisallowRule) {
                                if (isRule4AllAgents) deny4AllAgents.add(path);
                                if (isRule4ThisAgents) deny4ThisAgents.add(path);
                            } else {
                                if (isRule4AllAgents) allow4AllAgents.add(path);
                                if (isRule4ThisAgents) allow4ThisAgents.add(path);
                            }
                        }
                    }
                    continue lineparser;
                }
            }
        } catch (final IOException e) {}

        this.allowList.addAll(rule4ThisAgentsFound ? allow4ThisAgents : allow4AllAgents);
        this.denyList.addAll(rule4ThisAgentsFound ? deny4ThisAgents : deny4AllAgents);
    }

    /**
     * a crawl delay can be assigned to every agent or for all agents
     * a special case is where the user agent of this yacy peer is given explicitely
     * using the peer name and then if the crawl delay is given as '0' the crawler
     * does not make any no-DOS-forced crawl pause.
     * @return the crawl delay between two crawl access times in milliseconds
     */
    protected long crawlDelayMillis() {
        return this.crawlDelayMillis;
    }

    /**
     * the user agent that was applied to get the crawl properties is recorded
     * because it is possible that this robots.txt parser applies to several user agents
     * which may be i.e. 'yacy', 'yacybot', <peer-name>'.yacy' or <peer-hash>'.yacyh'
     * Effects: see also comment to crawlDelayMillis()
     * @return the name of the user agent that was used for the result properties or null if no user agent name was used to identify the agent
     */
    protected String agentName() {
        return this.agentName;
    }

    protected ArrayList<String> sitemap() {
        return this.sitemaps;
    }

    protected ArrayList<String> allowList() {
        return this.allowList;
    }

    protected ArrayList<String> denyList() {
        return this.denyList;
    }
}
