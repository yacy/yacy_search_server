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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;

public final class yacyVersion implements Comparator<yacyVersion>, Comparable<yacyVersion> {
    
    // general release info
    public static final float YACY_SUPPORTS_PORT_FORWARDING = (float) 0.383;
    public static final float YACY_SUPPORTS_GZIP_POST_REQUESTS = (float) 0.40300772;
    public static final float YACY_ACCEPTS_RANKING_TRANSMISSION = (float) 0.414;
    public static final float YACY_HANDLES_COLLECTION_INDEX = (float) 0.486;
    public static final float YACY_POVIDES_REMOTECRAWL_LISTS = (float) 0.550;
    public static final float YACY_STANDARDREL_IS_PRO = (float) 0.557;

    // information about latest release, retrieved by other peers release version
    public static double latestRelease = 0.1; // this value is overwritten when a peer with later version appears

    // information about latest release, retrieved from download pages
    // this static information should be overwritten by network-specific locations
    // for details see defaults/yacy.network.freeworld.unit
    private static HashMap<yacyURL, DevMain> latestReleases = new HashMap<yacyURL, DevMain>();
    public  static ArrayList<yacyURL> latestReleaseLocations = new ArrayList<yacyURL>(); // will be initialized with value in defaults/yacy.network.freeworld.unit
    
    // private static release info about this release; is generated only once and can be retrieved by thisVersion()
    private static yacyVersion thisVersion = null;
    
    // class variables
    public float releaseNr;
    public String dateStamp;
    public int svn;
    public boolean mainRelease;
    public yacyURL url;
    public String name;
    
