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

public class ClientIdentification {

    /**
     * provide system information (this is part of YaCy protocol)
     */
    public static final String yacySystem = System.getProperty("os.arch", "no-os-arch") + " " +
            System.getProperty("os.name", "no-os-name") + " " + System.getProperty("os.version", "no-os-version") +
            "; " + "java " + System.getProperty("java.version", "no-java-version") + "; " + generateLocation();

    /**
     * the default user agent: YaCy
     */
    private static String agent = generateYaCyBot("new");
    
    /**
     * produce a YaCy user agent string
     * @param addinfo
     * @return
     */
    public static String generateYaCyBot(String addinfo) {
        return "yacybot (" + addinfo + "; " + yacySystem  + ") http://yacy.net/bot.html";
    }
    
    /**
     * set the user agent
     * @param newagent
     */
    public static void setUserAgent(String newagent) {
        agent = newagent;
    }
    
    /**
     * produce a userAgent String for this cora client
     * @return
     */
    public static String getUserAgent() {
        return agent;
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
