// Threaddump_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Fieger
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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


import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.ArrayList;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.ThreadDump;
import net.yacy.kelondro.util.MemoryControl;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyBuildProperties;

public class Threaddump_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
    	
    	serverObjects prop = new serverObjects();
    	Switchboard sb = (Switchboard) env;
    	
    	final StringBuilder buffer = new StringBuilder(1000);
    	
	    final boolean plain = post != null && post.get("plain", "false").equals("true");
	    final int sleep = (post == null) ? 0 : post.getInt("sleep", 0); // a sleep before creation of a thread dump can be used for profiling
	    if (sleep > 0) try {Thread.sleep(sleep);} catch (final InterruptedException e) {}
	    prop.put("dump", "1");
    	// Thread dump
    	final Date dt = new Date();
    	final String versionstring = yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision();
    	Runtime runtime = Runtime.getRuntime();
    	
    	ThreadDump.bufferappend(buffer, plain, "************* Start Thread Dump " + dt + " *******************");
    	ThreadDump.bufferappend(buffer, plain, "");
    	ThreadDump.bufferappend(buffer, plain, "YaCy Version: " + versionstring);
    	ThreadDump.bufferappend(buffer, plain, "Assigned&nbsp;&nbsp;&nbsp;Memory = " + (runtime.maxMemory()));
    	ThreadDump.bufferappend(buffer, plain, "Used&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Memory = " + (runtime.totalMemory() - runtime.freeMemory()));
    	ThreadDump.bufferappend(buffer, plain, "Available&nbsp;&nbsp;Memory = " + (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()));
    	ThreadDump.bufferappend(buffer, plain, "");
    	ThreadDump.bufferappend(buffer, plain, "");
    	
    	int multipleCount = 100;
    	File appPath = sb.getAppPath();
        if (post != null && post.containsKey("multipleThreaddump")) {
        	multipleCount = post.getInt("count", multipleCount);
            final ArrayList<Map<Thread,StackTraceElement[]>> traces = new ArrayList<Map<Thread,StackTraceElement[]>>();
            for (int i = 0; i < multipleCount; i++) {
                traces.add(Thread.getAllStackTraces());
                if (MemoryControl.available() < 20 * 1024 * 1024) break;
            }
            ThreadDump.appendStackTraceStats(appPath, buffer, traces, plain, null);
            /*
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.BLOCKED);
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.RUNNABLE);
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.TIMED_WAITING);
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.WAITING);
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.NEW);
            ThreadDumpGenerator.appendStackTraceStats(appPath, buffer, traces, plain, Thread.State.TERMINATED);
            */
        } else {
            // generate a single thread dump
            final Map<Thread,StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.BLOCKED);
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.RUNNABLE);
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.TIMED_WAITING);
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.WAITING);
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.NEW);
            ThreadDump.appendStackTraces(appPath, buffer, stackTraces, plain, Thread.State.TERMINATED);
        }
        
        ThreadDump.bufferappend(buffer, plain, "************* End Thread Dump " + dt + " *******************");
    
    	prop.put("plain_count", multipleCount);
    	prop.put("plain_content", buffer.toString());
    	prop.put("plain", (plain) ? 1 : 0);
    	
       	return prop;    // return from serverObjects respond()
    }    
    
    

}
