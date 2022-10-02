// getpageinfo_p
// (C) 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.11.2011 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.htroot.api;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Remote resource analyzer
 */
public class getpageinfo_p {

	/**
	 * <p>Scrape and parse a resource at a specified URL to provide some information, depending on the requested actions.</p>
	 *
	 * <p>
	 * Example API calls :
	 * <ul>
	 * <li>With the minimum required parameters : http://localhost:8090/api/getpageinfo_p.xml?url=http://yacy.net</li>
	 * <li>Only check the robots.txt policy and sitemap presence : http://localhost:8090/api/getpageinfo_p.xml?url=https://en.wikipedia.org/wiki/Main_Page&actions=robots</li>
	 * <li>Only check for an OAI Repository at CiteSeerX : http://localhost:8090/api/getpageinfo_p.xml?url=http://citeseerx.ist.psu.edu/oai2&actions=oai</li>
	 * </ul>
	 * </p>
	 *
	 *
	 * @param header
	 *            servlet request header
	 * @param post
	 *            request parameters. Supported keys :
	 *            <ul>
	 *            <li>url (required) : the URL of the resource to analyze. HTTP protocol is assumed if not present at the beginning of the URL.</li>
	 *            <li>actions (optional) : a list of comma separated actions to perform (default to "title,robots"). Supported actions :
	 *            	<ul>
	 *            		<li>title : look for the resource title, description, language, icons, keywords, and links</li>
	 *            		<li>robots : check if crawling the resource is allowed by the eventual robots.txt policy file, and also if this file exposes sitemap(s) URLs.</li>
	 *            		<li>oai : send an "Identify" OAI-PMH request (http://www.openarchives.org/OAI/openarchivesprotocol.html#Identify)
	 *            			at the URL to check for a OAI-PMH response from an Open Archive Initiative Repository</li>
	 *            	</ul>
	 *            </li>
	 *            <li>agentName (optional) : the string identifying the agent used to fetch the resource. Example : "YaCy Internet (cautious)"</li>
	 *            <li>maxLinks (optional integer value) : the maximum number of links, sitemap URLs or icons to return on 'title' action</li>
	 *            <li>maxBytes (optional long integer value) : the maximum number of bytes to load and parse from the url on 'title' action</li>
	 *            </ul>
	 * @param env
	 *            server environment
	 * @return the servlet answer object
	 */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // avoid UNRESOLVED PATTERN
        prop.put("title", "");
        prop.put("desc", "");
        prop.put("lang", "");
        prop.put("robots-allowed", "3"); //unknown
        prop.put("robotsInfo", ""); //unknown
        prop.put("sitemap", "");
        prop.put("icons","0");
        prop.put("sitelist", "");
        prop.put("filter", ".*");
        prop.put("oai", 0);

        // default actions
        String actions = "title,robots";

