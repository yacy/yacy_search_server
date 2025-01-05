// FileCrawlStarterTask.java
// ---------------------------
// SPDX-FileCopyrightText: 2016 luccioman; https://github.com/luccioman
// SPDX-License-Identifier: GPL-2.0-or-later
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.crawler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.util.FileUtils;

/**
 * A task used trigger crawl starts from a file (HTML or any other supported
 * text file) containing anchor links. It does not wait full file parsing before
 * sending anchor links to the crawl stacker and thus can handle files with many
 * links.
 * 
 * @author luccioman
 */
public class FileCrawlStarterTask extends Thread {

	private final static ConcurrentLog log = new ConcurrentLog(FileCrawlStarterTask.class.getSimpleName());

	/** A text file containing crawl start links */
	private File crawlingFile;
	/** Alternative to crawlingFile : holds file content */
	private String crawlingFileContent;
	/** Content scraper that will scrape file content */
	private ContentScraper scraper;
	/** Active crawl profile */
	private CrawlProfile profile;
	/**
	 * CrawlStacker instance : will receive anchor links used as crawl starting
	 * points
	 */
	private CrawlStacker crawlStacker;
	/** Hash of the peer initiating the crawl */
	private final byte[] initiatorHash;

	/**
	 * Constructor
	 * 
	 * @param crawlingFile
	 *            a text file containing crawl start links (alternatively,
	 *            crawlingFileContent parameter can be used)
	 * @param crawlingFileContent
	 *            content of a text file containing crawl start links
	 *            (alternatively, crawlingFile parameter can be used)
	 * @param scraper
	 *            ContentScraper instance used to scrape links from the file
	 * @param profile
	 *            active crawl profile (must not be null)
	 * @param crawlStacker
	 *            CrawlStacker instance : will receive anchor links used as
	 *            crawl starting points (must not be null)
	 * @param initiatorHash
	 *            Hash of the peer initiating the crawl
	 * @throws IllegalArgumentException
	 *             when one of the required parameters is null
	 * @throws IOException
	 *             when crawlingFileContent is null and crawlingFile does not
	 *             exists or can not be read
	 */
	public FileCrawlStarterTask(final File crawlingFile, final String crawlingFileContent, final ContentScraper scraper,
			final CrawlProfile profile, final CrawlStacker crawlStacker, final byte[] initiatorHash)
			throws IllegalArgumentException, FileNotFoundException, IOException {
		super(FileCrawlStarterTask.class.getSimpleName());
		if (crawlingFile == null && crawlingFileContent == null) {
			throw new IllegalArgumentException(
					"At least one of crawlingFile or crawlingFileContent parameter must not be null");
		}
		if ((crawlingFileContent == null || crawlingFileContent.isEmpty()) && crawlingFile != null) {
			/*
			 * Lets check now if the crawlingFile exists and can be read so the
			 * error can be synchronously reported to the caller
			 */
			if (!crawlingFile.exists()) {
				throw new FileNotFoundException(crawlingFile.getAbsolutePath() + " does not exists");
			}
			if (!crawlingFile.isFile()) {
				throw new FileNotFoundException(crawlingFile.getAbsolutePath() + " exists but is not a regular file");
			}
			if (!crawlingFile.canRead()) {
				throw new IOException("Can not read : " + crawlingFile.getAbsolutePath());
			}
		}
		this.crawlingFile = crawlingFile;
		this.crawlingFileContent = crawlingFileContent;
		if (scraper == null) {
			throw new IllegalArgumentException("scraper parameter must not be null");
		}
		this.scraper = scraper;
		if (profile == null) {
			throw new IllegalArgumentException("profile parameter must not be null");
		}
		this.profile = profile;
		if (crawlStacker == null) {
			throw new IllegalArgumentException("crawlStacker parameter must not be null");
		}
		this.crawlStacker = crawlStacker;
		if (initiatorHash == null) {
			throw new IllegalArgumentException("initiatorHash parameter must not be null");
		}
		this.initiatorHash = initiatorHash;
	}

	/**
	 * Run the content scraping on the file and once detected push any anchor
	 * link to the crawlStacker.
	 */
	@Override
	public void run() {
		/*
		 * This is the listener which makes possible the push of links to the
		 * crawl stacker without waiting the complete end of content scraping
		 */
		CrawlStarterFromScraper anchorListener = new CrawlStarterFromScraper(this.crawlStacker, this.initiatorHash,
				this.profile, true);
		this.scraper.registerHtmlFilterEventListener(anchorListener);

		final Writer writer = new TransformerWriter(null, null, this.scraper, false);
		FileInputStream inStream = null;

		try {
			if (this.crawlingFile != null && this.crawlingFile.exists()) {
				inStream = new FileInputStream(this.crawlingFile);
				FileUtils.copy(inStream, writer);
			} else {
				FileUtils.copy(this.crawlingFileContent, writer);
			}
			writer.close();
		} catch (IOException e) {
			log.severe("Error parsing the crawlingFile " + this.crawlingFile.getAbsolutePath(), e);
		} catch (IllegalCrawlProfileException e) {
			/* We should get here when the crawl is stopped manually before termination */
			log.info("Parsing crawlingFile " + this.crawlingFile.getAbsolutePath() + " terminated. Crawl profile "
					+ this.profile.handle() + " is no more active.");
		} catch (Exception e) {
			/*
			 * Other errors are likely to occur when the crawl is interrupted :
			 * still log this at warning level to avoid polluting regular error
			 * log level
			 */
			log.warn("Error parsing the crawlingFile " + this.crawlingFile.getAbsolutePath(), e);
		} finally {
			if (inStream != null) {
				try {
					inStream.close();
				} catch (IOException e) {
					log.warn("Could not close crawlingFile : " + this.crawlingFile.getAbsolutePath());
				}
			}
		}
	}

}
