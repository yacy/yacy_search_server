// Transformer.java 
// ---------------------------
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

public interface Transformer {

    // the init method is used to initialize the transformer with some values
    // i.e. the initarg - String can be the name of a file which may contain
    // more specific transformation rules
    public void init(String initarg);

    // ask if this transformer will do any transformation whatsoever
    // this may return true if the initialization resulted in a status
    // that does not allow any transformation
    public boolean isIdentityTransformer();
    
    // tests, if a given body-less tag (i.e. <br> shall be supervised)
    // only tags that are defined here will be cached and not streamed
    public boolean isTag0(String tag);

    // tests if a given tag that may have a body (i.e. <tt> ..body.. </tt>)
    // shall be supervised
    public boolean isTag1(String tag);

    // method that is called with any text between tags
    // the returned text replaces the given text
    // if the text shall not be changed, it must be returned as called
    public char[] transformText(char[] text);

    // method that is called when a body-less tag occurs
    public char[] transformTag0(ContentScraper.Tag tag, char quotechar);

    // method that is called when a body-containing text occurs
    public char[] transformTag1(ContentScraper.Tag tag, char quotechar);

    public void close();
}
