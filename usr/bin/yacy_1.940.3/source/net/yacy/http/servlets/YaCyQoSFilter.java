/**
 *  YaCyQoSFilter
 *  Copyright 2015 by Burkhard Buelte
 *  First released 26.04.2015 at https://yacy.net
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
import net.yacy.cora.protocol.Domains;
import org.eclipse.jetty.servlets.QoSFilter;

/**
 * Quality of Service Filter based on Jetty QosFilter
 * to prioritize requests from localhost
 * The intention is to improve the responsivness of web/user interface for the local admin
 * To activate this filter uncomment the predefined filter setting in web.xml
 */
public class YaCyQoSFilter extends QoSFilter {

    /**
     * set priority for localhost to max
     * @param request
     * @return priority
     */
    @Override
    protected int getPriority(ServletRequest request) {
        if (request.getServerName().equalsIgnoreCase(Domains.LOCALHOST)) {
            return 10; // highest priority for "localhost"
        } else if (Domains.isLocalhost(request.getRemoteAddr())) {
            return 9;
        } else {
            return super.getPriority(request); // standard: authenticated = 2, other = 1 or 0
        }
    }
}
