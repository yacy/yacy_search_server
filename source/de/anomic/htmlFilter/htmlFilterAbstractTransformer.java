// htmlFilterAbstractTransformer.java 
// ----------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 18.02.2004
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

package de.anomic.htmlFilter;

import java.util.Properties;
import java.util.TreeSet;

public abstract class htmlFilterAbstractTransformer implements htmlFilterTransformer {

    private TreeSet<String> tags0;
    private TreeSet<String> tags1;

    public htmlFilterAbstractTransformer(final TreeSet<String> tags0, final TreeSet<String> tags1) {
        this.tags0  = tags0;
        this.tags1  = tags1;
    }

    public boolean isTag0(final String tag) {
        return tags0.contains(tag);
    }

    public boolean isTag1(final String tag) {
        return tags1.contains(tag);
    }

    //the 'missing' method that shall be implemented:
    public abstract char[] transformText(char[] text);
    /* could be easily implemented as:
    {
	return text;
    }
    */

    // the other methods must take into account to construct the return value correctly
    public char[] transformTag0(final String tagname, final Properties tagopts, final char quotechar) {
        return htmlFilterWriter.genTag0(tagname, tagopts, quotechar);
    }

    public char[] transformTag1(final String tagname, final Properties tagopts, final char[] text, final char quotechar) {
        return htmlFilterWriter.genTag1(tagname, tagopts, text, quotechar);
    }

    public void close() {
        // free resources
        tags0 = null;
        tags1 = null;
    }
    
    protected void finalize() {
        close();
    }
        
}
