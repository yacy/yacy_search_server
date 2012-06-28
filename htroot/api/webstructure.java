// webstructure.java
// ------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 01.05.2008 on http://yacy.net
//
// $LastChangedDate: 2009-03-16 19:08:43 +0100 (Mo, 16 Mrz 2009) $
// $LastChangedRevision: 5723 $
// $LastChangedBy: borg-0300 $
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


import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class webstructure {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        String about = post == null ? null : post.get("about", null); // may be a URL, a URL hash or a domain hash
        prop.put("out", 0);
        prop.put("in", 0);
        prop.put("references", 0);
        prop.put("citations", 0);
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        if (about != null) {
            DigestURI url = null;
            byte[] urlhash = null;
            String hosthash = null;
            if (about.length() == 6 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
            	hosthash = about;
            } else if (about.length() == 12 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
            	urlhash = ASCII.getBytes(about);
            	hosthash = about.substring(6);
            	url = authenticated ? sb.getURL(urlhash) : null;
            } else if (authenticated && about.length() > 0) {
            	// consider "about" as url or hostname
                try {
                    url = new DigestURI(about.indexOf("://") >= 0 ? about : "http://" + about); // accept also domains
                    urlhash = url.hash();
                    hosthash = ASCII.String(urlhash, 6, 6);
                } catch (MalformedURLException e) {
                }
            }
            if (hosthash != null) {
                prop.put("out", 1);
                prop.put("in", 1);
                WebStructureGraph.StructureEntry sentry = sb.webStructure.outgoingReferences(hosthash);
                if (sentry != null) {
                    reference(prop, "out", 0, sentry, sb.webStructure);
                    prop.put("out_domains", 1);
                } else {
                    prop.put("out_domains", 0);
                }
                sentry = sb.webStructure.incomingReferences(hosthash);
                if (sentry != null) {
                    reference(prop, "in", 0, sentry, sb.webStructure);
                    prop.put("in_domains", 1);
                } else {
                    prop.put("in_domains", 0);
                }
            }
            if (urlhash != null) {
            	// anchors
                prop.put("references", 1);
                net.yacy.document.Document scraper = null;
                if (url != null) try {
                    scraper = sb.loader.loadDocument(url, CacheStrategy.IFEXIST);
                } catch (final IOException e) {
                    Log.logException(e);
                }
                if (scraper != null) {
                    prop.put("references_count", 1);
                    prop.put("references_documents", 1);
                    prop.put("references_documents_0_hash", urlhash);
                    prop.put("references_documents_0_count", scraper.inboundLinks().size() + scraper.outboundLinks().size());
                    prop.put("references_documents_0_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date()));
                    prop.put("references_documents_0_urle", url == null ? 0 : 1);
                    if (url != null) prop.putXML("references_documents_0_urle_url", url.toNormalform(true, false));
                    int d = 0;
                    Iterator<MultiProtocolURI> i = scraper.inboundLinks().iterator();
            		while (i.hasNext()) {
            			DigestURI refurl = new DigestURI(i.next());
                    	byte[] refhash = refurl.hash();
                    	prop.putXML("references_documents_0_anchors_" + d + "_url", refurl.toNormalform(true, false));
                    	prop.put("references_documents_0_anchors_" + d + "_hash", refhash);
                    	prop.put("references_documents_0_anchors_" + d + "_outbound", 0);
                    	d++;
            		}
                    i = scraper.outboundLinks().iterator();
            		while (i.hasNext()) {
            			DigestURI refurl = new DigestURI(i.next());
                    	byte[] refhash = refurl.hash();
                    	prop.putXML("references_documents_0_anchors_" + d + "_url", refurl.toNormalform(true, false));
                    	prop.put("references_documents_0_anchors_" + d + "_hash", refhash);
                    	prop.put("references_documents_0_anchors_" + d + "_outbound", 1);
                    	d++;
            		}
                    prop.put("references_documents_0_count", d);
                    prop.put("references_documents_0_anchors", d);
                } else {
                    prop.put("references_count", 0);
                    prop.put("references_documents", 0);
            	}

                // citations
                prop.put("citations", 1);
            	IndexCell<CitationReference> citationReferences = sb.index.urlCitation();
            	ReferenceContainer<CitationReference> citations = null;
            	// citationReferences.count(urlhash) would give to the number of references good for ranking
            	try {
					citations = citationReferences.get(urlhash, null);
				} catch (IOException e) {
				}
            	if (citations != null) {
                    prop.put("citations_count", 1);
                    prop.put("citations_documents", 1);
                    prop.put("citations_documents_0_hash", urlhash);
                    prop.put("citations_documents_0_count", citations.size());
                    prop.put("citations_documents_0_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(citations.lastWrote())));
                    prop.put("citations_documents_0_urle", url == null ? 0 : 1);
                    if (url != null) prop.putXML("citations_documents_0_urle_url", url.toNormalform(true, false));
                    int d = 0;
                    Iterator<CitationReference> i = citations.entries();
            		while (i.hasNext()) {
                    	CitationReference cr = i.next();
                    	byte[] refhash = cr.urlhash();
                    	DigestURI refurl = authenticated ? sb.getURL(refhash) : null;
                    	prop.put("citations_documents_0_anchors_" + d + "_urle", refurl == null ? 0 : 1);
                    	if (refurl != null) prop.putXML("citations_documents_0_anchors_" + d + "_urle_url", refurl.toNormalform(true, false));
                    	prop.put("citations_documents_0_anchors_" + d + "_urle_hash", refhash);
                    	prop.put("citations_documents_0_anchors_" + d + "_urle_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date(cr.lastModified())));
                    	d++;
            		}
                    prop.put("citations_documents_0_count", d);
                    prop.put("citations_documents_0_anchors", d);
            	} else {
                    prop.put("citations_count", 0);
                    prop.put("citations_documents", 0);
            	}
            }
        } else if (authenticated) {
            // show a complete list of link structure informations in case that the user is authenticated
            final boolean latest = ((post == null) ? false : post.containsKey("latest"));
            final Iterator<WebStructureGraph.StructureEntry> i = sb.webStructure.structureEntryIterator(latest);
            int c = 0;
            WebStructureGraph.StructureEntry sentry;
            while (i.hasNext()) {
                sentry = i.next();
                reference(prop, "out", c, sentry, sb.webStructure);
                c++;
            }
            prop.put("out_domains", c);
            prop.put("out", 1);
            if (latest) sb.webStructure.joinOldNew();
        } else {
            // not-authenticated users show nothing
            prop.put("out_domains", 0);
            prop.put("out", 1);
        }
        prop.put("out_maxref", WebStructureGraph.maxref);
        prop.put("maxhosts", WebStructureGraph.maxhosts);

        // return rewrite properties
        return prop;
    }

    public static void reference(serverObjects prop, String prefix, int c, WebStructureGraph.StructureEntry sentry, WebStructureGraph ws) {
        prop.put(prefix + "_domains_" + c + "_hash", sentry.hosthash);
        prop.put(prefix + "_domains_" + c + "_domain", sentry.hostname);
        prop.put(prefix + "_domains_" + c + "_date", sentry.date);
        Iterator<Map.Entry<String, Integer>> k = sentry.references.entrySet().iterator();
        Map.Entry<String, Integer> refentry;
        String refdom, refhash;
        Integer refcount;
        int d = 0;
        refloop: while (k.hasNext()) {
            refentry = k.next();
            refhash = refentry.getKey();
            refdom = ws.hostHash2hostName(refhash);
            if (refdom == null) continue refloop;
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refhash", refhash);
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refdom", refdom);
            refcount = refentry.getValue();
            prop.put(prefix + "_domains_" + c + "_citations_" + d + "_refcount", refcount.intValue());
            d++;
        }
        prop.put(prefix + "_domains_" + c + "_citations", d);
    }
}
