// ScraperListener.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser.html;

import java.util.Properties;

/**
 * Listener interface to Scraper events
 */
public interface ScraperListener extends java.util.EventListener {
	/**
	 * Triggered by {@link Scraper#scrapeTag0(net.yacy.document.parser.html.ContentScraper.Tag)} implementations
	 * @param tagname tag name
	 * @param tagopts tag attributes
	 */
    public void scrapeTag0(String tagname, Properties tagopts);
    
	/**
	 * Triggered by {@link Scraper#scrapeTag1(net.yacy.document.parser.html.ContentScraper.Tag)} implementations
	 * @param tagname tag name
	 * @param tagopts tag attributes
	 * @param text tag content text
	 */
    public void scrapeTag1(String tagname, Properties tagopts, char[] text);
}
