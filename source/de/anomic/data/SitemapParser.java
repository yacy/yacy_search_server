//SitemapParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2007
//
//this file is contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.data;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.http.httpc;
import de.anomic.http.httpdByteCountInputStream;
import de.anomic.index.indexURLEntry;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;

/**
 * Class to parse a sitemap file.<br>
 * An example sitemap file is depicted below:<br>
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"&gt;
 *    &lt;url&gt;
 *       &lt;loc&gt;http://www.example.com/&lt;/loc&gt;
 *       &lt;lastmod&gt;2005-01-01&lt;/lastmod&gt;
 *       &lt;changefreq&gt;monthly&lt;/changefreq&gt;
 *       &lt;priority&gt;0.8&lt;/priority&gt;
 *    &lt;/url&gt;
 * &lt;/urlset&gt; 
 * </pre>
 * 
 * A real example can be found here: http://www.xt-service.de/sitemap.xml
 * An example robots.txt containing a sitemap URL: http://notepad.emaillink.de/robots.txt
 * 
 * @see http://www.sitemaps.org/protocol.php
 * @see https://www.google.com/webmasters/tools/docs/en/protocol.html
 */
public class SitemapParser extends DefaultHandler {
	public static final String XMLNS_SITEMAPS_ORG = "http://www.sitemaps.org/schemas/sitemap/0.9";
	public static final String XMLNS_SITEMAPS_GOOGLE = "http://www.google.com/schemas/sitemap/0.84";

	public static final String SITEMAP_XMLNS = "xmlns";
	public static final String SITEMAP_URLSET = "urlset";
	public static final String SITEMAP_URL = "url";
	public static final String SITEMAP_URL_LOC = "loc";
	public static final String SITEMAP_URL_LASTMOD = "lastmod";
	public static final String SITEMAP_URL_CHANGEFREQ = "changefreq";
	public static final String SITEMAP_URL_PRIORITY = "priority";
	
	// TODO: which local settings should we use here?
	private final SimpleDateFormat dateFormater = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
	
	
	/**
	 * The crawling profile used to parse the URLs contained in the sitemap file
	 */
	private plasmaCrawlProfile.entry crawlingProfile = null;
	
	/**
	 * Reference to the plasmaswitchboard.
	 */
	private plasmaSwitchboard switchboard = null;
	
	/**
	 * Name of the current XML element
	 */
	private String currentElement = null;
	
	/**
	 * A special stream to count how many bytes were processed so far
	 */
	private httpdByteCountInputStream counterStream;
	
	/**
	 * The total length of the sitemap file
	 */
	private long contentLength;
	
	/**
	 * The amount of urls processes so far
	 */
	private int urlCounter = 0;
	
	private serverLog logger = new serverLog("SITEMAP");
	
	private URL siteMapURL = null;
	private String nextURL = null;
	private Date lastMod = null;
	
	
	public SitemapParser(plasmaSwitchboard sb, URL sitemap, plasmaCrawlProfile.entry theCrawlingProfile) {
		if (sb == null) throw new NullPointerException("The switchboard must not be null");
		this.switchboard = sb;
		this.siteMapURL = sitemap;		
		
		if (theCrawlingProfile == null) {
			this.crawlingProfile = createProfile(this.siteMapURL.getHost(),this.siteMapURL.toString());
		} else {
			this.crawlingProfile = theCrawlingProfile;
		}
	}
	
	public void parse() {
		// download document
		httpc remote = null;
		try {
			remote = httpc.getInstance(
					this.siteMapURL.getHost(),
					this.siteMapURL.getHost(),
					this.siteMapURL.getPort(),
					5000,
					this.siteMapURL.getProtocol().equalsIgnoreCase("https"));
			
			httpc.response res = remote.GET(this.siteMapURL.getFile(), null);
			if (res.statusCode != 200) {
				throw new Exception("Unable to download the sitemap file. Server returned status: " + res.status);
			}
			
			// getting some metadata
			String contentMimeType = res.responseHeader.mime();
			this.contentLength = res.responseHeader.contentLength();
			
			InputStream contentStream = res.getContentInputStream();
			if ((contentMimeType != null) && (
				 contentMimeType.equals("application/x-gzip") ||
				 contentMimeType.equals("application/gzip")			
			)) {
				contentStream =  new GZIPInputStream(contentStream);
			}
			
			this.counterStream = new httpdByteCountInputStream(contentStream,null);
			
			// parse it
			SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(this.counterStream, this);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (remote != null) try { httpc.returnInstance(remote); } catch (Exception e) {}
		}
	}
	
