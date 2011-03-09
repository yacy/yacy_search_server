// URLLicense.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2007 on http://yacy.net
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

package de.anomic.data;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.yacy.kelondro.data.meta.DigestURI;


public class URLLicense {

    // this class defines a license-generation for URLs
    // it is used in case of snippet- and preview-Image-fetching to grant also non-authorized users the usage of a image-fetcher servlet
    private static final int maxQueue = 500;
    private static final long minCheck = 5000;
    
    private final Random random;
    private final ConcurrentHashMap<String, DigestURI> permissions;
    private final ConcurrentLinkedQueue<String> aging;
    private long lastCheck;
    private final int keylen;
    
    public URLLicense(final int keylen) {
        this.permissions = new ConcurrentHashMap<String, DigestURI>();
        this.aging = new ConcurrentLinkedQueue<String>();
        this.lastCheck = System.currentTimeMillis();
        this.random = new Random(System.currentTimeMillis());
        this.keylen = keylen;
    }
    
    public String aquireLicense(final DigestURI url) {
        // generate license key
        StringBuilder stringBuilder = new StringBuilder(keylen * 2);
        if (url == null) return stringBuilder.toString();
        while (stringBuilder.length() < keylen) stringBuilder.append(Integer.toHexString(random.nextInt()));
        String license = stringBuilder.substring(0, keylen);
        // store reference to url with license key
        permissions.put(license, url);
        aging.add(license);
        if (System.currentTimeMillis() - this.lastCheck > minCheck) {
            // check aging
            this.lastCheck = System.currentTimeMillis();
            String s;
            while (aging.size() > maxQueue) {
                s = aging.poll();
                if (s != null) permissions.remove(s);
            }
        }
        // return the license key
        return license;
    }
    
    public DigestURI releaseLicense(final String license) {
        DigestURI url = null;
        url = permissions.remove(license);
        return url;
    }
    
}
