// TableToken.java 
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

public class TableToken extends AbstractToken {
	
	private static final Pattern[] pattern = new Pattern[] {
		Pattern.compile(
				"\\{\\|" +					// "{|"
				"([^\n]|\n\\|[|-])*\n" +	// new line must start with "||" or "|-"
				"\\|\\}")					// "|}"
	};
	private static final String[] blockElementNames = new String[] { "table", "tr", "td" };
	
	protected void parse() {
		final String[] t = text.split("\n");
		String[] tds;
		final StringBuffer sb = new StringBuffer();
		sb.append("<table");
		if (t[0].length() > 2) sb.append(parseTableProperties(t[0].substring(2)));
		sb.append(">\n");
		boolean trOpen = false;
		for (int i=1, j, a; i<t.length-1; i++) {
			if (t[i].startsWith("|-")) {
				if (trOpen) sb.append("\t</tr>\n");
				trOpen = (i < t.length - 2);
				if (trOpen) sb.append("\t<tr>\n");
			} else if (t[i].startsWith("||")) {
				tds = t[i].split("\\|\\|");
				for (j=0; j<tds.length; j++) {
					if (tds[j].length() > (a = tds[j].indexOf('|')) + 1) {	// don't print empty td's
						sb.append("\t\t<td");
						if (a > -1) sb.append(parseTableProperties(tds[j].substring(0, a)));
						sb.append(">").append(tds[j].substring(a + 1)).append("</td>\n");
					}
				}
			}
		}
		if (trOpen) sb.append("\t</tr>\n");
		this.markup =  new String(sb.append("</table>"));
		this.parsed = true;
	}
	
    // from de.anomic.data.wikiCode.java.parseTableProperties, modified by [FB]
	private static final String[] tps = { "rowspan", "colspan", "vspace", "hspace", "cellspacing", "cellpadding", "border" };
    private static final HashMap<String, String[]> ps = new HashMap<String, String[]>();
    static {
        Arrays.sort(tps);
        String[] array;
        Arrays.sort(array = new String[] { "void", "above", "below", "hsides", "lhs", "rhs", "vsides", "box", "border" });
        ps.put("frame", array);
        Arrays.sort(array = new String[] { "none", "groups", "rows", "cols", "all" });
    	ps.put("rules", array);
        Arrays.sort(array = new String[] { "top", "middle", "bottom", "baseline" });
    	ps.put("valign", array);
        Arrays.sort(array = new String[] { "left", "right", "center" });
    	ps.put("align", array);
    }
    
	// contributed by [MN]
    /** This method takes possible table properties and tests if they are valid.
      * Valid in this case means if they are a property for the table, tr or td
      * tag as stated in the HTML Pocket Reference by Jennifer Niederst (1st edition)
      * The method is important to avoid XSS attacks on the wiki via table properties.
      * @param properties A string that may contain several table properties and/or junk.
      * @return A string that only contains table properties.
      */
    private static StringBuffer parseTableProperties(final String properties) {
        final String[] values = properties.replaceAll("&quot;", "").split("[= ]");     //splitting the string at = and blanks
        final StringBuffer sb = new StringBuffer(properties.length());
        String key, value;
        String[] posVals;
        final int numberofvalues = values.length;
        for (int i=0; i<numberofvalues; i++) {
        	key = values[i].trim();
            if (key.equals("nowrap")) {
                addPair("nowrap", "nowrap", sb);
            } else if (i + 1 < numberofvalues) {
        		value = values[++i].trim();
        		if (
        				(key.equals("summary")) ||
        				(key.equals("bgcolor") && value.matches("#{0,1}[0-9a-fA-F]{1,6}|[a-zA-Z]{3,}")) ||
        				((key.equals("width") || key.equals("height")) && value.matches("\\d+%{0,1}")) ||
                        ((posVals = ps.get(key)) != null && Arrays.binarySearch(posVals, value) >= 0) ||
        				(Arrays.binarySearch(tps, key) >= 0 && value.matches("\\d+"))
        		) {
                	addPair(key, value, sb);
        		}
        	}
        }
        return sb;
    }
    
    private static StringBuffer addPair(final String key, final String value, final StringBuffer sb) {
    	return sb.append(" ").append(key).append("=\"").append(value).append("\"");
    }
	
	public Pattern[] getRegex() { return pattern; }
	public String[] getBlockElementNames() { return blockElementNames; }
	
	public boolean setText(final String text, final int patternNr) {
		this.text = text;
		this.parsed = false;
		this.markup = null;
		return true;
	}
}
