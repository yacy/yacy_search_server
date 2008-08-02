// diskUsage.java
// -----------------------
// part of YaCy
// (C) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
//
// [MC] made many changes to remove side-effect-based routines towards a more functional programming style
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


// The HashMap contains the following values:
//
// key        = the device name e.g. /dev/hda1, on windows the drive e.g. c:
// value[0]   = the total space of the volume, on windows not used
// value[1]   = the free space of the volume

package de.anomic.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.anomic.server.logging.serverLog;

public class diskUsage {
    
    private static serverLog log = new serverLog("DISK USAGE");
    
    private static final List<String> allVolumes = new ArrayList<String>();
    private static final List<String> allMountPoints = new ArrayList<String>();
    private static final List<Boolean> usedVolumes = new ArrayList<Boolean>();
    
    private static final List<String> yacyUsedVolumes = new ArrayList<String>();
    private static final List<String> yacyUsedMountPoints = new ArrayList<String>();
    
    private static int usedOS = -1;
    private static String usageError = null;
    private static String windowsCommand = null;
    

        // Unix-like
    private static final int AIX = 0;                    // IBM
    private static final int BS2000 = 1;                 // Fujitsu Siemens (oficial BS2000/OSD)
    //private static final int BSD = 2;                    // all kind of BSD
    private static final int HAIKU = 3;                  // like BeOS; does not have a JRE til now, but they are working on it
    //private static final int HP_UX = 4;                  // Hewlett-Packard
    private static final int TRU64 = 5;                  // Hewlett-Packard
    //private static final int IRIX = 6;                   // sgi
    //private static final int LINUX = 7;                  // all kind of linux
    //private static final int MAC_OS_X = 8;               // Apple
    private static final int MINIX = 9;                  // don't know if there even is a JRE for minix...
    //private static final int SOLARIS = 10;               // SUN
    //private static final int SUNOS = 11;                 // The latest SunOS version is from 1990 but the Solaris java refferer remains SunOS
    private static final int UNICOS = 12;                // cray

    private static final int UNIX_END = UNICOS;

        // Windows dos based
    //private static final int WINDOWS_95 = 13;
    //private static final int WINDOWS_98 = 14;
    //private static final int WINDOWS_ME = 15;
    
        // Windows WinNT based
    //private static final int WINDOWS_NT = 16;
    //private static final int WINDOWS_2000 = 17;
    //private static final int WINDOWS_XP = 18;
    //private static final int WINDOWS_SERVER = 19;
    //private static final int WINDOWS_VISTA = 20;
    
    // don't change order of names!
    private static final String[] OSname =   {
                         "aix", "bs2000", "bsd", "haiku", "hp-ux", "tru64", "irix", "linux", "mac os x", "minix",
                         "solaris", "sunos", "unicos",
                         "windows 95", "windows 98", "windows me",
                         "windows nt", "windows 2000", "windows xp", "windows server", "windows vista"};
    
    //////////////////
    //  public API  //
    //////////////////

    public static void init(final ArrayList<String> pathsToCheck) {
        if (usedOS >= 0) return; // prevent double initialization
        usedOS = getOS();
        if (usedOS == -1) {
            return;
        } else {
            usageError = null;

            if (usedOS <= UNIX_END) {
                // some kind of *nix
                dfUnixGetVolumes();
                for (int i = 0; i < allMountPoints.size(); i++)
                    usedVolumes.add(false);
                checkVolumesInUseUnix ("DATA");
                checkMappedSubDirs(pathsToCheck);
                
                for (int i = 0; i < allVolumes.size(); i++){
                    if (usedVolumes.get(i) == true) {
                        yacyUsedVolumes.add(allVolumes.get (i));
                        yacyUsedMountPoints.add(allMountPoints.get (i));
                    }
                }
            } else {
                // all Windows versions
                initWindowsCommandVersion();
                checkStartVolume();
                checkMappedSubDirs(pathsToCheck);
            }
            if (yacyUsedVolumes.size() < 1)
                usageError = "found no volumes";
        }
    }

    public static HashMap<String, long[]> getDiskUsage () {
        if (usageError != null) return null;
        
        if (usedOS <= UNIX_END)
            return dfUnix();
        else
            return dfWindows();
    }

    public static boolean isUsable() {
        return usageError == null;
    }
    
    public static int getNumUsedVolumes () {
        return yacyUsedVolumes.size();
    }
    
    public static String getErrorMessage() {
        return usageError;
    }

