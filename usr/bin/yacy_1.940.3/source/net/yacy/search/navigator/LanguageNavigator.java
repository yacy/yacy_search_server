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

import java.util.Collection;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
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
     * @param title the navigator display name
	 * @param sort the sort properties to apply when iterating over keys with the
	 * {@link #navigatorKeys()} function
     */
    public LanguageNavigator(final String title, final NavigatorSort sort) {
        super(title, CollectionSchema.language_s, sort);
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

    /**
     * Increase the score for the key value contained in the defined field in
     * the doc, if no language info in doc try to use associated word reference.
     * @param doc URIMetadataNode with field for the key content
     */
    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val != null) {
                if (val instanceof Collection) {
                    Collection<?> ll = (Collection<?>) val;
                    for (Object obj : ll) {
                        if (obj instanceof String) {
                            final String s = (String) obj;
                            if (!s.isEmpty()) {
                                this.inc(s);
                            }
                        }
                    }
                } else {
                    this.inc((String) val);
                }
            } else {
                if (doc.word() != null && doc.word().getLanguageString() != null) {
                    /* Increase the language navigator here only if the word reference
                     * did not include information about language. Otherwise it should be done earlier in addRWIs() */
                    final String lang = doc.word().getLanguageString();
                    this.inc(lang);
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
        }
		return longname;
    }

    /**
     * Checks the query modifier.language string
     * @param modifier
     * @param name language code
     * @return true if contained in modifier.language
     */
    @Override
    public boolean modifieractive(final QueryModifier modifier, final String name) {
        return modifier.language != null && modifier.language.contains(name);
    }
}
