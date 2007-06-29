// yacyVersion.java 
// ----------------
// (C) 2007 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 27.04.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package de.anomic.yacy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;

public final class yacyVersion implements Comparator, Comparable {
    
    // general release info
    public static final float YACY_SUPPORTS_PORT_FORWARDING = (float) 0.383;
    public static final float YACY_SUPPORTS_GZIP_POST_REQUESTS = (float) 0.40300772;
    public static final float YACY_ACCEPTS_RANKING_TRANSMISSION = (float) 0.414;
    public static final float YACY_HANDLES_COLLECTION_INDEX = (float) 0.486;
    public static final float YACY_PROVIDES_CRAWLS_VIA_LIST_HTML = (float) 0.50403367;

    // information about latest release, retrieved by other peers release version
    public static double latestRelease = 0.1; // this value is overwritten when a peer with later version appears

    // information about latest release, retrieved from download pages
    // this static information should be overwritten by network-specific locations
    // for details see yacy.network.unit
    private static TreeSet[] allDevReleases = null;
    private static TreeSet[] allMainReleases = null;
    public  static String latestDevReleaseLocation = ""; // will be initialized with value in yacy.network.unit
    public  static String latestMainReleaseLocation = ""; // will be initialized with value in yacy.network.unit
    
    // class variables
    public float releaseNr;
    public String dateStamp;
    public int svn;
    public boolean proRelease, mainRelease;
    public URL url;
    public String name;
    
    public yacyVersion(URL url) {
        this(url.getFileName());
        this.url = url;
    }
        
    public yacyVersion(String release) {
        // parse a release file name
        // the have the following form:
        // yacy_dev_v${releaseVersion}_${DSTAMP}_${releaseNr}.tar.gz
        // yacy_v${releaseVersion}_${DSTAMP}_${releaseNr}.tar.gz
        // i.e. yacy_v0.51_20070321_3501.tar.gz
        this.url = null;
        this.name = release;
        if ((release == null) || (!release.endsWith(".tar.gz"))) {
            throw new RuntimeException("release file name '" + release + "' is not valid, no tar.gz");
        }
        // cut off tail
        release = release.substring(0, release.length() - 7);
        if (release.startsWith("yacy_pro_v")) {
            proRelease = true;
            release = release.substring(10);
        } else if (release.startsWith("yacy_v")) {
            proRelease = false;
            release = release.substring(6);
        } else {
            throw new RuntimeException("release file name '" + release + "' is not valid, wrong prefix");
        }
        // now all release names have the form
        // ${releaseVersion}_${DSTAMP}_${releaseNr}
        String[] comp = release.split("_"); // should be 3 parts
        if (comp.length != 3) {
            throw new RuntimeException("release file name '" + release + "' is not valid, 3 information parts expected");
        }
        try {
            this.releaseNr = Float.parseFloat(comp[0]);
        } catch (NumberFormatException e) {
            throw new RuntimeException("release file name '" + release + "' is not valid, '" + comp[0] + "' should be a float number");
        }
        this.mainRelease = ((int) (this.releaseNr * (float) 1000)) % 10 == 0;
        //System.out.println("Release version " + this.releaseNr + " is " + ((this.mainRelease) ? "main" : "std"));
        this.dateStamp = comp[1];
        if (this.dateStamp.length() != 8) {
            throw new RuntimeException("release file name '" + release + "' is not valid, '" + comp[1] + "' should be a 8-digit date string");
        }
        try {
            this.svn = Integer.parseInt(comp[2]);
        } catch (NumberFormatException e) {
            throw new RuntimeException("release file name '" + release + "' is not valid, '" + comp[2] + "' should be a integer number");
        }
        // finished! we parsed a relase string
    }
    
    public int compareTo(Object obj) {
        yacyVersion v = (yacyVersion) obj;
        return compare(this, v);
    }
    
    public int compare(Object arg0, Object arg1) {
        // compare-operator for two yacyVersion objects
        // must be implemented to make it possible to put this object into
        // a ordered structure, like TreeSet or TreeMap
        yacyVersion a0 = (yacyVersion) arg0, a1 = (yacyVersion) arg1;
        return (new Integer(a0.svn)).compareTo(new Integer(a1.svn));
    }
    
    public boolean equals(Object obj) {
        yacyVersion v = (yacyVersion) obj;
        return (this.svn == v.svn) && (this.url.toNormalform().equals(v.url.toNormalform()));
    }
    
    public int hashCode() {
        return this.url.toNormalform().hashCode();
    }
    
    public String toAnchor() {
        // generates an anchor string that can be used to embed in an html for direct download
        return "<a href=" + this.url.toNormalform() + ">YaCy " + ((this.proRelease) ? "pro release" : "standard release") + " v" + this.releaseNr + ", SVN " + this.svn + "</a>";
    }
    /*
    public static yacyVersion latestStandardRelease() {
        // get the latest release info from a internet resource
        yacyVersion devrel =  (yacyVersion) allDevReleases().last();
        yacyVersion mainrel =  (yacyVersion) allDevReleases().last();
    }
    
    public static yacyVersion latestProRelease() {
        // get the latest release info from a internet resource
        return (yacyVersion) allMainReleases().last();
    }
    */
    public static TreeSet[] allReleases() {
        // join the release infos
        // {promainreleases, prodevreleases, stdmainreleases, stddevreleases} 
        TreeSet[] a = allMainReleases();
        TreeSet[] b = allDevReleases();
        TreeSet[] r = new TreeSet[4];
        TreeSet s;
        for (int i = 0; i < 4; i++) {
            s = new TreeSet();
            if ((b != null) && (b[i] != null)) s.addAll((TreeSet) b[i]);
            if ((a != null) && (a[i] != null)) s.addAll((TreeSet) a[i]);
            r[i] = s;
        }
        return r;
    }
    
