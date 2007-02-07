// kelondroArray.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 01.06.2006 on http://www.anomic.de
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

package de.anomic.kelondro;

import java.io.IOException;

public interface kelondroArray {

    public int size();
    
    public kelondroRow row();
    
    public kelondroRow.Entry replace(int index, kelondroRow.Entry rowentry) throws IOException;
    public void overwrite(int index, kelondroRow.Entry rowentry) throws IOException;

    public kelondroRow.Entry get(int index) throws IOException;

    public int add(kelondroRow.Entry rowinstance) throws IOException;

    public void remove(int index) throws IOException;

    public void print() throws IOException;

}
