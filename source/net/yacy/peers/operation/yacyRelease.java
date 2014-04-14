// yacyRelease.java
// ----------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

package net.yacy.peers.operation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.storage.Files;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;
import net.yacy.document.parser.tarParser;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.OS;
import net.yacy.peers.Network;
import net.yacy.search.Switchboard;
import net.yacy.server.serverCore;
import net.yacy.utils.CryptoLib;
import net.yacy.utils.SignatureOutputStream;
import net.yacy.utils.tarTools;

public final class yacyRelease extends yacyVersion {

    // information about latest release, retrieved from download pages
    // this static information should be overwritten by network-specific locations
    // for details see defaults/yacy.network.freeworld.unit
    private static Map<yacyUpdateLocation, DevAndMainVersions> latestReleases = new ConcurrentHashMap<yacyUpdateLocation, DevAndMainVersions>();
    public final static List<yacyUpdateLocation> latestReleaseLocations = new ArrayList<yacyUpdateLocation>(); // will be initialized with value in defaults/yacy.network.freeworld.unit
    public static String startParameter = "";

    private MultiProtocolURL url;
    private File releaseFile;

    private PublicKey publicKey;

    public yacyRelease(final MultiProtocolURL url) {
        super(url.getFileName(), url.getHost());
        this.url = url;
    }

    private yacyRelease(final MultiProtocolURL url, final PublicKey publicKey) {
        this(url);
        this.publicKey = publicKey;
    }

    public yacyRelease(final File releaseFile) {
        super(releaseFile.getName(), null);
        this.releaseFile = releaseFile;
    }

    public MultiProtocolURL getUrl() {
        return this.url;
    }

