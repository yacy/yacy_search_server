
package de.anomic.data.wiki.tokens;


import java.util.ArrayList;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleToken extends AbstractToken {
	
	protected String content = null;
	protected int grade = 0;
	
	protected final Pattern[] pattern;
	protected MatchResult mresult = null;
	private final String[][] definitionList;
	private final String[] blockElements;
	
	public SimpleToken(char firstChar, char lastChar, String[][] definitionList, boolean isBlockElements) {
		this.definitionList = definitionList;
		int i;
		if (isBlockElements) {
			ArrayList<String> r = new ArrayList<String>();
			int j;
			for (i=0; i<definitionList.length; i++)
				if (definitionList[i] != null)
					for (j=0; j<definitionList[i].length; j++)
						r.add(definitionList[i][j]);
			this.blockElements = r.toArray(new String[r.size()]);
		} else {
			this.blockElements = null;
		}
		
		for (i=0; i<definitionList.length; i++)
			if (definitionList[i] != null) {
				i++;
				break;
			}
		this.pattern = new Pattern[] { Pattern.compile(String.format(
				"([\\%s]{%d,%d})(.*?)([\\%s]{%d,%d})",
				firstChar, i, definitionList.length,
				lastChar, i, definitionList.length)) };
	}
	
	@Override
	public String getMarkup() {
		if (this.content == null) {
			if (this.text == null) {
				throw new IllegalArgumentException();
			} else {
				setText(this.text, 0);
			}
		}
		if (!this.parsed && !parse()) return this.text;
		return this.markup;
	}
	
	protected boolean parse() {
		String[] e;
		if ((e = definitionList[this.grade]) == null || definitionList.length <= this.grade) {
			System.err.println("token not defined for grade: " + this.grade);
			return false;
		}
		this.markup = getMarkup(e);
		this.parsed = true;
		return true;
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
