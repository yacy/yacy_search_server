// plasmaCrawlLURLEntry.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 13.10.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.plasma;

import java.io.IOException;
import java.util.Date;

import de.anomic.net.URL;
import de.anomic.kelondro.kelondroRow;
import de.anomic.index.indexEntry;

public interface plasmaCrawlLURLEntry {

    public kelondroRow.Entry toRowEntry() throws IOException;

    public String hash();

    public URL url();

    public String descr();

    public Date moddate();

    public Date loaddate();

    public String referrerHash();

    public char doctype();

    public int copyCount();

    public boolean local();

    public int quality();

    public String language();

    public int size();

    public int wordCount();

    public String snippet();

    public indexEntry word();

    public boolean isOlder(plasmaCrawlLURLEntry other);

    public String toString(String snippet);

    public String toString();

    public void print();

}
