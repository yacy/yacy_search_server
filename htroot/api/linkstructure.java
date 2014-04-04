// linkstructure.java
// ------------
// (C) 2014 by Michael Peter Christen; mc@yacy.net
// first published 02.04.2014 on http://yacy.net
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.solr.common.SolrDocument;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.HyperlinkEdge;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class linkstructure {
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        
        final String ext = header.get("EXT", "");
        //final boolean json = ext.equals("json");
        final boolean xml = ext.equals("xml");
        
        final Switchboard sb = (Switchboard) env;
        Fulltext fulltext = sb.index.fulltext();
        if (post == null) return prop;
        String about = post.get("about", null); // may be a URL, a URL hash or a domain hash
        if (about == null) return prop;
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int maxtime = Math.min(post.getInt("maxtime", 1000), authenticated ? 60000 : 1000);
        int maxnodes = Math.min(post.getInt("maxnodes", 100), authenticated ? 1000 : 100);

        DigestURL url = null;
        String hostname = null;
        if (about.length() == 12 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
            byte[] urlhash = ASCII.getBytes(about);
            url = authenticated ? sb.getURL(urlhash) : null;
        } else if (url == null && about.length() > 0) {
            // consider "about" as url or hostname
            try {
                url = new DigestURL(about.indexOf("://") >= 0 ? about : "http://" + about); // accept also domains
                hostname = url.getHost();
                if (hostname.startsWith("www.")) hostname = hostname.substring(4);
            } catch (final MalformedURLException e) {
            }
        }
        if (hostname == null) return prop;
        
        // now collect _all_ documents inside the domain until a timeout appears
        StringBuilder q = new StringBuilder();
        q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(hostname).append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(':').append("www.").append(hostname);
        BlockingQueue<SolrDocument> docs = fulltext.getDefaultConnector().concurrentDocumentsByQuery(q.toString(), 0, maxnodes, maxtime, 100, 1,
                CollectionSchema.id.getSolrFieldName(),
                CollectionSchema.sku.getSolrFieldName(),
                CollectionSchema.failreason_s.getSolrFieldName(),
                CollectionSchema.failtype_s.getSolrFieldName(),
                CollectionSchema.inboundlinks_protocol_sxt.getSolrFieldName(),
                CollectionSchema.inboundlinks_urlstub_sxt.getSolrFieldName(),
                CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(),
                CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()
                );
        SolrDocument doc;
        Map<String, FailType> errorDocs = new HashMap<String, FailType>();
        Map<String, HyperlinkEdge> edges = new HashMap<String, HyperlinkEdge>();
        try {
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                String ids = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                DigestURL from = new DigestURL(u, ASCII.getBytes(ids));
                String errortype = (String) doc.getFieldValue(CollectionSchema.failtype_s.getSolrFieldName());
                FailType error = errortype == null ? null : FailType.valueOf(errortype);
                if (error != null) {
                    errorDocs.put(u, error);
                } else {
                    Iterator<String> links = URIMetadataNode.getLinks(doc, true); // inbound
                    String link;
                    while (links.hasNext()) {
                        link = links.next();
                        try {
                            DigestURL linkurl = new DigestURL(link, null);
                            String edgehash = ids + ASCII.String(linkurl.hash());
                            edges.put(edgehash, new HyperlinkEdge(from, linkurl, HyperlinkEdge.Type.InboundOk));
                        } catch (MalformedURLException e) {}
                    }
                    links = URIMetadataNode.getLinks(doc, false); // outbound
                    while (links.hasNext()) {
                        link = links.next();
                        try {
                            DigestURL linkurl = new DigestURL(link, null);
                            String edgehash = ids + ASCII.String(linkurl.hash());
                            edges.put(edgehash, new HyperlinkEdge(from, linkurl, HyperlinkEdge.Type.Outbound));
                        } catch (MalformedURLException e) {}
                    }
                }
                if (edges.size() > maxnodes) break;
            }
        } catch (InterruptedException e) {
        } catch (MalformedURLException e) {
        }
        // we use the errorDocs to mark all edges with endpoint to error documents
        for (Map.Entry<String, HyperlinkEdge> edge: edges.entrySet()) {
            if (errorDocs.containsKey(edge.getValue().target.toNormalform(true))) edge.getValue().type = HyperlinkEdge.Type.Dead;
        }

        // finally just write out the edge array
        int c = 0;
        for (Map.Entry<String, HyperlinkEdge> edge: edges.entrySet()) {
            prop.putJSON("list_" + c + "_source", edge.getValue().source.getPath());
            prop.putJSON("list_" + c + "_target", edge.getValue().type.equals(HyperlinkEdge.Type.Outbound) ? edge.getValue().target.toNormalform(true) : edge.getValue().target.getPath());
            prop.putJSON("list_" + c + "_type", edge.getValue().type.name());
            prop.put("list_" + c + "_eol", 1);
            c++;
        }
        prop.put("list_" + (c-1) + "_eol", 0);
        prop.put("list", c);

        // Adding CORS Access header for xml output
        if (xml) {
            final ResponseHeader outgoingHeader = new ResponseHeader(200);
            outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
            prop.setOutgoingHeader(outgoingHeader);
        }
        
        // return rewrite properties
        return prop;
    }

}
