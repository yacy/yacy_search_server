// LinkToken.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brausze
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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

package de.anomic.data.wiki.tokens;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.data.bookmarksDB;
import de.anomic.data.bookmarksDB.Bookmark;
import de.anomic.data.bookmarksDB.Tag;
import de.anomic.data.wiki.wikiParserException;
import de.anomic.plasma.plasmaSwitchboard;

public class LinkToken extends AbstractToken {
	
	private static final int IMG = 0;
    private static final int BKM = 1;
	private static final int INT = 2;
	private static final int EXT = 3;
	
	private static final Pattern imgPattern = Pattern.compile(
			"\\[\\[" +											// begin
			"(Image:([^\\]|]|\\][^\\]])*)" +					// "Image:" + URL
			"(" +												// <optional>
				"(\\|(bottom|left|center|right|middle|top))?" +	// optional align
				"(\\|(([^\\]]|\\][^\\]])*))" +					// description
			")?" +												// </optional>
			"\\]\\]");											// end
    
    private static final Pattern bkmPattern = Pattern.compile(
            "\\[\\[" +                                          // begin
            "(Bookmark:([^\\]|]|\\][^\\]])*)" +                 // "Bookmark:" + URL
            "(\\|(([^\\]]|\\][^\\]])*?))?" +                    // optional description
            "\\]\\]");                                          // end 
	
	private static final Pattern intPattern = Pattern.compile(
			"\\[\\[" +											// begin
			"(([^\\]|]|\\][^\\]])*?)" +							// wiki-page
			"(\\|(([^\\]]|\\][^\\]])*?))?" +					// optional desciption
			"\\]\\]");											// end
	
	private static final Pattern extPattern = Pattern.compile(
			"\\[" +												// begin
			"([^\\] ]*)" +										// URL
			"( ([^\\]]*))?" +									// optional description
			"\\]");												// end
	
	private static final Pattern[] patterns = new Pattern[] {
		imgPattern, bkmPattern, intPattern, extPattern };
	
	private final String localhost;
	private final String wikiPath;
    private final plasmaSwitchboard sb;
	private int patternNr = 0;
	
	public LinkToken(String localhost, String wikiPath, plasmaSwitchboard sb) {
		this.localhost = localhost;
		this.wikiPath = wikiPath;
        this.sb = sb;
	}
	
	protected void parse() throws wikiParserException {
		StringBuffer sb = new StringBuffer();
        if (this.patternNr < 0 || this.patternNr >= patterns.length)
            throw new wikiParserException("patternNr was not set correctly: " + this.patternNr);
		Matcher m = patterns[this.patternNr].matcher(this.text);
        if (!m.find())
            throw new wikiParserException("Didn't find match for: (" + this.patternNr + ") " + this.text);
        
        switch (this.patternNr) {
			case IMG:
				sb.append("<img src=\"").append(formatHref(m.group(1).substring(6))).append("\"");
				if (m.group(5) != null) sb.append(" align=\"").append(m.group(5)).append("\"");
                sb.append(" alt=\"").append((m.group(7) == null) ? formatHref(m.group(1).substring(6)) : m.group(7)).append("\"");
				sb.append(" />");
				break;
                
            case BKM:
                Link[] links = getLinksFromBookmarkTag(m.group(2));
                if (links == null) {
                    sb.append("<span class=\"error\">Couldn't find Bookmark-Tag '").append(m.group(2)).append("'.</span>");
                } else {
                    appendLinks(links, sb);
                }
                break;
				
			case INT:
				sb.append(new Link(
                                "http://" + this.localhost + "/" + this.wikiPath + m.group(1),
                                m.group(4),
                                (m.group(4) == null) ? m.group(1) : m.group(4)
                        ).toString());
				break;
				
			case EXT:
				sb.append(new Link(
                                m.group(1),
                                m.group(3),
                                (m.group(3) == null) ? m.group(1) : m.group(3)
                        ).toString());
				break;
		}
		this.parsed = true;
		this.markup = new String(sb);
	}
    
    private String formatHref(String link) {
        if (link.indexOf("://") == -1) {        // DATA/HTDOCS-link
            return "http://" + this.localhost + "/share/" + link;
        } else {                                // 'normal' link
            return link;
        }
    }
    
    private StringBuffer appendLinks(Link[] links, StringBuffer sb) {
        for (int i=0; i<links.length; i++)
            sb.append(links[i].toString());
        return sb;
    }
    
    private Link[] getLinksFromBookmarkTag(String tagName) {
        Tag tag = this.sb.bookmarksDB.getTag(bookmarksDB.tagHash(tagName));
        if (tag == null) return null;
        ArrayList<Link> r = new ArrayList<Link>();
        Iterator<String> it = tag.getUrlHashes().iterator();
        String hash;
        Bookmark bm;
        while (it.hasNext())
            if ((hash = it.next()) != null)
                if ((bm = this.sb.bookmarksDB.getBookmark(hash)) != null)
                    r.add(new Link(bm.getUrl(), bm.getTitle(), bm.getDescription()));
        return r.toArray(new Link[r.size()]);
    }
    
    private static class Link {
        
        private final String href;
        private final String title;
        private final String desc;
        
        public Link(String href, String title, String desc) {
            this.href = href;
            this.title = title;
            this.desc = desc;
        }
        
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("<a href=\"").append(this.href).append("\"");
            if (this.title != null) sb.append(" title=\"").append(this.title).append("\"");
            sb.append(">");
            if (this.desc == null) sb.append(this.href); else sb.append(this.desc);
            sb.append("</a>");
            return new String(sb);
        }
    }
	
	public String[] getBlockElementNames() { return null; }
	public Pattern[] getRegex() { return patterns; }
	
	public boolean setText(String text, int patternNr) {
		this.text = text;
		this.patternNr = patternNr;
		this.parsed = false;
		if (text == null) { this.markup = null; this.patternNr = -1; }
		return true;
	}
}
