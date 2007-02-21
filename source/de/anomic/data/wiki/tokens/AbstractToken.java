
package de.anomic.data.wiki.tokens;

public abstract class AbstractToken implements Token {
	
	protected String text = null;
	protected String markup = null;
	protected boolean parsed = false;
	
	protected abstract boolean parse();
	
	public String getMarkup() {
		if (this.text == null)
			throw new IllegalArgumentException();
		if (!this.parsed && !parse()) return this.text;
		return this.markup;
	}
	
	public String getText() { return this.text; }
	
	@Override
	public String toString() { return getMarkup(); }
}
