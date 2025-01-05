/**
 * FileTypeNavigator.java
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
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Navigator for file extension, counting known extension.
 * The field is expected to only contain the extension, like
 * CollectionSchema.url_file_ext_s
 */
public class FileTypeNavigator extends StringNavigator implements Navigator {

    public FileTypeNavigator(final String title, final CollectionSchema field, final NavigatorSort sort) {
        super(title, field, sort);
    }

    @Override
    public void incFacet(Map<String, ReversibleScoreMap<String>> facets) {
        if (field != null && facets != null && !facets.isEmpty()) {
            ReversibleScoreMap<String> fcts = facets.get(field.getSolrFieldName());

            if (fcts != null) {
                // remove all filetypes that we don't know
                Iterator<String> i = fcts.iterator();
                while (i.hasNext()) {
                    String ext = i.next();
                    if (Classification.isAnyKnownExtension(ext)) {
                        int cnt = fcts.get(ext);
                        this.inc(ext, cnt);
                    }
                }
            }
        }
    }

    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val instanceof Collection) {
                Collection<?> ll = (Collection<?>) val;
                for (Object obj : ll) {
                	if(obj instanceof String) {
                		final String s = (String)obj;
                		// remove all filetypes that we don't know
                		if (Classification.isAnyKnownExtension(s)) {
                			this.inc(s);
                		}
                	}
                }
            } else {
                if (val != null) {
                    String ext = (String) val;
                    if (Classification.isAnyKnownExtension(ext)) {
                        this.inc(ext);
                    }
                }
            }
        }
    }

    @Override
    public String getQueryModifier(final String key) {
        return "filetype:" + key;
    }

    /**
     * Checks the query modifier.filetype string
     *
     * @param modifier
     * @param name file extension
     * @return true if contained in modifier.filetype
     */
    @Override
    public boolean modifieractive(QueryModifier modifier, String name) {
        if (modifier.filetype != null) {
            return modifier.filetype.contains(name);
        }
        return false;
    }
}
