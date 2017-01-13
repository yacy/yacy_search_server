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
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.search.Switchboard;
import net.yacy.search.index.Segment.ReferenceReport;
import net.yacy.search.index.Segment.ReferenceReportCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Retrieval of a web links structure.
 */
public class webstructure {

	/**
	 * Retrieve the locally known web links structure of a specified resource ("about" parameter supplied) or
	 * the whole computed links structure since install (no parameter supplied)
	 * or since last start or last call ("latest" parameter supplied).
	 * Returned object contains the following information :
	 * <ul>
	 * 	<li>in all cases :
	 * 		<ul>
	 * 			<li>accumulated list of outgoing links to other domains (per host accumulated anchors)</li>
	 * 		</ul>
	 * 	</li>
	 *  <li>when "about" parameter is filled :
	 *  	<ul>
	 * 			<li>accumulated list of incoming links from other domains (per host accumulated references)</li>
	 * 			<li>detailed list of outgoing links (anchors) from documents to references</li>
     * 			<li>detailed list of incoming links (citations) from other documents (their references) - reverse link structure</li>
     * 		</ul>
     * 	</li>
	 * </ul>
	 * Information detail is limited by {@link WebStructureGraph#maxhosts} and {@link WebStructureGraph#maxref} constants.
	 * 
	 * @param header
	 *            servlet request header
	 * @param post
	 *            request parameters. Supported keys :
	 *            <ul>
	 *            <li>about : get only links structure about the resource
	 *            specified as value. Supported values : host hash, URL hash,
	 *            host name or URL</li>
	 *            <li>latest (ignored when about parameter is valued): get the structure that have been computed during
	 *            the current run-time of YaCy, and with each next call only an
	 *            update to the next list of references.</li>
	 *            <li>agentName : name of the user agent string used to load the
	 *            "about" resource</li>
	 *            </ul>
	 * @param env
	 *            server environment
	 * @return the servlet answer object
	 * @see WebStructureGraph
	 */
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
            DigestURL url = null;
            byte[] urlhash = null;
            String hosthash = null;
            if (about.length() == 6 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
            	hosthash = about;
            } else if (about.length() == 12 && Base64Order.enhancedCoder.wellformed(ASCII.getBytes(about))) {
            	urlhash = ASCII.getBytes(about);
            	hosthash = about.substring(6);
            	try {
                    url = authenticated ? sb.getURL(urlhash) : null;
                } catch (IOException e) {
                    url = null;
                    ConcurrentLog.logException(e);
                }
            } else if (about.length() > 0) {
            	// consider "about" as url or hostname
                try {
                    url = new DigestURL(about.indexOf("://") >= 0 ? about : "http://" + about); // accept also domains
                    urlhash = url.hash();
                    hosthash = url.hosthash();
                } catch (final MalformedURLException e) {
                }
            }
            if (hosthash != null) {
                prop.put("out", 1);
                prop.put("in", 1);
                WebStructureGraph.StructureEntry sentry = sb.webStructure.outgoingReferences(hosthash);
                if (sentry != null && sentry.references.size() > 0) {
                    reference(prop, "out", 0, sentry, sb.webStructure);
                    prop.put("out_domains", 1);
                } else {
                    prop.put("out_domains", 0);
                }
                sentry = sb.webStructure.incomingReferences(hosthash);
                if (sentry != null && sentry.references.size() > 0) {
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
                    ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                    scraper = sb.loader.loadDocument(url, CacheStrategy.IFEXIST, null, agent);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
                if (scraper != null) {
                    prop.put("references_count", 1);
                    prop.put("references_documents", 1);
                    prop.put("references_documents_0_hash", urlhash);
                    prop.put("references_documents_0_count", scraper.inboundLinks().size() + scraper.outboundLinks().size());
                    prop.put("references_documents_0_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date()));
                    prop.put("references_documents_0_urle", url == null ? 0 : 1);
                    if (url != null) prop.putXML("references_documents_0_urle_url", url.toNormalform(true));
                    int d = 0;
                    Iterator<DigestURL> i = scraper.inboundLinks().keySet().iterator();
            		while (i.hasNext()) {
            			DigestURL refurl = i.next();
                    	byte[] refhash = refurl.hash();
                    	prop.putXML("references_documents_0_anchors_" + d + "_url", refurl.toNormalform(true));
                    	prop.put("references_documents_0_anchors_" + d + "_hash", refhash);
                    	prop.put("references_documents_0_anchors_" + d + "_outbound", 0);
                    	d++;
            		}
                    i = scraper.outboundLinks().keySet().iterator();
            		while (i.hasNext()) {
            			DigestURL refurl = i.next();
                    	byte[] refhash = refurl.hash();
                    	prop.putXML("references_documents_0_anchors_" + d + "_url", refurl.toNormalform(true));
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
                ReferenceReportCache rrc = sb.index.getReferenceReportCache();
                ReferenceReport rr = null;
                try {rr = rrc.getReferenceReport(ASCII.String(urlhash), true);} catch (IOException e) {}
            	if (rr != null && rr.getInternalCount() > 0 && rr.getExternalCount() > 0) {
                    prop.put("citations_count", 1);
                    prop.put("citations_documents", 1);
                    prop.put("citations_documents_0_hash", urlhash);
                    prop.put("citations_documents_0_count", rr.getInternalCount() + rr.getExternalCount());
                    prop.put("citations_documents_0_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date())); // superfluous?
                    prop.put("citations_documents_0_urle", url == null ? 0 : 1);
                    if (url != null) prop.putXML("citations_documents_0_urle_url", url.toNormalform(true));
                    int d = 0;
                    HandleSet ids = rr.getInternallIDs();
                    try {ids.putAll(rr.getExternalIDs());} catch (SpaceExceededException e) {}
                    Iterator<byte[]> i = ids.iterator();
            		while (i.hasNext()) {
                    	byte[] refhash = i.next();
                    	DigestURL refurl;
                        try {
                            refurl = authenticated ? sb.getURL(refhash) : null;
                            prop.put("citations_documents_0_anchors_" + d + "_urle", refurl == null ? 0 : 1);
                            if (refurl != null) prop.putXML("citations_documents_0_anchors_" + d + "_urle_url", refurl.toNormalform(true));
                            prop.put("citations_documents_0_anchors_" + d + "_urle_hash", refhash);
                            prop.put("citations_documents_0_anchors_" + d + "_urle_date", GenericFormatter.SHORT_DAY_FORMATTER.format(new Date())); // superfluous?
                            d++;
                        } catch (IOException e) {
                            ConcurrentLog.logException(e);
                        }
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
