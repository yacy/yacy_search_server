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
    public String getElementDisplayName(String e) {
        return e;
    }

    @Override
    public String getQueryModifier() {
        String mod;
        if (field != null) {
            switch (field) {
                case author_sxt:
                    mod = "author:";
                    break;
                case url_protocol_s:
                    mod = "/";
                    break;
                case url_file_ext_s:
                    mod = "filetype:";
                    break;
                case collection_sxt:
                    mod = "collection:";
                    break;
                case host_s:
                    mod = "site:";
                    break;
                case language_s:
                    mod = "/language/";
                    break;
                default:
                    mod = ":";
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

    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val instanceof List) {
                List<String> ll = (List) val;
                for (String s : ll) {
                    if (!s.isEmpty()) {
                        this.inc(s);
                    }
                }
            } else {
                if (val != null) {
                    this.inc((String) val);
                }
            }
        }
    }

    @Override
    public boolean modifieractive(QueryModifier modifier, String name) {
        if (name.indexOf(' ') < 0) {
            return modifier.toString().contains(getQueryModifier() + name);
        } else {
            return modifier.toString().contains(getQueryModifier() + "(" + name + ")");
        }
    }

    @Override
    public String getIndexFieldName() {
        if (field != null)
            return field.getSolrFieldName();
        else
            return "";
    }
}
