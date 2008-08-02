// serverSystem.java 
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

package de.anomic.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

import de.anomic.server.logging.serverLog;

public final class serverSystem {

    // constants for system identification
    public static final int systemMacOSC  =  0; // 'classic' Mac OS 7.6.1/8.*/9.*
    public static final int systemMacOSX  =  1; // all Mac OS X
    public static final int systemUnix    =  2; // all Unix/Linux type systems
    public static final int systemWindows =  3; // all Windows 95/98/NT/2K/XP
    public static final int systemUnknown = -1; // any other system

    // constants for file type identification (Mac only)
    public static final String blankTypeString = "____";

    // system-identification statics
    public static int     systemOS = systemUnknown;
    public static boolean isMacArchitecture = false;
    public static boolean isUnixFS = false;
    public static boolean canExecUnix = false;
    public static boolean isWindows = false;

    // calculated system constants
    public static int maxPathLength = 65535;
    
    // Macintosh-specific statics
    private static Class<?>    macMRJFileUtils = null;
    private static Class<?>    macMRJOSType = null;
    private static Constructor<?> macMRJOSTypeConstructor = null;
    private static Object      macMRJOSNullObj = null;
    private static Method      macGetFileCreator = null;
    private static Method      macGetFileType = null;
    private static Method      macSetFileCreator = null;
    private static Method      macSetFileType = null;
    private static Method      macOpenURL = null;
    public  static final Hashtable<String, String> macFSTypeCache = new Hashtable<String, String>();
    public  static final Hashtable<String, String> macFSCreatorCache = new Hashtable<String, String>();

    // static initialization
    static {
	// check operation system type
	final Properties sysprop = System.getProperties();
	final String sysname = sysprop.getProperty("os.name","").toLowerCase();
	if (sysname.startsWith("mac os x")) systemOS = systemMacOSX;
	else if (sysname.startsWith("mac os")) systemOS = systemMacOSC;
	else if (sysname.startsWith("windows")) systemOS = systemWindows;
	else if ((sysname.startsWith("linux")) || (sysname.startsWith("unix"))) systemOS = systemUnix;
	else systemOS = systemUnknown;

	isMacArchitecture = ((systemOS == systemMacOSC) || (systemOS == systemMacOSX));
	isUnixFS = ((systemOS == systemMacOSX) || (systemOS == systemUnix));
    canExecUnix = ((isUnixFS) || (!((systemOS == systemMacOSC) || (systemOS == systemWindows))));
    isWindows = (systemOS == systemWindows);

	// set up the MRJ Methods through reflection
	if (isMacArchitecture) try {
	    macMRJFileUtils = Class.forName("com.apple.mrj.MRJFileUtils");
	    macMRJOSType = Class.forName("com.apple.mrj.MRJOSType");
	    macGetFileType = macMRJFileUtils.getMethod("getFileType", new Class[] {Class.forName("java.io.File")});
	    macGetFileCreator = macMRJFileUtils.getMethod("getFileCreator", new Class[] {Class.forName("java.io.File")});
	    macSetFileType = macMRJFileUtils.getMethod("setFileType", new Class[] {Class.forName("java.io.File"), macMRJOSType});
	    macSetFileCreator = macMRJFileUtils.getMethod("setFileCreator", new Class[] {Class.forName("java.io.File"), macMRJOSType});
	    macMRJOSTypeConstructor = macMRJOSType.getConstructor(new Class[] {Class.forName("java.lang.String")});
	    macOpenURL = macMRJFileUtils.getMethod("openURL", new Class[] {Class.forName("java.lang.String")});
	    final byte[] nullb = new byte[4];
	    for (int i = 0; i < 4; i++) nullb[i] = 0;
	    macMRJOSNullObj = macMRJOSTypeConstructor.newInstance(new Object[] {new String(nullb)});
	} catch (final Exception e) {
	    //e.printStackTrace();
	    macMRJFileUtils = null; macMRJOSType = null;
	}
    
        // set up maximum path length according to system
        if (isWindows) maxPathLength = 255; else maxPathLength = 65535;
    }

/*    public static boolean isWindows() {
        return systemOS == systemWindows;
    }*/
    
    public static Object getMacOSTS(final String s) {
	if ((isMacArchitecture) && (macMRJFileUtils != null)) try {
	    if ((s == null) || (s.equals(blankTypeString))) return macMRJOSNullObj;
	    return macMRJOSTypeConstructor.newInstance(new Object[] {s});
	} catch (final Exception e) {
	    return macMRJOSNullObj;
	}
	return null;
    }

