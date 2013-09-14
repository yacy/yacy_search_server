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

package net.yacy.data;

import java.util.Collections;
import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.SizeLimitedMap;


public class URLLicense {

    // this class defines a license-generation for URLs
    // it is used in case of snippet- and preview-Image-fetching to grant also non-authorized users the usage of a image-fetcher servlet
    private static final int maxQueue = 10000;
    private static final Map<String, String> permissions = Collections.synchronizedMap(new SizeLimitedMap<String, String>(maxQueue));

    public static String aquireLicense(final DigestURL url) {
        if (url == null) return "";
        // generate license key
        String license = ASCII.String(url.hash());
        // store reference to url with license key
        permissions.put(license, url.toNormalform(true));
        // return the license key
        return license;
    }

    public static String aquireLicense(final String license, final String url) {
        // store reference to url with license key
        permissions.put(license, url);
        // return the license key
        return license;
    }

    public static String releaseLicense(final String license) {
        return permissions.remove(license);
    }

}
