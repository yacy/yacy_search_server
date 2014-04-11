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

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Fulltext;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.search.schema.HyperlinkEdge;
import net.yacy.search.schema.HyperlinkGraph;
import net.yacy.search.schema.HyperlinkType;
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
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int maxtime = Math.min(post.getInt("maxtime", 60000), authenticated ? 300000 : 1000);
        int maxnodes = Math.min(post.getInt("maxnodes", 10000), authenticated ? 10000000 : 100);
        HyperlinkGraph hlg = new HyperlinkGraph();
        int maxdepth = 0;
        
        if (post.get("about", null) != null) try {
            // get link structure within a host
            String about = post.get("about", null); // may be a URL, a URL hash or a domain hash
            DigestURL url = null;
            String hostname = null;
            if (about.length() == 12 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
                byte[] urlhash = ASCII.getBytes(about);
                url = authenticated ? sb.getURL(urlhash) : null;
            } else if (url == null && about.length() > 0) { // consider "about" as url or hostname
                url = new DigestURL(about.indexOf("://") >= 0 ? about : "http://" + about); // accept also domains
                hostname = url.getHost();
            }
            if (hostname == null) return prop;
            
            // now collect _all_ documents inside the domain until a timeout appears
            hlg.fill(fulltext.getDefaultConnector(), hostname, null, maxtime, maxnodes);
            maxdepth = hlg.findLinkDepth();
        } catch (final MalformedURLException e) {}
        else if (post.get("to", null) != null) try {
            // get link structure between two links
            DigestURL to = new DigestURL(post.get("to", null), null); // must be an url
            DigestURL from = post.get("from", null) == null ? null : new DigestURL(post.get("from", null)); // can be null or must be an url
            ReferenceReportCache rrc = sb.index.getReferenceReportCache();
            hlg.path(sb.index, rrc, from, to, maxtime, maxnodes);
        } catch (final MalformedURLException e) {}
        
        // finally just write out the edge array
        writeGraph(prop, hlg, maxdepth);

        // Adding CORS Access header for xml output
        if (xml) {
            final ResponseHeader outgoingHeader = new ResponseHeader(200);
            outgoingHeader.put(HeaderFramework.CORS_ALLOW_ORIGIN, "*");
            prop.setOutgoingHeader(outgoingHeader);
        }
        
        // return rewrite properties
        return prop;
    }
    
    private static void writeGraph(final servletProperties prop, final HyperlinkGraph hlg, final int maxdepth) {
        int c = 0;
        for (HyperlinkEdge e: hlg) {
            prop.putJSON("edges_" + c + "_source", e.source.getPath());
            prop.putJSON("edges_" + c + "_target", e.target.type.equals(HyperlinkType.Outbound) ? e.target.toNormalform(true) : e.target.getPath());
            prop.putJSON("edges_" + c + "_type", e.target.type.name());
            Integer depth_source = hlg.getDepth(e.source);
            Integer depth_target = hlg.getDepth(e.target);
            prop.put("edges_" + c + "_depthSource", depth_source == null ? -1 : depth_source.intValue());
            prop.put("edges_" + c + "_depthTarget", depth_target == null ? -1 : depth_target.intValue());
            prop.put("edges_" + c + "_eol", 1);
            c++;
        }
        prop.put("edges_" + (c-1) + "_eol", 0);
        prop.put("edges", c);
        prop.put("maxdepth", maxdepth);
    }

}
