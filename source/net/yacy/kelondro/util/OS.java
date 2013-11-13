// OS.java
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.03.2004
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

package net.yacy.kelondro.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.server.serverCore;


public final class OS {

	// constants for system identification
    public enum System {
        MacOSC,  // 'classic' Mac OS 7.6.1/8.*/9.*
        MacOSX,  // all Mac OS X
        Unix,    // all Unix/Linux type systems
        Windows, // all Windows 95/98/NT/2K/XP
        Unknown; // any other system
    }

	// constants for file type identification (Mac only)
	public static final String blankTypeString = "____";

	// system-identification statics
	private static final System  systemOS;
	public static final boolean isMacArchitecture;
	private static final boolean isUnixFS;
	public static final boolean canExecUnix;
	public static final boolean isWindows;
	public static final boolean isWin32;

	// calculated system constants
	public static int maxPathLength = 65535;

	// Macintosh-specific statics
	public  static final Map<String, String> macFSTypeCache = new HashMap<String, String>();
	public  static final Map<String, String> macFSCreatorCache = new HashMap<String, String>();

	// static initialization
	static {
		// check operation system type
		final Properties sysprop = java.lang.System.getProperties();
		final String sysname = sysprop.getProperty("os.name","").toLowerCase();
		if (sysname.startsWith("mac os x")) systemOS = System.MacOSX;
		else if (sysname.startsWith("mac os")) systemOS = System.MacOSC;
		else if (sysname.startsWith("windows")) systemOS = System.Windows;
		else if ((sysname.startsWith("linux")) || (sysname.startsWith("unix"))) systemOS = System.Unix;
		else systemOS = System.Unknown;

		isMacArchitecture = ((systemOS == System.MacOSC) || (systemOS == System.MacOSX));
		isUnixFS = ((systemOS == System.MacOSX) || (systemOS == System.Unix));
		canExecUnix = ((isUnixFS) || (!((systemOS == System.MacOSC) || (systemOS == System.Windows))));
		isWindows = (systemOS == System.Windows);
		isWin32 = (isWindows && java.lang.System.getProperty("os.arch", "").contains("x86"));

		// set up maximum path length according to system
		if (isWindows) maxPathLength = 255; else maxPathLength = 65535;
	}


	/**
	 * finds the maximum possible heap (may cause high system load)
	 * @return heap in -Xmx<i>[heap]</i>m
	 * @author [DW], 07.02.2009
	 */
	private static int getWin32MaxHeap() {
		int maxmem = 1000;
		while(checkWin32Heap(maxmem)) maxmem += 100;
		while(!checkWin32Heap(maxmem)) maxmem -= 10;
		return maxmem;
	}

	private final static ConcurrentLog memchecklog = new ConcurrentLog("MEMCHECK");
	
	/**
	 * checks heap (may cause high system load)
	 * @param mem heap to check in -Xmx<i>[heap]</i>m
	 * @return true if possible
	 * @author [DW], 07.02.2009
	 */
	private static boolean checkWin32Heap(final int mem){
		String line = "";
        final List<String> processArgs = new ArrayList<String>();
        processArgs.add("java");
        processArgs.add("-Xms4m");
        processArgs.add("-Xmx" + Integer.toString(mem) + "m");
        try {
    		line = ConsoleInterface.getLastLineConsoleOutput(processArgs, memchecklog);
		} catch (final IOException e) {
			return false;
		}
		return (line.indexOf("space for object heap",0) > -1) ? false : true;
	}

	public static String infoString() {
		String s = "System=";
		if (systemOS == System.Unknown) s += "unknown";
		else if (systemOS == System.MacOSC) s += "Mac OS Classic";
		else if (systemOS == System.MacOSX) s += "Mac OS X";
		else if (systemOS == System.Unix) s += "Unix/Linux";
		else if (systemOS == System.Windows) s += "Windows";
		else s += "unknown";
		if (isMacArchitecture) s += ", Mac System Architecture";
		if (isUnixFS) s += ", has Unix-like File System";
		if (canExecUnix) s += ", can execute Unix-Shell Commands";
		return s;
	}

	/** generates a 2-character string containing information about the OS-type*/
	public static String infoKey() {
		String s = "";
		if (systemOS == System.Unknown) s += "o";
		else if (systemOS == System.MacOSC) s += "c";
		else if (systemOS == System.MacOSX) s += "x";
		else if (systemOS == System.Unix) s += "u";
		else if (systemOS == System.Windows) s += "w";
		else s += "o";
		if (isMacArchitecture) s += "m";
		if (isUnixFS) s += "f";
		if (canExecUnix) s += "e";
		return s;
	}

	public static void deployScript(final File scriptFile, final String theScript) throws IOException {
		FileUtils.copy(UTF8.getBytes(theScript), scriptFile);
		if(!isWindows){ // set executable
			try {
				Runtime.getRuntime().exec("chmod 755 " + scriptFile.getAbsolutePath().replaceAll(" ", "\\ ")).waitFor();
			} catch (final InterruptedException e) {
				ConcurrentLog.severe("DEPLOY", "deploy of script file failed. file = " + scriptFile.getAbsolutePath(), e);
				throw new IOException(e.getMessage());
			}
		}
	}

