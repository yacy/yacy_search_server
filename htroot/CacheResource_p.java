// CacheResource_p.java 
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

import java.net.MalformedURLException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import de.anomic.http.client.Cache;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class CacheResource_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        prop.put("resource", new byte[0]);
        
        if (post == null) return prop;
        
        final String u = post.get("url", "");
        DigestURI url;
        try {
            url = new DigestURI(u, null);
        } catch (MalformedURLException e) {
            Log.logException(e);
            return prop;
        }
        
        byte[] resource = null;
        resource = Cache.getContent(url);
        if (resource == null) return prop;
        //ResponseHeader responseHeader = Cache.getResponseHeader(url);
        //String resMime = responseHeader.mime();
        
        prop.put("resource", resource);
        return prop;
    }
}
