/**
 *  OpenSearchConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 03.11.2012 at https://yacy.net
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
package net.yacy.cora.federate.opensearch;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.AbstractFederateSearchConnector;
import net.yacy.cora.federate.FederateSearchConnector;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.Switchboard;
import net.yacy.search.query.QueryParams;
import net.yacy.search.schema.CollectionSchema;

/**
 * Handling of queries to remote OpenSearch systems. Iterates to a list of
 * configured systems until number of needed results are available.
 */
public class OpenSearchConnector extends AbstractFederateSearchConnector implements FederateSearchConnector {

	/**
	 * HTML mapping properties used to retrieve result from HTML when the results
	 * are not provided as a standard RSS/Atom feed but as simple HTML.
	 */
	private final Properties htmlMapping;

	/**
	 * @param instanceName open search instance name
	 * @return the html mapping configuration file name derived from the instance name
	 */
	public static String htmlMappingFileName(final String instanceName) {
		return instanceName + ".html.map.properties";
	}

	/**
	 * @param urlTemplate OpenSearch URL template
	 */
	public OpenSearchConnector(final String urlTemplate) {
		super();
		this.baseurl = urlTemplate;
		this.htmlMapping = new Properties();
	}

    @Override
    public boolean init(final String name, final String cfgFileName) {
        this.instancename = name;
        this.localcfg = null;
        this.htmlMapping.clear();
		if (cfgFileName != null && !cfgFileName.isEmpty()) {
			BufferedInputStream cfgFileStream = null;
			try {
				cfgFileStream = new BufferedInputStream(new FileInputStream(cfgFileName));
				this.htmlMapping.load(cfgFileStream);
			} catch (final IOException e) {
				ConcurrentLog.config("OpenSearchConnector." + this.instancename, "Error reading html mapping file : " + cfgFileName, e);
			} finally {
				if (cfgFileStream != null) {
					try {
						cfgFileStream.close();
					} catch (final IOException e) {
						ConcurrentLog.config("OpenSearchConnector." + this.instancename, "Error closing html mapping file : " + cfgFileName, e);
					}
				}
			}
		}
        return true;
    }

    /**
     * replace Opensearchdescription search template parameter with actual values
     */
    private String parseSearchTemplate(final String searchurltemplate, final String query, final int start, final int rows) {
        String tmps = searchurltemplate.replaceAll("\\?}", "}"); // some optional parameters may include question mark '{param?}='
        tmps = tmps.replace("{startIndex}", Integer.toString(start));
        tmps = tmps.replace("{startPage}", "");
        tmps = tmps.replace("{count}", Integer.toString(rows));
        tmps = tmps.replace("{language}", "");
        tmps = tmps.replace("{inputEncoding}", StandardCharsets.UTF_8.name());
        tmps = tmps.replace("{outputEncoding}", StandardCharsets.UTF_8.name());
        return tmps.replace("{searchTerms}", query);
    }

    /**
     * @param linkElement html link result node. Must not be null.
     * @return and {@link URIMetadataNode} instance from the html link element or null when minimum required information is missing or malformed
     */
	protected URIMetadataNode htmlLinkToMetadataNode(final Element linkElement) {
		URIMetadataNode doc = null;
		final String absoluteURL = linkElement.absUrl("href");
		try {
			if (!absoluteURL.isEmpty()) {
				final DigestURL uri = new DigestURL(absoluteURL);

				doc = new URIMetadataNode(uri);

				if(linkElement.hasText() && !this.htmlMapping.containsKey("title")) {
					/* Let's use the link text as default title when no mapping is defined.*/
					doc.setField(CollectionSchema.title.getSolrFieldName(), linkElement.text());
				}

				final String targetLang = linkElement.attr("hreflang");
				if(targetLang != null && !targetLang.isEmpty()) {
					doc.setField(CollectionSchema.language_s.getSolrFieldName(), targetLang);
				}

				final String mime = TextParser.mimeOf(uri);
				if (mime != null) {
					doc.setField(CollectionSchema.content_type.getSolrFieldName(), mime);
				}

				/*
				 * add collection "dht" which is used to differentiate metadata
				 * from full crawl data in the index
				 */
				doc.setField(CollectionSchema.collection_sxt.getSolrFieldName(), "dht");
			}
		} catch (final MalformedURLException e) {
			ConcurrentLog.fine("OpenSearchConnector." + this.instancename, "Malformed url : " + absoluteURL);
		}
		return doc;
	}