    public yacyVersion(yacyURL url) {
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
        if ((release == null) || (!((release.endsWith(".tar.gz") || (release.endsWith(".tar")))))) {
            throw new RuntimeException("release file name '" + release + "' is not valid, no tar.gz");
        }
        // cut off tail
        release = release.substring(0, release.length() - ((release.endsWith(".gz")) ? 7 : 4));
        if (release.startsWith("yacy_pro_v")) {
            release = release.substring(10);
        } else if (release.startsWith("yacy_emb_v")) {
            throw new RuntimeException("release file name '" + release + "' is not valid, no support for emb");
        } else if (release.startsWith("yacy_v")) {
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
    
    public static final class DevMain {
        public TreeSet<yacyVersion> dev, main;
        public DevMain(TreeSet<yacyVersion> dev, TreeSet<yacyVersion> main) {
            this.dev = dev;
            this.main = main;
        }
    }
    
    public int compareTo(yacyVersion obj) {
        // returns 0 if this object is equal to the obj, -1 if this is smaller than obj and 1 if this is greater than obj
        return compare(this, obj);
    }
    
    public int compare(yacyVersion v0, yacyVersion v1) {
        // compare-operator for two yacyVersion objects
        // must be implemented to make it possible to put this object into
        // a ordered structure, like TreeSet or TreeMap
        return (new Integer(v0.svn)).compareTo(new Integer(v1.svn));
    }
    
    public boolean equals(Object obj) {
        if(obj instanceof yacyVersion) {
            yacyVersion v = (yacyVersion) obj;
            return (this.svn == v.svn) && (this.url.toNormalform(true, true).equals(v.url.toNormalform(true, true)));
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        return this.url.toNormalform(true, true).hashCode();
    }
    
    public String toAnchor() {
        // generates an anchor string that can be used to embed in an html for direct download
        return "<a href=" + this.url.toNormalform(true, true) + ">YaCy " + ((this.mainRelease) ? "main release" : "dev release") + " v" + this.releaseNr + ", SVN " + this.svn + "</a>";
    }
    
    // static methods:

    public static final yacyVersion thisVersion() {
        // construct a virtual release name for this release
        if (thisVersion == null) {
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            if (sb == null) return null;
            boolean full = new File(sb.getRootPath(), "libx").exists();
            thisVersion = new yacyVersion(
                "yacy" + ((full) ? "" : "_emb") +
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
        
        // check if update process allows update retrieve
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
        DevMain releasess = yacyVersion.allReleases(true);
        yacyVersion latestmain = (releasess.main.size() == 0) ? null : releasess.main.last();
        yacyVersion latestdev  = (releasess.dev.size() == 0) ? null : releasess.dev.last();
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
    
    public static DevMain allReleases(boolean force) {
        // join the release infos
        DevMain[] a = new DevMain[latestReleaseLocations.size()];
        for (int j = 0; j < latestReleaseLocations.size(); j++) {
            a[j] = getReleases(latestReleaseLocations.get(j), force);
        }
        TreeSet<yacyVersion> alldev = new TreeSet<yacyVersion>();
        TreeSet<yacyVersion> allmain = new TreeSet<yacyVersion>();
        for (int j = 0; j < a.length; j++) if ((a[j] != null) && (a[j].dev != null)) alldev.addAll(a[j].dev);
        for (int j = 0; j < a.length; j++) if ((a[j] != null) && (a[j].main != null)) allmain.addAll(a[j].main);
            
        return new DevMain(alldev, allmain);
    }
    
    private static DevMain getReleases(yacyURL location, boolean force) {
        // get release info from a internet resource
        DevMain latestRelease = latestReleases.get(location);
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
    
    private static DevMain allReleaseFrom(yacyURL url) {
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
        Map<yacyURL, String> anchors = scraper.getAnchors(); // a url (String) / name (String) relation
        Iterator<yacyURL> i = anchors.keySet().iterator();
        TreeSet<yacyVersion> devreleases = new TreeSet<yacyVersion>();
        TreeSet<yacyVersion> mainreleases = new TreeSet<yacyVersion>();
        yacyVersion release;
        while (i.hasNext()) {
            url = i.next();
            try {
                release = new yacyVersion(url);
                //System.out.println("r " + release.toAnchor());
                if ( release.mainRelease) mainreleases.add(release);
                if (!release.mainRelease) devreleases.add(release);
            } catch (RuntimeException e) {
                // the release string was not well-formed.
                // that might have been another link
                // just dont care
                continue;
            }
        }
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.lookup", System.currentTimeMillis());
        return new DevMain(devreleases, mainreleases);
    }
    
    public static File downloadRelease(yacyVersion release) {
        File storagePath = plasmaSwitchboard.getSwitchboard().releasePath;
        // load file
        File download = null;
        JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(120000, null, null);
        JakartaCommonsHttpResponse res = null;
        String name = release.url.getFileName();
        try {
            res = client.GET(release.url.toString());
            boolean unzipped = res.getResponseHeader().gzip() && (res.getResponseHeader().mime().toLowerCase().equals("application/x-tar")); // if true, then the httpc has unzipped the file
            if ((unzipped) && (name.endsWith(".tar.gz"))) {
                download = new File(storagePath, name.substring(0, name.length() - 3));
            } else {
                download = new File(storagePath, name);
            }
            try {
                serverFileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(new FileOutputStream(download)));
            } finally {
                res.closeStream();
            }
            if ((!download.exists()) || (download.length() == 0)) throw new IOException("wget of url " + release.url + " failed");
        } catch (IOException e) {
            serverLog.logSevere("yacyVersion", "download of " + release.name + " failed: " + e.getMessage());
            if (download.exists()) download.delete();
            download = null;
        } finally {
            if (res != null) {
                // release connection
                res.closeStream();
            }
        }
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.download", System.currentTimeMillis());
        return ((download != null) && (download.exists())) ? download : null;
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
                    "#!/bin/sh" + serverCore.LF_STRING +
                    "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
                    "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                    "sleep 1" + serverCore.LF_STRING +
                    "done" + serverCore.LF_STRING +
                    "cd ../../" + serverCore.LF_STRING +
                    "nohup ./startYACY.sh > /dev/null" + serverCore.LF_STRING;
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
    
    public static void deployRelease(File releaseFile) {
        //byte[] script = ("cd " + plasmaSwitchboard.getSwitchboard().getRootPath() + ";while [ -e ../yacy.running ]; do sleep 1;done;tar xfz " + release + ";cp -Rf yacy/* ../../;rm -Rf yacy;cd ../../;startYACY.sh").getBytes();
        try {
            plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            String apphome = sb.getRootPath().toString();
            serverLog.logInfo("UPDATE", "INITIATED");
            String script =
                "#!/bin/sh" + serverCore.LF_STRING +
                "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
                ((releaseFile.getName().endsWith(".gz")) ?
                        // test gz-file for integrity and tar xfz then
                       ("if gunzip -t " + releaseFile.getAbsolutePath() + serverCore.LF_STRING +
                        "then" + serverCore.LF_STRING + 
                        "gunzip -c " + releaseFile.getAbsolutePath() + " | tar xf -" + serverCore.LF_STRING) :
                        // just tar xf the file, no integrity test possible?
                       ("tar xf " + releaseFile.getAbsolutePath() + serverCore.LF_STRING)
                ) +
                "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                "sleep 1" + serverCore.LF_STRING +
                "done" + serverCore.LF_STRING +
                "cp -Rf yacy/* " + apphome + serverCore.LF_STRING +
                "rm -Rf yacy" + serverCore.LF_STRING +
                ((releaseFile.getName().endsWith(".gz")) ?
                        // else-case of gunzip -t test: if failed, just restart
                       ("else" + serverCore.LF_STRING +
                        "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                        "sleep 1" + serverCore.LF_STRING +
                        "done" + serverCore.LF_STRING +
                        "fi" + serverCore.LF_STRING) :
                        // in case that we did not made the integrity test, there is no else case
                        ""
                ) +
                "cd " + apphome + serverCore.LF_STRING +
                "nohup ./startYACY.sh > /dev/null" + serverCore.LF_STRING;
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
     public static double versvn2combinedVersion(double version, int svn) {
        return (Math.rint((version*100000000.0) + ((double)svn))/100000000);
     }
     
     public static void main(String[] args) {
         System.out.println(thisVersion());
         float base = (float) 0.53;
         String blacklist = "....[123]";
         String test;
         for (int i = 0; i < 20; i++) {
             test = Float.toString(base + (((float) i) / 1000));
             System.out.println(test + " is " + ((test.matches(blacklist)) ? "blacklisted" : " not blacklisted"));
         }
     }

    /**
     * keep only releases of last month (minimum latest and 1 main (maybe the same))
     * 
     * @param filesPath where all downloaded files reside
     * @param deleteAfterDays 
     */
    public static void deleteOldDownloads(File filesPath, int deleteAfterDays) {
        // list downloaded releases
        yacyVersion release;
        String[] downloaded = filesPath.list();
          
        // parse all filenames and put them in a sorted set
        SortedSet<yacyVersion> downloadedreleases = new TreeSet<yacyVersion>();
        for (int j = 0; j < downloaded.length; j++) {
            try {
                release = new yacyVersion(downloaded[j]);
                downloadedreleases.add(release);
            } catch (RuntimeException e) {
                // not a valid release
            }
        }
        
        // if we have some files
        if(downloadedreleases.size() > 0) {
            serverLog.logFine("STARTUP", "deleting downloaded releases older than "+ deleteAfterDays +" days");
            
            // keep latest version
            final yacyVersion latest = downloadedreleases.last();
            downloadedreleases.remove(latest);
            // if latest is a developer release, we also keep a main release
            boolean keepMain = !latest.mainRelease;
            
            // remove old files
            long now = System.currentTimeMillis();
            final long deleteAfterMillis = deleteAfterDays * 24 * 60 * 60000l;
            
            String lastMain = null;
            String filename;
            for (final yacyVersion aRelease : downloadedreleases) {
                filename = aRelease.name;
                if (keepMain && aRelease.mainRelease) {
                    // keep this one, delete last remembered main release file
                    if (lastMain != null) {
                        filename = lastMain;
                    }
                    lastMain = aRelease.name;
                }

                // check file age
                File downloadedFile = new File(filesPath + File.separator + filename);
                if (now - downloadedFile.lastModified() > deleteAfterMillis) {
                    // delete file
                    if (!downloadedFile.delete()) {
                        serverLog.logWarning("STARTUP", "cannot delete old release " + downloadedFile.getAbsolutePath());
                    }
                }
            }
        }
    }
     
}
