/**
 * RestrictedStringNavigator.java
 * (C) 2016 by reger24; https://github.com/reger24
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.search.navigator;

import java.util.HashSet;
import java.util.Set;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.search.schema.CollectionSchema;

/**
 * A Navigator that allows to restrict the items
 * If allowed items are set only these will be counted or displayed
 * If forbidden items are set these are excluded from display
 */
public class RestrictedStringNavigator extends StringNavigator implements Navigator {

    Set<String> allowed; // complete list of keys, if empty all keys are allowed
    Set<String> forbidden; // keys to exclude

    public RestrictedStringNavigator(final String title, final CollectionSchema field, final NavigatorSort sort) {
        super(title, field, sort);
        this.allowed = new HashSet<String>();
        this.forbidden = new HashSet<String>();
    }

    /**
     * Add a allowed navigator key string. If set, only allowed items are displayed
     * which means you have to add all possible key which sall be allowed.
     *
     * @param s key
     * @return true if added, false if already existed
     */
    public boolean addAllowed(String s) {
        return this.allowed.add(s);
    }

    /**
     * Add a blacklisted item which shall be excluded from counting and display.
     *
     * @param s key
     * @return true if added, false if already existed
     */
    public boolean addForbidden(String s) {
        return this.forbidden.add(s);
    }

    /**
     * Increase counter if item allowed and not forbidden
     * @param key
     */
    @Override
    public void inc(final String key) {
        if (!forbidden.contains(key)) {
            if (allowed.isEmpty()) {
                super.inc(key);
            } else if (allowed.contains(key)) {
                super.inc(key);
            }
        }
    }

    /**
     * Increase counter if item allowed and not forbidden
     */
    @Override
    public void inc(ScoreMap<String> map) {
        if (map == null) {
            return;
        }
        for (String entry : map) {
            if (!forbidden.contains(entry)) {
                if (allowed.isEmpty()) {
                    int count = map.get(entry);
                    if (count > 0) {
                        this.inc(entry, count);
                    }
                } else if (allowed.contains(entry)) {
                    int count = map.get(entry);
                    if (count > 0) {
                        this.inc(entry, count);
                    }
                }
            }
        }
    }

}
