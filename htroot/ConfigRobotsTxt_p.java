// ConfigRobotsTxt_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Franz Brauße
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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
// javac -classpath .:../classes ConfigRobotsTxt_p.java
// if the shell's current path is HTROOT

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;

public class ConfigRobotsTxt_p {
    
    public static final Pattern entryBeginPattern = Pattern.compile("# (\\w*) \\((\\d*) entries\\)");
    
    private static HashMap disallowMap = null;
    
    private static Map getDisallowMap(String htrootPath) {
        if (disallowMap == null) {
            final File htroot = new File(htrootPath);
            if (!htroot.exists()) return null;
            disallowMap = new /* <String,String[]> */ HashMap();
            final ArrayList htrootFiles = new ArrayList();
            final ArrayList htrootDirs = new ArrayList();
            final String[] htroots = htroot.list();
            File file;
            for (int i=0, dot; i<htroots.length; i++) {
                if (htroots[i].equals("www")) continue;
                file = new File(htroot, htroots[i]);
                if (file.isDirectory()) {
                    htrootDirs.add("/" + file.getName());
                } else if (
                        (dot = htroots[i].lastIndexOf('.')) < 2 ||
                        htroots[i].charAt(dot - 2) == '_' && htroots[i].charAt(dot - 1) == 'p'
                ) {
                    htrootFiles.add("/" + file.getName());
                }
            }
            
            disallowMap.put("all", new String[] { "/" } );
            disallowMap.put("locked", htrootFiles.toArray(new String[htrootFiles.size()]));
            disallowMap.put("directories", htrootDirs.toArray(new String[htrootDirs.size()]));
            disallowMap.put("blog", new String[] {
                    "/Blog.html",
                    "/Blog.xml",
                    "/BlogComments.html" } );
            disallowMap.put("wiki", new String[] { "/Wiki.html" } );
            disallowMap.put("bookmarks", new String[] { "/Bookmarks.html" } );
            disallowMap.put("homepage", new String[] { "/www" } );
            disallowMap.put("fileshare", new String[] { "/share" } );
            disallowMap.put("surftips", new String[] { "/Surftips.html" } );
            disallowMap.put("news", new String[] { "/News.html" } );
            disallowMap.put("status", new String[] { "/Status.html" } );
            disallowMap.put("network", new String[] {
                    "/Network.html",
                    "/Network.xml",
                    "/Network.csv" } );
        }
        return disallowMap;
    }
    
    public static servletProperties respond(httpHeader header, serverObjects post, serverSwitch env) {
        final servletProperties prop = new servletProperties();
        
        prop.put("address", yacyCore.seedDB.mySeed.getAddress());
        
        final String htroot = ((plasmaSwitchboard)env).getConfig(plasmaSwitchboard.HTROOT_PATH, plasmaSwitchboard.HTROOT_PATH_DEFAULT);
        final File robots_txt = new File(htroot + File.separator + "robots.txt");
        if (!robots_txt.exists()) try {
            robots_txt.createNewFile();
        } catch (IOException e) {
            prop.put("error", 1);
            prop.put("error_msg", e.getMessage());
        }
        
        if (post != null) {
            if (post.containsKey("save")) {
                try {
                    if (robots_txt.delete() && robots_txt.createNewFile()) {
                        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(robots_txt)));
                        printHeader(out);
                        
                        final Iterator it = getDisallowMap(htroot).entrySet().iterator();
                        Map.Entry entry;
                        while (it.hasNext()) {
                            entry = (Map.Entry)it.next();
                            if (post.containsKey(entry.getKey())) {
                                out.println();
                                printEntry(out, entry);
                            }
                        }
                        out.flush();
                        out.close();
                    } else {
                        prop.put("error", 2);
                    }
                } catch (IOException e) {
                    serverLog.logSevere("ROBOTS.TXT", "Error writing " + robots_txt, e);
                    prop.put("error", 1);
                    prop.put("error_msg", e.getMessage());
                }
            }
        }
        
        // read htroot/robots.txt
        try {
            BufferedReader br = new BufferedReader(new FileReader(robots_txt));
            String line;
            Matcher m;
            while ((line = br.readLine()) != null) {
                if ((m = entryBeginPattern.matcher(line)).matches())
                    prop.put(m.group(1) + ".checked", 1);
            }
        } catch (IOException e) {
            prop.put("error", 1);
            prop.put("error_msg", e.getMessage());
        }
        
        return prop;
    }
    
    private static void printHeader(PrintWriter out) {
        out.print("# robots.txt for ");
        out.print(yacyCore.seedDB.mySeed.getName());
        out.println(".yacy");
        out.println();
        out.println("User-agent: *");
    }
    
    private static void printEntry(PrintWriter out, Map.Entry entry) {
        String[] disallows = (String[])entry.getValue();
        out.print("# ");
        out.print(entry.getKey());
        out.print(" (");
        out.print(disallows.length);
        out.println(" entries)");
        
        for (int i=0; i<disallows.length; i++) {
            out.print("Disallow: ");
            out.println(disallows[i]);
        }
    }
}
