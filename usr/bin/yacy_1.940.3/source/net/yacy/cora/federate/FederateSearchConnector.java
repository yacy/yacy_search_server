/**
 * FederateSearchConnector.java
 * Copyright 2015 by Burkhard Buelte
 * First released 19.01.2015 at https://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
package net.yacy.cora.federate;

import java.util.List;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;


/**
 * Interface for a query connector to search and gather query results from none
 * YaCy systems (for the YaCy heuristic options)
 */
public interface FederateSearchConnector {

    /**
     * Load the configuration for this connector every connector needs at least
     * a query target (where to query) and some definition to convert the remote
     * serch result to the internal result presentation (field mapping)
     *
     * @param instanceName is also the name of the config file DATA/SETTINGS/instanceName.schema
     * @param cfg config parameter
     * @return true if success  false if not
     */      
    abstract boolean init(String instanceName, String cfg);

    /**
     * Queries a remote system and adds the result metadata to the search events
     * result list. If SearchEvent.addResultsToLocalIndex (=default) result urls
     * are added to the crawler.
     * @param theSearch
     */
    abstract void search(SearchEvent theSearch);
    
    /**
     * Queries a remote system and returns the search result with field names
     * according to YaCy schema.
     *
     * @param query
     * @return result (metadata) in YaCy schema format
     */
    abstract List<URIMetadataNode> query(QueryParams query);

}
