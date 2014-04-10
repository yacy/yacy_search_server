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

    public boolean isTag0(String tag);

    public boolean isTag1(String tag);

    public void scrapeText(char[] text, String insideTag);

    public void scrapeTag0(ContentScraper.Tag tag);

    public void scrapeTag1(ContentScraper.Tag tag);

    public void scrapeComment(final char[] comment);

    public void finish();

    public void close();

    public void registerHtmlFilterEventListener(ScraperListener listener);

    public void deregisterHtmlFilterEventListener(ScraperListener listener);
}
