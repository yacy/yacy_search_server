// yacyVersion.java 
// ----------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.04.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-04-17 16:52:42 +0200 (Fr, 17. Apr 2009) $
// $LastChangedRevision: 5828 $
// $LastChangedBy: f1ori $
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import de.anomic.crawler.HTTPLoader;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpClient;
import de.anomic.http.httpResponse;
import de.anomic.http.httpResponseHeader;
import de.anomic.http.httpRequestHeader;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverCore;
import de.anomic.server.serverSystem;
import de.anomic.tools.CryptoLib;
import de.anomic.tools.SignatureOutputStream;
import de.anomic.tools.tarTools;
import de.anomic.yacy.logging.Log;

public final class yacyRelease extends yacyVersion {

    // information about latest release, retrieved from download pages
    // this static information should be overwritten by network-specific locations
    // for details see defaults/yacy.network.freeworld.unit
    private static HashMap<yacyUpdateLocation, DevAndMainVersions> latestReleases = new HashMap<yacyUpdateLocation, DevAndMainVersions>();
    public  final static ArrayList<yacyUpdateLocation> latestReleaseLocations = new ArrayList<yacyUpdateLocation>(); // will be initialized with value in defaults/yacy.network.freeworld.unit
    
    private yacyURL url;
    private File releaseFile;
    
    private PublicKey publicKey;
    
    public yacyRelease(final yacyURL url) {
        super(url.getFileName());
        this.url = url;
    }
    
    public yacyRelease(final yacyURL url, PublicKey publicKey) {
        this(url);
        this.publicKey = publicKey;
    }
    
    public yacyRelease(final File releaseFile) {
    super(releaseFile.getName());
    this.releaseFile = releaseFile;
    }

    public yacyURL getUrl() {
    return url;
    }
    
