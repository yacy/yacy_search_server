// LinkToken.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brauße
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.data.wiki.tokens;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkToken extends AbstractToken {
	
	private static final int IMG = 0;
	private static final int INT = 1;
	private static final int EXT = 2;
	
	private static final Pattern imgPattern = Pattern.compile(
			"\\[\\[" +											// begin
			"(Image:([^\\]|]|\\][^\\]])*)" +					// "Image:" + URL
			"(" +												// <optional>
				"(\\|(bottom|left|center|right|middle|top))?" +	// optional align
				"(\\|(([^\\]]|\\][^\\]])*))" +					// description
			")?" +												// </optional>
			"\\]\\]");											// end
	
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
		imgPattern, intPattern, extPattern };
	
	private final String localhost;
	private final String wikiPath;
	private int patternNr = 0;
	
	public LinkToken(String localhost, String wikiPath) {
		this.localhost = localhost;
		this.wikiPath = wikiPath;
	}
	
	protected boolean parse() {
		StringBuffer sb = new StringBuffer();
		Matcher m;
		switch (this.patternNr) {
			case IMG:
				m = imgPattern.matcher(this.text);
				if (!m.find()) return false;
				sb.append("<img src=\"").append(formatLink(m.group(1))).append("\"");
				if (m.group(5) != null) sb.append(" align=\"").append(m.group(5)).append("\"");
				if (m.group(7) != null) sb.append(" alt=\"").append(m.group(7)).append("\"");
				sb.append(" />");
				break;
				
			case INT:
				m = intPattern.matcher(this.text);
				if (!m.find()) return false;
				sb.append("<a href=\"").append("http://").append(this.localhost)
						.append("/").append(this.wikiPath).append(m.group(1))
						.append("\"");
				if (m.group(4) != null) sb.append(" title=\"").append(m.group(3)).append("\"");
				sb.append(">");
				if (m.group(4) != null) sb.append(m.group(4)); else sb.append(m.group(1));
				sb.append("</a>");
				break;
				
			case EXT:
				m = extPattern.matcher(this.text);
				if (!m.find()) return false;
				sb.append("<a href=\"").append(formatLink(m.group(1))).append("\"");
				if (m.group(3) != null) sb.append(" title=\"").append(m.group(3)).append("\"");
				sb.append(">");
				if (m.group(3) != null) sb.append(m.group(3)); else sb.append(m.group(1));
				sb.append("</a>");
				break;
				
			default: return false;
		}
		this.parsed = true;
		this.markup = new String(sb);
		return true;
	}
	
	private String formatLink(String link) {
		if (link.indexOf("://") == -1) {		// DATA/HTDOCS-link
			return "http://" + this.localhost + "/" + link;
		} else {								// 'normal' link
			return link;
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
