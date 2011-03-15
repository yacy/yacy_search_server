// IndexCreateWWWLocalQueue_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// You must compile this file with
// javac -classpath .:../classes IndexCreate_p.java
// if the shell's current path is HTROOT

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.NoticedURL;
import de.anomic.crawler.CrawlSwitchboard;
import de.anomic.crawler.retrieval.Request;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class IndexCreateWWWLocalQueue_p {
    
    private static SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    private static String daydate(final Date date) {
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

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
 
        int showLimit = 100;
        if (post != null) {
            showLimit = post.getInt("limit", 100);
            
            if (post.containsKey("deleteEntries")) {
                int c = 0;
                
                final String pattern = post.get("pattern", ".*").trim();
                final int option  = post.getInt("option", INVALID);
                if (".*".equals(pattern)) {
                    c = sb.crawlQueues.noticeURL.stackSize(NoticedURL.StackType.CORE);
                    sb.crawlQueues.noticeURL.clear(NoticedURL.StackType.CORE);
                    try { sb.cleanProfiles(); } catch (final InterruptedException e) {/* ignore this */}
                } else if (option > INVALID) {
                    try {
                        // compiling the regular expression
                        final Pattern compiledPattern = Pattern.compile(pattern);
                        
                        if (option == PROFILE) {
                            // search and delete the crawl profile (_much_ faster, independant of queue size)
                            // XXX: what to do about the annoying LOST PROFILE messages in the log?
                            CrawlProfile entry;
                            for (final byte[] handle: sb.crawler.getActive()) {
                                entry = sb.crawler.getActive(handle);
                                final String name = entry.name();
                                if (name.equals(CrawlSwitchboard.CRAWL_PROFILE_PROXY) ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_REMOTE) ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_TEXT)  ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT)  ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_LOCAL_MEDIA) ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_MEDIA) ||
                                        name.equals(CrawlSwitchboard.CRAWL_PROFILE_SURROGATE))
                                    continue;
                                if (compiledPattern.matcher(name).find()) sb.crawler.removeActive(entry.handle().getBytes());
                            }
                        } else {
                            // iterating through the list of URLs
                            final Iterator<Request> iter = sb.crawlQueues.noticeURL.iterator(NoticedURL.StackType.CORE);
                            Request entry;
                            final List<byte[]> removehashes = new ArrayList<byte[]>();
                            while (iter.hasNext()) {
                                if ((entry = iter.next()) == null) continue;
                                String value = null;
                                
                                location: switch (option) {
                                    case URL:       value = (entry.url() == null) ? null : entry.url().toString(); break location;
                                    case ANCHOR:    value = entry.name(); break location;
                                    case DEPTH:     value = Integer.toString(entry.depth()); break location;
                                    case INITIATOR:
                                        value = (entry.initiator() == null || entry.initiator().length == 0) ? "proxy" : UTF8.String(entry.initiator());
                                        break location;
                                    case MODIFIED:  value = daydate(entry.appdate()); break location;
                                    default: value = null; break location;
                                }
                                
                                if (value != null && compiledPattern.matcher(value).matches()) removehashes.add(entry.url().hash());
                            }
                            Log.logInfo("IndexCreateWWWLocalQueue", "created a remove list with " + removehashes.size() + " entries for pattern '" + pattern + "'");
                            for (final byte[] b: removehashes) {
                                sb.crawlQueues.noticeURL.removeByURLHash(b);
                            }
                        }
                    } catch (final PatternSyntaxException e) {
                        Log.logException(e);
                    }
                }
                
                prop.put("info", "3");//crawling queue cleared
                prop.putNum("info_numEntries", c);
            } else if (post.containsKey("deleteEntry")) {
                final String urlHash = post.get("deleteEntry");
                sb.crawlQueues.noticeURL.removeByURLHash(urlHash.getBytes());
                prop.put("LOCATION","");
                return prop;
            }
        }

        int showNum = 0, stackSize = sb.crawlQueues.noticeURL.stackSize(NoticedURL.StackType.CORE);
        if (stackSize == 0) {
            prop.put("crawler-queue", "0");
        } else {
            prop.put("crawler-queue", "1");
            final List<Request> crawlerList = sb.crawlQueues.noticeURL.top(NoticedURL.StackType.CORE, (int) (showLimit * 1.20));

            Request urle;
            boolean dark = true;
            yacySeed initiator;
            String profileHandle;
            CrawlProfile profileEntry;
            int i;
            for (i = 0; (i < crawlerList.size()) && (showNum < showLimit); i++) {
                urle = crawlerList.get(i);
                if ((urle != null)&&(urle.url()!=null)) {
                    initiator = sb.peers.getConnected(urle.initiator() == null ? "" : UTF8.String(urle.initiator()));
                    profileHandle = urle.profileHandle();
                    profileEntry = profileHandle == null ? null : sb.crawler.getActive(profileHandle.getBytes());
                    prop.put("crawler-queue_list_"+showNum+"_dark", dark ? "1" : "0");
                    prop.putHTML("crawler-queue_list_"+showNum+"_initiator", ((initiator == null) ? "proxy" : initiator.getName()) );
                    prop.put("crawler-queue_list_"+showNum+"_profile", ((profileEntry == null) ? "unknown" : profileEntry.name()));
                    prop.put("crawler-queue_list_"+showNum+"_depth", urle.depth());
                    prop.put("crawler-queue_list_"+showNum+"_modified", daydate(urle.appdate()) );
                    prop.putHTML("crawler-queue_list_"+showNum+"_anchor", urle.name());
                    prop.putHTML("crawler-queue_list_"+showNum+"_url", urle.url().toNormalform(false, true));
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
