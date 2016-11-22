/**
 * StringNavigator.java
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
import java.util.List;
import java.util.Map;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Search navigator for simple string entries based on ScoreMap to count and
 * order the result list by counted occurence
 */
public class StringNavigator  extends ConcurrentScoreMap<String> implements Navigator {

    public String title;
    protected final CollectionSchema field;

    public StringNavigator(String title, CollectionSchema field) {
        super();
        this.title = title;
        this.field = field;
    }

    @Override
    public String getDisplayName() {
        return title;
    }

    @Override
    public String getElementDisplayName(final String e) {
        return e;
    }

    @Override
    public String getQueryModifier(final String key) {
        String mod;
        if (field != null) {
            switch (field) {
                case author_sxt:
                    if (key.indexOf(' ') > 0) { // may contain spaces
                        mod = "author:(" + key + ")";
                    } else {
                        mod = "author:" + key;
                    }
                    break;
                case url_protocol_s:
                    mod = "/" + key;
                    break;
                case url_file_ext_s:
                    mod = "filetype:" + key;
                    break;
                case collection_sxt:
                    mod = "collection:" + key;
                    break;
                case host_s:
                    mod = "site:" + key;
                    break;
                case language_s:
                    mod = "/language/" + key;
                    break;
                default:
                    mod = key;
            }
        } else {
            mod = "";
        }
        return mod;
    }

    @Override
    public void incDocList(List<URIMetadataNode> docs) {
        if (field != null) {
            for (URIMetadataNode doc : docs) {
                incDoc(doc);
            }
        }
    }

    @Override
    public void incFacet(Map<String, ReversibleScoreMap<String>> facets) {
        if (field != null && facets != null && !facets.isEmpty()) {
            ReversibleScoreMap<String> fcts = facets.get(field.getSolrFieldName());
            if (fcts != null) {
                this.inc(fcts);
            }
        }
    }

    /**
     * Increase the score for the key value contained in the defined field in
     * the doc.
     * @param doc Solrdocument with field for the key content
     */
    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val != null) {
                if (val instanceof Collection) {
                    Collection<String> ll = (Collection) val;
                    for (String s : ll) {
                        if (!s.isEmpty()) {
                            this.inc(s);
                        }
                    }
                } else {
                    this.inc((String) val);
                }
            }
        }
    }

    @Override
    public boolean modifieractive(final QueryModifier modifier, final String name) {
        return modifier.toString().contains(getQueryModifier(name));
    }

    @Override
    public String getIndexFieldName() {
        if (field != null)
            return field.getSolrFieldName();
        else
            return "";
    }
}
