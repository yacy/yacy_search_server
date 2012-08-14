/**
 *  SolrConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 13.09.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

public interface SolrConnector {

    /**
     * get the solr autocommit delay
     * @return the maximum waiting time after a solr command until it is transported to the server
     */
    public int getCommitWithinMs();

    /**
     * set the solr autocommit delay
     * @param c the maximum waiting time after a solr command until it is transported to the server
     */
    public void setCommitWithinMs(int c);

    /**
     * close the server connection
     */
    public void close();

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    public void clear() throws IOException;

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    public void delete(final String id) throws IOException;

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    public void delete(final List<String> ids) throws IOException;

    /**
     * check if a given id exists in solr
     * @param id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    public boolean exists(final String id) throws IOException;

    /**
     * add a solr input document
     * @param solrdoc
     * @throws IOException
     * @throws SolrException
     */
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException;
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException;

    /**
     * get a document from solr by given id
     * @param id
     * @return one result or null if no result exists
     * @throws IOException
     */
    public SolrDocument get(final String id) throws IOException;

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    public SolrDocumentList query(final String querystring, final int offset, final int count) throws IOException, SolrException;

    /**
     * get the size of the index
     * @return number of results if solr is queries with a catch-all pattern
     */
    public long getSize();

}
