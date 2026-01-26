// SurrogateReader.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.04.2009 on http://yacy.net
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

package net.yacy.document.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.CrawlStacker;
import net.yacy.search.schema.CollectionConfiguration;


public class SurrogateReader extends DefaultHandler implements Runnable {

    // definition of the surrogate main element
    public final static String SURROGATES_MAIN_ELEMENT_NAME =
        "surrogates";
    public final static String SURROGATES_MAIN_ELEMENT_OPEN =
        "<" + SURROGATES_MAIN_ELEMENT_NAME +
        " xmlns:dc=\"" + DublinCore.NAMESPACE + "\"" +
        " xmlns:yacy=\"http://yacy.net/\"" +
        " xmlns:geo=\"" + Geo.NAMESPACE + "\">";
    public final static String SURROGATES_MAIN_ELEMENT_CLOSE =
        "</" + SURROGATES_MAIN_ELEMENT_NAME + ">";
    public final static SolrInputDocument POISON_DOCUMENT = new SolrInputDocument();
    
    /** Maximum bytes number that can be unread on the underlying input stream */
    private static final int PUSHBACK_SIZE = 1024;

    // class variables
    private final StringBuilder buffer;
    private boolean parsingValue;
    private DCEntry dcEntry;
    private String elementName;
    /** Surrogates are either SolrInputDocument or DCEntry instances*/
    private final BlockingQueue<Object> surrogates;
    private SAXParser saxParser;
    private final PushbackInputStream inputStream;
    private final CrawlStacker crawlStacker;
    private final CollectionConfiguration configuration;
    private final int concurrency;

    private static final ThreadLocal<SAXParser> tlSax = new ThreadLocal<SAXParser>();
    private static SAXParser getParser() throws SAXException {
    	SAXParser parser = tlSax.get();
    	if (parser == null) {
    		try {
				parser = SAXParserFactory.newInstance().newSAXParser();
			} catch (final ParserConfigurationException e) {
				throw new SAXException(e.getMessage(), e);
			}
    		tlSax.set(parser);
    	}
    	return parser;
    }

    public SurrogateReader(final InputStream stream, int queueSize, CrawlStacker crawlStacker, CollectionConfiguration configuration, int concurrency) throws IOException {
        this(new PushbackInputStream(stream, PUSHBACK_SIZE), queueSize, crawlStacker, configuration, concurrency);
    }
    
    public SurrogateReader(final PushbackInputStream stream, int queueSize, CrawlStacker crawlStacker, CollectionConfiguration configuration, int concurrency) throws IOException {
        this.crawlStacker = crawlStacker;
        this.configuration = configuration;
        this.concurrency = concurrency;
        this.buffer = new StringBuilder(300);
        this.parsingValue = false;
        this.dcEntry = null;
        this.elementName = null;
        this.surrogates = new ArrayBlockingQueue<>(queueSize);
        this.inputStream = stream;
        
        try {
            this.saxParser = getParser();
        } catch (final SAXException e) {
            ConcurrentLog.logException(e);
            throw new IOException(e.getMessage());
        }
    }
    
