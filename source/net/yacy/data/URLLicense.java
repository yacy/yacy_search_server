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
import java.util.UUID;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.SizeLimitedMap;

/**
 * This class defines a license-generation for URLs.
 * It is used in case of preview-Image-fetching to grant also non-authorized users the usage of a image-fetcher servlet,
 * but to prevent them to use this servlet as a proxy.
 */
public class URLLicense {

	
    private static final int maxQueue = 10000;
    
    /** Map URLs by licence keys */
    private static final Map<String, String> permissions = Collections.synchronizedMap(new SizeLimitedMap<String, String>(maxQueue));
    
    /**
     * Generates and stores a unique licence key for delayed url data fetching.
     * @param url URL for whose data should be fectched later
     * @return licence key generated or null when url is null
     */
    public static String aquireLicense(final DigestURL url) {
        if (url == null) return null;
        /* Generate license key : it must absolutely be a unique key, not related to url parameter (thus url.hash can not be used).
         * If the same key is generated for each call of this method with the same url parameter, 
         * problem may occur concurrent non authorized users try to fetch same url content.
         * Example scenario (emulated in URLLicenseConcurrentTest) : 
         * 1 - userA aquireLicence for url
         * 2 - userB aquireLicence for same url as A 
         * 3 - userA releaseLicense : he can now fetch url content
         * 4 - userB releaseLicense : if the same license was generated, it has been already released and url content can not be fetched! */
        String license = UUID.randomUUID().toString();

        permissions.put(license, url.toNormalform(true));
        
        // return the license key
        return license;
    }

    /**
     * Use it to retrieve source url and to ensures YaCy url containing this licence code can not be reused by non-authorized users. 
     * @param license unique code associated to source url
     * @return source url or null licence is no more valid
     * @throws NullPointerException when license is null
     */
    public static String releaseLicense(final String license) {
        return permissions.remove(license);
    }

}