    public static final yacyRelease rulebasedUpdateInfo(final boolean manual) {
        // according to update properties, decide if we should retrieve update information
        // if true, the release that can be obtained is returned.
        // if false, null is returned
        final plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        
        // check if update process allows update retrieve
        final String process = sb.getConfig("update.process", "manual");
        if ((!manual) && (!process.equals("auto"))) {
            yacyCore.log.logInfo("rulebasedUpdateInfo: not an automatic update selected");
            return null; // no, its a manual or guided process
        }
        
        // check if the last retrieve time is a minimum time ago
        final long cycle = Math.max(1, sb.getConfigLong("update.cycle", 168)) * 60 * 60 * 1000; // update.cycle is hours
        final long timeLookup = sb.getConfigLong("update.time.lookup", System.currentTimeMillis());
        if ((!manual) && (timeLookup + cycle > System.currentTimeMillis())) {
            yacyCore.log.logInfo("rulebasedUpdateInfo: too early for a lookup for a new release (timeLookup = " + timeLookup + ", cycle = " + cycle + ", now = " + System.currentTimeMillis() + ")");
            return null; // no we have recently made a lookup
        }
        
        // check if we know that there is a release that is more recent than that which we are using
        final DevAndMainVersions releases = yacyRelease.allReleases(true, sb.getConfig("update.onlySignedFiles", "1").equals("1"));
        final yacyRelease latestmain = (releases.main.size() == 0) ? null : releases.main.last();
        final yacyRelease latestdev  = (releases.dev.size() == 0) ? null : releases.dev.last();
        final String concept = sb.getConfig("update.concept", "any");
        String blacklist = sb.getConfig("update.blacklist", "...[123]");
        if (blacklist.equals("....[123]")) {
            // patch the blacklist because of a release strategy change from 0.7 and up
            blacklist = "...[123]";
            sb.setConfig("update.blacklist", blacklist);
        }
        
        if ((manual) || (concept.equals("any"))) {
            // return a dev-release or a main-release
            if ((latestdev != null) &&
                ((latestmain == null) || (latestdev.compareTo(latestmain) > 0)) &&
                (!(Float.toString(latestdev.getReleaseNr()).matches(blacklist)))) {
                // consider a dev-release
                if (latestdev.compareTo(thisVersion()) <= 0) {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest dev " + latestdev.getName() +
                            " is not more recent than installed release " + thisVersion().getName());
                    return null;
                }
                return latestdev;
            }
            if (latestmain != null) {
                // consider a main release
                if ((Float.toString(latestmain.getReleaseNr()).matches(blacklist))) {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest dev " + (latestdev == null ? "null" : latestdev.getName()) +
                            " matches with blacklist '" + blacklist + "'");
                    return null;
                }
                if (latestmain.compareTo(thisVersion()) <= 0) {
                    yacyCore.log.logInfo(
                            "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                            " is not more recent than installed release (1) " + thisVersion().getName());
                    return null;
                }
                return latestmain;
            }
        }
        if ((concept.equals("main")) && (latestmain != null)) {
            // return a main-release
            if ((Float.toString(latestmain.getReleaseNr()).matches(blacklist))) {
                yacyCore.log.logInfo(
                        "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                        " matches with blacklist'" + blacklist + "'");
                return null;
            }
            if (latestmain.compareTo(thisVersion()) <= 0) {
                yacyCore.log.logInfo(
                        "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                        " is not more recent than installed release (2) " + thisVersion().getName());
                return null; 
            }
            return latestmain;
        }
        yacyCore.log.logInfo("rulebasedUpdateInfo: failed to find more recent release");
        return null;
    }
    
    public static DevAndMainVersions allReleases(final boolean force, final boolean onlySigned) {
        // join the release infos
        final TreeSet<yacyRelease> alldev = new TreeSet<yacyRelease>();
        final TreeSet<yacyRelease> allmain = new TreeSet<yacyRelease>();
        for (yacyUpdateLocation updateLocation : latestReleaseLocations) {
            if(!onlySigned || updateLocation.getPublicKey() != null) {
                DevAndMainVersions versions = getReleases(updateLocation, force);
                if ((versions != null) && (versions.dev != null)) alldev.addAll(versions.dev);
                if ((versions != null) && (versions.main != null)) allmain.addAll(versions.main);
            }
        }
        return new DevAndMainVersions(alldev, allmain);
    }
    
    /**
     * get all Releases from update location using cache 
     * @param location Update location
     * @param force    when true, don't fetch from cache
     * @return
     */
    private static DevAndMainVersions getReleases(final yacyUpdateLocation location, final boolean force) {
        // get release info from a Internet resource
        DevAndMainVersions locLatestRelease = latestReleases.get(location);
        if (force ||
            (locLatestRelease == null) /*||
            ((latestRelease[0].size() == 0) &&
             (latestRelease[1].size() == 0) &&
             (latestRelease[2].size() == 0) &&
             (latestRelease[3].size() == 0) )*/) {
            locLatestRelease = allReleaseFrom(location);
            latestReleases.put(location, locLatestRelease);
        }
        return locLatestRelease;
    }
    
    /**
     * get all releases from update location
     * @param location
     * @return
     */
    private static DevAndMainVersions allReleaseFrom(yacyUpdateLocation location) {
        // retrieves the latest info about releases
        // this is done by contacting a release location,
        // parsing the content and filtering+parsing links
        // returns the version info if successful, null otherwise
        htmlFilterContentScraper scraper;
        try {
            scraper = htmlFilterContentScraper.parseResource(location.getLocationURL());
        } catch (final IOException e) {
            return null;
        }
        
        // analyse links in scraper resource, and find link to latest release in it
        final Map<yacyURL, String> anchors = scraper.getAnchors(); // a url (String) / name (String) relation
        final TreeSet<yacyRelease> mainReleases = new TreeSet<yacyRelease>();
        final TreeSet<yacyRelease> devReleases = new TreeSet<yacyRelease>();
        for(yacyURL url : anchors.keySet()) {
            try {
                yacyRelease release = new yacyRelease(url, location.getPublicKey());
                //System.out.println("r " + release.toAnchor());
                if(release.isMainRelease()) {
                    mainReleases.add(release);
                } else {
                    devReleases.add(release);
                }
            } catch (final RuntimeException e) {
                // the release string was not well-formed.
                // that might have been another link
                // just don't care
                continue;
            }
        }
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.lookup", System.currentTimeMillis());
        return new DevAndMainVersions(devReleases, mainReleases);
    }
    
    public static final class DevAndMainVersions {
        public TreeSet<yacyRelease> dev, main;
        public DevAndMainVersions(final TreeSet<yacyRelease> dev, final TreeSet<yacyRelease> main) {
            this.dev = dev;
            this.main = main;
        }
    }

    
    /**
     * <p>download this release and if public key is know, download signature and check it.
     * <p>The signature is named $releaseurl.sig and contains the base64 encoded signature
     * (@see de.anomic.tools.CryptoLib)
     * @return file object of release file, null in case of failure
     */
    public File downloadRelease() {
        final File storagePath = plasmaSwitchboard.getSwitchboard().releasePath;
        File download = null;
        // setup httpClient
        final httpRequestHeader reqHeader = new httpRequestHeader();
        reqHeader.put(httpResponseHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
        
        httpResponse res = null;
        final String name = this.getUrl().getFileName();
        byte[] signatureBytes = null;
        
        // download signature first, if public key is available
        if (this.publicKey != null) {
	        final byte[] signatureData = httpClient.wget(this.getUrl().toString() + ".sig", reqHeader, 6000);
	        if (signatureData == null) {
	            Log.logWarning("yacyVersion", "download of signature " + this.getUrl().toString() + " failed. ignoring signature file.");
	        } else try {
	            signatureBytes = Base64Order.standardCoder.decode(new String(signatureData, "UTF8").trim(), "decode signature");
	        } catch (UnsupportedEncodingException e) {
	            Log.logWarning("yacyVersion", "download of signature " + this.getUrl().toString() + " failed: unsupported encoding");
	        }
	        // in case that the download of a signature file failed (can be caused by bad working http servers), then it is assumed that no signature exists
	    }
	    try {
	        final httpClient client = new httpClient(120000, reqHeader);
	        res = client.GET(this.getUrl().toString());
	
	        final boolean unzipped = res.getResponseHeader().gzip() && (res.getResponseHeader().mime().toLowerCase().equals("application/x-tar")); // if true, then the httpc has unzipped the file
	        if ((unzipped) && (name.endsWith(".tar.gz"))) {
	        	download = new File(storagePath, name.substring(0, name.length() - 3));
	        } else {
	        	download = new File(storagePath, name);
	        }
	        if (this.publicKey != null && signatureBytes != null) {
		        // copy to file and check signature
		        SignatureOutputStream verifyOutput = null;
		        try {
		            verifyOutput = new SignatureOutputStream(new FileOutputStream(download), CryptoLib.signAlgorithm, publicKey);
		            FileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(verifyOutput));
		
		            if (!verifyOutput.verify(signatureBytes)) throw new IOException("Bad Signature!");
		        } catch (NoSuchAlgorithmException e) {
		            throw new IOException("No such algorithm");
		        } catch (SignatureException e) {
		            throw new IOException("Signature exception");
		        } finally {
		            if (verifyOutput != null)
		            verifyOutput.close();
		        }
		        // Save signature
		        File signatureFile = new File(download.getAbsoluteFile() + ".sig");
		        FileUtils.copy(Base64Order.standardCoder.encode(signatureBytes).getBytes("UTF-8"), signatureFile);
		        if ((!signatureFile.exists()) || (signatureFile.length() == 0)) throw new IOException("create signature file failed");
	        } else {
		        // just copy into file
		        FileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(new FileOutputStream(download)));
	        }
	        if ((!download.exists()) || (download.length() == 0)) throw new IOException("wget of url " + this.getUrl() + " failed");
	    } catch (final IOException e) {
	        // Saving file failed, abort download
	        if (res != null) res.abort();
	        Log.logSevere("yacyVersion", "download of " + this.getName() + " failed: " + e.getMessage());
	        if (download != null && download.exists()) {
	        	FileUtils.deletedelete(download);
	        	if (download.exists()) Log.logWarning("yacyVersion", "could not delete file "+ download);
	        }
	        download = null;
	    } finally {
	        if (res != null) {
	        	// release connection
	        	res.closeStream();
	        }
	    }
        this.releaseFile = download;
        plasmaSwitchboard.getSwitchboard().setConfig("update.time.download", System.currentTimeMillis());
        return this.releaseFile;
    }
    
