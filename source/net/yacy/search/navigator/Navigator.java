/**
 * Navigator.java
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
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryModifier;

/**
 * Interface for search navigators used in search servlets to restrict displayed
 * search results.
 */
public interface Navigator extends ScoreMap<String> {

    /**
     * @return name of the navigator for display in ui
     */
    public String getDisplayName() ;

    /**
     * Display form of the element (e.g. for languages the key might be "en",
     * the display form "English"
     * 
     * @param key original key as counted in this navigator
     * @return a translated display text for a key
     */
    public String getElementDisplayName(String key) ;

    /**
     * Returns the query modifier prefix. This is used (needed) within the servlet
     * to create a new modifier for a activated (clicked) navigator item.
     *
     * @return the query modifier prefix (if any, eg. "filetype:" or "author:" )
     */
    public String getQueryModifier() ;

    /**
     * Add counts for this navigator from documents in the provided list.
     * The navigator looks for a field in the document and increases the counts
     * depending on the value in the document field.
     *
     * @param docs list of documents
     */
    public void incDocList(List<URIMetadataNode> docs);

    /**
     * Add count for this navigator from provided Solr facet map.
     * The navigator looks for a field in the facet map and increases the counts
     * by the value in the facet map.
     *
     * @param facets Solr facets
     */
    public void incFacet(Map<String, ReversibleScoreMap<String>> facets);

    /**
     * Add count for this navigator from provided document.
     * The navigator looks for a field in the document and increases the counts
     * depending on the value in the document field.
     *
     * @param docs document
     */
    public void incDoc(URIMetadataNode doc);

    /**
     * Helper routine to determine if current navigator key is part of the query
     * modifier (is active)
     *
     * @param modifier current search query modifier
     * @param name the navigator key to check
     * @return true if navigator key is mentioned as modifier
     */
    public boolean modifieractive(QueryModifier modifier, String name);

    /**
     * @return the name of the index field, the fieldname counted in incDoc, incDoclList, incFacet
     */
    public String getIndexFieldName();
}