    public static String getMacFSType(final File f) {
	if ((isMacArchitecture) && (macMRJFileUtils != null)) try {
	    final String s = macGetFileType.invoke(null, new Object[] {f}).toString();
	    if ((s == null) || (s.charAt(0) == 0)) return blankTypeString;
	    return s;
	} catch (final Exception e) {
	    return null;
	}
	return null;
    }

    public static String getMacFSCreator(final File f) {
	if ((isMacArchitecture) && (macMRJFileUtils != null)) try {
	    final String s = macGetFileCreator.invoke(null, new Object[] {f}).toString();
	    if ((s == null) || (s.charAt(0) == 0)) return blankTypeString;
	    return s;
	} catch (final Exception e) {
	    return null;
	}
	return null;
    }

    public static void setMacFSType(final File f, final String t) {
	if ((isMacArchitecture) && (macMRJFileUtils != null)) try {
	    macSetFileType.invoke(null, new Object[] {f, getMacOSTS(t)});
	} catch (final Exception e) {/*System.out.println(e.getMessage()); e.printStackTrace();*/}
    }

    public static void setMacFSCreator(final File f, final String t) {
	if ((isMacArchitecture) && (macMRJFileUtils != null)) try {
	    macSetFileCreator.invoke(null, new Object[] {f, getMacOSTS(t)});
	} catch (final Exception e) {/*System.out.println(e.getMessage()); e.printStackTrace();*/}
    }

    public static boolean aquireMacFSType(final File f) {
	if ((!(isMacArchitecture)) || (macMRJFileUtils == null)) return false;
	final String name = f.toString();

	// check file type
	final int dot = name.lastIndexOf(".");
	if ((dot < 0) || (dot + 1 >= name.length())) return false;
	final String type = getMacFSType(f);
	if ((type == null) || (type.equals(blankTypeString))) return false;
	final String ext = name.substring(dot + 1).toLowerCase();
	final String oldType = macFSTypeCache.get(ext);
	if ((oldType != null) && (oldType.equals(type))) return false;
	macFSTypeCache.put(ext, type);
	return true;
    }

    public static boolean aquireMacFSCreator(final File f) {
	if ((!(isMacArchitecture)) || (macMRJFileUtils == null)) return false;
	final String name = f.toString();

	// check creator
	final String creator = getMacFSCreator(f);
	if ((creator == null) || (creator.equals(blankTypeString))) return false;
	final String oldCreator = macFSCreatorCache.get(name);
	if ((oldCreator != null) && (oldCreator.equals(creator))) return false;
	macFSCreatorCache.put(name, creator);
	return true;
    }

    public static boolean applyMacFSType(final File f) {
	if ((!(isMacArchitecture)) || (macMRJFileUtils == null)) return false;
	final String name = f.toString();

	// reconstruct file type
	final int dot = name.lastIndexOf(".");
	if ((dot < 0) || (dot + 1 >= name.length())) return false;
	final String type = macFSTypeCache.get(name.substring(dot + 1).toLowerCase());
	if (type == null) return false;
	final String oldType = getMacFSType(f);
	if ((oldType != null) && (oldType.equals(type))) return false;
	setMacFSType(f, type);
	return getMacFSType(f).equals(type);
    }

    public static boolean applyMacFSCreator(final File f) {
	if ((!(isMacArchitecture)) || (macMRJFileUtils == null)) return false;
	final String name = f.toString();

	// reconstruct file creator
	final String creator = macFSCreatorCache.get(name);
	if (creator == null) return false;
	final String oldCreator = getMacFSCreator(f);
	if ((oldCreator != null) && (oldCreator.equals(creator))) return false;
	//System.out.println("***Setting creator for " + f.toString() + " to " + creator);
	setMacFSCreator(f, creator);
	return getMacFSCreator(f).equals(creator); // this is not always true! I guess it's caused by deprecation of the interface in 1.4er Apple Extensions
    }
    
    public static String infoString() {
	String s = "System=";
	if (systemOS == systemUnknown) s += "unknown";
	else if (systemOS == systemMacOSC) s += "Mac OS Classic";
	else if (systemOS == systemMacOSX) s += "Mac OS X";
	else if (systemOS == systemUnix) s += "Unix/Linux";
	else if (systemOS == systemWindows) s += "Windows";
	else s += "unknown";
	if (isMacArchitecture) s += ", Mac System Architecture";
	if (isUnixFS) s += ", has Unix-like File System";
	if (canExecUnix) s += ", can execute Unix-Shell Commands";
	return s;
    }

