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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
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
    private static HashMap /* URL:TreeSet[]*/ latestReleases = new HashMap();
    public  static ArrayList latestReleaseLocations /*string*/ = new ArrayList(); // will be initialized with value in yacy.network.unit
    
    // private static release info about this release; is generated only once and can be retrieved by thisVersion()
    private static yacyVersion thisVersion = null;
    
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
        // returns 0 if this object is equal to the obj, -1 if this is smaller than obj and 1 if this is greater than obj
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
        return (this.svn == v.svn) && (this.url.toNormalform(true, true).equals(v.url.toNormalform(true, true)));
    }
    
    public int hashCode() {
        return this.url.toNormalform(true, true).hashCode();
    }
    
    public String toAnchor() {
        // generates an anchor string that can be used to embed in an html for direct download
        return "<a href=" + this.url.toNormalform(true, true) + ">YaCy " + ((this.proRelease) ? "pro release" : "standard release") + " v" + this.releaseNr + ", SVN " + this.svn + "</a>";
    }
    
    // static methods:

    public static final yacyVersion thisVersion() {
        // construct a virtual release name for this release
        if (thisVersion == null) {
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            boolean pro = new File(sb.getRootPath(), "libx").exists();
            thisVersion = new yacyVersion(
                "yacy" + ((pro) ? "_pro" : "") +
                "_v" + sb.getConfig("version", "0.1") + "_" +
                sb.getConfig("vdate", "19700101") + "_" +
                sb.getConfig("svnRevision", "0") + ".tar.gz");
        }
        return thisVersion;
    }
    
    public static final yacyVersion rulebasedUpdateInfo(boolean manual) {
        // according to update properties, decide if we should retrieve update information
        // if true, the release that can be obtained is returned.
        // if false, null is returned
        plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        
        // check if update process allowes update retrieve
        String process = sb.getConfig("update.process", "manual");
        if ((!manual) && (!process.equals("auto"))) {
            yacyCore.log.logInfo("rulebasedUpdateInfo: not an automatic update selected");
            return null; // no, its a manual or guided process
        }
        
        // check if the last retrieve time is a minimum time ago
        long cycle = Math.max(1, sb.getConfigLong("update.cycle", 168)) * 60 * 60 * 1000; // update.cycle is hours
        long timeLookup = sb.getConfigLong("update.time.lookup", System.currentTimeMillis());
        if ((!manual) && (timeLookup + cycle > System.currentTimeMillis())) {
            yacyCore.log.logInfo("rulebasedUpdateInfo: too early for a lookup for a new release (timeLookup = " + timeLookup + ", cycle = " + cycle + ", now = " + System.currentTimeMillis() + ")");
            return null; // no we have recently made a lookup
        }
        
        // check if we know that there is a release that is more recent than that which we are using
        TreeSet[] releasess = yacyVersion.allReleases(true); // {0=promain, 1=prodev, 2=stdmain, 3=stddev}
        boolean pro = new File(sb.getRootPath(), "libx").exists();
        yacyVersion latestmain = (releasess[(pro) ? 0 : 2].size() == 0) ? null : (yacyVersion) releasess[(pro) ? 0 : 2].last();
        yacyVersion latestdev  = (releasess[(pro) ? 1 : 3].size() == 0) ? null : (yacyVersion) releasess[(pro) ? 1 : 3].last();
        String concept = sb.getConfig("update.concept", "any");
        String blacklist = sb.getConfig("update.blacklist", ".\\...[123]");
        
        if ((manual) || (concept.equals("any"))) {
            // return a dev-release or a main-release
            if ((latestdev != null) &&
                ((latestmain == null) || (latestdev.compareTo(latestmain) > 0)) &&
                (!(Float.toString(latestdev.releaseNr).matches(blacklist)))) {
                // consider a dev-release
                if (latestdev.compareTo(thisVersion()) > 0) {
                    return latestdev;
                } else {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest dev " + latestdev.name +
                            " is not more recent than installed release " + thisVersion().name);
                    return null;
                }
            }
            if (latestmain != null) {
                // consider a main release
                if ((Float.toString(latestmain.releaseNr).matches(blacklist))) {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest dev " + latestdev.name +
                            " matches with blacklist '" + blacklist + "'");
                    return null;
                }
                if (latestmain.compareTo(thisVersion()) > 0) return latestmain; else {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest main " + latestmain.name +
                            " is not more recent than installed release (1) " + thisVersion().name);
                    return null;
                }
            }
        }
        if ((concept.equals("main")) && (latestmain != null)) {
            // return a main-release
            if ((Float.toString(latestmain.releaseNr).matches(blacklist))) {
                yacyCore.log.logInfo(
                        "rulebasedUpdateInfo: latest main " + latestmain.name +
                        " matches with blacklist'" + blacklist + "'");
                return null;
            }
            if (latestmain.compareTo(thisVersion()) > 0) return latestmain; else {
                yacyCore.log.logInfo(
                        "rulebasedUpdateInfo: latest main " + latestmain.name +
                        " is not more recent than installed release (2) " + thisVersion().name);
                return null; 
            }
        }
        yacyCore.log.logInfo("rulebasedUpdateInfo: failed to find more recent release");
        return null;
    }
    
    public static TreeSet[] allReleases(boolean force) {
        // join the release infos
        // {promainreleases, prodevreleases, stdmainreleases, stddevreleases}
        Object[] a = new Object[latestReleaseLocations.size()];
        for (int j = 0; j < latestReleaseLocations.size(); j++) {
            a[j] = getReleases((URL) latestReleaseLocations.get(j), force);
        }
        TreeSet[] r = new TreeSet[4];
        TreeSet s;
        for (int i = 0; i < 4; i++) {
            s = new TreeSet();
            for (int j = 0; j < a.length; j++) {
                if ((a[j] != null) && (((TreeSet[]) a[j])[i] != null)) s.addAll(((TreeSet[]) a[j])[i]);
            }
            r[i] = s;
        }
        return r;
    }
    
    private static TreeSet[] getReleases(URL location, boolean force) {
        // get release info from a internet resource
        // {promainreleases, prodevreleases, stdmainreleases, stddevreleases} 
        TreeSet[] latestRelease = (TreeSet[]) latestReleases.get(location);
        if (force ||
            (latestRelease == null) /*||
            ((latestRelease[0].size() == 0) &&
             (latestRelease[1].size() == 0) &&
             (latestRelease[2].size() == 0) &&
             (latestRelease[3].size() == 0) )*/) {
            latestRelease = allReleaseFrom(location);
            latestReleases.put(location, latestRelease);
        }
        return latestRelease;
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
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.lookup", System.currentTimeMillis());
        return new TreeSet[] {promainreleases, prodevreleases, stdmainreleases, stddevreleases} ;
    }
    
    public static void downloadRelease(yacyVersion release) throws IOException {
        File storagePath = plasmaSwitchboard.getSwitchboard().releasePath;
        // load file
        File download = new File(storagePath, release.url.getFileName());
        httpc.wget(
                release.url,
                release.url.getHost(),
                300000, 
                null, 
                null, 
                plasmaSwitchboard.getSwitchboard().remoteProxyConfig,
                null,
                download
        );
        if ((!download.exists()) || (download.length() == 0)) throw new IOException("wget of url " + release.url + " failed");
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.download", System.currentTimeMillis());
    }
    
    
    public static void restart() {
        plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        
        if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
            // create yacy.restart file which is used in Windows startscript
            final File yacyRestart = new File(sb.getRootPath(), "DATA/yacy.restart");
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
                String script =
                    "#!/bin/sh" + serverCore.lfstring +
                    "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.lfstring +
                    "while [ -f ../yacy.running ]; do" + serverCore.lfstring +
                    "sleep 1" + serverCore.lfstring +
                    "done" + serverCore.lfstring +
                    "cd ../../" + serverCore.lfstring +
                    "nohup ./startYACY.sh > /dev/null" + serverCore.lfstring;
                File scriptFile = new File(sb.getRootPath(), "DATA/RELEASE/restart.sh");
                serverSystem.deployScript(scriptFile, script);
                serverLog.logInfo("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                serverSystem.execAsynchronous(scriptFile);
                serverLog.logInfo("RESTART", "script is running");
                sb.terminate(5000);
            } catch (IOException e) {
                serverLog.logSevere("RESTART", "restart failed", e);
            }
        }
    }
    
    public static void deployRelease(String release) {
        //byte[] script = ("cd " + plasmaSwitchboard.getSwitchboard().getRootPath() + ";while [ -e ../yacy.running ]; do sleep 1;done;tar xfz " + release + ";cp -Rf yacy/* ../../;rm -Rf yacy;cd ../../;startYACY.sh").getBytes();
        try {
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            serverLog.logInfo("UPDATE", "INITIATED");
            String script =
                "#!/bin/sh" + serverCore.lfstring +
                "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.lfstring +
                "gunzip -c " + release + " | tar xf -" + serverCore.lfstring +
                "while [ -f ../yacy.running ]; do" + serverCore.lfstring +
                "sleep 1" + serverCore.lfstring +
                "done" + serverCore.lfstring +
                "cp -Rf yacy/* ../../" + serverCore.lfstring +
                "rm -Rf yacy" + serverCore.lfstring +
                "cd ../../" + serverCore.lfstring +
                "nohup ./startYACY.sh > /dev/null" + serverCore.lfstring;
            File scriptFile = new File(sb.getRootPath(), "DATA/RELEASE/update.sh");
            serverSystem.deployScript(scriptFile, script);
            serverLog.logInfo("UPDATE", "wrote update-script to " + scriptFile.getAbsolutePath());
            serverSystem.execAsynchronous(scriptFile);
            serverLog.logInfo("UPDATE", "script is running");
            sb.setConfig("update.time.deploy", System.currentTimeMillis());
            sb.terminate(5000);
        } catch (IOException e) {
            serverLog.logSevere("UPDATE", "update failed", e);
        }
    }
    
    /**
     * Converts combined version-string to a pretty string, e.g. "0.435/01818" or "dev/01818" (development version) or "dev/00000" (in case of wrong input)
     *
     * @param ver Combined version string matching regular expression:  "\A(\d+\.\d{3})(\d{4}|\d{5})\z" <br>
     *  (i.e.: start of input, 1 or more digits in front of decimal point, decimal point followed by 3 digits as major version, 4 or 5 digits for SVN-Version, end of input) 
     * @return If the major version is &lt; 0.11  - major version is separated from SVN-version by '/', e.g. "0.435/01818" <br>
     *         If the major version is &gt;= 0.11 - major version is replaced by "dev" and separated SVN-version by '/', e.g."dev/01818" <br> 
     *         "dev/00000" - If the input does not matcht the regular expression above 
     */
     public static String combined2prettyVersion(String ver) {
         return combined2prettyVersion(ver, "");
     }
     public static String combined2prettyVersion(String ver, String computerName) {
         final Matcher matcher = Pattern.compile("\\A(\\d+\\.\\d{1,3})(\\d{0,5})\\z").matcher(ver); 

         if (!matcher.find()) { 
             serverLog.logWarning("STARTUP", "Peer '"+computerName+"': wrong format of version-string: '" + ver + "'. Using default string 'dev/00000' instead");   
             return "dev/00000";
         }
         
         String mainversion = (Double.parseDouble(matcher.group(1)) < 0.11 ? "dev" : matcher.group(1));
        String revision = matcher.group(2);
        for(int i=revision.length();i<5;++i) revision += "0";
        return mainversion+"/"+revision;
     }
        
     /**
     * Combines the version of YaCy with the versionnumber from SVN to a
     * combined version
     *
     * @param version Current given version.
     * @param svn Current version given from SVN.
     * @return String with the combined version.
     */
     public static double versvn2combinedVersion(double v, int svn) {
        return (Math.rint((v*100000000.0) + ((double)svn))/100000000);
     }
     
     public static void main(String[] args) {
         float base = (float) 0.53;
         String blacklist = "....[123]";
         String test;
         for (int i = 0; i < 20; i++) {
             test = Float.toString(base + (((float) i) / 1000));
             System.out.println(test + " is " + ((test.matches(blacklist)) ? "blacklisted" : " not blacklisted"));
         }
     }
     
}
