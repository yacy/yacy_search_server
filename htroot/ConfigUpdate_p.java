// ConfigUpdate_p.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.serverSystem;
import de.anomic.yacy.yacyVersion;

public class ConfigUpdate_p {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        prop.put("candeploy_configCommit", 0);
        prop.put("candeploy_autoUpdate", 0);
        
        if (post != null) {
            if (post.containsKey("downloadRelease")) {
                // download a release
                String release = post.get("releasedownload", "");
                if (release.length() > 0) {
                    try {
                        yacyVersion.downloadRelease(new yacyVersion(new URL(release)));
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
         
            if (post.containsKey("checkRelease")) {
                yacyVersion.allReleases(true);
            }
         
            if (post.containsKey("autoUpdate")) {
                yacyVersion updateVersion = yacyVersion.rulebasedUpdateInfo(true);
                if (updateVersion == null) {
                    prop.put("candeploy_autoUpdate", 2); // no more recent release found
                } else try {
                    // there is a version that is more recent. Load it and re-start with it
                    sb.getLog().logInfo("AUTO-UPDATE: downloading more recent release " + updateVersion.url);
                    yacyVersion.downloadRelease(updateVersion);
                    prop.put("candeploy_autoUpdate_downloadedRelease", updateVersion.name);
                    File releaseFile = new File(sb.getRootPath(), "DATA/RELEASE/" + updateVersion.name);
                    boolean devenvironment = yacyVersion.combined2prettyVersion(sb.getConfig("version","0.1")).startsWith("dev");
                    if (devenvironment) {
                        sb.getLog().logInfo("AUTO-UPDATE: omiting update because this is a development environment");
                        prop.put("candeploy_autoUpdate", 3);
                    } else if ((!releaseFile.exists()) || (releaseFile.length() == 0)) {
                        sb.getLog().logInfo("AUTO-UPDATE: omiting update because download failed (file cannot be found or is too small)");
                        prop.put("candeploy_autoUpdate", 4);
                    } else {
                        yacyVersion.deployRelease(updateVersion.name);
                        sb.terminate(5000);
                        sb.getLog().logInfo("AUTO-UPDATE: deploy and restart initiated");
                        prop.put("candeploy_autoUpdate", 1);
                    }
                } catch (IOException e) {
                    sb.getLog().logSevere("AUTO-UPDATE: could not download and install release " + updateVersion.url + ": " + e.getMessage());
                }
            }
         
            if (post.containsKey("configSubmit")) {
                prop.put("candeploy_configCommit", 1);
                sb.setConfig("update.process", (post.get("updateMode", "manual").equals("manual")) ? "manual" : "auto");
                sb.setConfig("update.cycle", Math.max(12, post.getLong("cycle", 168)));
                sb.setConfig("update.blacklist", post.get("blacklist", ""));
                sb.setConfig("update.concept", (post.get("releaseType", "any").equals("any")) ? "any" : "main");
            }
        }
        
        // set if this should be visible
        if (serverSystem.canExecUnix) {
            // we can deploy a new system with (i.e.)
            // cd DATA/RELEASE;tar xfz $1;cp -Rf yacy/* ../../;rm -Rf yacy
            prop.put("candeploy", 1);
        } else {
            prop.put("candeploy", 0);
        }
        
        // version information
        String versionstring = yacyVersion.combined2prettyVersion(sb.getConfig("version","0.1"));
        prop.put("versionpp", versionstring);
        boolean devenvironment = versionstring.startsWith("dev");
        double thisVersion = Double.parseDouble(sb.getConfig("version","0.1"));
        // cut off the SVN Rev in the Version
        try {thisVersion = Math.round(thisVersion*1000.0)/1000.0;} catch (NumberFormatException e) {}

            
        // list downloaded releases
        yacyVersion release, dflt;
        String[] downloaded = sb.releasePath.list();
            
        prop.put("candeploy_deployenabled", (downloaded.length == 0) ? 0 : ((devenvironment) ? 1 : 2)); // prevent that a developer-version is over-deployed
          
        TreeSet downloadedreleases = new TreeSet();
        for (int j = 0; j < downloaded.length; j++) {
            try {
                release = (yacyVersion) new yacyVersion(downloaded[j]);
                downloadedreleases.add(release);
            } catch (RuntimeException e) {
                // not a valid release
                new File(sb.releasePath, downloaded[j]).deleteOnExit(); // can be also a restart- or deploy-file
            }
        }
        dflt = (downloadedreleases.size() == 0) ? null : (yacyVersion) downloadedreleases.last();
        Iterator i = downloadedreleases.iterator();
        int relcount = 0;
        while (i.hasNext()) {
            release = (yacyVersion) i.next();
            prop.put("candeploy_downloadedreleases_" + relcount + "_name", (release.proRelease ? "pro" : "standard") + "/" + ((release.mainRelease) ? "main" : "dev") + " " + release.releaseNr + "/" + release.svn);
            prop.put("candeploy_downloadedreleases_" + relcount + "_file", release.name);
            prop.put("candeploy_downloadedreleases_" + relcount + "_selected", (release == dflt) ? 1 : 0);
            relcount++;
        }
        prop.put("candeploy_downloadedreleases", relcount);

        // list remotely available releases
        TreeSet[] releasess = yacyVersion.allReleases(false); // {0=promain, 1=prodev, 2=stdmain, 3=stddev}
        relcount = 0;
        // main
        TreeSet releases = releasess[(yacy.pro) ? 0 : 2];
        releases.removeAll(downloadedreleases);
        i = releases.iterator();
        while (i.hasNext()) {
            release = (yacyVersion) i.next();
            prop.put("candeploy_availreleases_" + relcount + "_name", (release.proRelease ? "pro" : "standard") + "/" + ((release.mainRelease) ? "main" : "dev") + " " + release.releaseNr + "/" + release.svn);
            prop.put("candeploy_availreleases_" + relcount + "_url", release.url.toString());
            prop.put("candeploy_availreleases_" + relcount + "_selected", 0);
            relcount++;
        }
        // dev
        dflt = (releasess[(yacy.pro) ? 1 : 3].size() == 0) ? null : (yacyVersion) releasess[(yacy.pro) ? 1 : 3].last();
        releases = releasess[(yacy.pro) ? 1 : 3];
        releases.removeAll(downloadedreleases);
        i = releases.iterator();
        while (i.hasNext()) {
            release = (yacyVersion) i.next();
            prop.put("candeploy_availreleases_" + relcount + "_name", (release.proRelease ? "pro" : "standard") + "/" + ((release.mainRelease) ? "main" : "dev") + " " + release.releaseNr + "/" + release.svn);
            prop.put("candeploy_availreleases_" + relcount + "_url", release.url.toString());
            prop.put("candeploy_availreleases_" + relcount + "_selected", (release == dflt) ? 1 : 0);
            relcount++;
        }
        prop.put("candeploy_availreleases", relcount);

        // properties for automated system update
        prop.put("candeploy_manualUpdateChecked", (sb.getConfig("update.process", "manual").equals("manual")) ? 1 : 0);
        prop.put("candeploy_autoUpdateChecked", (sb.getConfig("update.process", "manual").equals("auto")) ? 1 : 0);
        prop.put("candeploy_cycle", sb.getConfigLong("update.cycle", 168));
        prop.put("candeploy_blacklist", sb.getConfig("update.blacklist", ""));
        prop.put("candeploy_releaseTypeMainChecked", (sb.getConfig("update.concept", "any").equals("any")) ? 0 : 1);
        prop.put("candeploy_releaseTypeAnyChecked", (sb.getConfig("update.concept", "any").equals("any")) ? 1 : 0);
        prop.put("candeploy_lastlookup", (sb.getConfigLong("update.time.lookup", 0) == 0) ? 0 : 1);
        prop.put("candeploy_lastlookup_time", new Date(sb.getConfigLong("update.time.lookup", 0)).toString());
        prop.put("candeploy_lastdownload", (sb.getConfigLong("update.time.download", 0) == 0) ? 0 : 1);
        prop.put("candeploy_lastdownload_time", new Date(sb.getConfigLong("update.time.download", 0)).toString());
        prop.put("candeploy_lastdeploy", (sb.getConfigLong("update.time.deploy", 0) == 0) ? 0 : 1);
        prop.put("candeploy_lastdeploy_time", new Date(sb.getConfigLong("update.time.deploy", 0)).toString());
        
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
        prop.put("hintVersionAvailable_latestVersion", Double.toString(yacyVersion.latestRelease));
         */
        
        return prop;
    }

}
