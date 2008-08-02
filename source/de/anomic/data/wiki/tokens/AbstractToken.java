// AbstractToken.java 
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

import de.anomic.data.wiki.wikiParserException;

public abstract class AbstractToken implements Token {
	
	protected String text = null;
	protected String markup = null;
	protected boolean parsed = false;
	
	protected abstract void parse() throws wikiParserException;
	
	public String getMarkup() throws wikiParserException {
		if (this.text == null)
			throw new IllegalArgumentException();
		if (!this.parsed) parse();
		return this.markup;
	}
	
	public String getText() { return this.text; }
	
	public String toString() { try { return getMarkup(); } catch (final wikiParserException e) { return null; } }
}
