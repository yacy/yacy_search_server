/**
 *  ErrorCache
 *  Copyright 2016 by luccioman
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

package net.yacy.search.index;

import java.io.IOException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;

import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

/**
 * A task to concurrently fill the ErrorCache from the index
 * @author luccioman
 *
 */
public class ErrorCacheFiller extends Thread {
	
	/** Switchboard instance */
	private Switchboard sb;
	
	/** The cache to fill */
	private ErrorCache cache;
	
	/**
	 * Constructor : this prepares the concurrent task
	 * @param sb switchboard instance. Must not be null.
	 * @param cache error cache to fill. Must not be null.
	 */
	public ErrorCacheFiller(Switchboard sb, ErrorCache cache) {
		super(ErrorCacheFiller.class.getSimpleName());
		if(sb == null || cache == null) {
			throw new IllegalArgumentException("Unexpected null parameters");
		}
		this.sb = sb;
		this.cache = cache;
	}
	
	/**
	 * Fills the error cache with recently failed document hashes found in the index
	 */
    @Override
    public void run() {
        final SolrQuery params = new SolrQuery();
        params.setParam("defType", "edismax");
        params.setStart(0);
        params.setRows(1000);
        params.setFacet(false);
        params.setSort(new SortClause(CollectionSchema.load_date_dt.getSolrFieldName(), SolrQuery.ORDER.desc)); // load_date_dt = faildate
        params.setFields(CollectionSchema.id.getSolrFieldName());
        params.setQuery(CollectionSchema.failreason_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);
        params.set(CommonParams.DF, CollectionSchema.id.getSolrFieldName()); // DisMaxParams.QF or CommonParams.DF must be given
        SolrDocumentList docList;
        try {
            docList = this.sb.index.fulltext().getDefaultConnector().getDocumentListByParams(params);
            if (docList != null) for (int i = docList.size() - 1; i >= 0; i--) {
                SolrDocument doc = docList.get(i);
                String hash = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                cache.putHashOnly(hash);
            }
        } catch (IOException e) {
            ConcurrentLog.logException(e);
        }
    }

}
