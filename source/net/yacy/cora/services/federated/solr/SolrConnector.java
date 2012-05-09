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
import java.util.List;

import net.yacy.kelondro.data.meta.DigestURI;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;

public interface SolrConnector {

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
    public void add(final SolrDoc solrdoc) throws IOException, SolrException;

    /**
     * register an entry as error document
     * @param digestURI
     * @param failReason
     * @param httpstatus
     * @throws IOException
     */
    public void err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException;


    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException;

    /**
     * get the size of the index
     * @return number of results if solr is queries with a catch-all pattern
     */
    public long getSize();

}
