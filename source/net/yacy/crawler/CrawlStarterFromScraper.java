// CrawlStarterFromScraper.java
// ---------------------------
// SPDX-FileCopyrightText: 2016 2017 luccioman; https://github.com/luccioman
// SPDX-FileCopyrightText: 2016 2017 Apply55gx; https://github.com/Apply55gx
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.parser.html.ContentScraperListener;

/**
 * Enqueue an entry to the crawlStacker each time an anchor is discovered by the ContentScraper
 * @author luccioman
 *
 */
public class CrawlStarterFromScraper implements ContentScraperListener {
	
	private final static ConcurrentLog log = new ConcurrentLog(CrawlStarterFromScraper.class.getSimpleName());
	
	/** CrawlStacker instance : will receive anchor links used as crawl starting points */
	private CrawlStacker crawlStacker;
	/** Hash of the peer initiating the crawl */
	private final byte[] initiatorHash;
	/** Active crawl profile */
	private CrawlProfile profile;
    /** Specify whether old indexed entries should be replaced */
    private final boolean replace;
	
    /**
     * Constructor 
     * @param crawlStacker CrawlStacker instance : will receive anchor links used as crawl starting points
     * @param initiatorHash Hash of the peer initiating the crawl (must not be null)
     * @param profile active crawl profile (must not be null)
     * @param replace Specify whether old indexed entries should be replaced
     * @throws IllegalArgumentException when a required parameter is null
     */
	public CrawlStarterFromScraper(final CrawlStacker crawlStacker, final byte[] initiatorHash,
                                   final CrawlProfile profile,
                                   final boolean replace) {
		if(crawlStacker == null) {
			throw new IllegalArgumentException("crawlStacker parameter must not be null");
		}
		this.crawlStacker = crawlStacker;
		if(initiatorHash == null) {
			throw new IllegalArgumentException("initiatorHash parameter must not be null");
		}
		this.initiatorHash = initiatorHash;
		this.replace = replace;
		if(profile == null) {
			throw new IllegalArgumentException("profile parameter must not be null");
		}
		this.profile = profile;
	}

	@Override
	public void scrapeTag0(String tagname, Properties tagopts) {
		// Nothing to do on this event
	}

	@Override
	public void scrapeTag1(String tagname, Properties tagopts, char[] text) {
		// Nothing to do on this event
	}

	@Override
	public void anchorAdded(String anchorURL) {
		List<AnchorURL> urls = new ArrayList<>();
		try {
			urls.add(new AnchorURL(anchorURL));
			this.crawlStacker.enqueueEntries(this.initiatorHash, this.profile.handle(), urls, this.replace, this.profile.timezoneOffset());
		} catch (MalformedURLException e) {
			log.warn("Malformed URL : " + anchorURL);
		}
	}
	
}
