/**
 *  HyperlinkGraph
 *  Copyright 2014 by Michael Peter Christen
 *  First released 08.04.2014 at http://yacy.net
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

package net.yacy.search.schema;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.index.Segment;
import net.yacy.search.index.Segment.ReferenceReportCache;

import org.apache.solr.common.SolrDocument;


public class HyperlinkGraph implements Iterable<HyperlinkEdge> {
    
    public final static Set<String> ROOTFNS = new HashSet<String>();
    static {
        for (String s: new String[]{"/", "/index.htm", "/index.html", "/index.php", "/home.htm", "/home.html", "/home.php", "/default.htm", "/default.html", "/default.php"}) {
            ROOTFNS.add(s);
        }
    }
    
    HyperlinkEdges edges;
    String hostname;
    
    public HyperlinkGraph() {
        this.edges = new HyperlinkEdges();
        this.hostname = null;
    }
    
    public void fill(final SolrConnector solrConnector, String hostname, final DigestURL stopURL, final int maxtime, final int maxnodes) {
        this.hostname = hostname;
        if (hostname.startsWith("www.")) hostname = hostname.substring(4);
        StringBuilder q = new StringBuilder();
        q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(hostname).append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(':').append("www.").append(hostname);
        BlockingQueue<SolrDocument> docs = solrConnector.concurrentDocumentsByQuery(q.toString(), CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, maxnodes, maxtime, 100, 1,
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
        HyperlinkEdges inboundEdges = new HyperlinkEdges();
        HyperlinkEdges outboundEdges = new HyperlinkEdges();
        HyperlinkEdges errorEdges = new HyperlinkEdges();
        try {
            retrieval: while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                MultiProtocolURL from = new MultiProtocolURL(u);
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
                            HyperlinkEdge.Target linkurl = new HyperlinkEdge.Target(link, HyperlinkType.Inbound);
                            inboundEdges.addEdge(from, linkurl);
                            if (stopURL != null && linkurl.equals(stopURL)) break retrieval;
                        } catch (MalformedURLException e) {}
                    }
                    links = URIMetadataNode.getLinks(doc, false); // outbound
                    while (links.hasNext()) {
                        link = links.next();
                        try {
                            HyperlinkEdge.Target linkurl = new HyperlinkEdge.Target(link, HyperlinkType.Outbound);
                            outboundEdges.addEdge(from, linkurl);
                            if (stopURL != null && linkurl.equals(stopURL)) break retrieval;
                        } catch (MalformedURLException e) {}
                    }
                }
                if (inboundEdges.size() + outboundEdges.size() > maxnodes) {
                    break retrieval;
                }
            }
        } catch (InterruptedException e) {
        } catch (MalformedURLException e) {
        }
        // we use the errorDocs to mark all edges with endpoint to error documents
        Iterator<HyperlinkEdge> i = inboundEdges.iterator();
        HyperlinkEdge edge;
        while (i.hasNext()) {
            edge = i.next();
            if (errorDocs.containsKey(edge.target.toNormalform(true))) {
                i.remove();
                edge.target.type = HyperlinkType.Dead;
                errorEdges.add(edge);
            }
        }
        i = outboundEdges.iterator();
        while (i.hasNext()) {
            edge = i.next();
            if (errorDocs.containsKey(edge.target.toNormalform(true))) {
                i.remove();
                edge.target.type = HyperlinkType.Dead;
                errorEdges.add(edge);
            }
        }
        // we put all edges together in a specific order which is used to create nodes in a svg display:
        // notes that appear first are possible painted over by nodes coming later.
        // less important nodes shall appear therefore first
        this.edges.addAll(outboundEdges);
        this.edges.addAll(inboundEdges);
        this.edges.addAll(errorEdges);
    }
    
    public void path(final Segment segment, ReferenceReportCache rrc, DigestURL from, DigestURL to, final int maxtime, final int maxnodes) {
        // two steps to find the graph: (1) create a HyperlinkGraph (to-down) and (2) backtrack backlinks up to an element of the graph (bottom-up)
        if (this.edges.size() == 0) {
            fill(segment.fulltext().getDefaultConnector(), from == null ? to.getHost() : from.getHost(), to, maxtime, maxnodes);
        }
        if (getDepth(to) >= 0 && (from == null || getDepth(from) >= 0)) return; // nothing to do.
        // now find the link bottom-up
        
    }
    
    public int findLinkDepth() {

        int remaining = this.edges.size();
        
        // first find root nodes
        Set<MultiProtocolURL> nodes = new HashSet<MultiProtocolURL>();
        Set<MultiProtocolURL> nextnodes = new HashSet<MultiProtocolURL>();
        for (HyperlinkEdge edge: this.edges) {
            String path = edge.source.getPath();
            if (ROOTFNS.contains(path)) {
                this.edges.updateDepth(edge.source, 0);
                if (edge.target.type == HyperlinkType.Inbound) this.edges.updateDepth(edge.target, 1);
                nodes.add(edge.source);
                nextnodes.add(edge.target);
                remaining--;
            }
        }
        if (nodes.size() == 0 && this.edges.size() > 0) {
            ConcurrentLog.warn("HyperlinkGraph", "could not find a root node for " + hostname + " in " + this.edges.size() + " edges");
        }
        // add virtual nodes
        for (String rootpath: ROOTFNS) {
            try {
                this.edges.updateDepth(new DigestURL("http://" + hostname + rootpath), 0);
            } catch (MalformedURLException e) {}
        }
        
        // recursively step into depth and find next level
        int depth = 1;
        while (remaining > 0) {
            boolean found = false;
            nodes = nextnodes;
            nextnodes = new HashSet<MultiProtocolURL>();
            for (HyperlinkEdge edge: this.edges) {
                if (nodes.contains(edge.source)) {
                    this.edges.updateDepth(edge.source, depth);
                    if (edge.target.type == HyperlinkType.Inbound) this.edges.updateDepth(edge.target, depth + 1);
                    nextnodes.add(edge.target);
                    remaining--;
                    found = true;
                }
            }
            depth++;
            if (!found) break; // terminating in case that not all edges are linked together
        }
        if (remaining > 0) ConcurrentLog.warn("HyperlinkGraph", "could not find all edges for " + hostname + ", " + remaining + " remaining.");
        return depth;
    }
    
    public Integer getDepth(MultiProtocolURL url) {
        return this.edges.getDepth(url);
    }

    @Override
    public Iterator<HyperlinkEdge> iterator() {
        return this.edges.iterator();
    }
    
}
