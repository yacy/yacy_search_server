// plasmaURLPool.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 16.06.2005
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

// this class combines all url storage methods into one. It is the host for all url storage


package de.anomic.plasma;

import java.io.File;
import java.io.IOException;

import de.anomic.index.indexURL;
import de.anomic.net.URL;

public class plasmaURLPool {
    
    
    public  final plasmaCrawlLURL        loadedURL;
    public  final plasmaCrawlNURL        noticeURL;
    public  final plasmaCrawlEURL        errorURL;
    
    public plasmaURLPool(File plasmaPath,
                         int ramLURL, boolean newLURL,
                         int ramNURL, boolean newNURL,
                         int ramEURL, boolean newEURL,
                         long preloadTime) {
        loadedURL = new plasmaCrawlLURL(plasmaPath, ramLURL, preloadTime, newLURL);
        noticeURL = new plasmaCrawlNURL(plasmaPath, ramNURL, -1, newNURL);
        errorURL = new plasmaCrawlEURL(plasmaPath, ramEURL, -1, newEURL);
    }
    
    public String exists(String hash) {
        // tests if hash occurrs in any database
        // if it exists, the name of the database is returned,
        // if it not exists, null is returned
        if (loadedURL.exists(hash)) return "loaded";
        if (noticeURL.existsInStack(hash)) return "crawler";
        if (errorURL.exists(hash)) return "errors";
        return null;
    }
    
    public URL getURL(String urlhash) throws IOException {
        if (urlhash.equals(indexURL.dummyHash)) return null;
        plasmaCrawlNURL.Entry ne = noticeURL.getEntry(urlhash);
        if (ne != null) return ne.url();
        plasmaCrawlLURL.Entry le = loadedURL.load(urlhash, null);
        if (le != null) return le.url();
        plasmaCrawlEURL.Entry ee = errorURL.getEntry(urlhash);
        if (ee != null) return ee.url();
        return null;
    }
    
    public void close() {
        try {loadedURL.close();} catch (IOException e) {}
        noticeURL.close();
        try {errorURL.close();} catch (IOException e) {}
    }
}