    ////////////
    //  Unix  //
    ////////////

    private static HashMap<String, long[]> dfUnix() {
        final HashMap<String, long[]> diskUsages = new HashMap<String, long[]>();
        try {
            final List<String> lines = dfUnixExec();
            nextLine: for (final String line: lines){
                if (line.charAt(0) != '/') continue;
                final String[] tokens = line.split(" +", 6);
                if (tokens.length < 6) continue;
                for (int i = 0; i < yacyUsedVolumes.size(); i++){
                    if (yacyUsedVolumes.get(i).equals(tokens[0])) {
                        final long[] vals = new long[2];
                        try { vals[0] = new Long(tokens[1]); } catch (final NumberFormatException e) { continue nextLine; }
                        try { vals[1] = new Long(tokens[3]); } catch (final NumberFormatException e) { continue nextLine; }
                        vals[0] *= 1024;
                        vals[1] *= 1024;
                        diskUsages.put(yacyUsedMountPoints.get(i), vals);
                    }
                }
            }
            return diskUsages;
        } catch (final IOException e) {
            usageError = "dfUnix: " + e.getMessage();
            return diskUsages;
        }
    }
    
    private static void dfUnixGetVolumes() {
        try {
            final List<String> lines = dfUnixExec();

            nextLine: for (final String line: lines){
                if (line.charAt(0) != '/') continue;
                final String[] tokens = line.split(" +", 6);
                if (tokens.length < 6) continue;
                for (int i = 0; i < allMountPoints.size(); i++) {
                    if (tokens[5].trim().compareTo(allMountPoints.get(i)) > 0) {
                        allMountPoints.add(i, tokens[5].trim());
                        allVolumes.add(i, tokens[0]);
                        continue nextLine;
                    }
                }
                allMountPoints.add(allMountPoints.size(), tokens[5]);
                allVolumes.add(allVolumes.size(), tokens[0]);
            }
        } catch (final IOException e) {
            usageError = "error during dfUnixGetVolumes: " + e.getMessage();
        }
    }
    
    private static List<String> dfUnixExec() throws IOException {
        
        // -k    set blocksize to 1024
        //   confirmed with tests:
        //     Linux
        //   verified with man pages or other docs:
        //     AIX, BS2000, *BSD, HP-UX, IRIX, minix, Mac OS X, Solaris, Tru64, UNICOS

        // -l    list local filesystems only
        //   confirmed with tests:
        //     Linux
        //   verified with man pages or other docs:
        //     AIX, BS2000, *BSD, HP-UX, IRIX, minix, Mac OS X, Solaris, UNICOS
        
        // please report all successes or fails for non-confirmed systems to
        // detlef!reichl()gmx!org. Thanks!
        
        final List<String> processArgs = new ArrayList<String>();
        processArgs.add("df");
        processArgs.add("-k");
        // Some systems need the additional -P parameter to return the data in Posix format.
        // Without it the mount point will be in the 7th and not in the 6th column
        if (usedOS == AIX || usedOS == BS2000 || usedOS == MINIX || usedOS == UNICOS)
            processArgs.add("-P");
        // Tru64 does not know the -l parameter
        // For haiku i didn't found online docs at all; so better exclude it
        if (usedOS != TRU64 && usedOS != HAIKU)
            processArgs.add("-l");

        final List<String> lines = getConsoleOutput(processArgs);
        return lines;
    }

    private static void checkVolumesInUseUnix (final String path) {
        final File file = new File(path);
        final File[] fileList = file.listFiles();
        String base;
        String dir;
        
        for (final File element : fileList) {
            if (element.isDirectory()) {
                try {
                    dir = element.getCanonicalPath();
                } catch (final IOException e) {
                    usageError = "checkVolumesInUseUnix(1): " + e.getMessage();
                    break;
                }
                if (dir.endsWith ("HTCACHE")
                        || dir.endsWith ("HTDOCS")
                        || dir.endsWith ("LOCALE")
                        || dir.endsWith ("RANKING")
                        || dir.endsWith ("RELEASE")
                        || dir.endsWith ("collection.0028.commons")) {
                    checkPathUsageUnix (dir);
                } else {
                    checkVolumesInUseUnix (dir);
                }
            } else {
                try {
                    base = element.getCanonicalPath();
                } catch (final IOException e) {
                    usageError = "checkVolumesInUseUnix(2): " + e.getMessage();
                    break;
                }
                checkPathUsageUnix (base);
            }
        }
    }

