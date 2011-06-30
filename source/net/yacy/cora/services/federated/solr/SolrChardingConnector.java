/**
 *  SolrChardingConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.05.2011 at http://yacy.net
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;


public class SolrChardingConnector {

    private final List<SolrSingleConnector> connectors;
    private final SolrScheme scheme;
    private final SolrChardingSelection charding;
    private final String[] urls;

    public SolrChardingConnector(final String urlList, final SolrScheme scheme, final SolrChardingSelection.Method method) throws IOException {
        this.urls = urlList.split(",");
        this.connectors = new ArrayList<SolrSingleConnector>();
        for (final String u: this.urls) {
            this.connectors.add(new SolrSingleConnector(u.trim(), scheme));
        }
        this.charding = new SolrChardingSelection(method, this.urls.length);
        this.scheme = scheme;
    }

    public SolrScheme getScheme() {
        return this.scheme;
    }

    public void close() {
        for (final SolrSingleConnector connector: this.connectors) connector.close();
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    public void clear() throws IOException {
        for (final SolrSingleConnector connector: this.connectors) connector.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    public void delete(final String id) throws IOException {
        for (final SolrSingleConnector connector: this.connectors) connector.delete(id);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    public void delete(final List<String> ids) throws IOException {
        for (final SolrSingleConnector connector: this.connectors) connector.delete(ids);
    }

    /**
     * add a YaCy document. This calls the scheme processor to add the document as solr document
     * @param id the url hash of the entry
     * @param header the http response header
     * @param doc the YaCy document
     * @throws IOException
     */
    public void add(final String id, final ResponseHeader header, final Document doc) throws IOException {
        add(this.scheme.yacy2solr(id, header, doc));
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    private void add(final SolrInputDocument solrdoc) throws IOException {
        this.connectors.get(this.charding.select(solrdoc)).add(solrdoc);
    }

    /**
     * add a collection of Solr documents
     * @param docs
     * @throws IOException
     */
    protected void addSolr(final Collection<SolrInputDocument> docs) throws IOException {
        for (final SolrInputDocument doc: docs) add(doc);
    }

    /**
     * register an entry as error document
     * @param digestURI
     * @param failReason
     * @param httpstatus
     * @throws IOException
     */
    public void err(final DigestURI digestURI, final String failReason, final int httpstatus) throws IOException {
        this.connectors.get(this.charding.selectURL(digestURI.toNormalform(true, false))).err(digestURI, failReason, httpstatus);
    }


    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException {
        final SolrDocumentList list = new SolrDocumentList();
        for (final SolrSingleConnector connector: this.connectors) {
            final SolrDocumentList l = connector.get(querystring, offset, count);
            for (final SolrDocument d: l) {
                list.add(d);
            }
        }
        return list;
    }

    public SolrDocumentList[] getList(final String querystring, final int offset, final int count) throws IOException {
        final SolrDocumentList[] list = new SolrDocumentList[this.connectors.size()];
        int i = 0;
        for (final SolrSingleConnector connector: this.connectors) {
            list[i++] = connector.get(querystring, offset, count);
        }
        return list;
    }

    public long[] getSizeList() throws IOException {
        final long[] size = new long[this.connectors.size()];
        int i = 0;
        for (final SolrSingleConnector connector: this.connectors) {
            final SolrDocumentList list = connector.get("*:*", 0, 1);
            size[i++] = list.getNumFound();
        }
        return size;
    }

    public long getSize() throws IOException {
        final long[] size = getSizeList();
        long s = 0;
        for (final long l: size) s += l;
        return s;
    }

    public String[] getAdminInterfaceList() {
        final String[] urlAdmin = new String[this.connectors.size()];
        int i = 0;
        for (final String u: this.urls) {
            urlAdmin[i++] = u + "/admin/";
        }
        return urlAdmin;
    }
}
