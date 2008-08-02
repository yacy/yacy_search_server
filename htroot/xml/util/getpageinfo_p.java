// /xml/util/gettitle_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// last major change: 29.12.2005
// this file is contributed by Alexander Schier
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
package xml.util;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;

import de.anomic.crawler.HTTPLoader;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class getpageinfo_p {
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("sitemap", "");
        prop.put("title", "");
        prop.put("favicon","");
        prop.put("robots-allowed", "3"); //unknown
        String actions="title";
        if(post!=null && post.containsKey("url")){
            if(post.containsKey("actions"))
                actions=post.get("actions");
            String url=post.get("url");
			if(url.toLowerCase().startsWith("ftp://")){
				prop.put("robots-allowed", "1");
				prop.putHTML("title", "FTP: "+url, true);
                return prop;
			} else if (!(url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://"))) {
                url = "http://" + url;
            }
            if (actions.indexOf("title")>=0) {
                try {
                    final yacyURL u = new yacyURL(url, null);
                    final httpHeader reqHeader = new httpHeader();
                    reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent); // do not set the crawler user agent, because this page was loaded by manual entering of the url
                    final byte[] r = HttpClient.wget(u.toString(), reqHeader, 5000);
                    if (r == null) return prop;
                    final String contentString=new String(r);
                    
                    final htmlFilterContentScraper scraper = new htmlFilterContentScraper(u);
                    //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                    final Writer writer = new htmlFilterWriter(null,null,scraper,null,false);
                    serverFileUtils.copy(contentString,writer);
                    writer.close();
                    
                    // put the document title 
                    prop.putHTML("title", scraper.getTitle(), true);
                    
                    // put the favicon that belongs to the document
                    prop.put("favicon", (scraper.getFavicon()==null) ? "" : scraper.getFavicon().toString());
                    
                    // put keywords
                    final String list[]=scraper.getKeywords();
                    int count = 0;
                    for(int i=0;i<list.length;i++){
                    	String tag = list[i];
                    	if (!tag.equals("")) {
                    		while (i<(list.length-1) && !list[i+1].equals("")) {
                    			i++;
                    			tag += " "+list[i];                    			
                    		}                    	                 	
                    		prop.putHTML("tags_"+count+"_tag", tag, true);
                    		count++;
                    	}
                    }
                    prop.put("tags", count);

                } catch (final MalformedURLException e) { /* ignore this */
                } catch (final IOException e) { /* ignore this */
                }
            }
            if(actions.indexOf("robots")>=0){
                try {
                    final yacyURL theURL = new yacyURL(url, null);
                	
                	// determine if crawling of the current URL is allowed
                	prop.put("robots-allowed", sb.robots.isDisallowed(theURL) ? "0" : "1");
                    
                    // get the sitemap URL of the domain
                    final yacyURL sitemapURL = sb.robots.getSitemapURL(theURL);
                    prop.putHTML("sitemap", (sitemapURL==null)?"":sitemapURL.toString(), true);
                } catch (final MalformedURLException e) {}
            }
            
        }
        // return rewrite properties
        return prop;
    }
    
}
