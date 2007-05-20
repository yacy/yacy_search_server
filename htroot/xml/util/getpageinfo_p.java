// /xml/util/gettitle_p.java
// -------------------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
package xml.util;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;

import de.anomic.data.htmlTools;
import de.anomic.data.robotsParser;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;

public class getpageinfo_p {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        serverObjects prop = new serverObjects();
        prop.put("title", "");
        prop.put("robots-allowed", 3); //unknown
        String actions="title";
        if(post!=null && post.containsKey("url")){
            if(post.containsKey("actions"))
                actions=(String)post.get("actions");
            ArrayList content;
            String url=(String) post.get("url");
			if(url.toLowerCase().startsWith("ftp://")){
				prop.put("robots-allowed", 1);
				prop.put("title", "FTP: "+url);
                return prop;
			} else if (!(url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://"))) {
                url = "http://" + url;
            }
            if (actions.indexOf("title")>=0) {
                try {
                    URL u = new URL(url);
                    content = nxTools.strings(httpc.wget(u, u.getHost(), 6000, null, null, ((plasmaSwitchboard) env).remoteProxyConfig, null));
                    Iterator it = content.iterator();
                    String line;
                    String title;
                    while (it.hasNext()) {
                        line = (String) it.next();
                        try {
                            title = line.substring(line.toLowerCase().indexOf(
                                    "<title>") + 7, line.toLowerCase().indexOf(
                                    "</title>"));
                            // de-replace html entities
                            title = htmlTools.deReplaceHTML(title);
                            prop.put("title", title);
                        } catch (IndexOutOfBoundsException e) {
                        }
                    }

                } catch (MalformedURLException e) {
                } catch (IOException e) {
                }
            }
            if(actions.indexOf("robots")>=0){
                try {
                	URL theURL = new URL(url);
                	
                	// determine if crawling of the current URL is allowed
                    if(robotsParser.isDisallowed(theURL)){
                        prop.put("robots-allowed", 0);
                    }else{
                        prop.put("robots-allowed", 1);
                    }
                    
                    // get the sitemap URL of the domain
                    URL sitemapURL = robotsParser.getSitemapURL(theURL);
                    prop.put("sitemap", (sitemapURL==null)?"":sitemapURL.toString());
                } catch (MalformedURLException e) {}
            }
            
        }
        // return rewrite properties
        return prop;
    }
    
}



