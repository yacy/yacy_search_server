// ListToken.java 
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
import java.util.regex.Pattern;

public class ListToken extends AbstractToken {
	
	protected final String[] blockElements;
	
	protected final char firstChar;
	protected final String listBlockElement;
	protected final String listElement;
	protected final boolean recursion;
	protected final Pattern[] pattern;
	
	protected int aktline = 0;
	
	public ListToken(char firstChar, String listBlockElement) {
		this.firstChar = firstChar;
		this.listBlockElement = listBlockElement;
		this.listElement = "li";
		this.recursion = true;
		this.pattern = new Pattern[] { Pattern.compile("^[" + firstChar + "]([^\n]|\n[" + firstChar + "])*", Pattern.MULTILINE) };
		ArrayList<String> r = new ArrayList<String>();
		if (this.listBlockElement != null) {
			if (this.recursion) r.add(this.listBlockElement);
			if (this.listElement != null) r.add(this.listElement);
		}
		blockElements = r.toArray(new String[r.size()]);
	}
	
	public ListToken(char firstChar, String listBlockElement, String listElement) {
		this.firstChar = firstChar;
		this.listBlockElement = listBlockElement;
		this.listElement = listElement;
		this.recursion = true;
		this.pattern = new Pattern[] { Pattern.compile("^[" + firstChar + "]([^\n]|\n[" + firstChar + "])*", Pattern.MULTILINE) };
		ArrayList<String> r = new ArrayList<String>();
		if (this.listBlockElement != null) {
			if (this.recursion) r.add(this.listBlockElement);
			if (this.listElement != null) r.add(this.listElement);
		}
		blockElements = r.toArray(new String[r.size()]);
	}
	
	public ListToken(char firstChar, String listBlockElement, String listElement, boolean recursion) {
		this.firstChar = firstChar;
		this.listBlockElement = listBlockElement;
		this.listElement = listElement;
		this.recursion = recursion;
		this.pattern = new Pattern[] { Pattern.compile("^[" + firstChar + "]([^\n]|\n[" + firstChar + "])*", Pattern.MULTILINE) };
		ArrayList<String> r = new ArrayList<String>();
		if (this.listBlockElement != null) {
			if (this.recursion) r.add(this.listBlockElement);
			if (this.listElement != null) r.add(this.listElement);
		}
		blockElements = r.toArray(new String[r.size()]);
	}
	
	protected void parse() {
		StringBuffer sb = new StringBuffer(this.text.length());
		parse(this.text.split("\n"), 0, sb);
		this.markup = new String(sb);
		this.parsed = true;
	}
	
	protected StringBuffer parse(String[] t, int depth, StringBuffer sb) {
		if (this.listBlockElement != null) sb.append("<").append(this.listBlockElement).append(">\n");
		while (this.aktline < t.length && getGrade(t[this.aktline]) >= depth) {
			if (recursion) for (int j=0; j<depth + 1; j++) sb.append("\t");
			if (this.listElement != null) sb.append("<").append(this.listElement).append(">");
			
			if (this.recursion && getGrade(t[this.aktline]) > depth) {
				parse(t, depth + 1, sb);
			} else {
				sb.append(t[this.aktline].substring(depth + 1));
			}
			
			if (this.listElement != null) sb.append("</").append(this.listElement).append(">");
			sb.append("\n");
			this.aktline++;
		}
		if (this.recursion) for (int j=0; j<depth; j++) sb.append("\t");
		if (this.listBlockElement != null) sb.append("</").append(this.listBlockElement).append(">");
		this.aktline--;
		return sb;
	}
	
	protected int getGrade(String t) {
		int i = 0;
		for (i=0; i<t.length(); i++)
			if (t.charAt(i) != this.firstChar) break;
		return i - 1;
	}
	
	public String[] getBlockElementNames() {
		return blockElements;
	}
	
	public Pattern[] getRegex() {
		return this.pattern;
	}
	
	public char getFirstChar() {
		return this.firstChar;
	}
	
	public boolean setText(String text, int patternNr) {
		this.text = text;
		this.markup = null;
		this.parsed = false;
		this.aktline = 0;
		return true;
	}
}
