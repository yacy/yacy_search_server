// SimpleToken.java 
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

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.data.wiki.wikiParserException;

public class SimpleToken extends AbstractToken {
	
	protected String content = null;
	protected int grade = 0;
	
	protected final Pattern[] pattern;
	private final String[][] definitionList;
	private final String[] blockElements;
	
	public SimpleToken(char firstChar, char lastChar, String[][] definitionList, boolean isBlockElements) {
		this.definitionList = definitionList;
		int i;
		if (isBlockElements) {
			ArrayList r = new ArrayList();
			int j;
			for (i=0; i<definitionList.length; i++)
				if (definitionList[i] != null)
					for (j=0; j<definitionList[i].length; j++)
						r.add(definitionList[i][j]);
			this.blockElements = (String[])r.toArray(new String[r.size()]);
		} else {
			this.blockElements = null;
		}
		
		for (i=0; i<definitionList.length; i++)
			if (definitionList[i] != null) {
				i++;
				break;
			}
		this.pattern = new Pattern[] { Pattern.compile(
				"([\\" + firstChar + "]{" + i + "," + definitionList.length + "})" +
                "(.*?)" +
                "([\\" + lastChar + "]{" + i + "," + definitionList.length + "})")};
	}
	
	public String getMarkup() throws wikiParserException {
		if (this.content == null) {
			if (this.text == null) {
				throw new IllegalArgumentException();
			} else {
				setText(this.text, 0);
			}
		}
		if (!this.parsed) parse();
		return this.markup;
	}
	
	protected void parse() throws wikiParserException {
		String[] e;
		if (this.grade >= this.definitionList.length || (e = this.definitionList[this.grade]) == null)
		    throw new wikiParserException("Token not defined for grade: " + this.grade);
		this.markup = getMarkup(e);
		this.parsed = true;
	}
	
	protected String getMarkup(String[] es) {
		return getMarkup(es, false) + this.content + getMarkup(es, true);
	}
	
	protected String getMarkup(String[] es, boolean closing) {
		StringBuffer result = new StringBuffer();
		// backwards if closing
		for (
				int i = (closing) ? es.length - 1 : 0, j;
				(closing && i >= 0) ^ (!closing && i < es.length);
				i += (closing) ? -1 : +1
		) {
			result.append("<");
			if (closing) {
				result.append("/");
				if ((j = es[i].indexOf(' ')) > -1) {
					result.append(es[i].substring(0, j));
				} else {
					result.append(es[i]);
				}
			} else {
				result.append(es[i]);
			}
			result.append(">");
		}
		return new String(result);
	}
	
	public boolean setText(String text, int patternNr) {
		this.text = text;
		this.markup = null;
		this.parsed = false;
		if (text != null) {
			Matcher m = getRegex()[0].matcher(text);
			if (
					(m.matches()) &&
					(m.group(1).length() == m.group(3).length()) &&
					(definitionList.length >= m.group(1).length()) &&
					(definitionList[m.group(1).length() - 1] != null)
			) {
				this.grade = m.group(1).length() - 1;
				this.content = m.group(2);
				return true;
			}
		}
		return false;
	}
	
	public Pattern[] getRegex() { return this.pattern; }
	public String[] getBlockElementNames() { return this.blockElements; }
}