    /** generates a 2-character string containing information about the OS-type*/
    public static String infoKey() {
	String s = "";
	if (systemOS == systemUnknown) s += "o";
	else if (systemOS == systemMacOSC) s += "c";
	else if (systemOS == systemMacOSX) s += "x";
	else if (systemOS == systemUnix) s += "u";
	else if (systemOS == systemWindows) s += "w";
	else s += "o";
	if (isMacArchitecture) s += "m";
	if (isUnixFS) s += "f";
	if (canExecUnix) s += "e";
	return s;
    }

    private static String errorResponse(final Process p) {
	final BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
	String line, error = "";
	try {
	    while ((line = err.readLine()) != null) {
		error = line + "\n";
	    }
	    return error;
	} catch (final IOException e) {
	    return null;
	}
    }

    /*
    public static void openBrowser(URL url) {
	if (openBrowserJNLP(url)) return;
	openBrowserExec(url.toString(), "firefox");
    }

    private static boolean openBrowserJNLP(URL url) {
       try {
           // Lookup the javax.jnlp.BasicService object
           javax.jnlp.BasicService bs = (javax.jnlp.BasicService) javax.jnlp.ServiceManager.lookup("javax.jnlp.BasicService");
           // Invoke the showDocument method
           return bs.showDocument(url);
       } catch (Exception ue) {
           // Service is not supported
           return false;
       }
    }
    */

    public static void openBrowser(final String url) {
	openBrowser(url, "firefox");
    }

    public static void openBrowser(final String url, final String app) {
	try {
	    String cmd;
	    Process p;
	    if (systemOS != systemUnknown) {
            if (systemOS == systemMacOSC) {
            if ((isMacArchitecture) && (macMRJFileUtils != null)) {
                macOpenURL.invoke(null, new Object[] {url});
            }
            } else if (systemOS == systemMacOSX) {
            p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open location \"" + url + "\""});
            p.waitFor();
            if (p.exitValue() != 0) throw new RuntimeException("EXEC ERROR: " + errorResponse(p));
            } else if (systemOS == systemUnix) {
            cmd = app + " -remote openURL(" + url + ") &";
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            if (p.exitValue() != 0) {
                cmd = app + " "  + url + " &";
                p = Runtime.getRuntime().exec(cmd);
                p.waitFor();
            }
            if (p.exitValue() != 0) throw new RuntimeException("EXEC ERROR: " + errorResponse(p));
            } else if (systemOS == systemWindows) {
            // see forum at http://forum.java.sun.com/thread.jsp?forum=57&thread=233364&message=838441
            if (System.getProperty("os.name").contains("2000")) cmd = "rundll32 url.dll,FileProtocolHandler " + url;
            else cmd = "rundll32 url.dll,FileProtocolHandler \"" + url + "\"";
            //cmd = "cmd.exe /c start javascript:document.location='" + url + "'";
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            if (p.exitValue() != 0) throw new RuntimeException("EXEC ERROR: " + errorResponse(p));
            }
	    }
	} catch (final Exception e) {
	    System.out.println("please start your browser and open the following location: " + url);
	}
    }

    public static void deployScript(final File scriptFile, final String theScript) throws IOException {
        serverFileUtils.copy(theScript.getBytes(), scriptFile);
        if(!isWindows){ // set executable
	        try {
	            Runtime.getRuntime().exec("chmod 755 " + scriptFile.getAbsolutePath().replaceAll(" ", "\\ ")).waitFor();
	        } catch (final InterruptedException e) {
	            serverLog.logSevere("DEPLOY", "deploy of script file failed. file = " + scriptFile.getAbsolutePath(), e);
	            throw new IOException(e.getMessage());
	        }
        }
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
        starterFile.delete();
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
        return output;
    }
    
    public static void main(final String[] args) {
	//try{System.getProperties().list(new PrintStream(new FileOutputStream(new File("system.properties.txt"))));} catch (FileNotFoundException e) {}
	//System.out.println("nullstr=" + macMRJOSNullObj.toString());
	if (args[0].equals("-f")) {
	    final File f = new File(args[1]);
	    System.out.println("File " + f.toString() + ": creator = " + getMacFSCreator(f) + "; type = " + getMacFSType(f));
	}
	if (args[0].equals("-u")) {
	    openBrowser(args[1]);
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