	/**
	 * use a hack to get the current process PID
	 * @return the PID of the current java process or -1 if the PID cannot be obtained
	 */
	public static int getPID() {
        final String pids = ManagementFactory.getRuntimeMXBean().getName();
        final int p = pids.indexOf('@');
        return p >= 0 ? NumberTools.parseIntDecSubstring(pids, 0, p) : -1;
	}

	public static void execAsynchronous(final File scriptFile) throws IOException {
		// runs a script as separate thread
		String starterFileExtension = null;
		String script = null;
		if(isWindows){
			starterFileExtension = ".starter.bat";
			// use /K to debug, /C for release
			script = "start /MIN CMD /C \"" + scriptFile.getAbsolutePath() + "\"";
		} else { // unix/linux
			starterFileExtension = ".starter.sh";
			script = "#!/bin/sh" + serverCore.LF_STRING + scriptFile.getAbsolutePath().replaceAll(" ", "\\ ") + " &" + serverCore.LF_STRING;
		}
		final File starterFile = new File(scriptFile.getAbsolutePath().replaceAll(" ", "\\ ") + starterFileExtension);
		deployScript(starterFile, script);
		try {
			Runtime.getRuntime().exec(starterFile.getAbsolutePath().replaceAll(" ", "\\ ")).waitFor();
		} catch (final InterruptedException e) {
			throw new IOException(e.getMessage());
		}
		FileUtils.deletedelete(starterFile);
	}

	public static Vector<String> execSynchronous(final String command) throws IOException {
		// runs a unix/linux command and returns output as Vector of Strings
		// this method blocks until the command is executed
		final Process p = Runtime.getRuntime().exec(command);
		final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String text;
		final Vector<String> output = new Vector<String>();
		while ((text = in.readLine()) != null) {
			output.add(text);
		}
		in.close();
		return output;
	}

	public static void main(final String[] args) {
		if (args[0].equals("-m")) {
			java.lang.System.out.println("Maximum possible memory: " + Integer.toString(getWin32MaxHeap()) + "m");
		}
	}

}

/*
table of common system properties
comparisment between different operation systems

property           |Mac OS 9.22           |Mac OSX 10.1.5        |Windows 98            |Linux Kernel 2.4.22   |
-------------------+----------------------+----------------------+----------------------+----------------------+
file.encoding      |MacTEC                |MacRoman              |Cp1252                |ANSI_X3.4-1968        |
file.separator     |/                     |/                     |\                     |/                     |
java.class.path    |/hdisc/...            |.                     |.                     |/usr/lib/j2se/ext     |
java.class.version |45.3                  |47.0                  |48.0                  |47.0                  |
java.home          |/hdisc/...            |/System/Library/...   |C:\PROGRAM\...        |/usr/lib/j2se/1.3/jre |
java.vendor        |Apple Computer, Inc.  |Apple Computer, Inc.  |Sun Microsystems Inc. |Blackdown Java-Linux  |
java.version       |1.1.8                 |1.3.1                 |1.4.0_02              |1.3.1                 |
os.arch            |PowerPC               |ppc                   |x86                   |i386                  |
os.name            |Mac OS                |Mac OS X              |Windows 98            |Linux                 |
os.version         |9.2.2                 |10.1.5                |4.10                  |2.4.22                |
path.separator     |:                     |:                     |;                     |:                     |
user.dir           |/hdisc/...            |/mydir/...            |C:\mydir\...          |/home/public          |
user.home          |/hdisc/...            |/Users/myself         |C:\WINDOWS            |/home/public          |
user.language      |de                    |de                    |de                    |en                    |
user.name          |Bob                   |myself                |User                  |public                |
user.timezone      |ECT                   |Europe/Berlin         |Europe/Berlin         |                      |
-------------------+----------------------+----------------------+----------------------+----------------------+
 */

/*
  static struct browser possible_browsers[] = {
  {N_("Opera"), "opera"},
  {N_("Netscape"), "netscape"},
  {N_("Mozilla"), "mozilla"},
  {N_("Konqueror"), "kfmclient"},
  {N_("Galeon"), "galeon"},
  {N_("Firebird"), "mozilla-firebird"},
  {N_("Firefox"), "firefox"},
  {N_("Gnome Default"), "gnome-open"}
  };

  new:
  command = exec("netscape -remote " "\" openURL(\"%s\",new-window) "", uri);
  command = exec("opera -newwindow \"%s\"", uri);
  command = exec("opera -newpage \"%s\"", uri);
  command = exec("galeon -w \"%s\"", uri);
  command = exec("galeon -n \"%s\"", uri);
  command = exec("%s -remote \"openURL(\"%s\"," "new-window)\"", web_browser, uri);
  command = exec("%s -remote \"openURL(\"%s\"," "new-tab)\"", web_browser, uri);

  current:
  command = exec("netscape -remote " "\"openURL(\"%s\")\"", uri);
  command = exec("opera -remote " "\"openURL(\"%s\")\"", uri);
  command = exec("galeon \"%s\"", uri);
  command = exec("%s -remote \"openURL(\"%s\")\"", web_browser, uri);

  no option:
  command = exec("kfmclient openURL \"%s\"", uri);
  command = exec("gnome-open \"%s\"", uri);
 */
