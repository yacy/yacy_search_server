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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaCrawlNURL.Entry;
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
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
 
        if (post != null) {
            if (post.containsKey("deleteEntries")) {
                int c = 0;
                
                String pattern = post.get("pattern", ".*").trim();
                String option  = post.get("option", ".*").trim();
                if (pattern.equals(".*")) {
                    c = switchboard.urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
                    switchboard.urlPool.noticeURL.clear(plasmaCrawlNURL.STACK_TYPE_CORE);
                    switchboard.cleanProfiles();
                } else{
                    Pattern compiledPattern = null;
                    try {
                        // compiling the regular expression
                        compiledPattern = Pattern.compile(pattern);
                        
                        // iterating through the list of URLs
                        Iterator iter = switchboard.urlPool.noticeURL.iterator(plasmaCrawlNURL.STACK_TYPE_CORE);
                        while (iter.hasNext()) {
                            String value = null;
                            String nextHash = new String((byte[]) iter.next());
                            Entry entry = null;
                            try {
                                entry = switchboard.urlPool.noticeURL.getEntry(nextHash);
                            } catch (IOException e) {
                                continue;
                            }
                            if ((option.equals("URL")&&(entry.url() != null))) {
                                value = entry.url().toString();
                            } else if ((option.equals("AnchorName"))) {
                                value = entry.name();
                            } else if ((option.equals("Profile"))) {
                                String profileHandle = entry.profileHandle();
                                if (profileHandle == null) {
                                    value = "unknown";
                                } else {                                    
                                    plasmaCrawlProfile.entry profile = switchboard.profiles.getEntry(profileHandle);
                                    if (profile == null) {
                                        value = "unknown";
                                    } else {                                    
                                        value = profile.name();
                                    }
                                }
                            } else if ((option.equals("Depth"))) {
                                value = Integer.toString(entry.depth());
                            } else if ((option.equals("Initiator"))) {
                                value = (entry.initiator()==null)?"proxy":wikiCode.replaceHTML(entry.initiator());
                            } else if ((option.equals("ModifiedDate"))) {
                                value = daydate(entry.loaddate());
                            }
                            
                            if (value != null) {
                                Matcher matcher = compiledPattern.matcher(value);
                                if (matcher.find()) {
                                    switchboard.urlPool.noticeURL.remove(nextHash);
                                }                                    
                            }
                            
                        }
                    } catch (PatternSyntaxException e) {
                        e.printStackTrace();
                    }
                }
                
                prop.put("info", 3);//crawling queue cleared
                prop.put("info_numEntries", c);
            } else if (post.containsKey("deleteEntry")) {
                String urlHash = (String) post.get("deleteEntry");
                switchboard.urlPool.noticeURL.remove(urlHash);
                prop.put("LOCATION","");
                return prop;
            }
        }

        int showNum = 0, stackSize = switchboard.urlPool.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE);
        if (stackSize == 0) {
            prop.put("crawler-queue", 0);
        } else {
            prop.put("crawler-queue", 1);
            plasmaCrawlNURL.Entry[] crawlerList = switchboard.urlPool.noticeURL.top(plasmaCrawlNURL.STACK_TYPE_CORE, 120);

            plasmaCrawlNURL.Entry urle;
            boolean dark = true;
            yacySeed initiator;
            String profileHandle;
            plasmaCrawlProfile.entry profileEntry;
            int i;
            for (i = 0; (i < crawlerList.length) && (showNum < 100); i++) {
                urle = crawlerList[i];
                if ((urle != null)&&(urle.url()!=null)) {
                    initiator = yacyCore.seedDB.getConnected(urle.initiator());
                    profileHandle = urle.profileHandle();
                    profileEntry = (profileHandle == null) ? null : switchboard.profiles.getEntry(profileHandle);
                    prop.put("crawler-queue_list_"+showNum+"_dark", ((dark) ? 1 : 0) );
                    prop.put("crawler-queue_list_"+showNum+"_initiator", ((initiator == null) ? "proxy" : wikiCode.replaceHTML(initiator.getName())) );
                    prop.put("crawler-queue_list_"+showNum+"_profile", ((profileEntry == null) ? "unknown" : profileEntry.name()));
                    prop.put("crawler-queue_list_"+showNum+"_depth", urle.depth());
                    prop.put("crawler-queue_list_"+showNum+"_modified", daydate(urle.loaddate()) );
                    prop.put("crawler-queue_list_"+showNum+"_anchor", wikiCode.replaceHTML(urle.name()));
                    prop.put("crawler-queue_list_"+showNum+"_url", wikiCode.replaceHTML(urle.url().toString()));
                    prop.put("crawler-queue_list_"+showNum+"_hash", urle.hash());
                    dark = !dark;
                    showNum++;
                } else {
                    stackSize--;
                }
            }
            prop.put("crawler-queue_list", showNum);
            prop.put("crawler-queue_num", stackSize);//num Entries
            prop.put("crawler-queue_show-num", showNum); //showin sjow-num most recent

        }

        // return rewrite properties
        return prop;
    }
    
}



