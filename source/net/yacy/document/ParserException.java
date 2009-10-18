//ParserException.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 24.04.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document;

import net.yacy.kelondro.data.meta.DigestURI;

public class ParserException extends Exception {
    private DigestURI url = null;
    
	private static final long serialVersionUID = 1L;

	public ParserException() {
        super();
    }

    public ParserException(final String message, final DigestURI url) {
        super(message + "; url = " + url.toNormalform(true, false));
        this.url = url;
    }
    
    public DigestURI getURL() {
        return this.url;
    }
}
