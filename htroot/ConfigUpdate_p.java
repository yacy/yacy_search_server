// ConfigUpdate_p.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2007 on http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.OS;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyBuildProperties;
import de.anomic.yacy.yacyRelease;
import de.anomic.yacy.yacyVersion;

public class ConfigUpdate_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        // set if this should be visible
        if (yacyBuildProperties.isPkgManager()) {
            prop.put("candeploy", "2");
            return prop;
        } else if (OS.canExecUnix || OS.isWindows) {
            // we can deploy a new system with (i.e.)
            // cd DATA/RELEASE;tar xfz $1;cp -Rf yacy/* ../../;rm -Rf yacy
            prop.put("candeploy", "1");
        } else {
            prop.put("candeploy", "0");
        }
        

        prop.put("candeploy_configCommit", "0");
        prop.put("candeploy_autoUpdate", "0");
        prop.put("candeploy_downloadsAvailable", "0");
        
        if (post != null) {
            // check if update is supposed to be installed and a release is defined
            if (post.containsKey("update") && !post.get("releaseinstall", "").isEmpty()) {
                prop.put("forwardToSteering", "1");
                prop.putHTML("forwardToSteering_release",post.get("releaseinstall", ""));
                prop.put("deploys", "1");
                prop.put("candeploy", "2"); // display nothing else
                return prop;
            }
            
            if (post.containsKey("downloadRelease")) {
                // download a release
                final String release = post.get("releasedownload", "");
                if (!release.isEmpty()) {
                    try {
                	yacyRelease versionToDownload = new yacyRelease(new DigestURI(release));
                	
                	// replace this version with version which contains public key
                	yacyRelease.DevAndMainVersions allReleases = yacyRelease.allReleases(false, false);
                	final Set<yacyRelease> mostReleases = versionToDownload.isMainRelease() ? allReleases.main : allReleases.dev;
                	for (final yacyRelease rel : mostReleases) {
                	    if (rel.equals(versionToDownload)) {
                		versionToDownload = rel;
                		break;
                	    }
                	}
                	versionToDownload.downloadRelease();
                    } catch (final IOException e) {
                	// TODO Auto-generated catch block
                        Log.logException(e);
                    }
                }
            }
         
            if (post.containsKey("checkRelease")) {
                yacyRelease.allReleases(true, false);
            }

            if (post.containsKey("deleteRelease")) {
                final String release = post.get("releaseinstall", "");
                if (!release.isEmpty()) {
                    try {
                        FileUtils.deletedelete(new File(sb.releasePath, release));
                        FileUtils.deletedelete(new File(sb.releasePath, release + ".sig"));
                    } catch (final NullPointerException e) {
                        sb.getLog().logSevere("AUTO-UPDATE: could not delete release " + release + ": " + e.getMessage());
                    }
                }
            }
         
            if (post.containsKey("autoUpdate")) {
                final yacyRelease updateVersion = yacyRelease.rulebasedUpdateInfo(true);
                if (updateVersion == null) {
                    prop.put("candeploy_autoUpdate", "2"); // no more recent release found
                } else {
                    // there is a version that is more recent. Load it and re-start with it
                    sb.getLog().logInfo("AUTO-UPDATE: downloading more recent release " + updateVersion.getUrl());
                    final File downloaded = updateVersion.downloadRelease();
                    prop.putHTML("candeploy_autoUpdate_downloadedRelease", updateVersion.getName());
                    final boolean devenvironment = new File(sb.getAppPath(), ".svn").exists();
                    if (devenvironment) {
                        sb.getLog().logInfo("AUTO-UPDATE: omitting update because this is a development environment");
                        prop.put("candeploy_autoUpdate", "3");
                    } else if ((downloaded == null) || (!downloaded.exists()) || (downloaded.length() == 0)) {
                        sb.getLog().logInfo("AUTO-UPDATE: omitting update because download failed (file cannot be found, is too small or signature was bad)");
                        prop.put("candeploy_autoUpdate", "4");
                    } else {
                        yacyRelease.deployRelease(downloaded);
                        sb.terminate(10, "manual release update to " + downloaded.getName());
                        sb.getLog().logInfo("AUTO-UPDATE: deploy and restart initiated");
                        prop.put("candeploy_autoUpdate", "1");
                    }
                }
            }
         
            if (post.containsKey("configSubmit")) {
                prop.put("candeploy_configCommit", "1");
                sb.setConfig("update.process", ("manual".equals(post.get("updateMode", "manual"))) ? "manual" : "auto");
                sb.setConfig("update.cycle", Math.max(12, post.getLong("cycle", 168)));
                sb.setConfig("update.blacklist", post.get("blacklist", ""));
                sb.setConfig("update.concept", ("any".equals(post.get("releaseType", "any"))) ? "any" : "main");
                sb.setConfig("update.onlySignedFiles", (post.getBoolean("onlySignedFiles", false)) ? "1" : "0");
            }
        }
        
        // version information
        final String versionstring = yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision();
        prop.putHTML("candeploy_versionpp", versionstring);
        final boolean devenvironment = new File(sb.getAppPath(), ".svn").exists();
        float thisVersion = Float.parseFloat(yacyBuildProperties.getVersion());
        // cut off the SVN Rev in the Version
        try {
            thisVersion = (float) (Math.round(thisVersion*1000.0)/1000.0);
        } catch (final NumberFormatException e) {}

            
        // list downloaded releases
        final File[] downloadedFiles = sb.releasePath.listFiles();
        // list can be null if RELEASE directory has been deleted manually
        final int downloadedFilesNum = (downloadedFiles == null) ? 0 : downloadedFiles.length;
            
        prop.put("candeploy_deployenabled", (downloadedFilesNum == 0) ? "0" : ((devenvironment) ? "1" : "2")); // prevent that a developer-version is over-deployed
          
        final NavigableSet<yacyRelease> downloadedReleases = new TreeSet<yacyRelease>();
        for (final File downloaded : downloadedFiles) {
            try {
                yacyRelease release = new yacyRelease(downloaded);
                downloadedReleases.add(release);
            } catch (final RuntimeException e) {
                // not a valid release
            	// can be also a restart- or deploy-file
                final File invalid = downloaded;
                if (!(invalid.getName().endsWith(".bat") || invalid.getName().endsWith(".sh") || invalid.getName().endsWith(".sig"))) { // Windows & Linux don't like deleted scripts while execution!
                    invalid.deleteOnExit();
                }
            }
        }
        // latest downloaded release
        yacyVersion dflt = (downloadedReleases.isEmpty()) ? null : downloadedReleases.last();
        // check if there are any downloaded releases and if there are enable the update buttons
        prop.put("candeploy_downloadsAvailable", (downloadedReleases.isEmpty()) ? "0" : "1");
        prop.put("candeploy_deployenabled_buttonsActive", (downloadedReleases.isEmpty() || devenvironment) ? "0" : "1");
		
        int relcount = 0;
        for(final yacyRelease release : downloadedReleases) {
            prop.put("candeploy_downloadedreleases_" + relcount + "_name", ((release.isMainRelease()) ? "main" : "dev") + " " + release.getReleaseNr() + "/" + release.getSvn());
            prop.put("candeploy_downloadedreleases_" + relcount + "_signature", (release.getSignatureFile().exists() ? "1" : "0"));
            prop.putHTML("candeploy_downloadedreleases_" + relcount + "_file", release.getName());
            prop.put("candeploy_downloadedreleases_" + relcount + "_selected", (release == dflt) ? "1" : "0");
            relcount++;
        }
        prop.put("candeploy_downloadedreleases", relcount);

        // list remotely available releases
        final yacyRelease.DevAndMainVersions releasess = yacyRelease.allReleases(false, false);
        relcount = 0;
        
        // main
        final Set<yacyRelease> remoteMainReleases = releasess.main;
        remoteMainReleases.removeAll(downloadedReleases);
        for (final yacyRelease release : remoteMainReleases) {
            prop.put("candeploy_availreleases_" + relcount + "_name", ((release.isMainRelease()) ? "main" : "dev") + " " + release.getReleaseNr() + "/" + release.getSvn());
            prop.put("candeploy_availreleases_" + relcount + "_url", release.getUrl().toString());
            prop.put("candeploy_availreleases_" + relcount + "_signatures", (release.getPublicKey()!=null?"1":"0"));
            prop.put("candeploy_availreleases_" + relcount + "_selected", "0");
            relcount++;
        }
        
        // dev
        dflt = (releasess.dev.isEmpty()) ? null : releasess.dev.last();
        final Set<yacyRelease> remoteDevReleases = releasess.dev;
        remoteDevReleases.removeAll(downloadedReleases);
        for (final yacyRelease release : remoteDevReleases) {
            prop.put("candeploy_availreleases_" + relcount + "_name", ((release.isMainRelease()) ? "main" : "dev") + " " + release.getReleaseNr() + "/" + release.getSvn());
            prop.put("candeploy_availreleases_" + relcount + "_url", release.getUrl().toString());
            prop.put("candeploy_availreleases_" + relcount + "_signatures", (release.getPublicKey()!=null?"1":"0"));
            prop.put("candeploy_availreleases_" + relcount + "_selected", (release == dflt) ? "1" : "0");
            relcount++;
        }
        prop.put("candeploy_availreleases", relcount);

        // properties for automated system update
        prop.put("candeploy_manualUpdateChecked", ("manual".equals(sb.getConfig("update.process", "manual"))) ? "1" : "0");
        prop.put("candeploy_autoUpdateChecked", ("auto".equals(sb.getConfig("update.process", "manual"))) ? "1" : "0");
        prop.put("candeploy_cycle", sb.getConfigLong("update.cycle", 168));
        prop.putHTML("candeploy_blacklist", sb.getConfig("update.blacklist", ""));
        prop.put("candeploy_releaseTypeMainChecked", ("any".equals(sb.getConfig("update.concept", "any"))) ? "0" : "1");
        prop.put("candeploy_releaseTypeAnyChecked", ("any".equals(sb.getConfig("update.concept", "any"))) ? "1" : "0");
        prop.put("candeploy_lastlookup", (sb.getConfigLong("update.time.lookup", 0) == 0) ? "0" : "1");
        prop.put("candeploy_lastlookup_time", new Date(sb.getConfigLong("update.time.lookup", 0)).toString());
        prop.put("candeploy_lastdownload", (sb.getConfigLong("update.time.download", 0) == 0) ? "0" : "1");
        prop.put("candeploy_lastdownload_time", new Date(sb.getConfigLong("update.time.download", 0)).toString());
        prop.put("candeploy_lastdeploy", (sb.getConfigLong("update.time.deploy", 0) == 0) ? "0" : "1");
        prop.put("candeploy_lastdeploy_time", new Date(sb.getConfigLong("update.time.deploy", 0)).toString());
        prop.put("candeploy_onlySignedFiles", ("1".equals(sb.getConfig("update.onlySignedFiles", "1"))) ? "1" : "0");
        
        /*
        if ((adminaccess) && (yacyVersion.latestRelease >= (thisVersion+0.01))) { // only new Versions(not new SVN)
            if ((yacyVersion.latestMainRelease != null) ||
                (yacyVersion.latestDevRelease != null)) {
                prop.put("hintVersionDownload", 1);
            } else if ((post != null) && (post.containsKey("aquirerelease"))) {
                yacyVersion.aquireLatestReleaseInfo();
                prop.put("hintVersionDownload", 1);
            } else {
                prop.put("hintVersionAvailable", 1);
            }
        }
        prop.put("hintVersionAvailable", 1); // for testing
        
        prop.putASIS("hintVersionDownload_versionResMain", (yacyVersion.latestMainRelease == null) ? "-" : yacyVersion.latestMainRelease.toAnchor());
        prop.putASIS("hintVersionDownload_versionResDev", (yacyVersion.latestDevRelease == null) ? "-" : yacyVersion.latestDevRelease.toAnchor());
        prop.put("hintVersionAvailable_latestVersion", Float.toString(yacyVersion.latestRelease));
         */
        
        return prop;
    }

}
