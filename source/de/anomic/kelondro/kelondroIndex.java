// kelondroIndex.java
// ------------------
// part of the Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 26.10.2005
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

/* A kelondroIndex is a table with indexed access on the first column
   Elements may be selected from the table with logarithmic computation time
   using the get-method. Inserts have also the same computation order and
   can be done with the put-method.
 
   The kelondro Database provides two implementations of this interface:
   kelondroTree and kelondroHashtable
 */

package de.anomic.kelondro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public interface kelondroIndex {

    public String filename(); // returns a unique identified for this index; can be a real or artificial file name
    public int size();
    public kelondroProfile profile();
    public kelondroRow row();
    public boolean has(byte[] key); // use this only if there is no get in case that has returns true
    public kelondroRow.Entry get(byte[] key) throws IOException;
    public kelondroRow.Entry put(kelondroRow.Entry row) throws IOException;
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException;
    public void putMultiple(List<kelondroRow.Entry> rows) throws IOException; // for R/W head path optimization
    public boolean addUnique(kelondroRow.Entry row) throws IOException; // no double-check
    public int addUniqueMultiple(List<kelondroRow.Entry> rows) throws IOException; // no double-check
    public ArrayList<kelondroRowCollection> removeDoubles() throws IOException; // removes all elements that are double (to be used after all addUnique)
    public kelondroRow.Entry remove(byte[] key) throws IOException;
    public kelondroRow.Entry removeOne() throws IOException;
    public kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException; // iterates only the key
    public kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] firstKey) throws IOException; // iterates the whole row
    public void clear() throws IOException;
    public void close();
}
