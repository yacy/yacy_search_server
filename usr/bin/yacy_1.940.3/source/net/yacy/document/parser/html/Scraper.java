// Scraper.java
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

public interface Scraper {

    /**
     * @param tag
     *            a tag name
     * @return true when the tag name belongs to the first category of tags
     *         according to the Scraper implementation, and is therefore candidate
     *         for processing by
     *         {@link #scrapeTag0(net.yacy.document.parser.html.ContentScraper.Tag)}
     *         implementation
     */
    public boolean isTag0(String tag);

    /**
     * @param tag
     *            a tag name
     * @return true when the tag name belongs to the second category of tags
     *         according to the Scraper implementation, and is therefore candidate
     *         for processing by
     *         {@link #scrapeTag0(net.yacy.document.parser.html.ContentScraper.Tag)}
     *         implementation
     */
    public boolean isTag1(String tag);

    /**
     * Process plain text
     * @param text text to process
     * @param insideTag the eventual direct parent tag. May be null.
     */
    public void scrapeText(char[] text, ContentScraper.Tag insideTag);

    /**
     * Process a tag belonging to the first category of tags according to the Scraper implementation
     * @param tag a parsed tag
     */
    public void scrapeTag0(ContentScraper.Tag tag);

    /**
     * Process a tag belonging to the second category of tags according to the Scraper implementation
     * @param tag a parsed tag
     */
    public void scrapeTag1(ContentScraper.Tag tag);
    
    /**
     * Processing applied to any kind of tag opening.
     * @param tag a parsed tag
     */
    public void scrapeAnyTagOpening(ContentScraper.Tag tag);
    
    /**
     * @param tag
     *            a parsed tag
     * @param parentTag the eventual parent tag
     * @return true when the tag should be ignored according to the scraper
     *         implementation rules
     */
    public TagValency tagValency(final ContentScraper.Tag tag, final ContentScraper.Tag parentTag);
    
    public TagValency defaultValency();

    public void scrapeComment(final char[] comment);

    public void finish();

    public void close();

    public void registerHtmlFilterEventListener(ScraperListener listener);

    public void deregisterHtmlFilterEventListener(ScraperListener listener);
}