    public static final yacyRelease rulebasedUpdateInfo(final boolean manual) {
        // according to update properties, decide if we should retrieve update information
        // if true, the release that can be obtained is returned.
        // if false, null is returned
        final Switchboard sb = Switchboard.getSwitchboard();

        // check if release was installed by packagemanager
        if (yacyBuildProperties.isPkgManager()) {
            Network.log.info("rulebasedUpdateInfo: package manager is used for update");
            return null;
        }

        // check if update process allows update retrieve
        final String process = sb.getConfig("update.process", "manual");
        if ((!manual) && (!process.equals("auto"))) {
            Network.log.info("rulebasedUpdateInfo: not an automatic update selected");
            return null; // no, its a manual or guided process
        }

        // check if the last retrieve time is a minimum time ago
        final long cycle = Math.max(1, sb.getConfigLong("update.cycle", 168)) * 60 * 60 * 1000; // update.cycle is hours
        final long timeLookup = sb.getConfigLong("update.time.lookup", System.currentTimeMillis());
        if ((!manual) && (timeLookup + cycle > System.currentTimeMillis())) {
            Network.log.info("rulebasedUpdateInfo: too early for a lookup for a new release (timeLookup = " + timeLookup + ", cycle = " + cycle + ", now = " + System.currentTimeMillis() + ")");
            return null; // no we have recently made a lookup
        }

        // check if we know that there is a release that is more recent than that which we are using
        final DevAndMainVersions releases = yacyRelease.allReleases(true, sb.getConfig("update.onlySignedFiles", "1").equals("1"));
        final yacyRelease latestmain = (releases.main.isEmpty()) ? null : releases.main.last();
        final yacyRelease latestdev  = (releases.dev.isEmpty()) ? null : releases.dev.last();
        final String concept = sb.getConfig("update.concept", "any");
        String blacklist = sb.getConfig("update.blacklist", "");

        if ((manual) || (concept.equals("any"))) {
            // return a dev-release or a main-release
            if ((latestdev != null) &&
                ((latestmain == null) || (latestdev.compareTo(latestmain) > 0)) &&
                (!(Double.toString(latestdev.getReleaseNr()).matches(blacklist)))) {
                // consider a dev-release
                if (latestdev.compareTo(thisVersion()) <= 0) {
                    Network.log.info(
                            "rulebasedUpdateInfo: latest dev " + latestdev.getName() +
                            " is not more recent than installed release " + thisVersion().getName());
                    return null;
                }
               return latestdev;
            }
            if (latestmain != null) {
                // consider a main release
                if ((Double.toString(latestmain.getReleaseNr()).matches(blacklist))) {
                    Network.log.info(
                            "rulebasedUpdateInfo: latest dev " + (latestdev == null ? "null" : latestdev.getName()) +
                            " matches with blacklist '" + blacklist + "'");
                    return null;
                }
                if (latestmain.compareTo(thisVersion()) <= 0) {
                    Network.log.info(
                            "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                            " is not more recent than installed release (1) " + thisVersion().getName());
                    return null;
                }
                return latestmain;
            }
        }
        if ((concept.equals("main")) && (latestmain != null)) {
            // return a main-release
            if ((Double.toString(latestmain.getReleaseNr()).matches(blacklist))) {
                Network.log.info(
                        "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                        " matches with blacklist'" + blacklist + "'");
                return null;
            }
            if (latestmain.compareTo(thisVersion()) <= 0) {
                Network.log.info(
                        "rulebasedUpdateInfo: latest main " + latestmain.getName() +
                        " is not more recent than installed release (2) " + thisVersion().getName());
                return null;
            }
            return latestmain;
        }
        Network.log.info("rulebasedUpdateInfo: failed to find more recent release");
        return null;
    }

    public static DevAndMainVersions allReleases(final boolean force, final boolean onlySigned) {
        // join the release infos
        final TreeSet<yacyRelease> alldev = new TreeSet<yacyRelease>();
        final TreeSet<yacyRelease> allmain = new TreeSet<yacyRelease>();
        for (final yacyUpdateLocation updateLocation : latestReleaseLocations) {
            if(!onlySigned || updateLocation.getPublicKey() != null) {
                final DevAndMainVersions versions = getReleases(updateLocation, force);
                if (versions != null && versions.dev != null) alldev.addAll(versions.dev);
                if (versions != null && versions.main != null) allmain.addAll(versions.main);
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
            ((latestRelease[0].isEmpty()) &&
             (latestRelease[1].isEmpty()) &&
             (latestRelease[2].isEmpty()) &&
             (latestRelease[3].isEmpty()) )*/) {
            locLatestRelease = allReleaseFrom(location);
            if (locLatestRelease != null) latestReleases.put(location, locLatestRelease);
        }
        return locLatestRelease;
    }

    /**
     * get all releases from update location
     * @param location
     * @return
     */
    private static DevAndMainVersions allReleaseFrom(final yacyUpdateLocation location) {
        // retrieves the latest info about releases
        // this is done by contacting a release location,
        // parsing the content and filtering+parsing links
        // returns the version info if successful, null otherwise
        Document scraper;
        try {
            final DigestURL uri = location.getLocationURL();
            Thread.currentThread().setName("allReleaseFrom - host " + uri.getHost()); // makes it more easy to see which release blocks process in thread dump
            scraper = Switchboard.getSwitchboard().loader.loadDocument(uri, CacheStrategy.NOCACHE, null, ClientIdentification.yacyInternetCrawlerAgent);
        } catch (final IOException e) {
            return null;
        }

        // analyze links in scraper resource, and find link to latest release in it
        final Collection<AnchorURL> anchors = scraper.getAnchors(); // a url (String) / name (String) relation
        final TreeSet<yacyRelease> mainReleases = new TreeSet<yacyRelease>();
        final TreeSet<yacyRelease> devReleases = new TreeSet<yacyRelease>();
        for (final DigestURL url : anchors) {
            try {
                final yacyRelease release = new yacyRelease(url, location.getPublicKey());
                //System.out.println("r " + release.toAnchor());
                if (release.isMainRelease()) {
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
        Switchboard.getSwitchboard().setConfig("update.time.lookup", System.currentTimeMillis());
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
        final File storagePath = Switchboard.getSwitchboard().releasePath;
        File download = null;

        final String name = getUrl().getFileName();
        byte[] signatureBytes = null;
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);

        // download signature first, if public key is available
        try {
            if (this.publicKey != null) {
            	final byte[] signatureData = client.GETbytes(getUrl().toString() + ".sig", null, null, false);
                if (signatureData == null) {
                    ConcurrentLog.warn("yacyVersion", "download of signature " + getUrl().toString() + " failed. ignoring signature file.");
                }
                else signatureBytes = Base64Order.standardCoder.decode(UTF8.String(signatureData).trim());
            }
            client.setTimout(120000);
            client.GET(getUrl().toString(), false);
            int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
            final ResponseHeader header = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());

            final boolean unzipped = header.gzip() && (header.mime().toLowerCase().equals("application/x-tar")); // if true, then the httpc has unzipped the file
            if (unzipped && name.endsWith(".tar.gz")) {
                download = new File(storagePath, name.substring(0, name.length() - 3));
            } else {
                download = new File(storagePath, name);
            }
            if (this.publicKey != null && signatureBytes != null) {
                // copy to file and check signature
                SignatureOutputStream verifyOutput = null;
                try {
                    verifyOutput = new SignatureOutputStream(new FileOutputStream(download), CryptoLib.signAlgorithm, this.publicKey);
                    client.writeTo(new BufferedOutputStream(verifyOutput));

                    if (!verifyOutput.verify(signatureBytes)) throw new IOException("Bad Signature!");
                } catch (final NoSuchAlgorithmException e) {
                    throw new IOException("No such algorithm");
                } catch (final SignatureException e) {
                    throw new IOException("Signature exception");
                } finally {
                    if (verifyOutput != null)
                    verifyOutput.close();
                }
                // Save signature
                final File signatureFile = new File(download.getAbsoluteFile() + ".sig");
                FileUtils.copy(UTF8.getBytes(Base64Order.standardCoder.encode(signatureBytes)), signatureFile);
                if ((!signatureFile.exists()) || (signatureFile.length() == 0)) throw new IOException("create signature file failed");
            } else {
                // just copy into file
                client.writeTo(new BufferedOutputStream(new FileOutputStream(download)));
            }
            if ((!download.exists()) || (download.length() == 0)) throw new IOException("wget of url " + getUrl() + " failed");
            // check again if this is actually a tar.gz or tar file since the httpc may have decompressed it
            if (download.getName().endsWith("tar.gz") && tarParser.isTar(download)) {
                String ts = download.getAbsoluteFile().toString();
                File tar = new File(ts.substring(0, ts.length() - 3));
                download.renameTo(tar);
                download = tar;
            }
        } catch (final IOException e) {
            // Saving file failed, abort download
            ConcurrentLog.severe("yacyVersion", "download of " + getName() + " failed: " + e.getMessage());
            if (download != null && download.exists()) {
                FileUtils.deletedelete(download);
                if (download.exists()) ConcurrentLog.warn("yacyVersion", "could not delete file "+ download);
            }
            download = null;
        } finally {
        	try {
				client.finish();
			} catch (final IOException e) {
				ConcurrentLog.severe("yacyVersion", "finish of " + getName() + " failed: " + e.getMessage());
			}
        }
        this.releaseFile = download;
        Switchboard.getSwitchboard().setConfig("update.time.download", System.currentTimeMillis());
        return this.releaseFile;
    }

    public boolean checkSignature() {
        if(this.releaseFile != null) {
            try {
                final CharBuffer signBuffer = new CharBuffer(getSignatureFile());
                final byte[] signByteBuffer = Base64Order.standardCoder.decode(signBuffer.toString().trim());
                signBuffer.close();
                final CryptoLib cl = new CryptoLib();
                for(final yacyUpdateLocation updateLocation : latestReleaseLocations) {
                    try {
                        if(cl.verifySignature(updateLocation.getPublicKey(),
                            new FileInputStream(this.releaseFile), signByteBuffer)) {
                            return true;
                        }
                    } catch (final InvalidKeyException e) {
                    } catch (final SignatureException e) {
                    }
                }
            } catch (final IOException e1) {
            } catch (final NoSuchAlgorithmException e) {
            }

        }
        return false;
    }

    /**
     * restart yacy by stopping yacy and previously running a batch
     * script, which waits until yacy is terminated and starts it again
     */
    public static void restart() {
        final Switchboard sb = Switchboard.getSwitchboard();

        if (OS.isWindows) {
            final File startType = new File(sb.getDataPath(), "DATA/yacy.noconsole".replace("/", File.separator));
            String starterFile = "startYACY_debug.bat";
            if (startType.exists()) starterFile = "startYACY.bat"; // startType noconsole
            if (startParameter.startsWith("-gui")) starterFile += " " + startParameter;
            try{
                ConcurrentLog.info("RESTART", "INITIATED");
                final String script =
                    "@echo off" + serverCore.LF_STRING +
                    "title YaCy restarter" + serverCore.LF_STRING +
                    "set loading=YACY RESTARTER" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "cd \"" + sb.getDataPath().toString() + "/DATA/RELEASE/".replace("/", File.separator) + "\"" + serverCore.LF_STRING +
                    ":WAIT" + serverCore.LF_STRING +
                    "set loading=%loading%." + serverCore.LF_STRING +
                    "cls" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "ping -n 2 127.0.0.1 >nul" + serverCore.LF_STRING +
                    "IF exist ..\\yacy.running goto WAIT" + serverCore.LF_STRING +
                    "cd \"" + sb.getAppPath().toString() + "\"" + serverCore.LF_STRING +
                    "start /MIN CMD /C " + starterFile + serverCore.LF_STRING;
                final File scriptFile = new File(sb.getDataPath(), "DATA/RELEASE/restart.bat".replace("/", File.separator));
                OS.deployScript(scriptFile, script);
                ConcurrentLog.info("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                OS.execAsynchronous(scriptFile);
                ConcurrentLog.info("RESTART", "script is running");
                sb.terminate(10, "windows restart");
            } catch (final IOException e) {
                ConcurrentLog.severe("RESTART", "restart failed", e);
            }
        }

        if (yacyBuildProperties.isPkgManager()) {
            // start a re-start daemon
            try {
                ConcurrentLog.info("RESTART", "INITIATED");
                final String script =
                    "#!/bin/sh" + serverCore.LF_STRING +
                    yacyBuildProperties.getRestartCmd() + " >/var/lib/yacy/RELEASE/log" + serverCore.LF_STRING;
                final File scriptFile = new File(sb.getDataPath(), "DATA/RELEASE/restart.sh");
                OS.deployScript(scriptFile, script);
                ConcurrentLog.info("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                OS.execAsynchronous(scriptFile);
                ConcurrentLog.info("RESTART", "script is running");
            } catch (final IOException e) {
                ConcurrentLog.severe("RESTART", "restart failed", e);
            }
        } else if (OS.canExecUnix) {
            // start a re-start daemon
            try {
                ConcurrentLog.info("RESTART", "INITIATED");
                final String script =
                    "#!/bin/sh" + serverCore.LF_STRING +
                    "cd " + sb.getDataPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
                    "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                    "sleep 1" + serverCore.LF_STRING +
                    "done" + serverCore.LF_STRING +
                    //"cd ../../" + serverCore.LF_STRING +
                    "cd " + sb.getAppPath() + serverCore.LF_STRING +
                    "nohup ./startYACY.sh " + (startParameter.startsWith("-gui") ? startParameter : "") + " > /dev/null" + serverCore.LF_STRING;
                final File scriptFile = new File(sb.getDataPath(), "DATA/RELEASE/restart.sh");
                OS.deployScript(scriptFile, script);
                ConcurrentLog.info("RESTART", "wrote restart-script to " + scriptFile.getAbsolutePath());
                OS.execAsynchronous(scriptFile);
                ConcurrentLog.info("RESTART", "script is running");
                sb.terminate(10, "unix restart");
            } catch (final IOException e) {
                ConcurrentLog.severe("RESTART", "restart failed", e);
            }
        }
    }

    /**
     * stop yacy and run a batch script, applies a new release and restarts yacy
     * @param releaseFile
     */
    public static void deployRelease(final File releaseFile) {
        if (yacyBuildProperties.isPkgManager()) {
            return;
        }
        try {
            final Switchboard sb = Switchboard.getSwitchboard();
            ConcurrentLog.info("UPDATE", "INITIATED");
            try{
                tarTools.unTar(tarTools.getInputStream(releaseFile), sb.getDataPath() + "/DATA/RELEASE/".replace("/", File.separator));
            } catch (final Exception e){
                ConcurrentLog.severe("UNTAR", "failed", e);
            }
            String script = null;
            String scriptFileName = null;
            if (OS.isMacArchitecture) {
                // overwrite Info.plist for Mac Applications (this holds the class paths and can be seen as the start script)
                final File InfoPlistSource = new File(sb.getDataPath(), "DATA/RELEASE/yacy/addon/YaCy.app/Contents/Info.plist");
                final File InfoPlistDestination = new File(sb.getAppPath(), "addon/YaCy.app/Contents/Info.plist");
                if (InfoPlistSource.exists() && InfoPlistDestination.exists()) {
                    Files.copy(InfoPlistSource, InfoPlistDestination);
                    ConcurrentLog.info("UPDATE", "replaced Info.plist");
                }
            }
            if (OS.isWindows) {
                final File startType = new File(sb.getDataPath(), "DATA/yacy.noconsole".replace("/", File.separator));
                String starterFile = "startYACY_debug.bat";
                if (startType.exists()) starterFile = "startYACY.bat"; // startType noconsole
                if (startParameter.startsWith("-gui")) starterFile += " " + startParameter;
                script =
                    "@echo off" + serverCore.LF_STRING +
                    "title YaCy updater" + serverCore.LF_STRING +
                    "set loading=YACY UPDATER" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "cd \"" + sb.getDataPath().toString() + "/DATA/RELEASE/".replace("/", File.separator) + "\"" + serverCore.LF_STRING +

                    ":WAIT" + serverCore.LF_STRING +
                    "set loading=%loading%." + serverCore.LF_STRING +
                    "cls" + serverCore.LF_STRING +
                    "echo %loading%" + serverCore.LF_STRING +
                    "ping -n 2 127.0.0.1 >nul" + serverCore.LF_STRING +
                    "IF exist ..\\yacy.running goto WAIT" + serverCore.LF_STRING +
                    "IF not exist yacy goto NODATA" + serverCore.LF_STRING +

                    "cd yacy" + serverCore.LF_STRING +
                    "del /Q \"" + sb.getAppPath().toString() + "\\lib\\*\"  >nul" + serverCore.LF_STRING +
                    "xcopy *.* \"" + sb.getAppPath().toString() + "\" /E /Y >nul" + serverCore.LF_STRING +
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
                    "cd \"" + sb.getAppPath().toString() + "\"" + serverCore.LF_STRING +
                    "start /MIN CMD /C " + starterFile + serverCore.LF_STRING;
                scriptFileName = "update.bat";
            } else { // unix/linux
                script =
                    "#!/bin/sh" + serverCore.LF_STRING +
                    "cd " + sb.getDataPath() + "/DATA/RELEASE/" + serverCore.LF_STRING +
                    "while [ -f ../yacy.running ]; do" + serverCore.LF_STRING +
                    "sleep 1" + serverCore.LF_STRING +
                    "done" + serverCore.LF_STRING +
                    "rm " + sb.getAppPath().toString() + "/lib/*" + serverCore.LF_STRING +
                    "cp -Rf yacy/* " + sb.getAppPath().toString() + serverCore.LF_STRING +
                    "rm -Rf yacy" + serverCore.LF_STRING +
                    "cd " + sb.getAppPath().toString() + serverCore.LF_STRING +
                    "chmod 755 *.sh" + serverCore.LF_STRING + // tarTools does not keep access/execute right
                    "chmod 755 bin/*.sh" + serverCore.LF_STRING +
                    "nohup ./startYACY.sh " + (startParameter.startsWith("-gui") ? startParameter : "") + " > /dev/null" + serverCore.LF_STRING;
                scriptFileName = "update.sh";
            }
            final File scriptFile = new File(sb.getDataPath(), "DATA/RELEASE/".replace("/", File.separator) + scriptFileName);
            OS.deployScript(scriptFile, script);
            ConcurrentLog.info("UPDATE", "wrote update-script to " + scriptFile.getAbsolutePath());
            OS.execAsynchronous(scriptFile);
            ConcurrentLog.info("UPDATE", "script is running");
            sb.setConfig("update.time.deploy", System.currentTimeMillis());
            sb.terminate(10, "auto-deploy for " + releaseFile.getName());
        } catch (final IOException e) {
            ConcurrentLog.severe("UPDATE", "update failed", e);
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
        for (final String element : downloaded) {
            try {
                release = new yacyVersion(element, null);
                downloadedreleases.add(release);
            } catch (final RuntimeException e) {
                // not a valid release
            }
        }

        // if we have some files
        if (!downloadedreleases.isEmpty()) {
            ConcurrentLog.fine("STARTUP", "deleting downloaded releases older than "+ deleteAfterDays +" days");

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
                    FileUtils.deletedelete(new File(downloadedFile.getAbsolutePath() + ".sig"));
                    if (downloadedFile.exists()) {
                        ConcurrentLog.warn("STARTUP", "cannot delete old release " + downloadedFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    public File getReleaseFile() {
        return this.releaseFile;
    }

    public File getSignatureFile() {
        return new File(this.releaseFile.getAbsoluteFile() + ".sig");
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

}
