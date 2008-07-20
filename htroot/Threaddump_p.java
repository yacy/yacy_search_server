// Threaddump_p.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Fieger
//
// $LastChangedDate: 2008-01-22 12:51:43 +0100 (Di, 22 Jan 2008) $
// $LastChangedRevision: 4374 $
// $LastChangedBy: low012 $
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

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import de.anomic.data.htmlTools;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyVersion;

public class Threaddump_p {

	private static final serverObjects prop = new serverObjects();
	private static plasmaSwitchboard sb = null;

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {

    	prop.clear();
    	sb = (plasmaSwitchboard) env;
    	StringBuffer buffer = new StringBuffer(1000);
    	
    	if (post != null && post.containsKey("createThreaddump")) {
    	    boolean plain = post.get("plain", "false").equals("true");
    	    int sleep = post.getInt("sleep", 0); // a sleep before creation of a thread dump can be used for profiling
    	    if (sleep > 0) try {Thread.sleep(sleep);} catch (InterruptedException e) {}
    	    prop.put("dump", "1");
        	// Thread dump
        	Map<Thread,StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        	Date dt = new Date();
        	String versionstring = yacyVersion.combined2prettyVersion(sb.getConfig("version","0.1"));
        	
        	bufferappend(buffer, plain, "************* Start Thread Dump " + dt + " *******************");
        	bufferappend(buffer, plain, "");
        	bufferappend(buffer, plain, "YaCy Version: " + versionstring);
        	bufferappend(buffer, plain, "Total Memory = " + (Runtime.getRuntime().totalMemory()));
        	bufferappend(buffer, plain, "Used&nbsp;&nbsp;Memory = " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        	bufferappend(buffer, plain, "Free&nbsp;&nbsp;Memory = " + (Runtime.getRuntime().freeMemory()));
        	bufferappend(buffer, plain, "");
        	bufferappend(buffer, plain, "");
            
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.BLOCKED);
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.RUNNABLE);
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.TIMED_WAITING);
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.WAITING);
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.NEW);
        	appendStackTraces(sb.getRootPath(), buffer, stackTraces, plain, Thread.State.TERMINATED);
            
        	bufferappend(buffer, plain, "************* End Thread Dump " + dt + " *******************");
        
        	prop.put("plain_content", buffer.toString());
        	prop.put("plain", (plain) ? 1 : 0);
    	}
    	
       	return prop;    // return from serverObjects respond()
    }    
    
    private static void appendStackTraces(File rootPath, StringBuffer buffer, Map<Thread,StackTraceElement[]> stackTraces, boolean plain, Thread.State stateIn) {
        bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
        bufferappend(buffer, plain, "");
        
        File classPath = new File(rootPath, "source");
  
        for (Thread thread: stackTraces.keySet()) {
            StackTraceElement[] stackTraceElements = stackTraces.get(thread);
            StackTraceElement ste;
            String line;
            String tracename = "";
            File classFile;
            if ((stateIn.equals(thread.getState()))  && (stackTraceElements.length > 0)) {
                if (plain) {
                    classFile = getClassFile(classPath, stackTraceElements[stackTraceElements.length - 1].getClassName());
                    tracename = classFile.getName();
                    if (tracename.endsWith(".java")) tracename = tracename.substring(0, tracename.length() - 5);
                    if (tracename.length() > 20) tracename = tracename.substring(0, 20);
                    while (tracename.length() < 20) tracename = tracename + "_";
                    tracename = "[" + tracename + "] ";                
                }                
                bufferappend(buffer, plain, tracename + "Thread= " + thread.getName() + " " + (thread.isDaemon()?"daemon":"") + " id=" + thread.getId() + " " + thread.getState().toString());
                for (int i = 0; i < stackTraceElements.length; i++) {
                    ste = stackTraceElements[i];
                    if (i == 0) {
                        line = getLine(getClassFile(classPath, ste.getClassName()), ste.getLineNumber());
                    } else {
                        line = null;
                    }
                    if ((line != null) && (line.length() > 0)) {
                        bufferappend(buffer, plain, tracename + "at " + htmlTools.encodeUnicode2html(ste.toString(), true) + " [" + line.trim() + "]");
                    } else {
                        bufferappend(buffer, plain, tracename + "at " + htmlTools.encodeUnicode2html(ste.toString(), true));
                    }
                }
                bufferappend(buffer, plain, "");
            }
        }
        bufferappend(buffer, plain, "");
    }
    
    private static File getClassFile(File sourcePath, String classname) {
        String classPath = classname.replace('.', '/') + ".java";
        File file = new File(sourcePath, classPath);
        return file;
    }
    
    private static String getLine(File file, int line) {
        // find class
        if (!file.exists()) return "";
        try {
            String lineString = nxTools.line(serverFileUtils.read(file), line);
            if (lineString == null) return "@ERROR";
            return lineString;
        } catch (IOException e) {
            return "@EXCEPTION: " + e.getMessage();
        }
    }
    
    private static void bufferappend(StringBuffer buffer, boolean plain, String a) {
        buffer.append(a);
        if (plain) {
            buffer.append("\n");
        } else {
            buffer.append("<br />");
        }
    }

}
