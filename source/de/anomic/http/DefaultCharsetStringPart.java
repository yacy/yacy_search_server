// DefaultCharsetStringPart.java
// (C) 2008 by Daniel Raap; danielr@users.berlios.de
// first published 02.08.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.http;

import java.nio.charset.Charset;

import org.apache.commons.httpclient.methods.multipart.StringPart;

/**
 * @author daniel
 *
 */
public class DefaultCharsetStringPart extends StringPart {

    /**
     * constructs a StringPart with the defaultCharset (instead of US-ASCII)
     * 
     * @see Charset#defaultCharset()
     * @param name
     * @param value
     */
    public DefaultCharsetStringPart(String name, String value) {
        super(name, value, Charset.defaultCharset().name());
    }

}
