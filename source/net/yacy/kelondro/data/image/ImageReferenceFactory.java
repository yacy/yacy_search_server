// ImageReferenceFactory.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 21.01.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.data.image;

import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.ReferenceFactory;

public class ImageReferenceFactory implements ReferenceFactory<ImageReference> {

    public ImageReference produceSlow(Entry e) {
        return null; //new ImageReferenceRow(e);
    }
    
    public ImageReference produceFast(ImageReference r) {
        if (r instanceof ImageReferenceVars) return r;
        return new ImageReferenceVars(r);
    }

    public Row getRow() {
        return ImageReferenceRow.urlEntryRow;
    }

}