	/**
	 * Extract results from the HTML result stream, using the html mapping properties.
	 * Important : it is the responsibility of the caller to close the stream.
	 * @param resultStream HTML stream containing OpenSearch results. Must not be null.
	 * @param charsetName characters set name. May be null : in that case the eventual {@code http-equiv} meta tag will be used.
	 * @return a list of URI nodes, eventually empty.
	 * @throws IOException when a read/write exception occurred
	 */
	protected List<URIMetadataNode> parseHTMLResult(final InputStream resultStream, final String charsetName) throws IOException {
		final List<URIMetadataNode> docs = new ArrayList<>();
		final String resultSelector = this.htmlMapping.getProperty("_result");
		final String skuSelector = this.htmlMapping.getProperty("_sku");
		if (resultSelector == null || skuSelector == null) {
			ConcurrentLog.warn("OpenSearchConnector." + this.instancename, "HTML mapping is incomplete!");
			return docs;
		}

		final Document jsoupDoc = Jsoup.parse(resultStream, charsetName, this.baseurl);
		final Elements results = jsoupDoc.select(resultSelector);

		for (final Element result : results) {
			final Elements skuNodes = result.select(skuSelector);
			if (!skuNodes.isEmpty()) {
				Element skuNode = skuNodes.first();
				if (!"a".equals(skuNode.tagName())) {
					/*
					 * The selector may refer to a node with link(s) inside
					 */
					final Elements links = skuNode.select("a[href]");
					if (!links.isEmpty()) {
						skuNode = links.first();
					}
				}
				if (skuNode.hasAttr("href")) {
					final URIMetadataNode newDoc = htmlLinkToMetadataNode(skuNode);
					if (newDoc != null) {
						/* Let's handle other field mappings */
						htmlResultToFields(result, newDoc);
						docs.add(newDoc);
					}
				}
			}
		}
		return docs;
    }

