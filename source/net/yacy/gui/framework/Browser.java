/**
 *  Browser
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 05.08.2010 at http://yacy.net
 *  
 *  $LastChangedDate: 2010-06-16 17:11:21 +0200 (Mi, 16 Jun 2010) $
 *  $LastChangedRevision: 6922 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.gui.framework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Properties;

import net.yacy.kelondro.logging.Log;

public class Browser {

 // constants for system identification
    public static final int systemMacOSC  =  0; // 'classic' Mac OS 7.6.1/8.*/9.*
    public static final int systemMacOSX  =  1; // all Mac OS X
    public static final int systemUnix    =  2; // all Unix/Linux type systems
    public static final int systemWindows =  3; // all Windows 95/98/NT/2K/XP
    public static final int systemUnknown = -1; // any other system

    // constants for file type identification (Mac only)
    public static final String blankTypeString = "____";

    // system-identification statics
    public static final int     systemOS;
    public static final boolean isMacArchitecture;
    public static final boolean isUnixFS;
    public static final boolean canExecUnix;
    public static final boolean isWindows;
    public static final boolean isWin32;

    // calculated system constants
    public static int maxPathLength = 65535;

    // Macintosh-specific statics
    private static Class<?>    macMRJFileUtils = null;
    private static Method      macOpenURL = null;

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
        isWin32 = (isWindows && System.getProperty("os.arch", "").contains("x86"));

        // set up the MRJ Methods through reflection
        if (isMacArchitecture) try {
            macMRJFileUtils = Class.forName("com.apple.mrj.MRJFileUtils");
            macOpenURL = macMRJFileUtils.getMethod("openURL", new Class[] {Class.forName("java.lang.String")});
            final byte[] nullb = new byte[4];
            for (int i = 0; i < 4; i++) nullb[i] = 0;
        } catch (final Exception e) {
            //Log.logException(e);
            macMRJFileUtils = null;
        }

        // set up maximum path length according to system
        if (isWindows) maxPathLength = 255; else maxPathLength = 65535;
    }


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
                    cmd = app + " -remote openURL(" + url + ")";
                    p = Runtime.getRuntime().exec(cmd);
                    p.waitFor();
                    if (p.exitValue() != 0) {
                        cmd = app + " "  + url;
                        p = Runtime.getRuntime().exec(cmd);
                        // no error checking, because it blocks until firefox is closed
                    }
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
            System.err.println("ERROR "+ e.getClass() +" in openBrowser(): "+ e.getMessage());
            // browser could not be started automatically, tell user to do it
            System.out.println("please start your browser and open the following location: " + url);
        }
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
        } finally {
            try {
                err.close();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
    
    public static void main(final String[] args) {
        if (args[0].equals("-u")) {
            openBrowser(args[1]);
        }
    }
}
