/**
 *  YaCyQoSFilter
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.http.servlets;

import javax.servlet.ServletRequest;

import org.eclipse.jetty.ee8.servlets.QoSFilter;

import net.yacy.cora.protocol.Domains;

/** Preserves YaCy's localhost request priority on Jetty 12 EE8. */
@SuppressWarnings("deprecation")
public class YaCyQoSFilter extends QoSFilter {

    @Override
    protected int getPriority(final ServletRequest request) {
        if (request.getServerName().equalsIgnoreCase(Domains.LOCALHOST)) {
            return 10;
        }
        if (Domains.isLocalhost(request.getRemoteAddr())) {
            return 9;
        }
        return super.getPriority(request);
    }
}
