/**
 *  SolrChardingSelection
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.05.2011 at https://yacy.net
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

package net.yacy.cora.federate.solr.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

public class ShardSelection implements Iterable<SolrClient> {

    private final Method method; // the sharding method
    private final AtomicLong shardID;  // the next id that shall be given away
    private final int dimension; // the number of chards
    private final ArrayList<SolrClient> server;
    
    public enum Method {
        MODULO_HOST_MD5("hash-based calculation of storage targets, select all for retrieval"),
        ROUND_ROBIN("round-robin of storage targets, select all for retrieval"),
        SOLRCLOUD("round-robin of storage targets and round-robin for retrieval");
        public final String description;
        private Method(final String description) {
            this.description = description;
        }
    }

    public ShardSelection(final ArrayList<SolrClient> server, final Method method) {
        this.server = server;
        this.method = method;
        this.dimension = server.size();
        this.shardID = new AtomicLong(0);
    }

    public Method getMethod() {
        return this.method;
    }
    
    private int selectRoundRobin() {
        int rr = (int) (this.shardID.getAndIncrement() % this.dimension);
        if (this.shardID.get() < 0) this.shardID.set(0);
        return rr;
    }

    public SolrClient server4write(final SolrInputDocument solrdoc) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            SolrInputField sif = solrdoc.getField(CollectionSchema.host_s.getSolrFieldName());
            if (sif != null) {
                final String host = (String) sif.getValue();
                if (host != null && host.length() > 0) return server4write(host);
            }
            sif = solrdoc.getField(CollectionSchema.sku.getSolrFieldName());
            if (sif != null) {
                final String url = (String) sif.getValue();
                if (url != null && url.length() > 0) try {
                    return server4write(new URI(url).toURL());
                } catch (final IOException | URISyntaxException e) {
                    ConcurrentLog.logException(e);
                    return this.server.get(0);
                }
            }
            return this.server.get(0);
        }

        // finally if no method matches use ROUND_ROBIN
        return this.server.get(selectRoundRobin());
    }

    public SolrClient server4write(final String host) throws IOException {
        if (host == null) throw new IOException("sharding - host url, host empty: " + host);
        if (host.indexOf("://") >= 0) try {
            return server4write(new URI(host).toURL()); // security catch for accidently using the wrong method
        } catch (URISyntaxException e) {
            throw new IOException("sharding - host url, host invalid: " + host);
        }   
        if (this.method == Method.MODULO_HOST_MD5) {
            try {
                final MessageDigest digest = MessageDigest.getInstance("MD5");
                digest.update(ASCII.getBytes(host));
                final byte[] md5 = digest.digest();
                return this.server.get((0xff & md5[0]) % this.dimension);
            } catch (final NoSuchAlgorithmException e) {
                throw new IOException("sharding - no md5 available: " + e.getMessage());
            }
        }

        // finally if no method matches use ROUND_ROBIN
        return this.server.get(selectRoundRobin());
    }
    
    public SolrClient server4write(final URL url) throws IOException {
        return server4write(url.getHost());
    }
    
    public List<SolrClient> server4read() {
        if (this.method == Method.MODULO_HOST_MD5 || this.method == Method.ROUND_ROBIN) return this.server; // return all
        // this is a SolrCloud, we select just one of the SolrCloud server(s)
        ArrayList<SolrClient> a = new ArrayList<>(1);
        a.add(this.server.get(selectRoundRobin()));
        return a;
    }

    /**
     * return all solr server
     */
    @Override
    public Iterator<SolrClient> iterator() {
        return this.server.iterator();
    }
}
