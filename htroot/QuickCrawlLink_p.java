//QuickCrawlLink_p.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file was contributed by Martin Thelian
//$LastChangedDate$ 
//$LastChangedBy$
//$LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../classes IndexCreate_p.java
//if the shell's current path is HTROOT


import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Date;

import de.anomic.crawler.CrawlProfile;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class QuickCrawlLink_p {
    
    /**
     * Example Javascript to call this servlet:
     * <code>javascript:w = window.open('http://user:pwd@localhost:8080/QuickCrawlLink_p.html?indexText=on&indexMedia=on&crawlingQ=on&xdstopw=on&title=' + escape(document.title) + '&url=' + location.href,'_blank','height=150,width=500,resizable=yes,scrollbar=no,directory=no,menubar=no,location=no'); w.focus();</code>
     * @param header the complete HTTP header of the request
     * @param post any arguments for this servlet, the request carried with (GET as well as POST)
     * @param env the serverSwitch object holding all runtime-data
     * @return the rewrite-properties for the template
     */
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        
        if (post == null) {
            // send back usage example
            prop.put("mode", "0");
            
            // getting the http host header
            String hostSocket = header.get(httpHeader.CONNECTION_PROP_HOST);
            
            //String host = hostSocket;
            int port = 80, pos = hostSocket.indexOf(":");
            if (pos != -1) {
                port = Integer.parseInt(hostSocket.substring(pos + 1));
                //host = hostSocket.substring(0, pos);
            }    
            
            prop.put("mode_host", "localhost");
            prop.put("mode_port", port);
            
            return prop;
        }
        prop.put("mode", "1");
        
        // getting the URL
        String crawlingStart = post.get("url",null);
        try {
            crawlingStart = URLDecoder.decode(crawlingStart, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        
        // getting the browser title
        String title = post.get("title",null);
        
        // getting other parameters if set
        String crawlingFilter  = post.get("crawlingFilter", ".*");
        int CrawlingDepth      = Integer.parseInt(post.get("crawlingDepth", "0"));        
        boolean crawlDynamic   = post.get("crawlingQ", "").equals("on");
        boolean indexText      = post.get("indexText", "on").equals("on");
        boolean indexMedia      = post.get("indexMedia", "on").equals("on");
        boolean storeHTCache   = post.get("storeHTCache", "").equals("on");
        boolean remoteIndexing = post.get("crawlOrder", "").equals("on");
        boolean xsstopw        = post.get("xsstopw", "").equals("on");
        boolean xdstopw        = post.get("xdstopw", "").equals("on");
        boolean xpstopw        = post.get("xpstopw", "").equals("on");

        prop.put("mode_url", (crawlingStart == null) ? "unknown" : crawlingStart);
        prop.putHTML("mode_title", (title == null) ? "unknown" : title);
        
        if (crawlingStart != null) {
            crawlingStart = crawlingStart.trim();
            try {crawlingStart = new yacyURL(crawlingStart, null).toNormalform(true, true);} catch (MalformedURLException e1) {}
            
            // check if url is proper
            yacyURL crawlingStartURL = null;
            try {
                crawlingStartURL = new yacyURL(crawlingStart, null);
            } catch (MalformedURLException e) {
                prop.put("mode_status", "1");
                prop.put("mode_code", "1");
                return prop;
            }
                    
            String urlhash = crawlingStartURL.hash();
            sb.webIndex.removeURL(urlhash);
            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
            sb.crawlQueues.errorURL.remove(urlhash);
            
            // create crawling profile
            CrawlProfile.entry pe = null;
            try {
                pe = sb.webIndex.profilesActiveCrawls.newEntry(
                        crawlingStartURL.getHost(), 
                        crawlingStartURL, 
                        crawlingFilter, 
                        crawlingFilter, 
                        CrawlingDepth, 
                        CrawlingDepth, 
                        60 * 24 * 30, // recrawlIfOlder (minutes); here: one month
                        -1, // domFilterDepth, if negative: no auto-filter
                        -1, // domMaxPages, if negative: no count restriction
                        crawlDynamic,
                        indexText,
                        indexMedia,
                        storeHTCache,
                        true,
                        remoteIndexing,
                        xsstopw,
                        xdstopw,
                        xpstopw
                );
            } catch (Exception e) {
                // mist
                prop.put("mode_status", "2");//Error with url
                prop.put("mode_code", "2");
                prop.putHTML("mode_status_error", e.getMessage());
                return prop;
            }
            
            // stack URL
            String reasonString = null;
            reasonString = sb.crawlStacker.stackCrawl(
                        crawlingStartURL, 
                        null, 
                        sb.webIndex.seedDB.mySeed().hash, 
                        (title==null)?"CRAWLING-ROOT":title, 
                                new Date(), 
                                0, 
                                pe
                );
            
            // validate rejection reason
            if (reasonString == null) {
                prop.put("mode_status", "0");//start msg
                prop.put("mode_code", "0");
            } else {
                prop.put("mode_status", "3");//start msg
                prop.put("mode_code","3");
                prop.putHTML("mode_status_error", reasonString);
            }
        }
        
        return prop;
    }
}