    @Override
    public void run() {
        // test the syntax of the stream by reading parts of the beginning
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(this.inputStream, StandardCharsets.UTF_8));
            if (isSolrDump()) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("<doc>")) continue;
                    try {
                        NamedList<Object> nl = new XMLResponseParser().processResponse(new StringReader("<result>" + line + "</result>")); // 
                        SolrDocument doc = (SolrDocument) nl.iterator().next().getValue();

                        // check if url is in accepted domain
                        String u = (String) doc.getFieldValue("sku");
                        if (u != null) {
                            try {
                                DigestURL url = new DigestURL(u);
                                final String urlRejectReason = this.crawlStacker.urlInAcceptedDomain(url);
                                if ( urlRejectReason == null ) {
                                    // convert SolrDocument to SolrInputDocument
                                    this.surrogates.put(this.configuration.toSolrInputDocument(doc));
                                }
                            } catch (MalformedURLException e) {
                            }
                        }
                    } catch (Throwable ee) {
                        // bad line
                    }
                }
            } else {
                final InputSource inputSource = new InputSource(br);
                inputSource.setEncoding(StandardCharsets.UTF_8.name());
                this.saxParser.parse(inputSource, this);
            }
        } catch (final SAXParseException e) {
            ConcurrentLog.logException(e);
        } catch (final SAXException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
            for (int i = 0; i < this.concurrency; i++) {
                try {
                    this.surrogates.put(POISON_DOCUMENT);
                } catch (final InterruptedException e1) {
                    ConcurrentLog.logException(e1);
                }
            }
            try {
                this.inputStream.close();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }
    
    /**
     * Check for format string in responseHeader "yacy.index.export.solr.xml"
     * (introduced v1.92/9188 2017-04-30) or guess format by existing "<response>"
     * and "<result>" or "<doc>" tag in the first {@value #PUSHBACK_SIZE} characters.
     *
     * @return true when inputStream is likely to contain a rich and full-text Solr xml data dump (see IndexExport_p.html)
     */
	private boolean isSolrDump() {
		boolean res = false;
		byte[] b = new byte[PUSHBACK_SIZE];
		int nbRead = -1;
		try {
                    nbRead = this.inputStream.read(b);
                    if (nbRead > 0) {
                        String s = new String(b, 0, nbRead, StandardCharsets.UTF_8);
                        if (s.contains("format=\"yacy.index.export.solr.xml\"")) {
                            res = true;
                        } else if ((s.contains("<response>") && s.contains("<result>")) || s.startsWith("<doc>")) {
                            res = true;
                        }
                    }
		} catch (IOException e) {
			ConcurrentLog.logException(e);
		} finally {
			if (nbRead > 0) {
				try {
					this.inputStream.unread(b, 0, nbRead);
				} catch (IOException e2) {
					ConcurrentLog.logException(e2);
				}
			}
		}
		return res;
	}
    
    @Override
    public void startElement(final String uri, final String name, String tag, final Attributes atts) throws SAXException {
        if (tag == null) return;
        tag = tag.toLowerCase(Locale.ROOT);
        if ("record".equals(tag) || "document".equals(tag) || "doc".equals(tag)) {
            this.dcEntry = new DCEntry();
        } else if ("element".equals(tag) || "str".equals(tag) || "int".equals(tag) || "bool".equals(tag) || "long".equals(tag)) {
            this.elementName = atts.getValue("name");
            this.parsingValue = true;
        } else if ("value".equals(tag)) {
            this.buffer.setLength(0);
            this.parsingValue = true;
        } else if (tag.startsWith("dc:") || tag.startsWith("geo:") || tag.startsWith("md:")) {
            // parse dublin core attribute
            this.elementName = tag;
            this.parsingValue = true;
        }
    }

    @Override
    public void endElement(final String uri, final String name, String tag) {
        if (tag == null) return;
        tag = tag.toLowerCase(Locale.ROOT);
        if ("record".equals(tag) || "document".equals(tag) || "doc".equals(tag)) {
            try {
                // check if url is in accepted domain
                final String urlRejectReason = this.crawlStacker.urlInAcceptedDomain(this.dcEntry.getIdentifier(true));
                if ( urlRejectReason == null ) {
                    // DCEntry can not be converted to SolrInputDocument as DC schema has nothing to do with Solr collection schema
                    this.surrogates.put(this.dcEntry);
                }
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            } finally {
                this.dcEntry = null;
                this.buffer.setLength(0);
                this.parsingValue = false;
            }
        } else if ("element".equals(tag)) {
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if ("str".equals(tag) || "int".equals(tag) || "bool".equals(tag) || "long".equals(tag)){
            final String value = buffer.toString().trim();
            if (this.elementName != null) {
                this.dcEntry.getMap().put(this.elementName, new String[]{value});
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if ("value".equals(tag)) {
            final String value = buffer.toString().trim();
            if (this.elementName != null) {
                this.dcEntry.getMap().put(this.elementName, new String[]{value});
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        } else if (tag.startsWith("dc:") || tag.startsWith("geo:") || tag.startsWith("md:")) {
            final String value = buffer.toString().trim();
            if (this.elementName != null && tag.equals(this.elementName)) {
                Map<String,String[]> map = this.dcEntry.getMap();
                String[] oldcontent = map.get(this.elementName);
                if (oldcontent == null || oldcontent.length == 0) {
                    map.put(this.elementName, new String[]{value});
                } else {
                    String[] newcontent = new String[oldcontent.length + 1];
                    System.arraycopy(oldcontent, 0, newcontent, 0, oldcontent.length);
                    newcontent[oldcontent.length] = value;
                    map.put(this.elementName, newcontent);
                }
            }
            this.buffer.setLength(0);
            this.parsingValue = false;
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length) {
        if (parsingValue) {
            buffer.append(ch, start, length);
        }
    }

    public Object take() {
        try {
            return this.surrogates.take();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

}
