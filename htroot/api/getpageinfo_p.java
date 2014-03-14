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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class getpageinfo_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // avoid UNRESOLVED PATTERN
        prop.put("title", "");
        prop.put("desc", "");
        prop.put("lang", "");
        prop.put("robots-allowed", "3"); //unknown
        prop.put("robotsInfo", ""); //unknown
        prop.put("sitemap", "");
        prop.put("favicon","");
        prop.put("sitelist", "");
        prop.put("filter", ".*");
        prop.put("oai", 0);

        // default actions
        String actions = "title,robots";

        if (post != null && post.containsKey("url")) {
            if (post.containsKey("actions"))
                actions=post.get("actions");
            String url=post.get("url");
			if (url.toLowerCase().startsWith("ftp://")) {
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
                    ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                    scraper = sb.loader.loadDocument(u, CacheStrategy.IFEXIST, BlacklistType.CRAWLER, agent);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                    // bad things are possible, i.e. that the Server responds with "403 Bad Behavior"
                    // that should not affect the robots.txt validity
                }
                if (scraper != null) {
                    // put the document title
                    prop.putXML("title", scraper.dc_title());

                    // put the favicon that belongs to the document
                    prop.put("favicon", (scraper.getFavicon()==null) ? "" : scraper.getFavicon().toString());

                    // put keywords
                    final String list[] = scraper.dc_subject();
                    int count = 0;
                    for (final String element: list) {
                        final String tag = element;
                        if (!tag.equals("")) {
                            prop.putXML("tags_"+count+"_tag", tag);
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
                    final Collection<AnchorURL> uris = scraper.getAnchors();
                    final StringBuilder links = new StringBuilder(uris.size() * 80);
                    final StringBuilder filter = new StringBuilder(uris.size() * 40);
                    count = 0;
                    for (final DigestURL uri: uris) {
                        if (uri == null) continue;
                        links.append(';').append(uri.toNormalform(true));
                        filter.append('|').append(uri.getProtocol()).append("://").append(uri.getHost()).append(".*");
                        prop.putXML("links_" + count + "_link", uri.toNormalform(true));
                        count++;
                    }
                    prop.put("links", count);
                    prop.putXML("sitelist", links.length() > 0 ? links.substring(1) : "");
                    prop.putXML("filter", filter.length() > 0 ? filter.substring(1) : ".*");
                }
            }
            if (actions.indexOf("robots",0) >= 0) {
                try {
                    final DigestURL theURL = new DigestURL(url);

                	// determine if crawling of the current URL is allowed
                    ClientIdentification.Agent agent = ClientIdentification.getAgent(post.get("agentName", ClientIdentification.yacyInternetCrawlerAgentName));
                    sb.robots.ensureExist(theURL, agent, true);
                    RobotsTxtEntry robotsEntry = sb.robots.getEntry(theURL, agent);
                	prop.put("robots-allowed", robotsEntry == null ? 1 : robotsEntry.isDisallowed(theURL) ? 0 : 1);
                    prop.putHTML("robotsInfo", robotsEntry == null ? "" : robotsEntry.getInfo());

                    // get the sitemap URL of the domain
                    final List<String> sitemaps = robotsEntry == null ? new ArrayList<String>(0) : robotsEntry.getSitemaps();
                    for (int i = 0; i < sitemaps.size(); i++) {
                        prop.putXML("sitemaps_" + i + "_sitemap", sitemaps.get(i));
                    }
                    prop.put("sitemaps", sitemaps.size());
                } catch (final MalformedURLException e) {
                    ConcurrentLog.logException(e);
                }
            }
            if (actions.indexOf("oai",0) >= 0) {
				try {
					final DigestURL theURL = new DigestURL(url
							+ "?verb=Identify");

					final String oairesult = checkOAI(theURL.toString());

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
