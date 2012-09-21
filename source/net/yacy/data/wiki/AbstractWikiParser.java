// abstractWikiParser.java
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.data.wiki;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

abstract class AbstractWikiParser implements WikiParser {

    protected abstract String transform(String hostport, BufferedReader reader, int length) throws IOException;

    @Override
    public String transform(String hostport, final String content) {
        try {
            return transform(
                    hostport,
                    new BufferedReader(new StringReader(content)),
                    content.length());
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }

    @Override
    public String transform(String hostport, final byte[] content) throws UnsupportedEncodingException {
        return transform(hostport, content, "UTF-8");
    }

    @Override
    public String transform(String hostport, final byte[] content, final String encoding) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(
                    hostport,
                    new BufferedReader(new InputStreamReader(bais, encoding)),
                    content.length);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }

}
