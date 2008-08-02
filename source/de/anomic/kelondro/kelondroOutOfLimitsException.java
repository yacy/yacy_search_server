// kelondroOutOfLimitsException.java
// ---------------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created: 17.01.2006
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

package de.anomic.kelondro;

public class kelondroOutOfLimitsException extends java.lang.RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public kelondroOutOfLimitsException() {
        super("unspecific-error");
    }
    
    public kelondroOutOfLimitsException(final int expectedLimit, final int actualSize) {
        super("Object size is " + actualSize + "; it exceeds the size limit " + expectedLimit);
    }
    
    public kelondroOutOfLimitsException(final int actualSize) {
        super("Object size is " + actualSize + "; must not be negative");
    }
    
}