	public long getTotalLength() {
		return this.contentLength;
	}
	
	public long getProcessedLength() {
		return (this.counterStream==null)?0:this.counterStream.getCount();
	}
	
	public long getUrlcount() {
		return this.urlCounter;
	}
	
	public void startDocument() throws SAXException {
		// TODO: create a new crawling profile
	}
	
	/**
	 * @param localName local name
	 * @param qName qualified name
	 * @see DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
			String namespaceURI,
			String localName,
			String qName,
			Attributes attrs) throws SAXException {
		this.currentElement = qName;
		
		// testing if the namespace is known
		if (qName.equalsIgnoreCase(SITEMAP_URLSET)) {
			String namespace = attrs.getValue(SITEMAP_XMLNS);
			if ((namespace == null) ||
				((!namespace.equals(XMLNS_SITEMAPS_ORG)) &&
				 (!namespace.equals(XMLNS_SITEMAPS_GOOGLE)))
			) throw new SAXException("Unknown sitemap namespace: " + namespace);				
		} 
	}
	
	/**
	 * @param localName local name
	 * @param qName qualified name
	 * @see DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement( String namespaceURI,
			String localName,
			String qName ) {
		this.currentElement = "";
		
		if (qName.equalsIgnoreCase(SITEMAP_URL)) {
			if (this.nextURL == null) return;
			
			// get the url hash
			String nexturlhash = plasmaURL.urlHash(this.nextURL);
			
			// check if the url is known and needs to be recrawled
			if (this.lastMod != null) {
				String dbocc = this.switchboard.urlExists(nexturlhash);
				if ((dbocc != null) && (dbocc.equalsIgnoreCase("loaded"))) {
					// the url was already loaded. we need to check the date
					indexURLEntry oldEntry = this.switchboard.wordIndex.loadedURL.load(nexturlhash, null);
					if (oldEntry != null) {
						Date modDate = oldEntry.moddate();
						// check if modDate is null
						if (modDate.after(this.lastMod)) return;
					}		        
				}
			}
			
			
			// URL needs to crawled
			String error = null;
			try {
				error = this.switchboard.sbStackCrawlThread.stackCrawl(
						this.nextURL,
						this.siteMapURL.toString(),
						yacyCore.seedDB.mySeed.hash,
						this.nextURL, 
						new Date(),
						0,
						this.crawlingProfile
				);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if (error != null) {
				try {
					plasmaCrawlZURL.Entry ee = this.switchboard.errorURL.newEntry(new URL(this.nextURL), error);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				this.urlCounter++;
			}
		}		
	}
	
	public void characters(char[] buf, int offset, int len) throws SAXException {
		if (this.currentElement.equalsIgnoreCase(SITEMAP_URL_LOC)) {
			// TODO: we need to decode the URL here
			this.nextURL =(new String(buf,offset,len)).trim();
			if (!this.nextURL.startsWith("http")) {
				System.out.println(this.nextURL);
			}
		} 
//		else if (this.currentElement.equalsIgnoreCase(SITEMAP_URL_LASTMOD)) {
//			try {
//				this.lastMod = this.dateFormater.parse(new String(buf,offset,len));
//			} catch (ParseException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
	}
	
	private plasmaCrawlProfile.entry createProfile(String domainName, String sitemapURL) {
        return this.switchboard.profiles.newEntry(
        		domainName, 
        		sitemapURL,
        		// crawlingFilter
        		".*", ".*",
        		// Depth
        		0, 0,
        		// force recrawling
        		0,
        		// disable Auto-Dom-Filter
        		-1, -1,
        		// allow crawling of dynamic URLs
        		true,
        		// index text + media
        		true, true,
        		// don't store downloaded pages to Web Cache
        		false,
        		// store to TX cache
        		true,
        		// remote Indexing disabled
        		false, 
        		// exclude stop-words
        		true, true, true 
        );		
	}
}

