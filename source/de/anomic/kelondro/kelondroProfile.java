// kelondroProfile.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 23.06.2006 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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

public class kelondroProfile implements Cloneable {

    private long accRead;
    private long accWrite;
    private long accDelete;
    
    public kelondroProfile() {
        accRead = 0;
        accWrite = 0;
        accDelete = 0;
    }
    
    public long timeRead() {
        return accRead;
    }
    
    public long timeWrite() {
        return accWrite;
    }
    
    public long timeDelete() {
        return accDelete;
    }
    
    public void timeReset() {
        accRead = 0;
        accWrite = 0;
        accDelete = 0;
    }

    protected long startRead() {
        return System.currentTimeMillis();
    }
    
    protected void stopRead(long handle) {
        accRead += System.currentTimeMillis() - handle;
    }
    
    protected long startWrite() {
        return System.currentTimeMillis();
    }
    
    protected void stopWrite(long handle) {
        accWrite += System.currentTimeMillis() - handle;
    }
    
    protected long startDelete() {
        return System.currentTimeMillis();
    }
    
    protected void stopDelete(long handle) {
        accDelete += System.currentTimeMillis() - handle;
    }
    
    public kelondroProfile clone() {
        kelondroProfile clone = new kelondroProfile();
        clone.accRead = this.accRead;
        clone.accWrite = this.accWrite;
        clone.accDelete = this.accDelete;
        return clone;
    }
    
    public String toString() {
        return "read=" + accRead + ", write=" + accWrite + ", delete=" + accDelete;
    }
    
    public static kelondroProfile consolidate(kelondroProfile[] profiles) {
        for (int i = 1; i < profiles.length; i++) consolidate(profiles[0], profiles[i]);
        return profiles[0];
    }
    
    public static kelondroProfile consolidate(kelondroProfile profile1, kelondroProfile profile2) {
        profile1.accRead += profile2.accRead;
        profile1.accWrite += profile2.accWrite;
        profile1.accDelete += profile2.accDelete;
        return profile1;
    }
    
    public static kelondroProfile delta(kelondroProfile newer, kelondroProfile older) {
        kelondroProfile result = new kelondroProfile();
        result.accRead = newer.accRead - older.accRead;
        result.accWrite = newer.accWrite - older.accWrite;
        result.accDelete = newer.accDelete - older.accDelete;
        return result;
    }
}
