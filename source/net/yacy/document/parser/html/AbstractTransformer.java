// AbstractTransformer.java
// ----------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.document.parser.html;

import java.util.TreeSet;

public abstract class AbstractTransformer implements Transformer {

    private TreeSet<String> tags0;
    private TreeSet<String> tags1;

    public AbstractTransformer(final TreeSet<String> tags0, final TreeSet<String> tags1) {
        this.tags0  = tags0;
        this.tags1  = tags1;
    }

    @Override
    public boolean isTag0(final String tag) {
        return this.tags0.contains(tag);
    }

    @Override
    public boolean isTag1(final String tag) {
        return this.tags1.contains(tag);
    }

    //the 'missing' method that shall be implemented:
    @Override
    public abstract char[] transformText(char[] text);
    /* could be easily implemented as:
    {
	return text;
    }
    */

    // the other methods must take into account to construct the return value correctly
    @Override
    public char[] transformTag0(final ContentScraper.Tag tag, final char quotechar) {
        return TransformerWriter.genTag0(tag.name, tag.opts, quotechar);
    }

    @Override
    public char[] transformTag1(final ContentScraper.Tag tag, final char quotechar) {
        return TransformerWriter.genTag1(tag.name, tag.opts, tag.content.getChars(), quotechar);
    }

    @Override
    public synchronized void close() {
        // free resources
        this.tags0 = null;
        this.tags1 = null;
    }

}
