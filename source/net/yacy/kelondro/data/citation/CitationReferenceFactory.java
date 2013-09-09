/**
 *  CitationReferenceFactory
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.02.2012 at http://yacy.net
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

package net.yacy.kelondro.data.citation;

import java.io.Serializable;

import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.ReferenceFactory;

public class CitationReferenceFactory implements ReferenceFactory<CitationReference>, Serializable {

    private static final long serialVersionUID=-1098504892965986149L;

    @Override
    public CitationReference produceSlow(final Entry e) {
        return new CitationReference(e);
    }

    @Override
    public CitationReference produceFast(final CitationReference r, final boolean local) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Row getRow() {
        return CitationReference.citationRow;
    }

}
