// diskUsage.java
// -----------------------
// part of YaCy
// (C) by Detlef Reichl; detlef!reichl()gmx!org
// Pforzheim, Germany, 2008
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


// The HashMap contains the following values:
//
// key        = the device name e.g. /dev/hda1, on windows the drive e.g. c:
// value[0]   = the total space of the volume, on windows not used
// value[1]   = the free space of the volume

package de.anomic.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

// FIXME entfernen
import de.anomic.server.logging.serverLog;
import de.anomic.plasma.plasmaSwitchboard;

public class diskUsage {
// FIXME entfernen
    private serverLog log = new serverLog("DISK USAGE");
    private static final HashMap<String, long[]> diskUsages = new HashMap<String, long[]>();
    
    private static final ArrayList<String> allVolumes = new ArrayList<String>();
    private static final ArrayList<String> allMountPoints = new ArrayList<String>();
    private static final ArrayList<Boolean> usedVolumes = new ArrayList<Boolean>();
    
    private static final ArrayList<String> yacyUsedVolumes = new ArrayList<String>();
    private static final ArrayList<String> yacyUsedMountPoints = new ArrayList<String>();
    
    private static plasmaSwitchboard sb;
    private static int usedOS;
    private static boolean usable;
    private static String windowsCommand;
    private static String errorMessage;
    private static boolean consoleError;
    

        // Unix-like
    private final int AIX = 0;                    // IBM
    private final int BS2000 = 1;                 // Fujitsu Siemens (oficial BS2000/OSD)
    private final int BSD = 2;                    // all kind of BSD
    private final int HAIKU = 3;                  // like BeOS; does not have a JRE til now, but they are working on it
    private final int HP_UX = 4;                  // Hewlett-Packard
    private final int TRU64 = 5;                  // Hewlett-Packard
    private final int IRIX = 6;                   // sgi
    private final int LINUX = 7;                  // all kind of linux
    private final int MAC_OS_X = 8;               // Apple
    private final int MINIX = 9;                  // don't know if there even is a JRE for minix...
    private final int SOLARIS = 10;               // SUN  The latest SunOS version is from 1990 and that is much
                                                  //      older as Java 1.5. So we can ignore it.
    private final int UNICOS = 11;                // cray

    private final int UNIX_END = UNICOS;

        // Windows dos based
    private final int WINDOWS_95 = 12;
    private final int WINDOWS_98 = 13;
    private final int WINDOWS_ME = 14;
    
        // Windows WinNT based
    private final int WINDOWS_NT = 15;
    private final int WINDOWS_2000 = 16;
    private final int WINDOWS_XP = 17;
    private final int WINDOWS_SERVER = 18;
    private final int WINDOWS_VISTA = 19;
    
    String[] OSname =   {"aix", "bs2000", "bsd", "haiku", "hp-ux", "tru64", "irix", "linux", "mac os x", "minix",
                         "solaris", "unicos",
                         "windows 95", "windows 98", "windows me",
                         "windows nt", "windows 2000", "windows xp", "windows server", "windows vista"};
    
    //////////////////
    //  public API  //
    //////////////////

    public diskUsage (plasmaSwitchboard sb) {
        errorMessage = null;
        diskUsage.sb = sb;
        usedOS = getOS();
        if (usedOS == -1) {
            usable = false;
        } else {
            usable = true;

            // some kind of *nix
            if (usedOS <= UNIX_END) {
                dfUnix (true);
                for (int i = 0; i < allMountPoints.size(); i++)
                    usedVolumes.add(false);
                checkVolumesInUseUnix ("DATA");
                checkMapedSubDirs ();
                
                for (int i = 0; i < allVolumes.size(); i++){
                    if (usedVolumes.get(i) == true) {
                        yacyUsedVolumes.add(allVolumes.get (i));
                        yacyUsedMountPoints.add(allMountPoints.get (i));
                    }
                }

            // all Windows version
            } else {
                checkWindowsCommandVersion();
                getAllVolumesWindows ();
                for (int i = 0; i < allVolumes.size(); i++)
                    usedVolumes.add(false);
                checkStartVolume();
                checkMapedSubDirs ();
                for (int i = 0; i < allVolumes.size(); i++){
                    if (usedVolumes.get(i) == true)
                        yacyUsedVolumes.add(allVolumes.get (i));
                }
            }
            if (yacyUsedVolumes.size() < 1)
                usable = false;
        }
    }

    public HashMap<String, long[]> getDiskUsage () {
        if (!usable)
            return null;
        
        if (usedOS <= UNIX_END)
            dfUnix(false);
        else
            dfWindows ();            
        return diskUsages;
    }

