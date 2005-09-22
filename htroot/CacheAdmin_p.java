// CacheAdmin_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.util.TreeSet;
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
        public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        final String action = ((post == null) ? "info" : post.get("action", "info"));
        String pathString = ((post == null) ? "" : post.get("path", "/"));
        while (pathString.startsWith("//")) { // where is the BUG ?
            pathString = pathString.substring(1);
        }
        final String fileString = pathString;

        final File cache = new File(switchboard.getConfig("proxyCache", "DATA/HTCACHE"));    

        File       dir;
        final File file = new File(cache, pathString);
        final URL  url  = plasmaHTCache.getURL(cache, file);

        if (file.isDirectory()) {
            dir = file;
        } else {
            dir = file.getParentFile();
            pathString = (new File(pathString)).getParent().replace('\\','/');
        }

        // generate sorted dir/file listing
        final String[] list = dir.list();
        final StringBuffer tree  = new StringBuffer((list.length + 2) * 256);
        tree.append("Directory of<br>").append((pathString.length() == 0) ? "domain list" : linkPathString(pathString)).append("<br><br>");
        if (list == null) {
            tree.append("[empty]");
        } else {
            final TreeSet dList = new TreeSet();
            final TreeSet fList = new TreeSet();
            File object;
            int size = list.length - 1;
            for (int i = size; i >= 0 ; i--) { // Rückwärts ist schneller
                object = new File(dir, list[i]);
                if (!object.getName().equalsIgnoreCase("responseHeader.db")) {
                    if (object.isDirectory()) {
                        dList.add(list[i]);
                    } else {
                        fList.add(list[i]);
                    }
                }
            }
            Iterator iter = dList.iterator();
            String str;
            while (iter.hasNext()) {
                str = iter.next().toString();
                tree.append("<img src=\"/env/grafics/folderIconSmall.gif\" align=\"top\" alt=\"Folder\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=").append(pathString).append("/").append(str).append("\" class=\"tt\">").append(str).append("</a><br>").append(serverCore.crlfString);
            } 
            iter = fList.iterator();
            while (iter.hasNext()) {
                str = iter.next().toString();
                tree.append("<img src=\"/env/grafics/fileIconSmall.gif\" align=\"top\" alt=\"File\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=").append(pathString).append("/").append(str).append("\" class=\"tt\">").append(str).append("</a><br>").append(serverCore.crlfString);
            }
        }

        final StringBuffer info = new StringBuffer();
        if (action.equals("info") && !file.isDirectory()) {
            info.ensureCapacity(40000);
            final String urls = htmlFilterContentScraper.urlNormalform(url);
            info.append("<b>Info for URL <a href=\"").append(urls).append("\">").append(urls).append("</a></b><br><br>");
            try {
                final httpHeader fileheader = switchboard.cacheManager.getCachedResponse(plasmaURL.urlHash(url));
                info.append("<b>HTTP Header:</b><br>").append(formatHeader(fileheader)).append("<br>");
                final String ff = file.toString();
                final int dotpos = ff.lastIndexOf('.');
                final String ext = (dotpos >= 0) ? ff.substring(dotpos + 1).toLowerCase() : "";
                if (ext.equals("gif") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")) {
                    info.append("<img src=\"" + "CacheResource_p.html?path=").append(fileString).append("\">");
                } else {
                    final htmlFilterContentScraper scraper = new htmlFilterContentScraper(url);
                    final OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                    serverFileUtils.copy(file, os);
//                  os.flush();
                    final plasmaParserDocument document = switchboard.parser.transformScraper(url, "text/html", scraper);
                    info.append("<b>HEADLINE:</b><br>").append(scraper.getHeadline()).append("<br>").append("<br>")
                        .append("<b>HREF:</b><br>").append(formatAnchor(document.getHyperlinks())).append("<br>")
                        .append("<b>MEDIA:</b><br>").append(formatAnchor(document.getMedialinks())).append("<br>")
                        .append("<b>EMAIL:</b><br>").append(formatAnchor(document.getEmaillinks())).append("<br>")
                        .append("<b>TEXT:</b><br><span class=\"small\">").append(new String(scraper.getText())).append("</span><br>")
                        .append("<b>LINES:</b><br><span class=\"small\">");
                    final String[] sentences = document.getSentences();
                    for (int i = 0; i < sentences.length; i++) {
                        info.append(sentences[i]).append("<br>");
                    }
                    info.append("</span><br>");
                }
            } catch (Exception e) {
                info.append("- This file is not cached -");
                info.append(e.toString());
                e.printStackTrace();
            }           
        }

        prop.put("cachesize", Long.toString(switchboard.cacheManager.currCacheSize/1024));
        prop.put("cachemax", Long.toString(switchboard.cacheManager.maxCacheSize/1024));
        prop.put("tree", tree.toString());
        prop.put("info", info.toString());
        // return rewrite properties
        return prop;
    }

    private static String formatHeader(httpHeader header) {
        final StringBuffer result = new StringBuffer(2048);
        if (header == null) {
            result.append("- no header in header cache -");
        } else {
            result.append("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\">");
            final Iterator iter = header.entrySet().iterator();
            Map.Entry entry;
            while (iter.hasNext()) {
                entry = (Map.Entry) iter.next();
                result.append("<tr valign=\"top\"><td class=\"tt\">").append(entry.getKey()).append("</td><td class=\"tt\">&nbsp;=&nbsp;</td><td class=\"tt\">").append(entry.getValue()).append("</td></tr>");
            }
            result.append("</table>");
        }
        return result.toString();
    }

    private static String formatAnchor(Map anchor) {
        final StringBuffer result = new StringBuffer((anchor.entrySet().size() + 1) * 256);
        result.append("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\">");        
        final Iterator iter = anchor.entrySet().iterator();
        String url, descr;
        Map.Entry entry;
        while (iter.hasNext()) {
            entry = (Map.Entry) iter.next();
            url = (String) entry.getKey();
            descr = ((String) entry.getValue()).trim();
            if (descr.length() == 0) { descr = "-"; }
            result.append("<tr valign=\"top\"><td><span class=\"small\">").append(descr).append("&nbsp;</span></td><td class=\"tt\">").append(url).append("</td></tr>");            
        }
        return result.append("</table>").toString();
    }

    private static String linkPathString(String Path){ // contributed by Alexander Schier
        final String[] Elements = Path.split("/");
        final StringBuffer result = new StringBuffer(Elements.length * 256);
        final StringBuffer tmpPath = new StringBuffer(256);
        for(int i=0;i<(Elements.length-1);i++){
            tmpPath.append(Elements[i]).append("/");
            result.append("<a href=\"CacheAdmin_p.html?action=info&path=").append(tmpPath).append("\" class=\"tt\">").append(Elements[i]).append("/</a>");
        }
        if (Elements.length > 0) {
            tmpPath.append(Elements[Elements.length - 1]).append("/");
            result.append("<span class=\"tt\">").append(Elements[Elements.length - 1]).append("/</span>");
        }
        return result.toString();
    }

}
