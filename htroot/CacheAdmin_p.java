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
import de.anomic.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.http.httpHeader;
import de.anomic.index.indexURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CacheAdmin_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        final String action = ((post == null) ? "info" : post.get("action", "info"));
        String pathString = ((post == null) ? "" : post.get("path", "/"));
//      String pathString = ((post == null) ? "" : post.get("path", "/").replaceAll("//", "/")); // where is the BUG ?

        // don't left the htCachePath
        File file = new File(switchboard.htCachePath, pathString);
        try {
            if (!file.getCanonicalPath().startsWith(switchboard.htCachePath.getCanonicalPath())) {
                pathString = "/";
                file = new File(switchboard.htCachePath, pathString);
            }
        } catch (Exception e) {
            pathString = "/";
            file = new File(switchboard.htCachePath, pathString);
        }

        final StringBuffer path = new StringBuffer(256);
        final StringBuffer tree = new StringBuffer();
        final StringBuffer info = new StringBuffer();

        final URL  url  = plasmaHTCache.getURL(switchboard.htCachePath, file);
        
        String urlstr = "";
        if (action.equals("info") && !file.isDirectory()) {
            prop.put("info", 0);
            path.append((pathString.length() == 0) ? linkPathString("/", true) : linkPathString(pathString, false));

            urlstr = htmlFilterContentScraper.urlNormalform(url);
            prop.put("info_url", urlstr);

            info.ensureCapacity(40000);
            try {
                final httpHeader fileheader = switchboard.cacheManager.getCachedResponse(indexURL.urlHash(url));
                info.append("<b>HTTP Header:</b><br>").append(formatHeader(fileheader)).append("<br>");
                final String ff = file.toString();
                final int dotpos = ff.lastIndexOf('.');
                final String ext = (dotpos >= 0) ? ff.substring(dotpos + 1).toLowerCase() : "";
                if (ext.equals("gif") || ext.equals("jpg") ||
                    ext.equals("png") || ext.equals("jpeg")) {
                    info.append("<img src=\"" + "CacheResource_p.html?path=").append(pathString).append("\">");
                } else {
                    final htmlFilterContentScraper scraper = new htmlFilterContentScraper(url);
                    final OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                    serverFileUtils.copy(file, os);
                    final plasmaParserDocument document = switchboard.parser.transformScraper(url, "text/html", scraper);
                    info.append("<b>TITLE:</b><br>").append(scraper.getTitle()).append("<br>").append("<br>")
                        .append("<b>SECTION HEADLINES:</b><br>").append(formatTitles(document.getSectionTitles())).append("<br>")
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
                info.append("- This file is not cached -<br><br>");
                info.append(e.toString());
                e.printStackTrace();
            }
        } else {
            prop.put("info", 1);

            File dir;
            if (file.isDirectory()) {
                dir = file;
            } else {
                dir = file.getParentFile();
                pathString = (new File(pathString)).getParent().replace('\\','/');
            }

            // generate sorted dir/file listing
            final String[] list = dir.list();
            tree.ensureCapacity((list == null) ? 70 : (list.length + 1) * 256);
            path.append((pathString.length() == 0) ? linkPathString("/", true) : linkPathString(pathString, true));
            if (list == null) {
                tree.append("[empty]");
            } else {
                final TreeSet dList = new TreeSet();
                final TreeSet fList = new TreeSet();
                File object;
                int size = list.length - 1;
                for (int i = size; i >= 0 ; i--) { // Rueckwaerts ist schneller
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
                    tree.append("<img src=\"/env/grafics/folderIconSmall.gif\" align=\"top\" alt=\"Folder\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=").append(pathString).append("/").append(str).append("\" class=\"tt\"><bobr>").append(str).append("</bobr></a><br>").append(serverCore.crlfString);
                } 
                iter = fList.iterator();
                while (iter.hasNext()) {
                    str = iter.next().toString();
                    tree.append("<img src=\"/env/grafics/fileIconSmall.gif\" align=\"top\" alt=\"File\">&nbsp;<a href=\"CacheAdmin_p.html?action=info&path=").append(pathString).append("/").append(str).append("\" class=\"tt\"><bobr>").append(str).append("</bobr></a><br>").append(serverCore.crlfString);
                }
            }
        }

        prop.put("cachesize", Long.toString(switchboard.cacheManager.curCacheSize/1024));
        prop.put("cachemax", Long.toString(switchboard.cacheManager.maxCacheSize/1024));
        prop.put("path", path.toString());
        prop.put("info_info", info.toString());
        prop.put("info_tree", tree.toString());
        // return rewrite properties
        return prop;
    }

    private static String formatTitles(String[] titles) {
        StringBuffer s = new StringBuffer();
        s.append("<ul>");
        for (int i = 0; i < titles.length; i++) {
            s.append("<li>").append(titles[i]).append("</li>");
        }
        s.append("</ul>");
        return new String(s);
    }
    
    private static String formatHeader(httpHeader header) {
        final StringBuffer result = new StringBuffer(2048);
        if (header == null) {
            result.append("- no header in header cache -<br>");
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

    private static String linkPathString(String path, boolean dir){
        final String[] elements = path.split("/");
        final StringBuffer tmpstr = new StringBuffer(256);
        final StringBuffer result = new StringBuffer(elements.length + 1 * 128);
        int i, e;
        if (dir) { e = elements.length; } else { e = elements.length - 1; }
        for(i = 0; i < e; i++) {
            if (!elements[i].equals("")) {
                tmpstr.append(elements[i]).append("/");
                result.append("<a href=\"CacheAdmin_p.html?action=info&path=").append(tmpstr).append("\" class=\"tt\">").append(elements[i]).append("/</a>");
            }
        }
        return result.toString();
    }

}
