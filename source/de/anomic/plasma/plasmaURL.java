// plasmaURL.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.08.2004
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

package de.anomic.plasma;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;

public class plasmaURL {

    // day formatter for entry export
    protected static SimpleDateFormat shortDayFormatter = new SimpleDateFormat("yyyyMMdd");
    
    // statics for value lengths
    public static final int urlHashLength               = yacySeedDB.commonHashLength; // 12
    public static final int urlStringLength             = 256;// not too short for links without parameters
    public static final int urlDescrLength              = 80; // The headline of a web page (meta-tag or <h1>)
    public static final int urlNameLength               = 40; // the tag content between <a> and </a>
    public static final int urlErrorLength              = 20; // a reason description for unavailable urls
    public static final int urlDateLength               = 4;  // any date, shortened
    public static final int urlCopyCountLength          = 2;  // counter for numbers of copies of this index
    public static final int urlFlagLength               = 2;  // any stuff
    public static final int urlQualityLength            = 3;  // taken from heuristic
    public static final int urlLanguageLength           = 2;  // taken from TLD suffix as quick-hack
    public static final int urlDoctypeLength            = 1;  // taken from extension
    public static final int urlSizeLength               = 6;  // the source size, from cache
    public static final int urlWordCountLength          = 3;  // the number of words, from condenser
    public static final int urlCrawlProfileHandleLength = 4;  // name of the prefetch profile
    public static final int urlCrawlDepthLength         = 2;  // prefetch depth, first is '0'
    public static final int urlParentBranchesLength     = 3;  // number of anchors of the parent
    public static final int urlForkFactorLength         = 4;  // sum of anchors of all ancestors
    public static final int urlRetryLength              = 2;  // number of load retries
    public static final int urlHostLength               = 8;  // the host as struncated name
    public static final int urlHandleLength             = 4;  // a handle
    
    
    /* nw data fields to become valid after migration
     * age of page at time of load
     */
    
    public static String dummyHash;
    static {
        dummyHash = "";
        for (int i = 0; i < urlHashLength; i++) dummyHash += "-";
    }
    
    // the class object
    public kelondroTree urlHashCache;
    private HashSet existsIndex;

    public plasmaURL() throws IOException {
        urlHashCache = null;
        existsIndex = new HashSet();
    }

    public int size() {
	return urlHashCache.size();
    }

    public void close() throws IOException {
	urlHashCache.close();
    }

    public boolean exists(String urlHash) {
        if (existsIndex.contains(urlHash)) return true;
	try {
	    if (urlHashCache.get(urlHash.getBytes()) != null) {
                existsIndex.add(urlHash);
                return true;
            } else {
                return false;
            }
	} catch (IOException e) {
	    return false;
	}
    }

    public void remove(String urlHash) {
	try {
            existsIndex.remove(urlHash);
	    urlHashCache.remove(urlHash.getBytes());
	} catch (IOException e) {}
    }

    public static String urlHash(URL url) {
	if (url == null) return null;
        String hash = serverCodings.encodeMD5B64(htmlFilterContentScraper.urlNormalform(url), true).substring(0, urlHashLength);
        return hash;
    }
        
    public static String urlHash(String url) {
	if ((url == null) || (url.length() < 10)) return null;
        String hash = serverCodings.encodeMD5B64(htmlFilterContentScraper.urlNormalform(url), true).substring(0, urlHashLength);
        return hash;
    }
    
    public Iterator urlHashes(String urlHash, boolean up) throws IOException {
        return urlHashCache.rows(up, false, urlHash.getBytes());
    }
    
}
