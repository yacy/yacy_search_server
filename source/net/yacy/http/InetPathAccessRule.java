/**
 *  InetPathAccessRule
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

/** Container-neutral representation of a server-client address/path rule. */
public final class InetPathAccessRule {

    private static final String DEFAULT_PATH = "/*";

    private final String addressPattern;
    private final String pathPattern;

    private InetPathAccessRule(final String addressPattern, final String pathPattern) {
        this.addressPattern = addressPattern;
        this.pathPattern = pathPattern;
    }

    public static InetPathAccessRule parse(final String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Access rule must not be empty");
        }
        final int separator = pattern.indexOf('|');
        final String address = separator > 0 ? pattern.substring(0, separator) : pattern;
        final String path = separator > 0 && pattern.length() > separator + 1
                ? pattern.substring(separator + 1)
                : DEFAULT_PATH;
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Access rule has no address: " + pattern);
        }
        return new InetPathAccessRule(address, path);
    }

    public String addressPattern() {
        return this.addressPattern;
    }

    public String pathPattern() {
        return this.pathPattern;
    }

    public String asJettyPattern() {
        return this.addressPattern + '|' + this.pathPattern;
    }
}