    public boolean getUsable () {
        return usable;
    }
    
    public String getErrorMessage () {
        return errorMessage;
    }
    
    public int getNumUsedVolumes () {
        return yacyUsedVolumes.size();
    }




    ////////////
    //  Unix  //
    ////////////

    private void dfUnix(boolean getVolumesOnly) {
        if (!getVolumesOnly)
            diskUsages.clear ();

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
        
        ArrayList<String> processArgs = new ArrayList<String>();
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

        ArrayList<String> lines = getConsoleOutput(processArgs);
        if (consoleError) {
            errorMessage = "df:";
            Iterator<String> iter = lines.iterator();
            while (iter.hasNext()){
                errorMessage += "\n" + iter.next();
                usable = false;
                return;
            }
        }

        Iterator<String> iter = lines.iterator();
        while (iter.hasNext()){
            String line = iter.next();
            if (! line.startsWith ("/"))
                continue;
            String[] tokens = line.split(" ++", 6);
            if (tokens.length < 6)
                continue;
nextLine:
            if (getVolumesOnly) {
                for (int i = 0; i < allMountPoints.size(); i++) {
                    if (tokens[5].trim().compareTo(allMountPoints.get(i)) > 0) {
                        allMountPoints.add(i, tokens[5].trim());
                        allVolumes.add(i, tokens[0]);
                        break nextLine;
                    }
                }
                allMountPoints.add(allMountPoints.size(), tokens[5]);
                allVolumes.add(allVolumes.size(), tokens[0]);
            } else { 
                for (int i = 0; i < yacyUsedVolumes.size(); i++){
                    if (yacyUsedVolumes.get(i).equals(tokens[0])) {
                        long[] vals = new long[2];
                        try { vals[0] = new Long(tokens[1]); } catch (NumberFormatException e) { break nextLine; }
                        try { vals[1] = new Long(tokens[3]); } catch (NumberFormatException e) { break nextLine; }
                        vals[0] *= 1024;
                        vals[1] *= 1024;
                        diskUsages.put (yacyUsedMountPoints.get(i), vals);
                    }
                }
            }
        }
    }



    private void checkVolumesInUseUnix (String path) {
        File file = new File(path);
        File[] fileList = file.listFiles();
        String base;
        String dir;
        
        for (int i = 0; i < fileList.length; i++) {
            // ATTENTION! THIS LOOP NEEDS A TIME-OUT
            if (fileList[i].isDirectory()) {
                try {
                    dir = fileList[i].getCanonicalPath();
                } catch (IOException e) {
                    usable = false;
                    break;
                }
                if (!dir.endsWith ("HTCACHE")
                    && !dir.endsWith ("LOCALE")
                    && !dir.endsWith ("RANKING")
                    && !dir.endsWith ("RELEASE")
                    && !dir.endsWith ("collection.0028.commons")) {
                    checkVolumesInUseUnix (dir);
                } else {
                    checkPathUsage (dir);
                }
            } else {
                try {
                    base = fileList[i].getCanonicalPath();
                } catch (IOException e) {
                    usable = false;
                    break;
                }
                checkPathUsage (base);
            }
        }
    }



   ///////////////
   //  Windows  //
   ///////////////
   
   private void checkWindowsCommandVersion () {
        windowsCommand = null;
        String os = System.getProperty("os.name").toLowerCase();
        String[] oses = {"windows 95", "windows 98", "windows me"};
        
        for (int i = 0; i < oses.length; i++)
        {
            if (os.indexOf(oses[i]) >= 0){
                windowsCommand = "command.com";
                break;
            }
        }
        if (windowsCommand == null)
            windowsCommand = "cmd.exe";
    }
    
    private String dfWindowsGetConsoleOutput (String device) {
        ArrayList<String> processArgs = new ArrayList<String>();
        processArgs.add(windowsCommand);
        processArgs.add("/c");
        processArgs.add("dir");
        processArgs.add(device);
        

        ProcessBuilder processBuilder = new ProcessBuilder(processArgs);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {return null;}

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        String lastLine = null;
        while (true) {
            try {
                line = bufferedReader.readLine ();
            } catch (IOException e) { return null; }
            if (line == null)
              break;
            if (line.trim().length() > 0)
                lastLine = line;
        }
        return lastLine;
    }
    
    private void getAllVolumesWindows () {
        for (char c = 'C'; c <= 'Z'; c++) { // A and B are reserved for floppy on Windows
            String dirName = c + ":\\";
            if (dfWindowsGetConsoleOutput (dirName) != null) {
                allVolumes.add(String.valueOf(c));
            }
        }    
    }

