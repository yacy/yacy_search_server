// CacheAdmin_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.06.2003
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
// javac -classpath .:../classes CacheAdmin_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CacheAdmin_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }


    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();

        String action = ((post == null) ? "info" : post.get("action", "info"));
        String pathString   = ((post == null) ? "" : post.get("path", "/"));
        String fileString = pathString;
        File   cache  = new File(switchboard.getRootPath(), switchboard.getConfig("proxyCache", "DATA/HTCACHE"));
        File   file   = new File(cache, pathString);
        File   dir;
        URL    url    = plasmaHTCache.getURL(cache, file);

        if (file.isDirectory()) {
            dir = file;
        } else {
            dir = file.getParentFile();
            pathString = (new File(pathString)).getParent().replace('\\','/');
        }
        // generate dir listing
        String[] list = dir.list();
        File f; String tree  = "Directory of<br>" + ((pathString.length() == 0) ? "domain list" : linkPathString(pathString)) + "<br><br>";
        if (list == null)
            tree += "[empty]";
        else {
            for (int i = 0; i < list.length; i++) {
                f = new File(dir, list[i]);
                if (f.isDirectory())
                    tree += "<img src=\"/env/grafics/folderIconSmall.gif\" align=\"top\" alt=\"Folder\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=" + pathString + "/" + list[i] + "\" class=\"tt\">" + list[i] + "</a><br>" + serverCore.crlfString;
                else
                    tree += "<img src=\"/env/grafics/fileIconSmall.gif\" align=\"top\" alt=\"File\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=" + pathString + "/" + list[i] + "\" class=\"tt\">" + list[i] + "</a><br>" + serverCore.crlfString;
            }
        }
        
        String info = "";

        if (action.equals("info")) {
            if (!(file.isDirectory())) {
		String urls = htmlFilterContentScraper.urlNormalform(url);
                info += "<b>Info for URL <a href=\"" + urls + "\">" + urls + "</a>:</b><br><br>";
                try {
                    httpHeader fileheader = switchboard.cacheManager.getCachedResponse(plasmaURL.urlHash(url));
                    info += "<b>HTTP Header:</b><br>" + formatHeader(fileheader) + "<br>";
                    String ff = file.toString();
                    int p = ff.lastIndexOf('.');
                    String ext = (p >= 0) ? ff.substring(p + 1).toLowerCase() : "";
                    if ((ext.equals("gif")) || (ext.equals("jpg")) || (ext.equals("jpeg")) || (ext.equals("png")))
                        info += "<img src=\"" + "CacheResource_p.html?path=" + fileString + "\">";
                    else {
                        htmlFilterContentScraper scraper = new htmlFilterContentScraper(url);
                        OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                        plasmaParserDocument document = switchboard.parser.transformScraper(url, "text/html", scraper);
                        serverFileUtils.copy(file, os);
                        info += "<b>HEADLINE:</b><br>" + scraper.getHeadline() + "<br><br>";
                        info += "<b>HREF:</b><br>" + formatAnchor(document.getHyperlinks()) + "<br>";
                        info += "<b>MEDIA:</b><br>" + formatAnchor(document.getMedialinks()) + "<br>";
                        info += "<b>EMAIL:</b><br>" + formatAnchor(document.getEmaillinks()) + "<br>";
                        info += "<b>TEXT:</b><br><span class=\"small\">" + new String(scraper.getText()) + "</span><br>";
                        info += "<b>LINES:</b><br><span class=\"small\">";
                        String[] sentences = document.getSentences();
                        for (int i = 0; i < sentences.length; i++) info += sentences + "<br>";
                        info += "</span><br>";
                    }
                } catch (Exception e) {
                    info += e.toString();
                    e.printStackTrace();
                }
            }
        }
        
        //
	prop.put("cachesize", "" + (switchboard.cacheManager.currCacheSize/1024));
	prop.put("cachemax", "" + (switchboard.cacheManager.maxCacheSize/1024));
        prop.put("tree", tree);
        prop.put("info", info);
        // return rewrite properties
	return prop;
    }

    private static String formatHeader(httpHeader header) {
        if (header == null) return "- no header in header cache -";
        String out = "<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\">";
        Iterator it = header.entrySet().iterator();
        Map.Entry entry;
        while (it.hasNext()) {
            entry = (Map.Entry) it.next();
            out += "<tr valign=\"top\"><td class=\"tt\">" + entry.getKey() + "</td><td class=\"tt\">&nbsp;=&nbsp;</td><td class=\"tt\">" + entry.getValue() + "</td></tr>";
        }
        out += "</table>";
        return out;
    }
    
    private static String formatAnchor(Map a) {
        String out = "<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\">";
        Iterator i = a.entrySet().iterator();
        String url, descr;
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            url = (String) entry.getKey();
            descr = ((String) entry.getValue()).trim();
            if (descr.length() == 0) descr = "-";
            out += "<tr valign=\"top\"><td><span class=\"small\">" + descr + "&nbsp;</span></td><td class=\"tt\">" + url + "</td></tr>";            
        }
        out += "</table>";
        return out;
    }
    
    private static String linkPathString(String Path){ // contributed by Alexander Schier
        String Elements[] = Path.split("/");
        String result = "";
        String tmpPath = "";
        for(int i=0;i<(Elements.length-1);i++){
            tmpPath += Elements[i] + "/";
            result += "<a href=\"CacheAdmin_p.html?action=info&path=" + tmpPath + "\" class=\"tt\">" + Elements[i] + "/</a>";
        }
        if (Elements.length > 0) {
            tmpPath += Elements[Elements.length - 1] + "/";
            result += "<span class=\"tt\">" + Elements[Elements.length - 1] + "/</span>";
        }
        return result;
    }

}