        if (post != null && post.containsKey("url")) {
        	final int maxLinks = post.getInt("maxLinks", Integer.MAX_VALUE);
            if (post.containsKey("actions"))
                actions=post.get("actions");
            String url=post.get("url");
			if (url.toLowerCase(Locale.ROOT).startsWith("ftp://")) {
				prop.put("robots-allowed", "1"); // ok to crawl
		        prop.put("robotsInfo", "ftp does not follow robots.txt");
				prop.putXML("title", "FTP: " + url);
                return prop;
			} else if (!url.startsWith("http://") &&
		               !url.startsWith("https://") &&
		               !url.startsWith("ftp://") &&
		               !url.startsWith("smb://") &&
		              !url.startsWith("file://")) {
                url = "http://" + url;
            }
            if (actions.indexOf("title",0) >= 0) {
                DigestURL u = null;
                try {
                    u = new DigestURL(url);
                } catch (final MalformedURLException e) {
                    ConcurrentLog.logException(e);
                }
                net.yacy.document.Document scraper = null;
                if (u != null) try {
                    final ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));

                	if(post.containsKey("maxBytes")) {
                		/* A maxBytes limit is specified : let's try to parse only the amount of bytes given */
                    	final long maxBytes = post.getLong("maxBytes", sb.loader.protocolMaxFileSize(u));
                        scraper = sb.loader.loadDocumentAsLimitedStream(u, CacheStrategy.IFEXIST, BlacklistType.CRAWLER, agent, maxLinks, maxBytes);
                	} else {
                		/* No maxBytes limit : apply regular parsing with default crawler limits.
                		 * Eventual maxLinks limit will apply after loading and parsing the document. */
                		scraper = sb.loader.loadDocumentAsStream(u, CacheStrategy.IFEXIST, BlacklistType.CRAWLER, agent);
                	}

                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                    // bad things are possible, i.e. that the Server responds with "403 Bad Behavior"
                    // that should not affect the robots.txt validity
                }
                if (scraper != null) {
                    // put the document title
                    prop.putXML("title", scraper.dc_title());

                    // put the icons that belong to the document
                    final Set<DigestURL> iconURLs = scraper.getIcons().keySet();
                    long count = 0;
                    for (final DigestURL iconURL : iconURLs) {
                        if(count >= maxLinks) {
                        	break;
                        }
                        prop.putXML("icons_" + count + "_icon", iconURL.toNormalform(false));
						prop.put("icons_" + count + "_eol", 1);
						count++;
                    }
                    if(count > 0) {
                    	prop.put("icons_" + (count - 1) + "_eol", 0);
                    }
                    prop.put("icons", count);

                    // put keywords
                    final Set<String> list = scraper.dc_subject();
                    count = 0;
                    for (final String element: list) {
                        if (!element.equals("")) {
                            prop.putXML("tags_"+count+"_tag", element);
                            count++;
                        }
                    }
                    prop.put("tags", count);
                    // put description
                    prop.putXML("desc", scraper.dc_description().length > 0 ? scraper.dc_description()[0] : "");
                    // put language
                    final Set<String> languages = scraper.getContentLanguages();
                    prop.putXML("lang", (languages == null || languages.size() == 0) ? "unknown" : languages.iterator().next());

                    // get links and put them into a semicolon-separated list
                    final LinkedHashSet<AnchorURL> uris = new LinkedHashSet<>();
                    uris.addAll(scraper.getAnchors());
                    final StringBuilder links = new StringBuilder(uris.size() * 80);
                    final StringBuilder filter = new StringBuilder(uris.size() * 40);
                    count = 0;
                    final Iterator<AnchorURL> urisIt = uris.iterator();
                    while (urisIt.hasNext()) {
                    	final AnchorURL uri = urisIt.next();
                        if (uri == null) continue;
                        if(count >= maxLinks) {
                        	break;
                        }
                        links.append(';').append(uri.toNormalform(true));
                        filter.append('|').append(uri.getProtocol()).append("://").append(uri.getHost()).append(".*");
                        prop.putXML("links_" + count + "_link", uri.toNormalform(true));
                        count++;
                    }
                    prop.put("links", count);
                   	prop.put("hasMoreLinks", scraper.isPartiallyParsed() || (count >= maxLinks && urisIt.hasNext()) ? "1" : "0");
                    prop.putXML("sitelist", links.length() > 0 ? links.substring(1) : "");
                    prop.putXML("filter", filter.length() > 0 ? filter.substring(1) : ".*");
                }
            }
            if (actions.indexOf("robots",0) >= 0) {
                try {
                    final DigestURL theURL = new DigestURL(url);

                	// determine if crawling of the current URL is allowed
                    final ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                    final RobotsTxtEntry robotsEntry = sb.robots.getEntry(theURL, agent);
                	prop.put("robots-allowed", robotsEntry == null ? 1 : robotsEntry.isDisallowed(theURL) ? 0 : 1);
                    prop.putHTML("robotsInfo", robotsEntry == null ? "" : robotsEntry.getInfo());

                    // get the sitemap URL(s) of the domain
                    final List<String> sitemaps = robotsEntry == null ? new ArrayList<String>(0) : robotsEntry.getSitemaps();
                    int count = 0;
                    for (final String sitemap : sitemaps) {
                        if(count >= maxLinks) {
                        	break;
                        }
                        prop.putXML("sitemaps_" + count + "_sitemap", sitemap);
                        count++;
                    }
                    prop.put("sitemaps", count);
                } catch (final MalformedURLException e) {
                    ConcurrentLog.logException(e);
                }
            }
            if (actions.indexOf("oai",0) >= 0) {
				try {
					final DigestURL theURL = new DigestURL(url
							+ "?verb=Identify");

					final String oairesult = checkOAI(theURL.toNormalform(false));

					prop.put("oai", oairesult == "" ? 0 : 1);

					if (oairesult != "") {
						prop.putXML("title", oairesult);
					}

				} catch (final MalformedURLException e) {
				}
			}

        }
        // return rewrite properties
        return prop;
    }

    /**
     * @param url an OIA-PHM "Identify" request URL (http://www.openarchives.org/OAI/openarchivesprotocol.html#Identify). Must not be null.
     * @return the OAI Repository name or an empty String when the response could not be parsed as an OAI-PMH response
     */
    private static String checkOAI(final String url) {
		final DocumentBuilderFactory factory = DocumentBuilderFactory
				.newInstance();
		try {
			final DocumentBuilder builder = factory.newDocumentBuilder();
			return parseXML(builder.parse(url));
		} catch (final ParserConfigurationException ex) {
			ConcurrentLog.logException(ex);
		} catch (final SAXException ex) {
			ConcurrentLog.logException(ex);
		} catch (final IOException ex) {
			ConcurrentLog.logException(ex);
		}

		return "";
	}

    /**
     * Extract the OAI repository name from an OAI-PMH "Identify" response
     * @param doc an XML document to parse. Must not be null.
     * @return the repository name or an empty String when the XML document is not an OAI-PMH "Identify" response
     */
	private static String parseXML(final Document doc) {

		String repositoryName = null;

		final NodeList items = doc.getDocumentElement().getElementsByTagName(
				"Identify");
		if (items.getLength() == 0) {
			return "";
		}

		for (int i = 0, n = items.getLength(); i < n; ++i) {

			if (!"Identify".equals(items.item(i).getNodeName()))
				continue;

			final NodeList currentNodeChildren = items.item(i).getChildNodes();

			for (int j = 0, m = currentNodeChildren.getLength(); j < m; ++j) {
				final Node currentNode = currentNodeChildren.item(j);
				if ("repositoryName".equals(currentNode.getNodeName())) {
					repositoryName = currentNode.getFirstChild().getNodeValue();
				}
			}

			if (repositoryName == null) {
				return "";
			}

		}
		return repositoryName;
	}


}
