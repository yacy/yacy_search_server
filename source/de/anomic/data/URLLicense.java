// URLLicense.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2007 on http://yacy.net
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

package de.anomic.data;

import java.util.HashMap;
import java.util.Random;

import de.anomic.yacy.yacyURL;

public class URLLicense {

    // this class defines a license-generation for URLs
    // it is used in case of snippet- and preview-Image-fetching to grant also non-authorized users the usage of a image-fetcher servlet

    private Random random;
    private HashMap<String, yacyURL> permissions;
    private int keylen;
    
    public URLLicense(int keylen) {
        this.permissions = new HashMap<String, yacyURL>();
        this.random = new Random(System.currentTimeMillis());
        this.keylen = keylen;
    }
    
    public String aquireLicense(yacyURL url) {
        // generate license key
        String license = "";
        while (license.length() < keylen) license += Integer.toHexString(random.nextInt());
        license = license.substring(0, keylen);
        // store reference to url with license key
        synchronized (permissions) {
            permissions.put(license, url);
        }
        // return the license key
        return license;
    }
    
    public yacyURL releaseLicense(String license) {
        yacyURL url = null;
        synchronized (permissions) {
            url = permissions.remove(license);
        }
        /*
        if (url == null) {
            System.out.println("DEBUG-URLLICENSE: no URL license present for code=" + license);
        } else {
            System.out.println("DEBUG-URLLICENSE: granted download of " + url.toString());
        }
        */
        return url;
    }
    
}
