// RowSpaceExceededException
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 06.12.2009 on http://yacy.net
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

package net.yacy.kelondro.index;

import java.util.Date;

import net.yacy.kelondro.util.MemoryControl;

public class RowSpaceExceededException extends Exception {

    private static final long serialVersionUID = 9059516027929222151L;

    private final String forUsage;
    private final long neededRAM, availableRAM, time;
    
    public RowSpaceExceededException(final long neededRAM, final String forUsage) {
        super(Long.toString(neededRAM) + " bytes needed for " + forUsage + ": " + MemoryControl.available() + " free at " + (new Date()).toString());
        this.time = System.currentTimeMillis();
        this.availableRAM = MemoryControl.available();
        this.neededRAM = neededRAM;
        this.forUsage = forUsage;
    }

    public RowSpaceExceededException(final long neededRAM, final String forUsage, final Throwable t) {
        super(Long.toString(neededRAM) + " bytes needed for " + forUsage + ": " + MemoryControl.available() + " free at " + (new Date()).toString(), t);
        this.time = System.currentTimeMillis();
        this.availableRAM = MemoryControl.available();
        this.neededRAM = neededRAM;
        this.forUsage = forUsage;
    }

    public String getUsage() {
        return forUsage;
    }

    public long getNeededRAM() {
        return neededRAM;
    }

    public long getAvailableRAM() {
        return availableRAM;
    }

    public long getTime() {
        return time;
    }
    
}
