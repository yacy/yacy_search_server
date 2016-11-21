/**
 * LanguageNavigator.java
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

import net.yacy.cora.sorting.ScoreMap;
import net.yacy.kelondro.util.ISO639;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Navigator for languages, restricting items to known languages and returning
 * long language names as display names.
 */
public class LanguageNavigator extends StringNavigator implements Navigator {

    /**
     * Default constructor, using the default YaCy Solr field language_s.
     *
     * @param title
     * @param field the SolrDocument schema field containing language code
     */
    public LanguageNavigator(String title) {
        super(title, CollectionSchema.language_s);
    }

    public LanguageNavigator(String title, CollectionSchema field) {
        super(title, field);
    }

    /**
     * Increases counter for language (if language known)
     * @param lang
     */
    @Override
    public void inc(final String lang) {
        if (ISO639.exists(lang)) {
            super.inc(lang);
        }
    }

    /**
     * Increases counter for languages in scoremap. Each language from the
     * scoremap is checked it known
     * @param map
     */
    @Override
    public void inc(ScoreMap<String> map) {
        if (map == null) {
            return;
        }
        for (String entry : map) {
            if (ISO639.exists(entry)) {
                int count = map.get(entry);
                if (count > 0) {
                    this.inc(entry, count);
                }
            }
        }
    }

    @Override
    public String getQueryModifier(final String key) {
        return "/language/" + key;
    }

    /**
     * Convert language code to long display name
     * @param lng language code
     * @return display name of language
     */
    @Override
    public String getElementDisplayName(final String lng) {
        String longname = ISO639.country(lng);
        if (longname == null) {
            return lng;
        } else {
            return longname;
        }
    }

    /**
     * Checks the query modifier.language string
     * @param modifier
     * @param name language code
     * @return true if contained in modifier.language
     */
    @Override
    public boolean modifieractive(final QueryModifier modifier, final String name) {
        if (modifier.language != null && modifier.language.contains(name)) {
            return true;
        } else {
            return false;
        }
    }
}
