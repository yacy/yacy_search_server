/**
 * HostNavigator.java
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
import java.util.Map;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Navigator for (internet) host names, removing www. part of url and counting
 * www.host.org and host.org as same url
 */
public class HostNavigator extends StringNavigator implements Navigator {
	
    public HostNavigator(final String title, final CollectionSchema field, final NavigatorSort sort) {
        super(title, field, sort);
    }

    @Override
    public void incFacet(Map<String, ReversibleScoreMap<String>> facets) {
        if (field != null && facets != null && !facets.isEmpty()) {
            ReversibleScoreMap<String> fcts = facets.get(field.getSolrFieldName());
            if (fcts != null) {
                for (String host : fcts) {
                    int hc = fcts.get(host);
                    if (hc == 0) {
                        continue;
                    }
                    if (host.startsWith("www.")) {
                        host = host.substring(4);
                    }
                    this.inc(host, hc);
                }
            }
        }
    }

    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            Object val = doc.getFieldValue(field.getSolrFieldName());
            if (val != null) {
                if (val instanceof Collection) {
                    Collection<?> ll = (Collection<?>) val;
                    for (Object obj : ll) {
                    	if(obj instanceof String) {
                    		String s = (String)obj;
                    		if (s.startsWith("www.")) {
                    			s = s.substring(4);
                    		}
                    		this.inc(s);
                    	}
                    }
                } else {
                    String host = (String) val;
                    if (host.startsWith("www.")) {
                        host = host.substring(4);
                    }
                    this.inc(host);
                }
            }
        }
    }

    @Override
    public String getQueryModifier(final String key) {
        return "site:" + key;
    }

    /**
     * Checks the query modifier.sitehost string
     *
     * @param modifier
     * @param name host name
     * @return true if contained in modifier.sitehost
     */
    @Override
    public boolean modifieractive(QueryModifier modifier, String name) {
        if (modifier.sitehost != null) {
            return modifier.sitehost.contains(name);
        }
        return false;
    }
}