   ///////////////
   //  Windows  //
   ///////////////
   
   private static void initWindowsCommandVersion () {
        windowsCommand = null;
        final String os = System.getProperty("os.name").toLowerCase();
        final String[] oses = {"windows 95", "windows 98", "windows me"};
        
        for (final String element : oses) {
            if (os.indexOf(element) >= 0){
                windowsCommand = "command.com";
                break;
            }
        }
        if (windowsCommand == null)
            windowsCommand = "cmd.exe";
    }
    
    private static void checkStartVolume() {
        final File file = new File("DATA");

        String path = null;
        try { path = file.getCanonicalPath().toString(); } catch (final IOException e) {
            usageError = "Cant get DATA directory";
            return;
        }
        if (path.length() < 6)
          return;
        yacyUsedVolumes.add(path.substring(0, 1));
    }

    public static HashMap<String, long[]> dfWindows() {
        final HashMap<String, long[]> diskUsages = new HashMap<String, long[]>();
        for (int i = 0; i < yacyUsedVolumes.size(); i++){
            final List<String> processArgs = new ArrayList<String>();
            processArgs.add(windowsCommand);
            processArgs.add("/c");
            processArgs.add("dir");
            processArgs.add(yacyUsedVolumes.get(i) + ":\\");

            try {
                final List<String> lines = getConsoleOutput(processArgs);

                String line = "";
                for (int l = lines.size() - 1; l >= 0; l--) {
                    line = lines.get(l).trim();
                    if (line.length() > 0) break;
                }
                if (line.length() == 0) {
                    usageError = "unable to get free size of volume " + yacyUsedVolumes.get(i);
                    return diskUsages;
                }

                final String[] tokens = line.trim().split(" ++");
                final long[] vals = new long[2];
                vals[0] = -1;
                try { vals[1] = new Long(tokens[2].replaceAll("[.,]", "")); } catch (final NumberFormatException e) {continue;}
                diskUsages.put (yacyUsedVolumes.get(i), vals);
            } catch (final IOException e) {
                usageError = "dfWindows: " + e.getMessage();
                return diskUsages;
            }
            
        }
        return diskUsages;
    }
   
   /////////////
   // common  //
   /////////////

    private static int getOS() {
        final String os = System.getProperty("os.name").toLowerCase();
        for (int i = 0; i < OSname.length; i++)
        {
            if (os.indexOf(OSname[i]) >= 0)
                return i;
        }
        usageError = "unknown operating system (" + System.getProperty("os.name") + ")";
        return -1;
    }

    private static void checkMappedSubDirs (final ArrayList<String> pathsToCheck) {
        for (final String path : pathsToCheck) {
            if (usedOS <= UNIX_END)
                checkPathUsageUnix (path);
            else
                checkPathUsageWindows (path);
        }
    }
    
    private static void checkPathUsageUnix (final String path) {
        for (int i = 0; i < allMountPoints.size(); i++){
            if (path.startsWith (allMountPoints.get(i))) {
                usedVolumes.set(i, true);
                return;
            }
        }
    }

    private static void checkPathUsageWindows(final String path) {
        int index = -1;
        final String sub = path.substring(0, 1); // ?? nur ein character?
        try {
            index = yacyUsedVolumes.indexOf(sub);
        } catch (final IndexOutOfBoundsException e) {
            usageError = "internal error while checking used windows volumes";
            return;
        }
        if (index < 0)
            yacyUsedVolumes.add(sub);
    }

    private static List<String> getConsoleOutput(final List<String> processArgs) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(processArgs);
        Process process = null;
        consoleInterface inputStream = null;
        consoleInterface errorStream = null;

        try {
            process = processBuilder.start();

            inputStream = new consoleInterface(process.getInputStream(), "input", log);
            errorStream = new consoleInterface(process.getErrorStream(), "error", log);

            inputStream.start();
            errorStream.start();

            /*int retval =*/ process.waitFor();

        } catch (final IOException iox) {
            log.logWarning("logpoint 0 " + iox.getMessage());
            throw new IOException(iox.getMessage());
        } catch (final InterruptedException ix) {
            log.logWarning("logpoint 1 " + ix.getMessage());
            throw new IOException(ix.getMessage());
        }
        final List<String> list = inputStream.getOutput();
        if (list.isEmpty()) {
            final String error = errorStream.getOutput().toString();
            log.logWarning("logpoint 2: "+ error);
            throw new IOException("empty list: " + error);
        } else
            return list;
    }

}

