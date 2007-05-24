// kelondroAbstractOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import de.anomic.kelondro.kelondroRecords.Node;

public abstract class kelondroAbstractOrder implements kelondroOrder {

    protected byte[] zero = null;
    protected boolean asc = true;
    
    public abstract Object clone();

    public void direction(boolean ascending) {
        asc = ascending;
    }
    
    public long partition(byte[] key, int forks) {
        final long d = (Long.MAX_VALUE / forks) + ((Long.MAX_VALUE % forks) + 1) / forks;
        return cardinal(key) / d;
    }
    
    public int compare(Object a, Object b) {
        if ((a instanceof byte[]) && (b instanceof byte[])) {
            return compare((byte[]) a, (byte[]) b);
        } else if ((a instanceof Node) && (b instanceof Node)) {
            return compare(((Node) a).getKey(), ((Node) b).getKey());
        } else if ((a instanceof String) && (b instanceof String)) {
            return compare(((String) a).getBytes(), ((String) b).getBytes());
        } else if ((a instanceof kelondroRow.Entry) && (b instanceof kelondroRow.Entry)) {
            return compare(((kelondroRow.Entry) a).getColBytes(0), ((kelondroRow.Entry) b).getColBytes(0));
        } /* else if ((a instanceof Integer) && (b instanceof Integer)) {
            return ((Integer) a).compareTo((Integer) b);
        } */ else
            throw new IllegalArgumentException("Object type or Object type combination not supported: a=" + a + ((a != null) ? "[" + a.getClass().getName() + "]" : "") + ", b=" + b + ((b != null) ? "[" + b.getClass().getName() + "]" : ""));
    }

    public byte[] zero() {
        return zero;
    }
    
    public void rotate(byte[] newzero) {
        this.zero = newzero;
    }
    
    public boolean equals(kelondroOrder otherOrder) {
        if (otherOrder == null) return false;
        String thisSig = this.signature();
        String otherSig = otherOrder.signature();
        if ((thisSig == null) || (otherSig == null)) return false;
        return thisSig.equals(otherSig);
    }
    
}