    private void checkStartVolume() {
        File file = new File("DATA");

        String path = null;
        try { path = file.getCanonicalPath().toString(); } catch (IOException e) {
            errorMessage = "Cant get DATA directory";
            usable = false;
            return;
        }
        if (path.length() < 6)
          return;
          
        int index = -1;
        try { index = allVolumes.indexOf(path.substring(0, 1)); } catch (IndexOutOfBoundsException e) {
            errorMessage = "Start volume not found in all volumes";
            usable = false;
            return;
        }
        if (index > -1)
            usedVolumes.set(index, true);
        else {
            errorMessage = "No start volume found";
            usable = false;
        }
    }

    public void dfWindows () {
        for (int i = 0; i < yacyUsedVolumes.size(); i++){
            // in yacyUsedMountPoints aendern
            String line = dfWindowsGetConsoleOutput(yacyUsedVolumes.get(i) + ":\\");
            String[] tokens = line.trim().split(" ++");
            
            long[] vals = new long[2];
            vals[0] = -1;
            try { vals[1] = new Long(tokens[2].replaceAll("[.,]", "")); } catch (NumberFormatException e) {continue;}
            diskUsages.put (yacyUsedVolumes.get(i), vals);
        }
    }

   
   
   /////////////
   // common  //
   /////////////

    private int getOS () {
        String os = System.getProperty("os.name").toLowerCase();
        for (int i = 0; i < OSname.length; i++)
        {
            if (os.indexOf(OSname[i]) >= 0)
                return i;
        }
        errorMessage = "unknown operating system (" + System.getProperty("os.name") + ")";
        return -1;
    }

    private void checkMapedSubDirs () {
        //  FIXME whats about the secondary path???
        //   = (getConfig(plasmaSwitchboard.INDEX_SECONDARY_PATH, "");
        String[] pathes =  {plasmaSwitchboard.HTDOCS_PATH,        
                            plasmaSwitchboard.INDEX_PRIMARY_PATH,
                            plasmaSwitchboard.LISTS_PATH,
                            plasmaSwitchboard.PLASMA_PATH,
                            plasmaSwitchboard.RANKING_PATH,
                            plasmaSwitchboard.WORK_PATH};

        for (int i = 0; i < pathes.length; i++) {
            String path = null;
            try {
                path = sb.getConfigPath(pathes[i], "").getCanonicalPath().toString();
            } catch (IOException e) { continue; }
            if (path.length() > 0)
                checkPathUsage (path);
        }
    }
    
    private void checkPathUsage (String path) {
        for (int i = 0; i < allMountPoints.size(); i++){
            if (path.startsWith (allMountPoints.get(i))) {
                usedVolumes.set(i, true);
                return;
            }
        }
    }


    private ArrayList<String> getConsoleOutput (ArrayList<String> processArgs) {
        ArrayList<String> list = new ArrayList<String>();
        ProcessBuilder processBuilder = new ProcessBuilder(processArgs);
        Process process = null;
        consoleInterface inputStream = null;
        consoleInterface errorStream = null;
        consoleError = false;

        try {
            process = processBuilder.start();;

            inputStream = new consoleInterface(process.getInputStream());
            errorStream = new consoleInterface(process.getErrorStream());

            inputStream.start();
            errorStream.start();

            int retval = process.waitFor();

        } catch(IOException iox) {
            consoleError = true;
            log.logWarning("logpoint 0 " + iox.getMessage());
            list.add(iox.getMessage());
            return list;
        } catch(InterruptedException ix) {
            consoleError = true;
            log.logWarning("logpoint 1 " + ix.getMessage());
            list.add(ix.getMessage());
            return list;
        }
        list = inputStream.getOutput();
        if (list.isEmpty()) {
            consoleError = true;
            log.logWarning("logpoint 2 ");
            return errorStream.getOutput();
        } else
            return list;
    }

    public class consoleInterface extends Thread
    {
        private InputStream stream;
        private boolean getInputStream;
        private ArrayList<String> output;

        public consoleInterface (InputStream stream)
        {
            this.stream = stream;
            output = new ArrayList<String>();
        }

        public void run() {
            try {
                InputStreamReader input = new InputStreamReader(stream);
                BufferedReader buffer = new BufferedReader(input);
                String line = null;
                int tries = 1000;
                while (tries-- > 0) {
                    if (buffer.ready())
                        break;
                }
                log.logInfo("logpoint 3 " + tries + " tries");
                while((line = buffer.readLine()) != null) {
                        output.add(line);
                }
            } catch(IOException ix) { log.logWarning("logpoint 4 " +  ix.getMessage());}
        }
        
        public ArrayList<String> getOutput(){
            return output;
        }
    }
}

