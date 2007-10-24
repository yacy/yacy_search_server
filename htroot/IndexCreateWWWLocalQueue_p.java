// IndexCreateWWWCrawlQueue_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 04.07.2005
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class IndexCreateWWWLocalQueue_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(Date date) {
        if (date == null) return "";
        return dayFormatter.format(date);
    }
    
    private static final int INVALID    = 0;
    private static final int URL        = 1;
    private static final int ANCHOR     = 2;
    private static final int PROFILE    = 3;
    private static final int DEPTH      = 4;
    private static final int INITIATOR  = 5;
    private static final int MODIFIED   = 6;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
 
        int showLimit = 100;
        if (post != null) {
            if (post.containsKey("limit")) {
                try {
                    showLimit = Integer.valueOf((String)post.get("limit")).intValue();
                } catch (NumberFormatException e) {}
            }
            
            if (post.containsKey("deleteEntries")) {
                int c = 0;
                
                String pattern = post.get("pattern", ".*").trim();
                final int option  = post.getInt("option", INVALID);
                if (pattern.equals(".*")) {
                    c = switchboard.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
                    switchboard.noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_CORE);
                    try { switchboard.cleanProfiles(); } catch (InterruptedException e) {/* ignore this */}
                } else if (option > INVALID) {
                    Pattern compiledPattern = null;
                    try {
                        // compiling the regular expression
                        compiledPattern = Pattern.compile(pattern);
                        
                        if (option == PROFILE) {
                            // search and delete the crawl profile (_much_ faster, independant of queue size)
                            // XXX: what to do about the annoying LOST PROFILE messages in the log?
                            Iterator it = switchboard.profilesActiveCrawls.profiles(true);
                            plasmaCrawlProfile.entry entry;
                            while (it.hasNext()) {
                                entry = (plasmaCrawlProfile.entry)it.next();
                                final String name = entry.name();
                                if (name.equals(plasmaSwitchboard.CRAWL_PROFILE_PROXY) ||
                                        name.equals(plasmaSwitchboard.CRAWL_PROFILE_REMOTE) ||
                                        name.equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_TEXT) ||
                                        name.equals(plasmaSwitchboard.CRAWL_PROFILE_SNIPPET_MEDIA))
                                    continue;
                                if (compiledPattern.matcher(name).find()) {
                                    switchboard.profilesActiveCrawls.removeEntry(entry.handle());
                                }
                            }
                        } else {
                            // iterating through the list of URLs
                            Iterator iter = switchboard.noticeURL.iterator(plasmaCrawlNURL.STACK_TYPE_CORE);
                            plasmaCrawlEntry entry;
                            while (iter.hasNext()) {
                                if ((entry = (plasmaCrawlEntry) iter.next()) == null) continue;
                                String value = null;
                                
                                switch (option) {
                                    case URL:       value = (entry.url() == null) ? null : entry.url().toString(); break;
                                    case ANCHOR:    value = entry.name(); break;
                                    case DEPTH:     value = Integer.toString(entry.depth()); break;
                                    case INITIATOR:
                                        value = (entry.initiator() == null) ? "proxy" : entry.initiator();
                                        break;
                                    case MODIFIED:  value = daydate(entry.loaddate()); break;
                                    default: value = null;
                                }
                                
                                if (value != null) {
                                    Matcher matcher = compiledPattern.matcher(value);
                                    if (matcher.find()) {
                                        switchboard.noticeURL.removeByURLHash(entry.url().hash());
                                    }                                    
                                }
                            }
                        }
                    } catch (PatternSyntaxException e) {
                        e.printStackTrace();
                    }
                }
                
                prop.put("info", "3");//crawling queue cleared
                prop.putNum("info_numEntries", c);
            } else if (post.containsKey("deleteEntry")) {
                String urlHash = (String) post.get("deleteEntry");
                switchboard.noticeURL.removeByURLHash(urlHash);
                prop.put("LOCATION","");
                return prop;
            }
        }

        int showNum = 0, stackSize = switchboard.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
        if (stackSize == 0) {
            prop.put("crawler-queue", "0");
        } else {
            prop.put("crawler-queue", "1");
            plasmaCrawlEntry[] crawlerList = switchboard.noticeURL.top(plasmaCrawlNURL.STACK_TYPE_CORE, (int) (showLimit * 1.20));

            plasmaCrawlEntry urle;
            boolean dark = true;
            yacySeed initiator;
            String profileHandle;
            plasmaCrawlProfile.entry profileEntry;
            int i;
            for (i = 0; (i < crawlerList.length) && (showNum < showLimit); i++) {
                urle = crawlerList[i];
                if ((urle != null)&&(urle.url()!=null)) {
                    initiator = yacyCore.seedDB.getConnected(urle.initiator());
                    profileHandle = urle.profileHandle();
                    profileEntry = (profileHandle == null) ? null : switchboard.profilesActiveCrawls.getEntry(profileHandle);
                    prop.put("crawler-queue_list_"+showNum+"_dark", dark ? "1" : "0");
                    prop.put("crawler-queue_list_"+showNum+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                    prop.put("crawler-queue_list_"+showNum+"_profile", ((profileEntry == null) ? "unknown" : profileEntry.name()));
                    prop.put("crawler-queue_list_"+showNum+"_depth", urle.depth());
                    prop.put("crawler-queue_list_"+showNum+"_modified", daydate(urle.loaddate()) );
                    prop.putHTML("crawler-queue_list_"+showNum+"_anchor", urle.name());
                    prop.put("crawler-queue_list_"+showNum+"_url", urle.url().toNormalform(false, true));
                    prop.put("crawler-queue_list_"+showNum+"_hash", urle.url().hash());
                    dark = !dark;
                    showNum++;
                } else {
                    stackSize--;
                }
            }
            prop.putNum("crawler-queue_list", showNum);
            prop.putNum("crawler-queue_num", stackSize);//num Entries
            prop.putNum("crawler-queue_show-num", showNum); //showin sjow-num most recent

        }

        // return rewrite properties
        return prop;
    }
}
