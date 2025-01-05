// getpageinfo
// (C) 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.11.2011 on http://yacy.net
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

package net.yacy.htroot.api;

import java.util.List;
import java.util.Map.Entry;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * @deprecated use now {@link getpageinfo_p}
 */
@Deprecated
public class getpageinfo {

	@SuppressWarnings("unused")
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        /* Redirect to getpageinfo_p */
        StringBuilder redirectedLocation;
        if(header != null && header.getPathInfo() != null && header.getPathInfo().endsWith(".json")) {
        	redirectedLocation = new StringBuilder("getpageinfo_p.json");
        } else {
        	redirectedLocation = new StringBuilder("getpageinfo_p.xml");
        }

        /* Append eventual request parameters to the redirected location */
		if (post != null) {
			final List<Entry<String, String>> parameters = post.entrySet();
			if (parameters != null && !parameters.isEmpty()) {
				redirectedLocation.append("?");
				for (final Entry<String, String> entry : parameters) {
					redirectedLocation.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
				}
				/* Remove trailing "&" */
				redirectedLocation.setLength(redirectedLocation.length() - 1);
			}
		}

        prop.put(serverObjects.ACTION_LOCATION, redirectedLocation.toString());
        return prop;
    }

}