    private static TreeSet[] allDevReleases() {
        // get release info from a internet resource
        // {promainreleases, prodevreleases, stdmainreleases, stddevreleases} 
        if ((allDevReleases == null) ||
            ((allDevReleases[0].size() == 0) &&
             (allDevReleases[1].size() == 0) &&
             (allDevReleases[2].size() == 0) &&
             (allDevReleases[3].size() == 0) )) try {
            allDevReleases = allReleaseFrom(new URL(latestDevReleaseLocation));
        } catch (MalformedURLException e) {
            return null;
        }
        return allDevReleases;
    }
    
    private static TreeSet[] allMainReleases() {
        // get release info from a internet resource
        // {promainreleases, prodevreleases, stdmainreleases, stddevreleases} 
        if ((allMainReleases == null)  ||
            ((allMainReleases[0].size() == 0) &&
             (allMainReleases[1].size() == 0) &&
             (allMainReleases[2].size() == 0) &&
             (allMainReleases[3].size() == 0) )) try {
            allMainReleases = allReleaseFrom(new URL(latestMainReleaseLocation));
        } catch (MalformedURLException e) {
            return null;
        }
        return allMainReleases;
    }
    
    private static TreeSet[] allReleaseFrom(URL url) {
        // retrieves the latest info about releases
        // this is done by contacting a release location,
        // parsing the content and filtering+parsing links
        // returns the version info if successful, null otherwise
        htmlFilterContentScraper scraper;
        try {
            scraper = htmlFilterContentScraper.parseResource(url);
        } catch (IOException e) {
            return null;
        }
        
        // analyse links in scraper resource, and find link to latest release in it
        Map anchors = scraper.getAnchors(); // a url (String) / name (String) relation
        Iterator i = anchors.keySet().iterator();
        TreeSet stddevreleases = new TreeSet();
        TreeSet prodevreleases = new TreeSet();
        TreeSet stdmainreleases = new TreeSet();
        TreeSet promainreleases = new TreeSet();
        yacyVersion release;
        while (i.hasNext()) {
            try {
                url = new URL((String) i.next());
            } catch (MalformedURLException e1) {
                continue; // just ignore invalid urls
            }
            try {
                release = new yacyVersion(url);
                //System.out.println("r " + release.toAnchor());
                if ( release.proRelease &&  release.mainRelease) promainreleases.add(release);
                if ( release.proRelease && !release.mainRelease) prodevreleases.add(release);
                if (!release.proRelease &&  release.mainRelease) stdmainreleases.add(release);
                if (!release.proRelease && !release.mainRelease) stddevreleases.add(release);
            } catch (RuntimeException e) {
                // the release string was not well-formed.
                // that might have been another link
                // just dont care
                continue;
            }
        }
        return new TreeSet[] {promainreleases, prodevreleases, stdmainreleases, stddevreleases} ;
    }
    
    public static void downloadRelease(yacyVersion release) throws IOException {
        File storagePath = plasmaSwitchboard.getSwitchboard().releasePath;
        // load file
        byte[] file = httpc.wget(
                release.url,
                release.url.getHost(),
                10000, 
                null, 
                null, 
                plasmaSwitchboard.getSwitchboard().remoteProxyConfig
        );
        if (file == null) throw new IOException("wget of url " + release.url + " failed");
        // save file
        serverFileUtils.write(file, new File(storagePath, release.url.getFileName()));
    }
    
    
    public static void restart() {
        if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            // create yacy.restart file which is used in Windows startscript
            final File yacyRestart = new File(plasmaSwitchboard.getSwitchboard().getRootPath(), "DATA/yacy.restart");
            if (!yacyRestart.exists()) {
                try {
                    yacyRestart.createNewFile();
                    plasmaSwitchboard.getSwitchboard().terminate(5000);
                } catch (IOException e) {
                    serverLog.logSevere("SHUTDOWN", "restart failed", e);
                }
            }
            
        }
        
        if (serverSystem.canExecUnix) {
            // start a re-start daemon
            try {
                serverLog.logInfo("RESTART", "INITIATED");
                String script = "cd " + plasmaSwitchboard.getSwitchboard().getRootPath() + "/DATA/RELEASE/;while [ -e ../yacy.running ]; do sleep 1;done;cd ../../;./startYACY.sh";
                File scriptFile = new File(plasmaSwitchboard.getSwitchboard().getRootPath(), "DATA/RELEASE/restart.sh");
                serverFileUtils.write(script.getBytes(), scriptFile);
                serverLog.logInfo("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                Runtime.getRuntime().exec("chmod 755 " + scriptFile.getAbsolutePath()).waitFor();
                /*Process p =*/ Runtime.getRuntime().exec(scriptFile.getAbsolutePath() + " &");
                /*serverLog.logInfo("RESTART", "script started");
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String text;
                while ((text = in.readLine()) != null) {
                    serverLog.logInfo("RESTART", " SCRIPT-LOG " + text);
                }*/
                serverLog.logInfo("RESTART", "script is running");
                plasmaSwitchboard.getSwitchboard().terminate(5000);
            } catch (IOException e) {
                serverLog.logSevere("RESTART", "restart failed", e);
            } catch (InterruptedException e) {
                serverLog.logSevere("RESTART", "restart failed", e);
            }
        }
    }
    
    
    public static void writeDeployScript(String release) {
        //byte[] script = ("cd " + plasmaSwitchboard.getSwitchboard().getRootPath() + ";while [ -e ../yacy.running ]; do sleep 1;done;tar xfz " + release + ";cp -Rf yacy/* ../../;rm -Rf yacy;cd ../../;startYACY.sh").getBytes();
        
    }
}
