/**
 *  SolrChardingSelection
 *  Copyright 2011 by Michael Peter Christen
 *  First released 25.05.2011 at http://yacy.net
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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

public class ShardSelection {

    private final Method method; // the sharding method
    private final AtomicLong chardID;  // the next id that shall be given away
    private final int dimension; // the number of chards
    public enum Method {
        MODULO_HOST_MD5, ROUND_ROBIN;
    }

    public ShardSelection(final Method method, final int dimension) {
        this.method = method;
        this.dimension = dimension;
        this.chardID = new AtomicLong(0);
    }

    private int selectRoundRobin() {
        return (int) (this.chardID.getAndIncrement() % this.dimension);
    }

    public int select(final SolrInputDocument solrdoc) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            SolrInputField sif = solrdoc.getField(CollectionSchema.host_s.getSolrFieldName());
            if (sif != null) {
                final String host = (String) sif.getValue();
                return selectHost(host);
            }
            sif = solrdoc.getField(CollectionSchema.sku.getSolrFieldName());
            if (sif != null) {
                final String url = (String) sif.getValue();
                try {
                    return selectURL(url);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                    return 0;
                }
            }
            return 0;
        }

        // finally if no method matches use ROUND_ROBIN
        return selectRoundRobin();
    }

    public int selectHost(final String host) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            if (host == null) throw new IOException("sharding - host url, host empty: " + host);
            try {
                final MessageDigest digest = MessageDigest.getInstance("MD5");
                digest.update(ASCII.getBytes(host));
                final byte[] md5 = digest.digest();
                return (0xff & md5[0]) % this.dimension;
            } catch (final NoSuchAlgorithmException e) {
                throw new IOException("sharding - no md5 available: " + e.getMessage());
            }
        }

        // finally if no method matches use ROUND_ROBIN
        return (int) (this.chardID.getAndIncrement() % this.dimension);
    }
    
    public int selectURL(final String sku) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            try {
                final URL url = new URL(sku);
                final String host = url.getHost();
                return selectHost(host);
            } catch (final MalformedURLException e) {
                throw new IOException("sharding - bad url: " + sku);
            }
        }

        // finally if no method matches use ROUND_ROBIN
        return (int) (this.chardID.getAndIncrement() % this.dimension);
    }
}
