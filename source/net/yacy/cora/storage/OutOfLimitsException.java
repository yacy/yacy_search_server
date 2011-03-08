/**
 *  OutOfLimitsException
 *  Copyright 2006 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 17.01.2006 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.storage;

public class OutOfLimitsException extends java.lang.RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public OutOfLimitsException() {
        super("unspecific-error");
    }
    
    public OutOfLimitsException(final int expectedLimit, final int actualSize) {
        super("Object size is " + actualSize + "; it exceeds the size limit " + expectedLimit);
    }
    
    public OutOfLimitsException(final int actualSize) {
        super("Object size is " + actualSize + "; must not be negative");
    }
    
}