	/**
	 * Perform mapping from an HTML result node to YaCy fields using the htmlMapping configuration.
	 * @param resultNode html single result node
	 * @param newdoc result document to fill
	 */
	private void htmlResultToFields(final Element resultNode, final URIMetadataNode newdoc) {
		for (final Entry<Object, Object> entry : this.htmlMapping.entrySet()) {
			if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
				final String yacyFieldName = (String) entry.getKey();
				final String selector = (String) entry.getValue();

				if (!yacyFieldName.startsWith("_")) {
					/* If Switchboard environment is set, check the index configuration has this field enabled */
					if (Switchboard.getSwitchboard() == null || Switchboard.getSwitchboard().index == null
							|| Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration()
									.contains(yacyFieldName)) {

						final Elements nodes = resultNode.select(selector);

						SchemaDeclaration est;
						try {
							est = CollectionSchema.valueOf(yacyFieldName);
						} catch(final IllegalArgumentException e) {
							ConcurrentLog.config("OpenSearchConnector." + this.instancename,
									"Ignored " + yacyFieldName + " field mapping : not a field of this schema.");
							continue;
						}
						if (est.isMultiValued()) {
							if (!nodes.isEmpty()) {
								for (final Element node : nodes) {
									final String value = node.text();
									if (!value.isEmpty()) {
										newdoc.addField(yacyFieldName, value);
									}
								}
							}
						} else {
							if (!nodes.isEmpty()) {
								final Element node = nodes.first();
								final String value = node.text();
								if (!value.isEmpty()) {
									/* Perform eventual type conversion */
									try {
										if (est.getType() == SolrType.num_integer) {
											newdoc.setField(yacyFieldName, Integer.parseInt(value));
										} else {
											newdoc.setField(yacyFieldName, value);
										}
									} catch (final NumberFormatException ex) {
										continue;
									}
								}
							}
						}
					}
				}
			}
		}
	}

    /**
     * queries remote system and returns the resultlist (waits until results
     * transmitted or timeout) This is the main access routine used for the
     * search and query operation For internal access delay time, also the
     * this.lastaccessed time needs to be set here.
     *
     * @return query results (metadata) with fields according to YaCy schema
     */
    @Override
    public List<URIMetadataNode> query(final QueryParams query) {

        return query(query.getQueryGoal().getQueryString(false), 0, query.itemsPerPage);
    }

    /**
     * Query the remote system at baseurl with the specified search terms
     * @param searchTerms search terms
     * @param startIndex index offset
     * @param count maximum results number
     * @return a result list eventually empty when no results where found or when an error occured
     */
    public List<URIMetadataNode> query(final String searchTerms, final int startIndex, final int count) {
    	List<URIMetadataNode> docs = new ArrayList<>();

        // see http://www.loc.gov/standards/sru/
        final String searchurl = this.parseSearchTemplate(this.baseurl, searchTerms, startIndex, count);
        try {
        	final DigestURL aurl = new DigestURL(searchurl);
        	try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
                this.lastaccesstime = System.currentTimeMillis();

                final byte[] result = httpClient.GETbytes(aurl, null, null, false);

    			if(result == null) {
    				String details;
    				if(httpClient.getHttpResponse() != null && httpClient.getHttpResponse().getStatusLine() != null) {
    					details = " HTTP status code : " + httpClient.getStatusCode();
    				} else {
    					details = "";
    				}
                	throw new IOException("Could not get a response." + details);
    			}

                if("text/html".equals(httpClient.getMimeType())) {
					if (this.htmlMapping.isEmpty()) {
						ConcurrentLog.warn("OpenSearchConnector." + this.instancename, "Received HTML result but mapping is not configured!");
					} else {
						/*
						 * Result was received as html : let's try to use the
						 * provided mapping to retrieve results from HTML
						 */
						docs = parseHTMLResult(new ByteArrayInputStream(result), httpClient.getCharacterEncoding());
					}
                } else {
                	/* Other mime types or unknown : let's try to parse the result as RSS or Atom Feed */
                    final RSSReader rssReader =  RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
                    if (rssReader != null) {
                        final RSSFeed feed = rssReader.getFeed();
                        if (feed != null) {
                            for (final RSSMessage item : feed) {
                                try {
                                    final DigestURL uri = new DigestURL(item.getLink());

                                    final URIMetadataNode doc = new URIMetadataNode(uri);
                                    doc.setField(CollectionSchema.charset_s.getSolrFieldName(), StandardCharsets.UTF_8.name());
                                    doc.setField(CollectionSchema.author.getSolrFieldName(), item.getAuthor());
                                    doc.setField(CollectionSchema.title.getSolrFieldName(), item.getTitle());
                                    doc.setField(CollectionSchema.language_s.getSolrFieldName(), item.getLanguage());
                                    doc.setField(CollectionSchema.last_modified.getSolrFieldName(), item.getPubDate());
                                    final String mime = TextParser.mimeOf(uri);
                                    if (mime != null) {
                                        doc.setField(CollectionSchema.content_type.getSolrFieldName(), mime);
                                    }
                                    if (item.getCategory().isEmpty()) {
                                        doc.setField(CollectionSchema.keywords.getSolrFieldName(), Arrays.toString(item.getSubject()));
                                    } else {
                                        doc.setField(CollectionSchema.keywords.getSolrFieldName(), Arrays.toString(item.getSubject()) + " " + item.getCategory());
                                    }
                                    doc.setField(CollectionSchema.publisher_t.getSolrFieldName(), item.getCopyright());

                                    doc.setField(CollectionSchema.text_t.getSolrFieldName(), item.getDescriptions());
                                    // we likely got only a search related snippet (take is as text content)
                                    // add collection "dht" which is used to differentiate metadata from full crawl data in the index
                                    doc.setField(CollectionSchema.collection_sxt.getSolrFieldName(), "dht");

                                    if (item.getLat() != 0.0 && item.getLon() != 0.0) {
                                        doc.setField(CollectionSchema.coordinate_p.getSolrFieldName(), item.getLat() + "," + item.getLon());
                                    }
                                    if (item.getSize() > 0) {
                                        doc.setField(CollectionSchema.size_i.getSolrFieldName(), item.getSize());
                                    }

                                    docs.add(doc);
                                } catch (final MalformedURLException e) {
                                }
                            }
                			ConcurrentLog.info("OpenSearchConnector." + this.instancename, "received " + docs.size() + " results from " + this.instancename);
                		}
                	}
                }
            } catch (final IOException ex) {
                ConcurrentLog.logException(ex);
                ConcurrentLog.info("OpenSearchConnector." + this.instancename, "no connection to " + searchurl);
            }
        } catch (final MalformedURLException ee) {
            ConcurrentLog.warn("OpenSearchConnector." + this.instancename, "malformed url " + searchurl);
        }
        return docs;
    }

    /**
     * Main procedure : can be used to test results retrieval from an open search system
     * @param args main arguments list:
     * <ol>
     * 	<li>OpenSearch URL template (required)</li>
     * 	<li>Search term (required)</li>
     * 	<li>Html mapping file path (optional)</li>
     * </ol>
     */
	public static void main(final String args[]) {
		try {
			if (args.length < 2) {
				System.out.println("Usage : java " + OpenSearchConnector.class.getCanonicalName()
						+ " <templateURL> <\"searchTerms\"> [htmlMappingFile]");
				return;
			}
			final OpenSearchConnector connector = new OpenSearchConnector(args[0]);
			String htmlMappingFile;
			if (args.length > 2) {
				htmlMappingFile = args[2];
			} else {
				htmlMappingFile = null;
			}
			connector.init("testConnector", htmlMappingFile);
			String searchTerms = args[1];
			if(searchTerms.length() > 2 && searchTerms.startsWith("\"") && searchTerms.endsWith("\"")) {
				searchTerms = searchTerms.substring(1, searchTerms.length() - 1);
			}
			final List<URIMetadataNode> docs = connector.query(searchTerms, 0, 20);
			if (docs.isEmpty()) {
				System.out.println("No results");
			} else {

				for (final URIMetadataNode doc : docs) {
					System.out.println("title : " + doc.getFieldValue(CollectionSchema.title.getSolrFieldName()));
					System.out.println("sku : " + doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
					System.out.println(
							"Description : " + doc.getFieldValue(CollectionSchema.description_txt.getSolrFieldName()) + "\n");
				}
			}
		} finally {
			/* Shutdown running threads */
			Domains.close();
			try {
				HTTPClient.closeConnectionManager();
			} catch (final InterruptedException e) {
			}
			ConcurrentLog.shutdown();
		}
	}
}
