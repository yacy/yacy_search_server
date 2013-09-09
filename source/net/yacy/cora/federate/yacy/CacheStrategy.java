/**
 *  CacheStrategy
 *  Copyright 2011 by Michael Peter Christen
 *  First released 2011 at http://yacy.net
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

package net.yacy.cora.federate.yacy;

public enum CacheStrategy {

    /** Never use the cache, all content from fresh internet source. */
    NOCACHE(0),
    
    /** Use the cache if the cache exists and is fresh using the
     * proxy-fresh rules.
     */
    IFFRESH(1),
    
    /** Use the cache if the cache exists. Do not check freshness. Otherwise
     * use online source.
     */
    IFEXIST(2),
    
    /** Never go online, use all content from cache. If no cache entry exist,
     * consider content nevertheless as available
     */
    CACHEONLY(3),
    
    /** create a snippet that does not necessarily contain the searched word,
     * but has a pretty description of the content instead
     */
    NIFTY(4);
    
    /** the fifth case may be that the CacheStrategy object is assigned NULL.
     * That means that no snippet creation is wanted.
     */

    public int code;

    private CacheStrategy(final int code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return Integer.toString(this.code);
    }

    public static CacheStrategy decode(final int code) {
        for (final CacheStrategy strategy: CacheStrategy.values()) if (strategy.code == code) return strategy;
        return NOCACHE;
    }

    public static CacheStrategy parse(final String name) {
        if (name == null || name.length() == 0) return null;
        if (name.equals("nocache")) return NOCACHE;
        if (name.equals("iffresh")) return IFFRESH;
        if (name.equals("ifexist")) return IFEXIST;
        if (name.equals("cacheonly")) return CACHEONLY;
        if (name.equals("nifty")) return NIFTY;
        if (name.equals("true")) return IFEXIST;
        if (name.equals("false")) return null; // if this cache strategy is assigned as query attribute, null means "do not create a snippet"
        return null;
    }

    public String toName() {
        return name().toLowerCase();
    }

    public boolean isAllowedToFetchOnline() {
        return this.code < 3;
    }

    public boolean mustBeOffline() {
        return this.code == 3;
    }
}
