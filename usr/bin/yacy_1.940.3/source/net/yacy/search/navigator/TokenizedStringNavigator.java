/**
 * TokenizedStringNavigator.java
 * (C) 2017 by reger24; https://github.com/reger24
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
import java.util.StringTokenizer;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Search navigator for string entries based on ScoreMap to count and
 * order the result list by counted occurence. The string values are tokenized
 * and each word is added (lowercased) to the score map.
 */
public class TokenizedStringNavigator  extends StringNavigator implements Navigator {

    public TokenizedStringNavigator(String title, CollectionSchema field, final NavigatorSort sort) {
        super(title, field, sort);
    }

    /**
     * Increase the score for the key value contained in the defined field in
     * the doc. The value string is tokenized using delimiter " ,;"
     * @param doc Solrdocument with field for the key content
     */
    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val != null) {
                if (val instanceof Collection) {
                    Collection<?> ll = (Collection<?>) val;
                    for (Object obj : ll) {
                    	if(obj instanceof String) {
                    		final String s = (String)obj;
                    		if (!s.isEmpty()) {
                    			StringTokenizer token = new StringTokenizer(s.toLowerCase()," ,;"); // StringTokenizer faster than regex pattern
                    			while (token.hasMoreTokens()) {
                    				String word = token.nextToken();
                    				if (word.length() > 1 && !Switchboard.stopwords.contains(word)) {
                    					this.inc(word);
                    				}
                    			}
                    		}
                    	}
                    }
                } else {
                    StringTokenizer token = new StringTokenizer((String) val, " ,;");
                    while (token.hasMoreTokens()) {
                        String word = token.nextToken().toLowerCase();
                        if (word.length() > 1 && !Switchboard.stopwords.contains(word)) {
                            this.inc(word);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if query parameter/modifier with specific key is active.
     * The comparison is case insensitive.
     * 
     * @param modifier querymodifier to check
     * @param key the key/term to check for
     * @return true if the modifier contains the 'modifiername:key'
     */
    @Override
    public boolean modifieractive(final QueryModifier modifier, final String key) {
        return modifier.toString().toLowerCase().contains(getQueryModifier(key));
    }
}
