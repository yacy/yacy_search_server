/**
 *  Browser
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 05.08.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Properties;

import net.yacy.cora.util.ConcurrentLog;

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

    // static initialization
    static {
        // check operation system type
        final Properties sysprop = System.getProperties();
        final String sysname = sysprop.getProperty("os.name","").toLowerCase();
        if (sysname.startsWith("mac os x")) {
            systemOS = systemMacOSX;
        } else if (sysname.startsWith("mac os")) {
            systemOS = systemMacOSC;
        } else if (sysname.startsWith("windows")) {
            systemOS = systemWindows;
        } else if ((sysname.startsWith("linux")) || (sysname.startsWith("unix"))) {
            systemOS = systemUnix;
        } else {
            systemOS = systemUnknown;
        }

        isMacArchitecture = ((systemOS == systemMacOSC) || (systemOS == systemMacOSX));
        isUnixFS = ((systemOS == systemMacOSX) || (systemOS == systemUnix));
        canExecUnix = ((isUnixFS) || (!((systemOS == systemMacOSC) || (systemOS == systemWindows))));
        isWindows = (systemOS == systemWindows);
        isWin32 = (isWindows && System.getProperty("os.arch", "").contains("x86"));

        // set up maximum path length according to system
        if (isWindows) {
            maxPathLength = 255;
        } else {
            maxPathLength = 65535;
        }
    }

    public static void openBrowser(final String url) {
        boolean head = System.getProperty("java.awt.headless", "").equals("false");
        try {
            if (systemOS == systemMacOSX) {
                openBrowserMac(url);
            } else if (systemOS == systemUnix) {
                //try {
                //    openBrowserUnixGeneric(url);
                //} catch (final Exception e) {
                    openBrowserUnixFirefox(url);
                //}
            } else if (systemOS == systemWindows) {
                openBrowserWin(url);
            } else {
                throw new RuntimeException("System unknown");
            }
        } catch (final Throwable e) {
            if (head) {
                try {
                    openBrowserJava(url);
                } catch (final Exception ee) {
                    logBrowserFail(url);
                }
            } else {
                logBrowserFail(url);
            }
        }
    }

    private static void openBrowserJava(final String url) throws Exception {
        Desktop.getDesktop().browse(new URI(url));
    }

    private static void openBrowserMac(final String url) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open location \"" + url + "\""});
        p.waitFor();
        if (p.exitValue() != 0) {
            throw new RuntimeException("Mac Exec Error: " + errorResponse(p));
        }
    }

    private static void openBrowserUnixFirefox(final String url) throws Exception {
        String cmd = "firefox -remote openURL(" + url + ")";
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        if (p.exitValue() != 0) {
            throw new RuntimeException("Unix Exec Error/Firefox: " + errorResponse(p));
        }
    }
    /*
    private static void openBrowserUnixGeneric(final String url) throws Exception {
        String cmd = "/etc/alternatives/www-browser "  + url;
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        if (p.exitValue() != 0) throw new RuntimeException("Unix Exec Error/generic: " + errorResponse(p));
    }
    */
    private static void openBrowserWin(final String url) throws Exception {
        // see forum at http://forum.java.sun.com/thread.jsp?forum=57&thread=233364&message=838441
        String cmd;
        if (System.getProperty("os.name").contains("2000")) {
            cmd = "rundll32 url.dll,FileProtocolHandler " + url;
        } else {
            cmd = "rundll32 url.dll,FileProtocolHandler \"" + url + "\"";
        }
        //cmd = "cmd.exe /c start javascript:document.location='" + url + "'";
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        if (p.exitValue() != 0) {
            throw new RuntimeException("EXEC ERROR: " + errorResponse(p));
        }
    }

    private static void logBrowserFail(final String url) {
        //if (e != null) Log.logException(e);
        ConcurrentLog.info("Browser", "please start your browser and open the following location: " + url);
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
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static void main(final String[] args) {
        if ("-u".equals(args[0])) {
            openBrowser(args[1]);
        }
    }
}
