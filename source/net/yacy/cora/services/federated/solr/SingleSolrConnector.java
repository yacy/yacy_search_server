/**
 *  SolrSingleConnector
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.solr.client.solrj.impl.HttpSolrServer;


public class SingleSolrConnector extends AbstractSolrConnector implements SolrConnector {

    private final String solrurl, host, solrpath, solraccount, solrpw;
    private final int port;

    /**
     * create a new solr connector
     * @param url the solr url, like http://192.168.1.60:8983/solr/ or http://admin:pw@192.168.1.60:8983/solr/
     * @param scheme
     * @throws IOException
     */
    public SingleSolrConnector(final String url) throws IOException {
        super();
        this.solrurl = url;

        // connect using authentication
        final MultiProtocolURI u = new MultiProtocolURI(this.solrurl);
        this.host = u.getHost();
        this.port = u.getPort();
        this.solrpath = u.getPath();
        final String userinfo = u.getUserInfo();
        if (userinfo == null || userinfo.length() == 0) {
            this.solraccount = ""; this.solrpw = "";
        } else {
            final int p = userinfo.indexOf(':');
            if (p < 0) {
                this.solraccount = userinfo; this.solrpw = "";
            } else {
                this.solraccount = userinfo.substring(0, p); this.solrpw = userinfo.substring(p + 1);
            }
        }
        HttpSolrServer s;
        if (this.solraccount.length() > 0) {
            final DefaultHttpClient client = new DefaultHttpClient() {
                @Override
                protected HttpContext createHttpContext() {
                    HttpContext context = super.createHttpContext();
                    AuthCache authCache = new BasicAuthCache();
                    BasicScheme basicAuth = new BasicScheme();
                    HttpHost targetHost = new HttpHost(u.getHost(), u.getPort(), u.getProtocol());
                    authCache.put(targetHost, basicAuth);
                    context.setAttribute(ClientContext.AUTH_CACHE, authCache);
                    return context;
                }
            };
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(this.host, AuthScope.ANY_PORT), new UsernamePasswordCredentials(this.solraccount, this.solrpw));
            client.setCredentialsProvider(credsProvider);
            s = new HttpSolrServer("http://" + this.host + ":" + this.port + this.solrpath, client);
        } else {
            s = new HttpSolrServer(this.solrurl);
        }
        s.setAllowCompression(true);
        s.setConnectionTimeout(60000);
        s.setMaxRetries(1); // Solr-Doc: No more than 1 recommended (depreciated)
        s.setSoTimeout(60000);
        super.init(s);
    }

    public String getAdminInterface() {
        final InetAddress localhostExternAddress = Domains.myPublicLocalIP();
        final String localhostExtern = localhostExternAddress == null ? "127.0.0.1" : localhostExternAddress.getHostAddress();
        String u = this.solrurl;
        int p = u.indexOf("localhost",0);
        if (p < 0) p = u.indexOf("127.0.0.1",0);
        if (p < 0) p = u.indexOf("0:0:0:0:0:0:0:1",0);
        if (p >= 0) u = u.substring(0, p) + localhostExtern + u.substring(p + 9);
        return u + (u.endsWith("/") ? "admin/" : "/admin/");
    }

    public static void main(final String args[]) {
        SingleSolrConnector solr;
        try {
            solr = new SingleSolrConnector("http://127.0.0.1:8983/solr");
            solr.clear();
            final File exampleDir = new File("test/parsertest/");
            long t, t0, a = 0;
            int c = 0;
            System.out.println("push files in " + exampleDir.getAbsolutePath() + " to Solr");
            for (final String s: exampleDir.list()) {
                if (s.startsWith(".")) continue;
                t = System.currentTimeMillis();
                solr.add(new File(exampleDir, s), s);
                t0 = (System.currentTimeMillis() - t);
                a += t0;
                c++;
                System.out.println("pushed file " + s + " to solr, " + t0 + " milliseconds");
            }
            System.out.println("pushed " + c + " files in " + a + " milliseconds, " + (a / c) + " milliseconds average; " + (60000 / a * c) + " PPM");
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
