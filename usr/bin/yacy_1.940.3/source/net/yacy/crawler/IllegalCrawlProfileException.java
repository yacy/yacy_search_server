// IllegalCrawlProfileException.java
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

import net.yacy.crawler.data.CrawlProfile;

/**
 * Exception used to signal that an operation is trying to use an inactive or deleted {@link CrawlProfile}.
 * @author luccioman
 *
 */
public class IllegalCrawlProfileException extends RuntimeException {

	/** Generated serial ID */
	private static final long serialVersionUID = 8482302347823257958L;
	
	/**
	 * Default constructor : use a generic message
	 */
	public IllegalCrawlProfileException() {
		super("Crawl profile can not be used");
	}

	/**
	 * @param message detail message
	 */
	public IllegalCrawlProfileException(String message) {
		super(message);
	}

}