    public boolean checkSignature() {
    if(releaseFile != null) {
        try {
        serverCharBuffer signBuffer;
        signBuffer = new serverCharBuffer(getSignatureFile());
        byte[] signByteBuffer = Base64Order.standardCoder.decode(
            signBuffer.toString().trim(), "Signature");
        CryptoLib cl = new CryptoLib();
        for(yacyUpdateLocation updateLocation : latestReleaseLocations) {
            try {
            if(cl.verifySignature(updateLocation.getPublicKey(),
                new FileInputStream(releaseFile), signByteBuffer)) {
                return true;
            }
            } catch (InvalidKeyException e) {
            } catch (SignatureException e) {
            }
        }
        } catch (IOException e1) {
        } catch (NoSuchAlgorithmException e) {
        }

    }
    return false;
    }

    /**
     * restart yacy by stopping yacy and previously running a batch
     * script, which waits until yacy is terminated and starts it again
     */
    public static void restart() {
            final plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            final String apphome = sb.getRootPath().toString();
            
            if (serverSystem.isWindows) {
                final File startType = new File(sb.getRootPath(), "DATA/yacy.noconsole".replace("/", File.separator));
                String starterFile = "startYACY_debug.bat";
                if (startType.exists()) starterFile = "startYACY.bat"; // startType noconsole
                
                try{
                    Log.logInfo("RESTART", "INITIATED");
                    final String script =
                        "@echo off" + serverCore.LF_STRING +
                        "title YaCy restarter" + serverCore.LF_STRING +
                        "set loading=YACY RESTARTER" + serverCore.LF_STRING +
                        "echo %loading%" + serverCore.LF_STRING +
                        "cd \"" + apphome + "/DATA/RELEASE/".replace("/", File.separator) + "\"" + serverCore.LF_STRING +
                        ":WAIT" + serverCore.LF_STRING +
                        "set loading=%loading%." + serverCore.LF_STRING +
                        "cls" + serverCore.LF_STRING +
                        "echo %loading%" + serverCore.LF_STRING +
                        "ping -n 2 127.0.0.1 >nul" + serverCore.LF_STRING +
                        "IF exist ..\\yacy.running goto WAIT" + serverCore.LF_STRING +
                        "cd \"" + apphome + "\"" + serverCore.LF_STRING +
                        "start /MIN CMD /C " + starterFile + serverCore.LF_STRING;
                    final File scriptFile = new File(sb.getRootPath(), "DATA/RELEASE/restart.bat".replace("/", File.separator));
                    serverSystem.deployScript(scriptFile, script);
                    Log.logInfo("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                    serverSystem.execAsynchronous(scriptFile);
                    Log.logInfo("RESTART", "script is running");
                    sb.terminate(5000);
                } catch (final IOException e) {
                    Log.logSevere("RESTART", "restart failed", e);
                }
            
                // create yacy.restart file which is used in Windows startscript
    /*            final File yacyRestart = new File(sb.getRootPath(), "DATA/yacy.restart");
                if (!yacyRestart.exists()) {
                    try {
                        yacyRestart.createNewFile();
                        plasmaSwitchboard.getSwitchboard().terminate(5000);
                    } catch (IOException e) {
                        serverLog.logSevere("SHUTDOWN", "restart failed", e);
                    }
                }*/
                
            }
    
            if (serverSystem.canExecUnix) {
                // start a re-start daemon
                try {
                    Log.logInfo("RESTART", "INITIATED");
                    final String script =
                        "#!/bin/sh" + serverCore.LF_STRING +
                        "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
                        "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                        "sleep 1" + serverCore.LF_STRING +
                        "done" + serverCore.LF_STRING +
                        "cd ../../" + serverCore.LF_STRING +
                        "nohup ./startYACY.sh > /dev/null" + serverCore.LF_STRING;
                    final File scriptFile = new File(sb.getRootPath(), "DATA/RELEASE/restart.sh");
                    serverSystem.deployScript(scriptFile, script);
                    Log.logInfo("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                    serverSystem.execAsynchronous(scriptFile);
                    Log.logInfo("RESTART", "script is running");
                    sb.terminate(5000);
                } catch (final IOException e) {
                    Log.logSevere("RESTART", "restart failed", e);
                }
            }
        }

    /**
     * stop yacy and run a batch script, applies a new release and restarts yacy
     * @param releaseFile
     */
    public static void deployRelease(final File releaseFile) {
        //byte[] script = ("cd " + plasmaSwitchboard.getSwitchboard().getRootPath() + ";while [ -e ../yacy.running ]; do sleep 1;done;tar xfz " + release + ";cp -Rf yacy/* ../../;rm -Rf yacy;cd ../../;startYACY.sh").getBytes();
        try {
            final plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
            final String apphome = sb.getRootPath().toString();
            Log.logInfo("UPDATE", "INITIATED");
            try{
            tarTools.unTar(tarTools.getInputStream(releaseFile), sb.getRootPath() + "/DATA/RELEASE/".replace("/", File.separator));
            } catch (final Exception e){
                Log.logSevere("UNTAR", "failed", e);
            }
            String script = null;
            String scriptFileName = null;
            if (serverSystem.isWindows) {
                final File startType = new File(sb.getRootPath(), "DATA/yacy.noconsole".replace("/", File.separator));
                String starterFile = "startYACY_debug.bat";
                if (startType.exists()) starterFile = "startYACY.bat"; // startType noconsole
                script = 
                    "@echo off" + serverCore.LF_STRING +
                    "title YaCy updater" + serverCore.LF_STRING +
                    "set loading=YACY UPDATER" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "cd \"" + apphome + "/DATA/RELEASE/".replace("/", File.separator) + "\"" + serverCore.LF_STRING +
    
                    ":WAIT" + serverCore.LF_STRING +
                    "set loading=%loading%." + serverCore.LF_STRING +
                    "cls" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "ping -n 2 127.0.0.1 >nul" + serverCore.LF_STRING +
                    "IF exist ..\\yacy.running goto WAIT" + serverCore.LF_STRING +
                    "IF not exist yacy goto NODATA" + serverCore.LF_STRING +

                    "cd yacy" + serverCore.LF_STRING +
                    "xcopy *.* \"" + apphome + "\" /E /Y >nul" + serverCore.LF_STRING +
                    // /E - all subdirectories
                    // /Y - don't ask
                    "cd .." + serverCore.LF_STRING +
                    "rd yacy /S /Q" + serverCore.LF_STRING +
                    // /S delete tree
                    // /Q don't ask
                    "goto END" + serverCore.LF_STRING +
    
                    ":NODATA" + serverCore.LF_STRING +
                    "echo YACY UPDATER ERROR: NO UPDATE SOURCE FILES ON FILESYSTEM" + serverCore.LF_STRING +
                    "pause" + serverCore.LF_STRING +
    
                    ":END" + serverCore.LF_STRING +
                    "cd \"" + apphome + "\"" + serverCore.LF_STRING +
                    "start /MIN CMD /C " + starterFile + serverCore.LF_STRING;
                scriptFileName = "update.bat";
            } else { // unix/linux
                script =
                    "#!/bin/sh" + serverCore.LF_STRING +
                    "cd " + sb.getRootPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
    /*                ((releaseFile.getName().endsWith(".gz")) ?
                            // test gz-file for integrity and tar xfz then
                           ("if gunzip -t " + releaseFile.getAbsolutePath() + serverCore.LF_STRING +
                            "then" + serverCore.LF_STRING + 
                            "gunzip -c " + releaseFile.getAbsolutePath() + " | tar xf -" + serverCore.LF_STRING) :
                            // just tar xf the file, no integrity test possible?
                           ("tar xf " + releaseFile.getAbsolutePath() + serverCore.LF_STRING)
                    ) +*/
                    "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                    "sleep 1" + serverCore.LF_STRING +
                    "done" + serverCore.LF_STRING +
                    "cp -Rf yacy/* " + apphome + serverCore.LF_STRING +
                    "rm -Rf yacy" + serverCore.LF_STRING +
    /*                ((releaseFile.getName().endsWith(".gz")) ?
                            // else-case of gunzip -t test: if failed, just restart
                           ("else" + serverCore.LF_STRING +
                            "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                            "sleep 1" + serverCore.LF_STRING +
                            "done" + serverCore.LF_STRING +
                            "fi" + serverCore.LF_STRING) :
                            // in case that we did not made the integrity test, there is no else case
                            ""
                    ) +*/
                    "cd " + apphome + serverCore.LF_STRING +
                    "nohup ./startYACY.sh > /dev/null" + serverCore.LF_STRING;
                scriptFileName = "update.sh";
            }
            final File scriptFile = new File(sb.getRootPath(), "DATA/RELEASE/".replace("/", File.separator) + scriptFileName); 
            serverSystem.deployScript(scriptFile, script);
            Log.logInfo("UPDATE", "wrote update-script to " + scriptFile.getAbsolutePath());
            serverSystem.execAsynchronous(scriptFile);
            Log.logInfo("UPDATE", "script is running");
            sb.setConfig("update.time.deploy", System.currentTimeMillis());
            sb.terminate(5000);
        } catch (final IOException e) {
            Log.logSevere("UPDATE", "update failed", e);
        }
    }
    
    public static void main(final String[] args) {
         System.out.println(thisVersion());
         final float base = (float) 0.53;
         final String blacklist = "....[123]";
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
    public static void deleteOldDownloads(final File filesPath, final int deleteAfterDays) {
        // list downloaded releases
        yacyVersion release;
        final String[] downloaded = filesPath.list();
          
        // parse all filenames and put them in a sorted set
        final SortedSet<yacyVersion> downloadedreleases = new TreeSet<yacyVersion>();
        for (int j = 0; j < downloaded.length; j++) {
            try {
                release = new yacyVersion(downloaded[j]);
                downloadedreleases.add(release);
            } catch (final RuntimeException e) {
                // not a valid release
            }
        }
        
        // if we have some files
        if(downloadedreleases.size() > 0) {
            Log.logFine("STARTUP", "deleting downloaded releases older than "+ deleteAfterDays +" days");
            
            // keep latest version
            final yacyVersion latest = downloadedreleases.last();
            downloadedreleases.remove(latest);
            // if latest is a developer release, we also keep a main release
            final boolean keepMain = !latest.isMainRelease();
            
            // remove old files
            final long now = System.currentTimeMillis();
            final long deleteAfterMillis = deleteAfterDays * 24L * 60 * 60000;
            
            String lastMain = null;
            String filename;
            for (final yacyVersion aVersion : downloadedreleases) {
                filename = aVersion.getName();
                if (keepMain && aVersion.isMainRelease()) {
                    // keep this one, delete last remembered main release file
                    if (lastMain != null) {
                        filename = lastMain;
                    }
                    lastMain = aVersion.getName();
                }

                // check file age
                final File downloadedFile = new File(filesPath + File.separator + filename);
                if (now - downloadedFile.lastModified() > deleteAfterMillis) {
                    // delete file
                    FileUtils.deletedelete(downloadedFile);
                    if (downloadedFile.exists()) {
                        Log.logWarning("STARTUP", "cannot delete old release " + downloadedFile.getAbsolutePath());
                    }
                }
            }
        }
    }
    
    public File getReleaseFile() {
        return releaseFile;
    }
    
    public File getSignatureFile() {
        return new File(releaseFile.getAbsoluteFile() + ".sig");
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

}
