// MemoryTracker.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.11.2007 on http://yacy.net
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

package net.yacy.search;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;

public class MemoryTracker extends Thread {
    
    private static MemoryTracker systemProfiler = null;
    
    public static void startSystemProfiling() {
    	systemProfiler = new MemoryTracker(1500);
    	systemProfiler.start();
    }
    
    public static void stopSystemProfiling() {
    	systemProfiler.running = false;
    }

    private final long delaytime;
    private boolean running;
    
    public MemoryTracker(final long time) {
    	this.delaytime = time;
    	running = true;
    	this.setName("MemoryTracker");
    }
    
    @Override
    public void run() {
        try {
        	while (running) {
        		EventTracker.update(EventTracker.EClass.MEMORY, Long.valueOf(MemoryControl.used()), true);
        		try {
    				Thread.sleep(this.delaytime);
    			} catch (final InterruptedException e) {
    				this.running = false;
    			}
        	}
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

}
