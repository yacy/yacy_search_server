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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.yacy.cora.protocol.Domains;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;


public class ShardSolrConnector implements SolrConnector {

    private final List<SolrConnector> connectors;
    private final ShardSelection sharding;
    private final String[] urls;

    public ShardSolrConnector(final String urlList, final ShardSelection.Method method, final long timeout, boolean multipleConnections) throws IOException {
        urlList.replace(' ', ',');
        this.urls = urlList.split(",");
        this.connectors = new ArrayList<SolrConnector>();
        SolrConnector s;
        for (final String u: this.urls) {
            s = multipleConnections ? new MultipleSolrConnector(u.trim(), 2) : new SingleSolrConnector(u.trim());
            this.connectors.add(new RetrySolrConnector(s, timeout));
        }
        this.sharding = new ShardSelection(method, this.urls.length);
    }

    @Override
    public synchronized void close() {
        for (final SolrConnector connector: this.connectors) connector.close();
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        for (final SolrConnector connector: this.connectors) connector.clear();
    }

    /**
     * delete an entry from solr
     * @param id the url hash of the entry
     * @throws IOException
     */
    @Override
    public void delete(final String id) throws IOException {
        for (final SolrConnector connector: this.connectors) connector.delete(id);
    }

    /**
     * delete a set of entries from solr; entries are identified by their url hash
     * @param ids a list of url hashes
     * @throws IOException
     */
    @Override
    public void delete(final List<String> ids) throws IOException {
        for (final SolrConnector connector: this.connectors) connector.delete(ids);
    }

    /**
     * check if a given id exists in solr
     * @param id
     * @return true if any entry in solr exists
     * @throws IOException
     */
    @Override
    public boolean exists(final String id) throws IOException {
        for (final SolrConnector connector: this.connectors) {
            if (connector.exists(id)) return true;
        }
        return false;
    }

    /**
     * add a Solr document
     * @param solrdoc
     * @throws IOException
     */
    @Override
    public void add(final SolrDoc solrdoc) throws IOException {
        this.connectors.get(this.sharding.select(solrdoc)).add(solrdoc);
    }

    @Override
    public void add(final Collection<SolrDoc> solrdocs) throws IOException, SolrException {
        for (SolrDoc d: solrdocs) this.connectors.get(this.sharding.select(d)).add(d);
    }

    /**
     * add a collection of Solr documents
     * @param docs
     * @throws IOException
     */
    protected void addSolr(final Collection<SolrDoc> docs) throws IOException {
        for (final SolrDoc doc: docs) add(doc);
    }

    /**
     * get a query result from solr
     * to get all results set the query String to "*:*"
     * @param querystring
     * @throws IOException
     */
    @Override
    public SolrDocumentList get(final String querystring, final int offset, final int count) throws IOException {
        final SolrDocumentList list = new SolrDocumentList();
        for (final SolrConnector connector: this.connectors) {
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
        for (final SolrConnector connector: this.connectors) {
            list[i++] = connector.get(querystring, offset, count);
        }
        return list;
    }

    public long[] getSizeList() {
        final long[] size = new long[this.connectors.size()];
        int i = 0;
        for (final SolrConnector connector: this.connectors) {
            size[i++] = connector.getSize();
        }
        return size;
    }

    @Override
    public long getSize() {
        final long[] size = getSizeList();
        long s = 0;
        for (final long l: size) s += l;
        return s;
    }

    public String[] getAdminInterfaceList() {
        final String[] urlAdmin = new String[this.connectors.size()];
        int i = 0;
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? "127.0.0.1" : localhostExternAddress.getHostAddress();
        for (String u: this.urls) {
            int p = u.indexOf("localhost",0); if (p < 0) p = u.indexOf("127.0.0.1",0);
            if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
            urlAdmin[i++] = u + (u.endsWith("/") ? "admin/" : "/admin/");
        }
        return urlAdmin;
    }

}
