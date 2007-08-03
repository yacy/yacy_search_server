// kelondroNode.java
// (C) 2003 - 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2003 on http://yacy.net
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

package de.anomic.kelondro;

import java.io.IOException;

public interface kelondroNode {

    public kelondroHandle handle();
    public void setOHByte(int i, byte b);
    public void setOHHandle(int i, kelondroHandle otherhandle);
    public byte getOHByte(int i);
    public kelondroHandle getOHHandle(int i);
    public void commit() throws IOException;
    public void setValueRow(byte[] row) throws IOException;
    public boolean valid();
    public byte[] getKey();
    public byte[] getValueRow() throws IOException;
    public String toString();

}
