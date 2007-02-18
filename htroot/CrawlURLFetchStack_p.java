// CrawlURLFetchStack_p.java 
// -------------------------------------
// part of YACY
//
// (C) 2007 by Franz Brausse
//
// last change: $LastChangedDate: $ by $LastChangedBy: $
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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.data.URLFetcherStack;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpHeader;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;

public class CrawlURLFetchStack_p {
    
    public static final HashMap /* of PeerName, sent URLs */ fetchMap = new HashMap();
    private static URLFetcherStack stack = null;
    public static int maxURLsPerFetch = 50;
    
    public static URLFetcherStack getURLFetcherStack(serverSwitch env) {
        if (stack == null) try {
            stack = new URLFetcherStack(env.getConfig(plasmaSwitchboard.DBPATH, plasmaSwitchboard.DBPATH_DEFAULT));
        } catch (IOException e) {
            serverLog.logSevere("URLFETCHER", "Couldn't initialize URL stack: " + e.getMessage());
        }
        return stack;
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard)env;
        
        if (post != null) {
            if (post.containsKey("addurls")) {
                prop.put("addedUrls", 1);
                prop.put("addedUrls_added", addURLs(post, post.getInt("addurls", -1), getURLFetcherStack(env)));
            }
            else if (post.containsKey("setMaxSize")) {
                final int count = post.getInt("maxSize", maxURLsPerFetch);
                if (count > 0) {
                    maxURLsPerFetch = count;
                    prop.put("set", 1);
                    prop.put("set_value", maxURLsPerFetch);
                } else {
                    prop.put("set", 2);
                    prop.put("set_value", count);
                }
            }
            else if (post.containsKey("shiftlcq")) {
                final int count = Math.min(post.getInt("shiftloc", 0), sb.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE));
                final int failed = shiftFromNotice(sb.noticeURL, plasmaCrawlNURL.STACK_TYPE_CORE, getURLFetcherStack(env), count);
                prop.put("shiftloc", 1);
                prop.put("shiftloc_value", count - failed);
                prop.put("shiftloc_failed", failed);
            }
            else if (post.containsKey("shiftrcq")) {
                final int count = post.getInt("shiftrem", 0);
                final int failed = shiftFromNotice(sb.noticeURL, plasmaCrawlNURL.STACK_TYPE_LIMIT, getURLFetcherStack(env), count);
                prop.put("shiftrem", 1);
                prop.put("shiftrem_value", count - failed);
                prop.put("shiftrem_failed", failed);
            }
            else if (post.containsKey("subupload")) {
                if (post.get("upload", "").length() == 0) {
                    prop.put("uploadError", 1);
                } else {
                    final File file = new File(post.get("upload", ""));
                    final String content = new String((byte[])post.get("upload$file"));
                    
                    final String type = post.get("uploadType", "");
                    if (type.equals("plain")) {
                        prop.put("upload_added", addURLs(content.split("\n"), getURLFetcherStack(env)));
                        prop.put("upload_failed", 0);
                        prop.put("upload", 1);
                    } else if (type.equals("html")) {
                        try {
                            final htmlFilterContentScraper scraper = new htmlFilterContentScraper(new URL(file));
                            final Writer writer = new htmlFilterWriter(null, null, scraper, null, false);
                            serverFileUtils.write(content, writer);
                            writer.close();
                            
                            final Iterator it = ((HashMap)scraper.getAnchors()).keySet().iterator();
                            int added = 0, failed = 0;
                            String url;
                            while (it.hasNext()) try {
                                url = (String)it.next();
                                getURLFetcherStack(env).push(new URL(url));
                                added++;
                            } catch (MalformedURLException e) { failed++; }
                            prop.put("upload", 1);
                            prop.put("upload_added", added);
                            prop.put("upload_failed", failed);
                        } catch (Exception e) {
                            e.printStackTrace();
                            prop.put("upload", 2);
                            prop.put("upload_error", e.getMessage());
                        }
                    }
                }
            }
        }
        
        putFetched(prop);
        prop.put("urlCount", getURLFetcherStack(env).size());
        prop.put("totalFetched", getURLFetcherStack(env).getPopped());
        prop.put("totalAdded", getURLFetcherStack(env).getPushed());
        prop.put("maxSize", maxURLsPerFetch);
        prop.put("locurls", sb.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE));
        prop.put("remurls", sb.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT));
        prop.put("locurlsVal", Math.min(sb.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_CORE), 500));
        prop.put("remurlsVal", Math.min(sb.noticeURL.stackSize(plasmaCrawlNURL.STACK_TYPE_LIMIT), 500));
        
        return prop;
    }
    
    private static void putFetched(serverObjects prop) {
        Iterator it = fetchMap.keySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            String key = (String)it.next();
            prop.put("peers_" + count + "_peer", key);
            prop.put("peers_" + count + "_amount", ((Integer)fetchMap.get(key)).intValue());
            count++;
        }
        prop.put("peers", count);
    }
    
    private static int addURLs(String[] urls, URLFetcherStack stack) {
        int count = -1;
        for (int i=0; i<urls.length; i++) try {
            if (urls[i].length() == 0) continue;
            stack.push(new URL(urls[i]));
            count++;
        } catch (MalformedURLException e) { /* ignore this */ }
        return count;
    }
    
    private static int shiftFromNotice(plasmaCrawlNURL nurl, int fromStackType, URLFetcherStack stack, int count) {
        plasmaCrawlNURL.Entry entry;
        int failed = 0;
        for (int i=0; i<count; i++) try {
            entry = nurl.pop(fromStackType);
            stack.push(entry.url());
        } catch (IOException e) { failed++; }
        return failed;
    }
    
    private static int addURLs(serverObjects post, int amount, URLFetcherStack stack) {
        int count = 0;
        String url;
        for (int i=0; i<amount; i++) {
            url = post.get("url" + count++, null);
            if (url == null || url.length() == 0) continue;
            try {
                stack.push(new URL(url));
                count++;
            } catch (MalformedURLException e) {
                serverLog.logInfo("URLFETCHER", "retrieved invalid url for adding to the stack: " + url);
            }
        }
        return count;
    }
}
