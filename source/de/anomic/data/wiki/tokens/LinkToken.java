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
	
	@Override
	protected boolean parse() {
		StringBuilder sb = new StringBuilder();
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
