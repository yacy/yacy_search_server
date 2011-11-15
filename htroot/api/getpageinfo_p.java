
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import de.anomic.crawler.RobotsTxtEntry;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class getpageinfo_p {

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
				prop.put("robots-allowed", "1");
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
            if (actions.indexOf("title") >= 0) {
                DigestURI u = null;
                try {
                    u = new DigestURI(url);
                } catch (final MalformedURLException e) {
                    Log.logException(e);
                }
                ContentScraper scraper = null;
                if (u != null) try {
                    scraper = sb.loader.parseResource(u, CacheStrategy.IFEXIST);
                } catch (final IOException e) {
                    Log.logException(e);
                }
                if (scraper != null) {
                    // put the document title
                    prop.putXML("title", scraper.getTitle());

                    // put the favicon that belongs to the document
                    prop.put("favicon", (scraper.getFavicon()==null) ? "" : scraper.getFavicon().toString());

                    // put keywords
                    final String list[] = scraper.getKeywords();
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
                    prop.putXML("desc", scraper.getDescription());
                    // put language
                    final Set<String> languages = scraper.getContentLanguages();
                    prop.putXML("lang", (languages == null) ? "unknown" : languages.iterator().next());

                    // get links and put them into a semicolon-separated list
                    final Set<MultiProtocolURI> uris = scraper.getAnchors().keySet();
                    final StringBuilder links = new StringBuilder(uris.size() * 80);
                    final StringBuilder filter = new StringBuilder(uris.size() * 40);
                    count = 0;
                    for (final MultiProtocolURI uri: uris) {
                        links.append(';').append(uri.toNormalform(true, false));
                        filter.append('|').append(uri.getProtocol()).append("://").append(uri.getHost()).append(".*");
                        prop.putXML("links_" + count + "_link", uri.toNormalform(true, false));
                        count++;
                    }
                    prop.put("links", count);
                    prop.putXML("sitelist", links.length() > 0 ? links.substring(1) : "");
                    prop.putXML("filter", filter.length() > 0 ? filter.substring(1) : ".*");
                }
            }
            if (actions.indexOf("robots") >= 0) {
                try {
                    final DigestURI theURL = new DigestURI(url);

                	// determine if crawling of the current URL is allowed
                    RobotsTxtEntry robotsEntry;
                    try {
                        robotsEntry = sb.robots.getEntry(theURL, sb.peers.myBotIDs());
                    } catch (final IOException e) {
                        robotsEntry = null;
                        Log.logException(e);
                    }
                	prop.put("robots-allowed", robotsEntry == null ? 1 : robotsEntry.isDisallowed(theURL) ? 0 : 1);
                    prop.putHTML("robotsInfo", robotsEntry.getInfo());

                    // get the sitemap URL of the domain
                    final MultiProtocolURI sitemapURL = robotsEntry == null ? null : robotsEntry.getSitemap();
                    prop.putXML("sitemap", sitemapURL == null ? "" : sitemapURL.toString());
                } catch (final MalformedURLException e) {
                    Log.logException(e);
                }
            }
            if (actions.indexOf("oai") >= 0) {
				try {
					final DigestURI theURL = new DigestURI(url
							+ "?verb=Identify");

					String oairesult = checkOAI(theURL.toString());

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
			Log.logException(ex);
		} catch (final SAXException ex) {
			Log.logException(ex);
		} catch (final IOException ex) {
			Log.logException(ex);
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
