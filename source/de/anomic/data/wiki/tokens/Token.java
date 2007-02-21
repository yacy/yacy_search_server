
package de.anomic.data.wiki.tokens;

import java.util.regex.Pattern;

public interface Token {
	
	public Pattern[] getRegex();
	public boolean setText(String text, int patternNr);
	public String getText();
	public String getMarkup();
	public String[] getBlockElementNames();
}
