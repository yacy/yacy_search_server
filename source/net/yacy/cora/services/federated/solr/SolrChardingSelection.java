/**
 *  SolrChardingSelection
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.solr.common.SolrInputDocument;

public class SolrChardingSelection {
    
    public final static Charset charsetUTF8;
    static {
        charsetUTF8 = Charset.forName("UTF-8");
    }
    
    private final Method method; // the charding method
    private AtomicLong chardID;  // the next id that shall be given away
    private final int dimension; // the number of chards
    public enum Method {
        MODULO_HOST_MD5, ROUND_ROBIN;
    }
    
    public SolrChardingSelection(Method method, int dimension) {
        this.method = method;
        this.dimension = dimension;
        this.chardID = new AtomicLong(0);
    }

    private int selectRoundRobin() {
        return (int) (this.chardID.getAndIncrement() % this.dimension);
    }
    
    public int select(SolrInputDocument solrdoc) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            String sku = (String) solrdoc.getField("sku").getValue();
            return selectURL(sku);
        }
        
        // finally if no method matches use ROUND_ROBIN
        return selectRoundRobin();
    }
    
    public int selectURL(String sku) throws IOException {
        if (this.method == Method.MODULO_HOST_MD5) {
            try {
                URL url = new URL(sku);
                String host = url.getHost();
                if (host == null) throw new IOException("charding - bad url, host empty: " + sku);
                try {
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    digest.update(host.getBytes(charsetUTF8));
                    byte[] md5 = digest.digest();
                    return (0xff & md5[0]) % this.dimension;
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException("charding - no md5 available: " + e.getMessage());
                }
            } catch (MalformedURLException e) {
                throw new IOException("charding - bad url: " + sku);
            }
        }
        
        // finally if no method matches use ROUND_ROBIN
        return (int) (this.chardID.getAndIncrement() % this.dimension);
    }
}
