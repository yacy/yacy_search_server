/**
 *  ProxyAccessPolicy
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

package net.yacy.http;

/** Container-neutral checks for the configured transparent-proxy client list. */
public final class ProxyAccessPolicy {

    private ProxyAccessPolicy() {
    }

    public static boolean isClientAllowed(final String configuredPatterns, final String clientHost) {
        if ("*".equals(configuredPatterns)) {
            return true;
        }
        if (configuredPatterns == null || configuredPatterns.isEmpty() || clientHost == null) {
            return false;
        }
        for (final String pattern : configuredPatterns.split(",")) {
            if (!pattern.isEmpty() && clientHost.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}
