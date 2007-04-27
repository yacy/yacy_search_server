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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.net.URL;

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
    public static yacyVersion latestDevRelease = null;
    public static yacyVersion latestMainRelease = null;
    
    
    // class variables
    public float releaseNr;
    public String dateStamp;
    public int svn;
    public boolean mainRelease;
    public URL url;
    
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
        if ((release == null) || (!release.endsWith(".tar.gz"))) {
            throw new RuntimeException("release file name '" + release + "' is not valid, no tar.gz");
        }
        // cut off tail
        release = release.substring(0, release.length() - 7);
        if (release.startsWith("yacy_dev_v")) {
            mainRelease = false;
            release = release.substring(10);
        } else if (release.startsWith("yacy_v")) {
            mainRelease = true;
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
    
    /*
    public yacyVersion(URL url, float releaseNr, String dateStamp, int svn, boolean mainRelease) {
        this.url = url;
        this.releaseNr = releaseNr;
        this.dateStamp = dateStamp;
        this.svn = svn;
        this.mainRelease = mainRelease;
    }
    */
    
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
        return "<a href=" + this.url.toNormalform() + ">YaCy " + ((this.mainRelease) ? "main release" : "developer release") + " v" + this.releaseNr + ", SVN " + this.svn + "</a>";
    }
    
    public static void aquireLatestReleaseInfo() {
        if ((latestDevRelease == null) && (latestMainRelease == null)) {
            if (latestDevRelease == null) latestDevRelease = aquireLatestDevRelease();
            if (latestMainRelease == null) latestMainRelease = aquireLatestMainRelease();
        }
    }
    
    public static yacyVersion aquireLatestDevRelease() {
        // get the latest release info from a internet resource
        try {
            return latestReleaseFrom(new URL("http://latest.yacy-forum.net"));
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    public static yacyVersion aquireLatestMainRelease() {
        // get the latest release info from a internet resource
        try {
            return latestReleaseFrom(new URL("http://yacy.net/yacy/Download.html"));
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    public static yacyVersion latestReleaseFrom(URL url) {
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
        TreeSet releases = new TreeSet(); // will contain a release (Float) / url (String) relation
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
                releases.add(release);
            } catch (RuntimeException e) {
                // the release string was not well-formed.
                // that might have been another link
                // just dont care
                continue;
            }
        }
        if (releases.size() == 0) return null;
        //i = releases.iterator(); while (i.hasNext()) {System.out.println("v " + ((yacyVersion) i.next()).toAnchor());}
        return (yacyVersion) releases.last();
    }
}
