/**
 *  Parser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

// this is a new implementation of the parser interface using multiple documents as result set
// and a much simpler method structure with only one single parser method to implement

package net.yacy.document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.yacy.cora.util.ConcurrentLog;

public abstract class AbstractParser implements Parser {

    public final static ConcurrentLog log = new ConcurrentLog("PARSER");
    protected final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    protected final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    private   final String name;

    /**
     * initialize a parser with a name
     * @param name
     */
    public AbstractParser(final String name) {
	    this.name = name;
	}

    /**
     * return the name of the parser
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * each parser must define a set of supported mime types
     * @return a set of mime type strings that are supported
     */
    @Override
    public Set<String> supportedMimeTypes() {
        return this.SUPPORTED_MIME_TYPES;
    }

    /**
     * each parser must define a set of supported file extensions
     * @return a set of file name extensions that are supported
     */
    @Override
    public Set<String> supportedExtensions() {
        return this.SUPPORTED_EXTENSIONS;
    }

    /**
     * check equivalence of parsers; this simply tests equality of parser names
     * @param o
     * @return
     */
    @Override
    public boolean equals(final Object o) {
        return getName().equals(((Parser) o).getName());
    }

    /**
     * the hash code of a parser
     * @return the hash code of the parser name string
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    public static List<String> singleList(String t) {
        List<String> c = new ArrayList<String>(1);
        c.add(t);
        return c;
    }

}
