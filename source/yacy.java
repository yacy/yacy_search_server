// yacy.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.yacy.net
// Frankfurt, Germany, 2004, 2005
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
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.GZIPOutputStream;

import de.anomic.data.translator;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.http.httpdFileHandler;
import de.anomic.http.httpdProxyHandler;
import de.anomic.http.httpc.response;
import de.anomic.index.indexEntryAttribute;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMap;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaURLPool;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.plasmaWordIndexAssortment;
import de.anomic.plasma.plasmaWordIndexAssortmentCluster;
import de.anomic.plasma.plasmaWordIndexClassicDB;
import de.anomic.plasma.plasmaWordIndexEntity;
import de.anomic.plasma.plasmaWordIndexEntryInstance;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeedDB;

/**
* This is the main class of YaCy. Several threads are started from here:
* <ul>
* <li>one single instance of the plasmaSwitchboard is generated, which itself
* starts a thread with a plasmaHTMLCache object. This object simply counts
* files sizes in the cache and terminates them. It also generates a
* plasmaCrawlerLoader object, which may itself start some more httpc-calling
* threads to load web pages. They terminate automatically when a page has
* loaded.
* <li>one serverCore - thread is started, which implements a multi-threaded
* server. The process may start itself many more processes that handle
* connections.lo
* <li>finally, all idle-dependent processes are written in a queue in
* plasmaSwitchboard which are worked off inside an idle-sensitive loop of the
* main process. (here)
* </ul>
*
* On termination, the following must be done:
* <ul>
* <li>stop feeding of the crawling process because it othervise fills the
* indexing queue.
* <li>say goodbye to connected peers and disable new connections. Don't wait for
* success.
* <li>first terminate the serverCore thread. This prevents that new cache
* objects are queued.
* <li>wait that the plasmaHTMLCache terminates (it should be normal that this
* process already has terminated).
* <li>then wait for termination of all loader process of the
* plasmaCrawlerLoader.
* <li>work off the indexing and cache storage queue. These values are inside a
* RAM cache and would be lost otherwise.
* <li>write all settings.
* <li>terminate.
* </ul>
*/

public final class yacy {
    // static objects
    private static String vString = "@REPL_VERSION@";
    private static float version = (float) 0.1;

    private static final String vDATE   = "@REPL_DATE@";
    private static final String copyright = "[ YaCy v" + vString + ", build " + vDATE + " by Michael Christen / www.yacy.net ]";
    private static final String hline = "-------------------------------------------------------------------------------";
   
    /**
    * Convert the combined versionstring into a pretty string.
    * FIXME: Why is this so complicated?
    *
    * @param s Combined version string
    * @return Pretty string where version and SVN-version are separated by a
    * slash, e.g. "0.435/01818"
    */
    public static String combinedVersionString2PrettyString(String s) {
        long svn;
        try {svn = (long) (100000000.0 * Double.parseDouble(s));} catch (NumberFormatException ee) {svn = 0;}
        double v = (Math.floor((double) svn / (double) 100000) / (double) 1000);
        String vStr = (v < 0.11) ? "dev" : Double.toString(v);
        //while (vStr.length() < 5) vStr = vStr + "0";
        svn = svn % 100000;
        if (svn > 4000) svn=svn / 10; // fix a previous bug online
        String svnStr = Long.toString(svn);
        while (svnStr.length() < 5) svnStr = "0" + svnStr;
        return vStr + "/" + svnStr;
    }

    /**
    * Combines the version of YaCy with the versionnumber from SVN to a
    * combined version
    *
    * @param version Current given version.
    * @param svn Current version given from svn.
    * @return String with the combined version
    */
    public static float versvn2combinedVersion(float v, int svn) {
        return (float) (((double) v * 100000000.0 + ((double) svn)) / 100000000.0);
    }

    /**
    * Starts up the whole application. Sets up all datastructures and starts
    * the main threads.
    *
    * @param homePath Root-path where all information is to be found.
    * @param startupFree free memory at startup time, to be used later for statistics
    */
    private static void startup(String homePath, long startupMemFree, long startupMemTotal) {
        long startup = System.currentTimeMillis();
        String restart = "false";
        int oldRev=0;
        int newRev=0;
        
        try {
            // start up
            System.out.println(copyright);
            System.out.println(hline);

            // check java version
            try {
                /*String[] check =*/ "a,b".split(","); // split needs java 1.4
            } catch (NoSuchMethodError e) {
                System.err.println("STARTUP: Java Version too low. You need at least Java 1.4.2 to run YaCy");
                Thread.sleep(3000);
                System.exit(-1);
            }
            
            // ensure that there is a DATA directory
            File f = new File(homePath); if (!(f.exists())) f.mkdirs();
            f = new File(homePath, "DATA/"); if (!(f.exists())) f.mkdirs();
            
            // setting up logging
            f = new File(homePath, "DATA/LOG/"); if (!(f.exists())) f.mkdirs();
            if (!((new File(homePath, "DATA/LOG/yacy.logging")).exists())) try {
                serverFileUtils.copy(new File(homePath, "yacy.logging"), new File(homePath, "DATA/LOG/yacy.logging"));
            }catch (IOException e){
                System.out.println("could not copy yacy.logging");
            }
            try{
                serverLog.configureLogging(new File(homePath, "DATA/LOG/yacy.logging"));
            } catch (IOException e) {
                System.out.println("could not find logging properties in homePath=" + homePath);
                e.printStackTrace();
            }
            serverLog.logConfig("STARTUP", "java version " + System.getProperty("java.version", "no-java-version"));
            serverLog.logConfig("STARTUP", "Application Root Path: " + homePath);
            serverLog.logConfig("STARTUP", "Time Zone: UTC" + serverDate.UTCDiffString() + "; UTC+0000 is " + System.currentTimeMillis());
            serverLog.logConfig("STARTUP", "Maximum file system path length: " + serverSystem.maxPathLength);

            // create data folder
            final File dataFolder = new File(homePath, "DATA");
            if (!(dataFolder.exists())) dataFolder.mkdir();

            // Testing if the yacy archive file were unzipped correctly.
            // This test is needed because of classfile-names longer than 100 chars
            // which could cause problems with incompatible unzip software.
            // See:
            // - http://www.yacy-forum.de/viewtopic.php?t=1763
            // - http://www.yacy-forum.de/viewtopic.php?t=715
            // - http://www.yacy-forum.de/viewtopic.php?t=1674
            File unzipTest = new File(homePath,"doc/This_is_a_test_if_the_archive_file_containing_YaCy_was_unpacked_correctly_If_not_please_use_gnu_tar_instead.txt");
            if (!unzipTest.exists()) {
                String errorMsg = "The archive file containing YaCy was not unpacked correctly. " +
                                  "Please use 'GNU-Tar' or upgrade to a newer version of your unzip software.\n" +
                                  "For detailed information on this bug see: " + 
                                  "http://www.yacy-forum.de/viewtopic.php?t=715";
                System.err.println(errorMsg);
                serverLog.logSevere("STARTUP", errorMsg);
                System.exit(1); 
            }
                    
            
            final plasmaSwitchboard sb = new plasmaSwitchboard(homePath, "yacy.init", "DATA/SETTINGS/httpProxy.conf");
            
            // save information about available memory at startup time
            sb.setConfig("memoryFreeAfterStartup", startupMemFree);
            sb.setConfig("memoryTotalAfterStartup", startupMemTotal);
            
            // hardcoded, forced, temporary value-migration
            sb.setConfig("htTemplatePath", "htroot/env/templates");
            sb.setConfig("parseableExt", "html,htm,txt,php,shtml,asp");

            // set default = no restart
            sb.setConfig("restart", "false");

            // if we are running an SVN version, we try to detect the used svn revision now ...
            final Properties buildProp = new Properties();
            File buildPropFile = null;
            try {
                buildPropFile = new File(homePath,"build.properties");
                buildProp.load(new FileInputStream(buildPropFile));
            } catch (Exception e) {
                serverLog.logWarning("STARTUP", buildPropFile.toString() + " not found in settings path");
            }
            
            oldRev=Integer.parseInt(sb.getConfig("svnRevision", "0"));
            try {
                if (buildProp.containsKey("releaseNr")) {
                    // this normally looks like this: $Revision$
                    final String svnReleaseNrStr = buildProp.getProperty("releaseNr");
                    final Pattern pattern = Pattern.compile("\\$Revision:\\s(.*)\\s\\$",Pattern.DOTALL+Pattern.CASE_INSENSITIVE);
                    final Matcher matcher = pattern.matcher(svnReleaseNrStr);
                    if (matcher.find()) {
                        final String svrReleaseNr = matcher.group(1);
                        try {
                            try {version = Float.parseFloat(vString);} catch (NumberFormatException e) {version = (float) 0.1;}
                            version = versvn2combinedVersion(version, Integer.parseInt(svrReleaseNr));
                        } catch (NumberFormatException e) {}
                        sb.setConfig("svnRevision", svrReleaseNr);
                    }
                }
                newRev=Integer.parseInt(sb.getConfig("svnRevision", "0"));
            } catch (Exception e) {
                System.err.println("Unable to determine the currently used SVN revision number.");
            }

            sb.setConfig("version", Float.toString(version));
            sb.setConfig("vString", combinedVersionString2PrettyString(Float.toString(version)));
            sb.setConfig("vdate", vDATE);
            sb.setConfig("applicationRoot", homePath);
            sb.setConfig("startupTime", Long.toString(startup));
            serverLog.logConfig("STARTUP", "YACY Version: " + version + ", Built " + vDATE);
            yacyCore.latestVersion = version;

            // read environment
            int timeout       = Integer.parseInt(sb.getConfig("httpdTimeout", "60000"));
            if (timeout < 60000) timeout = 60000;

            // create some directories
            final File htRootPath = new File(homePath, sb.getConfig("htRootPath", "htroot"));
            final File htDocsPath = new File(homePath, sb.getConfig("htDocsPath", "DATA/HTDOCS"));
            //final File htTemplatePath = new File(homePath, sb.getConfig("htTemplatePath","htdocs"));

            // create default notifier picture
            //TODO: Use templates instead of copying images ...
            if (!((new File(htDocsPath, "notifier.gif")).exists())) try {
                serverFileUtils.copy(new File(htRootPath, "env/grafics/empty.gif"),
                                     new File(htDocsPath, "notifier.gif"));
            } catch (IOException e) {}

            if (!(htDocsPath.exists())) htDocsPath.mkdir();
            final File htdocsDefaultReadme = new File(htDocsPath, "readme.txt");
            if (!(htdocsDefaultReadme.exists())) try {serverFileUtils.write((
                    "This is your root directory for individual Web Content\r\n" +
                    "\r\n" +
                    "Please place your html files into the www subdirectory.\r\n" +
                    "The URL of that path is either\r\n" +
                    "http://www.<your-peer-name>.yacy    or\r\n" +
                    "http://<your-ip>:<your-port>/www\r\n" +
                    "\r\n" +
                    "Other subdirectories may be created; they map to corresponding sub-domains.\r\n" +
                    "This directory shares it's content with the applications htroot path, so you\r\n" +
                    "may access your yacy search page with\r\n" +
                    "http://<your-peer-name>.yacy/\r\n" +
                    "\r\n").getBytes(), htdocsDefaultReadme);} catch (IOException e) {
                        System.out.println("Error creating htdocs readme: " + e.getMessage());
                    }

            final File wwwDefaultPath = new File(htDocsPath, "www");
            if (!(wwwDefaultPath.exists())) wwwDefaultPath.mkdir();

            final File wwwDefaultClass = new File(wwwDefaultPath, "welcome.class");
            //if ((!(wwwDefaultClass.exists())) || (wwwDefaultClass.length() != (new File(htRootPath, "htdocsdefault/welcome.class")).length())) try {
            if((new File(htRootPath, "htdocsdefault/welcome.java")).exists())
                serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.java"), new File(wwwDefaultPath, "welcome.java"));
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.class"), wwwDefaultClass);
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.html"), new File(wwwDefaultPath, "welcome.html"));
            //} catch (IOException e) {}

            final File shareDefaultPath = new File(htDocsPath, "share");
            if (!(shareDefaultPath.exists())) shareDefaultPath.mkdir();

            final File shareDefaultClass = new File(shareDefaultPath, "dir.class");
            //if ((!(shareDefaultClass.exists())) || (shareDefaultClass.length() != (new File(htRootPath, "htdocsdefault/dir.class")).length())) try {
            if((new File(htRootPath, "htdocsdefault/dir.java")).exists())
                serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.java"), new File(shareDefaultPath, "dir.java"));
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.class"), shareDefaultClass);
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.html"), new File(shareDefaultPath, "dir.html"));
            //} catch (IOException e) {}

            migration.migrate(sb, oldRev, newRev);
            
            // start main threads
            final String port = sb.getConfig("port", "8080");
            try {
                final httpd protocolHandler = new httpd(sb, new httpdFileHandler(sb), new httpdProxyHandler(sb));
                final serverCore server = new serverCore(
                        timeout /*control socket timeout in milliseconds*/,
                        true /* block attacks (wrong protocol) */,
                        protocolHandler /*command class*/,
                        sb,
                        30000 /*command max length incl. GET args*/);
                server.setName("httpd:"+port);
                server.setPriority(Thread.MAX_PRIORITY);
                server.setObeyIntermission(false);
                if (server == null) {
                    serverLog.logSevere("STARTUP", "Failed to start server. Probably port " + port + " already in use.");
                } else {
                    // first start the server
                    sb.deployThread("10_httpd", "HTTPD Server/Proxy", "the HTTPD, used as web server and proxy", null, server, 0, 0, 0, 0);
                    //server.start();

                    // open the browser window
                    final boolean browserPopUpTrigger = sb.getConfig("browserPopUpTrigger", "true").equals("true");
                    if (browserPopUpTrigger) {
                        String  browserPopUpPage        = sb.getConfig("browserPopUpPage", "ConfigBasic.html");
                        boolean properPW = (sb.getConfig("adminAccount", "").length() == 0) && (sb.getConfig("adminAccountBase64MD5", "").length() > 0);
                        if (!properPW) browserPopUpPage = "ConfigBasic.html";
                        final String  browserPopUpApplication = sb.getConfig("browserPopUpApplication", "netscape");
                        serverSystem.openBrowser("http://localhost:" + serverCore.getPortNr(port) + "/" + browserPopUpPage, browserPopUpApplication);
                    }

                    //Copy the shipped locales into DATA
                    final File localesPath = new File(homePath, sb.getConfig("localesPath", "DATA/LOCALE"));
                    final File defaultLocalesPath = new File(homePath, "locales");
                    

                    try{
                        final File[] defaultLocales = defaultLocalesPath.listFiles();
                        localesPath.mkdirs();
                        for(int i=0;i < defaultLocales.length; i++){
                            if(defaultLocales[i].getName().endsWith(".lng"))
                                serverFileUtils.copy(defaultLocales[i], new File(localesPath, defaultLocales[i].getName()));
                        }
                        serverLog.logInfo("STARTUP", "Copied the default locales to DATA/LOCALE");
                    }catch(NullPointerException e){
                        serverLog.logSevere("STARTUP", "Nullpointer Exception while copying the default Locales");
                    }

                    //regenerate Locales from Translationlist, if needed
                    final String lang = sb.getConfig("htLocaleSelection", "");
                    if(! lang.equals("") && ! lang.equals("default") ){ //locale is used
                        String currentRev = "";
                        try{
                            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File( sb.getConfig("htLocalePath", "DATA/HTDOCS/locale"), lang+"/version" ))));
                            currentRev = br.readLine();
                            br.close();
                        }catch(IOException e){
                            //Error
                        }

                        try{ //seperate try, because we want this, even if the file "version" does not exist.
                            if(! currentRev.equals(sb.getConfig("svnRevision", "")) ){ //is this another version?!
                                final File sourceDir = new File(sb.getConfig("htRootPath", "htroot"));
                                final File destDir = new File(sb.getConfig("htLocalePath", "DATA/HTDOCS/locale"), lang);
                                
                              if(translator.translateFilesRecursive(sourceDir, destDir, new File("DATA/LOCALE/"+lang+".lng"), "html,template,inc", "locale")){ //translate it
                                    //write the new Versionnumber
                                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                                    bw.write(sb.getConfig("svnRevision", "Error getting Version"));
                                    bw.close();
                                }
                            }
                        }catch(IOException e){
                            //Error
                        }
                    }

                    // registering shutdown hook
                    serverLog.logConfig("STARTUP", "Registering Shutdown Hook");
                    final Runtime run = Runtime.getRuntime();
                    run.addShutdownHook(new shutdownHookThread(Thread.currentThread(), sb));

                    // save information about available memory after all initializations
                    //try {
                        sb.setConfig("memoryFreeAfterInitBGC", Runtime.getRuntime().freeMemory());
                        sb.setConfig("memoryTotalAfterInitBGC", Runtime.getRuntime().totalMemory());
                        System.gc();
                        sb.setConfig("memoryFreeAfterInitAGC", Runtime.getRuntime().freeMemory());
                        sb.setConfig("memoryTotalAfterInitAGC", Runtime.getRuntime().totalMemory());
                    //} catch (ConcurrentModificationException e) {}
                    
                    // wait for server shutdown
                    try {
                        sb.waitForShutdown();
                    } catch (Exception e) {
                        serverLog.logSevere("MAIN CONTROL LOOP", "PANIC: " + e.getMessage(),e);
                    }
                    restart = sb.getConfig("restart", "false");
                    
                    // shut down
                    serverLog.logConfig("SHUTDOWN", "caught termination signal");
                    server.terminate(false);
                    server.interrupt();
                    if (server.isAlive()) try {
                        URL u = new URL("http://localhost:" + serverCore.getPortNr(port));
                        httpc.wget(u, u.getHost(), 1000, null, null, null); // kick server
                        serverLog.logConfig("SHUTDOWN", "sent termination signal to server socket");
                    } catch (IOException ee) {
                        serverLog.logConfig("SHUTDOWN", "termination signal to server socket missed (server shutdown, ok)");
                    }

                    // idle until the processes are down
                    while (server.isAlive()) {
                        Thread.sleep(2000); // wait a while
                    }
                    serverLog.logConfig("SHUTDOWN", "server has terminated");
                    sb.close();
                }
            } catch (Exception e) {
                serverLog.logSevere("STARTUP", "Unexpected Error: " + e.getClass().getName(),e);
                //System.exit(1);
            }
        } catch (Exception ee) {
            serverLog.logSevere("STARTUP", "FATAL ERROR: " + ee.getMessage(),ee);
        }

        // restart YaCy
        if (restart.equals("true")) {
            serverLog.logConfig("SHUTDOWN", "RESTART...");
            long count = 0;
            if (Thread.activeCount() > 1) {
                serverLog.logConfig("SHUTDOWN", "Wait maximally 5 minutes for " + (Thread.activeCount() - 1) + " running threads to restart YaCy");
                while (Thread.activeCount() > 1 && count <= 60) { // wait 5 minutes                 
                    count++;
                    try { Thread.sleep(5000); } catch (InterruptedException e) {}
                }
            }
            if (count < 60) {
                System.gc();
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
                startupMemFree  = Runtime.getRuntime().freeMemory();  // the amount of free memory in the Java Virtual Machine
                startupMemTotal = Runtime.getRuntime().totalMemory(); // the total amount of memory in the Java virtual machine; may vary over time
                startup(homePath, startupMemFree, startupMemTotal);
            } else {
                serverLog.logConfig("SHUTDOWN", "RESTART BREAK, more than 5 minutes waited to try a restart, goodbye. (this is the last line)");
//              serverLog.logConfig("SHUTDOWN", "RESTART BREAK, getAllStackTraces()\n" + Thread.getAllStackTraces()); // needs java 1.5
            }
        } else {
            serverLog.logConfig("SHUTDOWN", "goodbye. (this is the last line)");
        }
        try {
            System.exit(0);
        } catch (Exception e) {} // was once stopped by de.anomic.net.ftpc$sm.checkExit(ftpc.java:1790)
    }

    /**
    * Loads the configuration from the data-folder.
    * FIXME: Why is this called over and over again from every method, instead
    * of setting the configurationdata once for this class in main?
    *
    * @param mes Where are we called from, so that the errormessages can be
    * more descriptive.
    * @param homePath Root-path where all the information is to be found.
    * @return Properties read from the configurationfile.
    */
    private static Properties configuration(String mes, String homePath) {
        serverLog.logConfig(mes, "Application Root Path: " + homePath.toString());

        // read data folder
        File dataFolder = new File(homePath, "DATA");
        if (!(dataFolder.exists())) {
            serverLog.logSevere(mes, "Application was never started or root path wrong.");
            System.exit(-1);
        }

        Properties config = new Properties();
        try {
            config.load(new FileInputStream(new File(homePath, "DATA/SETTINGS/httpProxy.conf")));
        } catch (FileNotFoundException e) {
            serverLog.logSevere(mes, "could not find configuration file.");
            System.exit(-1);
        } catch (IOException e) {
            serverLog.logSevere(mes, "could not read configuration file.");
            System.exit(-1);
        }

        return config;
    }

    static void shutdown() {
        String applicationRoot = System.getProperty("user.dir").replace('\\', '/');
        shutdown(applicationRoot);
    }
    
    /**
    * Call the shutdown-page from yacy to tell it to shut down. This method is
    * called if you start yacy with the argument -shutdown.
    *
    * @param homePath Root-path where all the information is to be found.
    */
    static void shutdown(String homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);

        Properties config = configuration("REMOTE-SHUTDOWN", homePath);

        // read port
        int port = serverCore.getPortNr(config.getProperty("port", "8080"));

        // read password
        String encodedPassword = (String) config.get("adminAccountBase64MD5");
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        httpHeader requestHeader = new httpHeader();
        requestHeader.put("Authorization", "realm=" + encodedPassword); // for http-authentify
        try {
            httpc con = httpc.getInstance("localhost", "localhost", port, 10000, false);
            httpc.response res = con.GET("Steering.html?shutdown=", requestHeader);

            // read response
            if (res.status.startsWith("2")) {
                serverLog.logConfig("REMOTE-SHUTDOWN", "YACY accepted shutdown command.");
                serverLog.logConfig("REMOTE-SHUTDOWN", "Stand by for termination, which may last some seconds.");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                res.writeContent(bos, null);
                con.close();
            } else {
                serverLog.logSevere("REMOTE-SHUTDOWN", "error response from YACY socket: " + res.status);
                System.exit(-1);
            }
        } catch (IOException e) {
            serverLog.logSevere("REMOTE-SHUTDOWN", "could not establish connection to YACY socket: " + e.getMessage());
            System.exit(-1);
        }

        // finished
        serverLog.logConfig("REMOTE-SHUTDOWN", "SUCCESSFULLY FINISHED remote-shutdown:");
        serverLog.logConfig("REMOTE-SHUTDOWN", "YACY will terminate after working off all enqueued tasks.");
    }

    /**
    * This method gets all found words and outputs a statistic about the score
    * of the words. The output of this method can be used to create stop-word
    * lists. This method will be called if you start yacy with the argument
    * -genwordstat.
    * FIXME: How can stop-word list be created from this output? What type of
    * score is output?
    *
    * @param homePath Root-Path where all the information is to be found.
    */
    private static void genWordstat(String homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);

        Properties config = configuration("GEN-WORDSTAT", homePath);

        // load words
        serverLog.logInfo("GEN-WORDSTAT", "loading words...");
        HashMap words = loadWordMap(new File(homePath, "yacy.words"));

        // find all hashes
        serverLog.logInfo("GEN-WORDSTAT", "searching all word-hash databases...");
        File dbRoot = new File(homePath, config.getProperty("dbPath"));
        enumerateFiles ef = new enumerateFiles(new File(dbRoot, "WORDS"), true, false, true, true);
        File f;
        String h;
        kelondroMScoreCluster hs = new kelondroMScoreCluster();
        while (ef.hasMoreElements()) {
            f = (File) ef.nextElement();
            h = f.getName().substring(0, plasmaURL.urlHashLength);
            hs.addScore(h, (int) f.length());
        }

        // list the hashes in reverse order
        serverLog.logInfo("GEN-WORDSTAT", "listing words in reverse size order...");
        String w;
        Iterator i = hs.scores(false);
        while (i.hasNext()) {
            h = (String) i.next();
            w = (String) words.get(h);
            if (w == null) System.out.print("# " + h); else System.out.print(w);
            System.out.println(" - " + hs.getScore(h));
        }

        // finished
        serverLog.logConfig("GEN-WORDSTAT", "FINISHED");
    }

    /**
    * Migrates the PLASMA WORDS structure to the assortment cache if possible.
    * This method will be called if you start yacy with the argument
    * -migratewords.
    * Caution: This might take a long time to finish. Don't interrupt it!
    * FIXME: Shouldn't this method be private?
    *
    * @param homePath Root-path where all the information is to be found.
    */
    public static void migrateWords(String homePath) {
        // run with "java -classpath classes yacy -migratewords"
        try {serverLog.configureLogging(new File(homePath, "yacy.logging"));} catch (Exception e) {}
        File dbroot = new File(new File(homePath), "DATA/PLASMADB");
        serverLog log = new serverLog("WORDMIGRATION");
        log.logInfo("STARTING MIGRATION");
        plasmaWordIndex wordIndexCache = new plasmaWordIndex(dbroot, 20000, log);
        enumerateFiles words = new enumerateFiles(new File(dbroot, "WORDS"), true, false, true, true);
        String wordhash;
        File wordfile;
        Object migrationStatus;
        while (words.hasMoreElements())
            try {
                wordfile = (File) words.nextElement();
                wordhash = wordfile.getName().substring(0, 12);
                // System.out.println("NOW: " + wordhash);
                migrationStatus = wordIndexCache.migrateWords2Assortment(wordhash);
                if (migrationStatus instanceof Integer) {
                    int migrationCount = ((Integer) migrationStatus).intValue();
                    if (migrationCount == 0)
                        log.logInfo("SKIPPED  " + wordhash + ": empty");
                    else if (migrationCount > 0)
                        log.logInfo("MIGRATED " + wordhash + ": " + migrationCount + " entries");
                    else
                        log.logInfo("REVERSED " + wordhash + ": " + (-migrationCount) + " entries");
                } else if (migrationStatus instanceof String) {
                    log.logInfo("SKIPPED  " + wordhash + ": " + migrationStatus);
                }
            } catch (Exception e) {
                log.logSevere("Exception", e);
            }
        log.logInfo("FINISHED MIGRATION JOB, WAIT FOR DUMP");
        wordIndexCache.close(60);
        log.logInfo("TERMINATED MIGRATION");
    }
    
    public static void importAssortment(String homePath, String importAssortmentFileName) {
        if (homePath == null) throw new NullPointerException();
        if (importAssortmentFileName == null) throw new NullPointerException();       
        
        // initialize logging
        try {serverLog.configureLogging(new File(homePath, "yacy.logging"));} catch (Exception e) {}
        serverLog log = new serverLog("ASSORTMENT-IMPORT");
        log.logInfo("STARTING ASSORTMENT-IMPORT");     
        
        // initializing importAssortmentFile
        String errorMsg = null;
        File importAssortmentFile = new File(importAssortmentFileName);
        if (!importAssortmentFile.exists()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' does not exist.";
        else if (importAssortmentFile.isDirectory()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is a directory.";
        else if (!importAssortmentFile.canRead()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is not readable.";
        else if (!importAssortmentFile.canWrite()) errorMsg = "AssortmentFile '" + importAssortmentFile + "' is not writeable.";
        if (errorMsg != null) {
            log.logSevere(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        File importAssortmentPath = null;
        int assortmentNr = -1;
        try {
            importAssortmentPath = new File(importAssortmentFile.getParent());
            assortmentNr = Integer.valueOf(importAssortmentFile.getName().substring("indexAssortment".length(),importAssortmentFile.getName().length()-3)).intValue();
        } catch (NumberFormatException e) {
            errorMsg = "Unable to parse the assortment file number.";
            log.logSevere(errorMsg,e);
            throw new IllegalStateException(errorMsg);
        }
        
        plasmaWordIndex homeWordIndex = null;
        try {
            // initializing assortment source file
            log.logInfo("Initializing source assortment file");
            plasmaWordIndexAssortment assortmentFile = new plasmaWordIndexAssortment(importAssortmentPath,assortmentNr,16*1024*1024, log);
            
            // configure destination DB
            log.logInfo("Initializing destination word index db.");
            File homeDBroot = new File(new File(homePath), "DATA/PLASMADB");
            if (!homeDBroot.exists()) errorMsg = "DB Directory '" + homeDBroot + "' does not exist.";
            else if (!homeDBroot.isDirectory()) errorMsg = "DB Directory '" + homeDBroot + "' is not directory.";
            else if (!homeDBroot.canRead()) errorMsg = "DB Directory '" + homeDBroot + "' is not readable.";
            else if (!homeDBroot.canWrite()) errorMsg = "DB Directory '" + homeDBroot + "' is not writeable.";
            if (errorMsg != null) {
                log.logSevere(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            
            // opening the destination database
            homeWordIndex = new plasmaWordIndex(homeDBroot, 16*1024*1024, log);        
            
            // iterating through the content
            log.logInfo("Importing assortment file containing '" + assortmentFile.size() + "' entities.");
            
            int wordEntityCount = 0, wordEntryCount = 0;
            Iterator contentIter = assortmentFile.content();
            while (contentIter.hasNext()) {
                wordEntityCount++;
                
                byte[][] row = (byte[][]) contentIter.next();
                String hash = new String(row[0]);
                plasmaWordIndexEntryContainer container = assortmentFile.row2container(hash, row);
                wordEntryCount += container.size();
                
                // importing entity container to home db
                homeWordIndex.addEntries(container, System.currentTimeMillis(), true);
                
                if (wordEntityCount % 500 == 0) {
                    log.logFine(wordEntityCount + " word entities processed so far.");
                }
                if (wordEntryCount % 2000 == 0) {
                    log.logFine(wordEntryCount + " word entries processed so far.");
                }                
            }
        } catch (Error e) {
            log.logWarning("Error", e);
        } catch (Exception e) {
            log.logWarning("Exception", e);
        } finally {
            log.logInfo("ASSORTMENT-IMPORT FINISHED");
            if (homeWordIndex != null) try { homeWordIndex.close(5000); } catch (Exception e){/* nothing todo here */}
        }
    }
    
    public static void importDB(String homePath, String importPath) {
        if (homePath == null) throw new NullPointerException();
        if (importPath == null) throw new NullPointerException();
        if (homePath.equals(importPath)) throw new IllegalArgumentException("Import and home DB directory must not be equal");
        
        // configure logging                    
        try {serverLog.configureLogging(new File(homePath, "yacy.logging"));} catch (Exception e) {}
        serverLog log = new serverLog("DB-IMPORT");
        log.logInfo("STARTING DB-IMPORT");  
        
        plasmaWordIndex homeWordIndex = null, importWordIndex = null;
        plasmaCrawlLURL homeUrlDB = null, importUrlDB = null;
        try {                                        
            //
            Runtime rt = Runtime.getRuntime();
            String errorMsg = null;
            
            // configure destination DB
            File homeDBroot = new File(new File(homePath), "DATA/PLASMADB");
            if (!homeDBroot.exists()) errorMsg = "Home DB directory does not exist.";
            if (!homeDBroot.canRead()) errorMsg = "Home DB directory is not readable.";
            if (!homeDBroot.canWrite()) errorMsg = "Home DB directory is not writeable";
            if (!homeDBroot.isDirectory()) errorMsg = "Home DB Directory is not a directory.";
            if (errorMsg != null) {
                log.logSevere(errorMsg + "\nName: " + homeDBroot.getAbsolutePath());
                return;
            }              
            
            if ((!homeDBroot.exists())&&(!homeDBroot.canRead())&&(!homeDBroot.isDirectory())) {
                log.logSevere("DB home directory can not be opened.");
                return;
            }
            log.logFine("Initializing destination word index db.");
            homeWordIndex = new plasmaWordIndex(homeDBroot, 8*1024*1024, log);
            log.logFine("Initializing destination URL db.");
            homeUrlDB = new plasmaCrawlLURL(new File(homeDBroot, "urlHash.db"), 4*1024*1024);            
            
            // configure import DB
            errorMsg = null;
            File importDBroot = new File(importPath);
            if (!importDBroot.exists()) errorMsg = "Import directory does not exist.";
            if (!importDBroot.canRead()) errorMsg = "Import directory is not readable.";
            if (!importDBroot.canWrite()) errorMsg = "Import directory is not writeable";
            if (!importDBroot.isDirectory()) errorMsg = "ImportDirectory is not a directory.";
            if (errorMsg != null) {
                log.logSevere(errorMsg + "\nName: " + homeDBroot.getAbsolutePath());
                return;
            }    
            
            log.logFine("Initializing source word index db.");
            importWordIndex = new plasmaWordIndex(importDBroot, 8*1024*1024, log);
            log.logFine("Initializing source URL db.");
            importUrlDB = new plasmaCrawlLURL(new File(importDBroot, "urlHash.db"), 4*1024*1024);
            int startSize = importWordIndex.size();
            
            log.logInfo("Importing DB from '" + importDBroot.getAbsolutePath() + "' to '" + homeDBroot.getAbsolutePath() + "'.");
            log.logInfo("Home word index contains " + homeWordIndex.size() + " words and " + homeUrlDB.size() + " URLs.");
            log.logInfo("Import word index contains " + importWordIndex.size() + " words and " + importUrlDB.size() + " URLs.");                        
            
            // iterate over all words from import db
            String wordHash = "";
            long urlCounter = 0, wordCounter = 0, entryCounter = 0;
            long globalStart = System.currentTimeMillis(), wordChunkStart = System.currentTimeMillis(), wordChunkEnd = 0;
            String wordChunkStartHash = "------------", wordChunkEndHash;
            
            Iterator importWordHashIterator = importWordIndex.wordHashes(wordChunkStartHash, plasmaWordIndex.RL_WORDFILES, true);
            while (importWordHashIterator.hasNext()) {
                
                // testing if import process was aborted
                if (Thread.interrupted()) break;
                
                plasmaWordIndexEntryContainer newContainer;
                try {
                    wordCounter++;
                    wordHash = (String) importWordHashIterator.next();
                    newContainer = importWordIndex.getContainer(wordHash, true, -1);
                    
                    if (newContainer.size() == 0) continue;
                    
                    // the combined container will fit, read the container
                    Iterator importWordIdxEntries = newContainer.entries();
                    plasmaWordIndexEntryInstance importWordIdxEntry;
                    while (importWordIdxEntries.hasNext()) {
                        
                        // testing if import process was aborted
                        if (Thread.interrupted()) break;

                        // getting next word index entry
                        entryCounter++;
                        importWordIdxEntry = (plasmaWordIndexEntryInstance) importWordIdxEntries.next();
                        String urlHash = importWordIdxEntry.getUrlHash();                    
                        if ((importUrlDB.exists(urlHash)) && (!homeUrlDB.exists(urlHash))) try {
                            // importing the new url
                            plasmaCrawlLURL.Entry urlEntry = importUrlDB.getEntry(urlHash, null);                       
                            urlCounter++;
                            plasmaCrawlLURL.Entry homeEntry = homeUrlDB.newEntry(urlEntry);
                            homeEntry.store();
                            
                            if (urlCounter % 500 == 0) {
                                log.logFine(urlCounter + " URLs processed so far.");
                            }
                        } catch (IOException e) {}
                        
                        if (entryCounter % 500 == 0) {
                            log.logFine(entryCounter + " word entries and " + wordCounter + " word entries processed so far.");
                        }
                    }
                    
                    // testing if import process was aborted
                    if (Thread.interrupted()) break;
                    
                    // importing entity container to home db
                    homeWordIndex.addEntries(newContainer, System.currentTimeMillis(), true);
                                        
                    // delete complete index entity file
                    importWordIndex.deleteIndex(wordHash);                 
                    
                    // print out some statistical information
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = wordHash;
                        wordChunkEnd = System.currentTimeMillis();
                        long duration = wordChunkEnd - wordChunkStart;
                        log.logInfo(wordCounter + " word entities imported " +
                                "[" + wordChunkStartHash + " .. " + wordChunkEndHash + "] " +
                                ((startSize-importWordIndex.size())/(importWordIndex.size()/100)) + 
                                 "%\n" + 
                                "Speed: "+ 500*1000/duration + " word entities/s" +
                                " | Elapsed time: " + serverDate.intervalToString(wordChunkEnd-globalStart) +
                                " | Estimated time: " + serverDate.intervalToString(importWordIndex.size()*((wordChunkEnd-globalStart)/wordCounter)) + "\n" + 
                                "Free memory: " + rt.freeMemory() + 
                                " | Total memory: " + rt.totalMemory() + "\n" + 
                                "Home Words = " + homeWordIndex.size() + 
                                " | Import Words = " + importWordIndex.size());
                        wordChunkStart = wordChunkEnd;
                        wordChunkStartHash = wordChunkEndHash;
                    }                    
                    
                } catch (Exception e) {
                    log.logSevere("Import of word entity '" + wordHash + "' failed.",e);
                } finally {
                }
            }
            
            log.logInfo("Home word index contains " + homeWordIndex.size() + " words and " + homeUrlDB.size() + " URLs.");
            log.logInfo("Import word index contains " + importWordIndex.size() + " words and " + importUrlDB.size() + " URLs.");
            
            log.logInfo("DB-IMPORT FINISHED");
        } catch (Exception e) {
            log.logSevere("Database import failed.",e);
        } finally {
            if (homeUrlDB != null) try { homeUrlDB.close(); } catch (Exception e){}
            if (importUrlDB != null) try { importUrlDB.close(); } catch (Exception e){}
            if (homeWordIndex != null) try { homeWordIndex.close(5000); } catch (Exception e){}
            if (importWordIndex != null) try { importWordIndex.close(5000); } catch (Exception e){}
        }
    }
    
    public static void minimizeUrlDB(String homePath, int dbcache) {
        // run with "java -classpath classes yacy -minimizeUrlDB"
        try {serverLog.configureLogging(new File(homePath, "yacy.logging"));} catch (Exception e) {}
        File dbroot = new File(new File(homePath), "DATA/PLASMADB");
        serverLog log = new serverLog("URL-CLEANUP");
        try {
            log.logInfo("STARTING URL CLEANUP");
            
            // db containing all currently loades urls
            int cache = dbcache * 1024 * 1024;
            log.logFine("URLDB-Caches: "+cache+" bytes");
            plasmaCrawlLURL currentUrlDB = new plasmaCrawlLURL(new File(dbroot, "urlHash.db"), cache);
            
            // db used to hold all neede urls
            plasmaCrawlLURL minimizedUrlDB = new plasmaCrawlLURL(new File(dbroot, "urlHash.temp.db"), cache);
            
            Runtime rt = Runtime.getRuntime();
            int cacheMem = (int)(rt.maxMemory()-rt.totalMemory())-5*1024*1024;
            plasmaWordIndex wordIndex = new plasmaWordIndex(dbroot, cacheMem, log);
            Iterator wordHashIterator = wordIndex.wordHashes("------------", plasmaWordIndex.RL_WORDFILES, false);
            
            String wordhash;
            long urlCounter = 0, wordCounter = 0;
            long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = 0;
            String wordChunkStartHash = "------------", wordChunkEndHash;
            
            while (wordHashIterator.hasNext()) {
                plasmaWordIndexEntryContainer wordIdxContainer = null;
                try {
                    wordCounter++;
                    wordhash = (String) wordHashIterator.next();
                    wordIdxContainer = wordIndex.getContainer(wordhash, true, -1);
                    
                    // the combined container will fit, read the container
                    Iterator wordIdxEntries = wordIdxContainer.entries();
                    plasmaWordIndexEntryInstance wordIdxEntry;
                    while (wordIdxEntries.hasNext()) {
                        wordIdxEntry = (plasmaWordIndexEntryInstance) wordIdxEntries.next();
                        String urlHash = wordIdxEntry.getUrlHash();                    
                        if ((currentUrlDB.exists(urlHash)) && (!minimizedUrlDB.exists(urlHash))) try {
                            plasmaCrawlLURL.Entry urlEntry = currentUrlDB.getEntry(urlHash, null);                       
                            urlCounter++;
                            plasmaCrawlLURL.Entry newEntry = minimizedUrlDB.newEntry(urlEntry);
                            newEntry.store();
                            if (urlCounter % 500 == 0) {
                                log.logInfo(urlCounter + " URLs found so far.");
                            }
                        } catch (IOException e) {}
                    }
                    // we have read all elements, now we can close it
                    wordIdxContainer = null;
                    
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = wordhash;
                        wordChunkEnd = System.currentTimeMillis();
                        long duration = wordChunkEnd - wordChunkStart;
                        log.logInfo(wordCounter + " words scanned " +
                                "[" + wordChunkStartHash + " .. " + wordChunkEndHash + "]\n" + 
                                "Duration: "+ 500*1000/duration + " words/s" +
                                " | Free memory: " + rt.freeMemory() + 
                                " | Total memory: " + rt.totalMemory());
                        wordChunkStart = wordChunkEnd;
                        wordChunkStartHash = wordChunkEndHash;
                    }
                    
                    
                } catch (Exception e) {
                    log.logSevere("Exception", e);
                } finally {
                    if (wordIdxContainer != null) try { wordIdxContainer = null; } catch (Exception e) {}
                }
            }
            currentUrlDB.close();
            minimizedUrlDB.close();
            wordIndex.close(600);
            
            log.logInfo("current LURL DB contains " + currentUrlDB.size() + " entries.");
            log.logInfo("mimimized LURL DB contains " + minimizedUrlDB.size() + " entries.");
            
            // TODO: rename the mimimized UrlDB to the name of the previous UrlDB
            
            log.logInfo("FINISHED URL CLEANUP, WAIT FOR DUMP");
            log.logInfo("TERMINATED URL CLEANUP");
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
    }

    /**
    * Reads all words from the given file and creates a hashmap, where key is
    * the plasma word hash and value is the word itself.
    *
    * @param wordlist File where the words are stored.
    * @return HashMap with the hash-word - relation.
    */
    private static HashMap loadWordMap(File wordlist) {
        // returns a hash-word - Relation
        HashMap wordmap = new HashMap();
        try {
            String word;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wordlist)));
            while ((word = br.readLine()) != null) wordmap.put(indexEntryAttribute.word2hash(word),word);
            br.close();
        } catch (IOException e) {}
        return wordmap;
    }

    /**
    * Reads all words from the given file and creats as HashSet, which contains
    * all found words.
    *
    * @param wordlist File where the words are stored.
    * @return HashSet with the words
    */
    private static HashSet loadWordSet(File wordlist) {
        // returns a set of words
        HashSet wordset = new HashSet();
        try {
            String word;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wordlist)));
            while ((word = br.readLine()) != null) wordset.add(word);
            br.close();
        } catch (IOException e) {}
        return wordset;
    }

    /**
    * Cleans a wordlist in a file according to the length of the words. The
    * file with the given filename is read and then only the words in the given
    * length-range are written back to the file.
    *
    * @param wordlist Name of the file the words are stored in.
    * @param minlength Minimal needed length for each word to be stored.
    * @param maxlength Maximal allowed length for each word to be stored.
    */
    private static void cleanwordlist(String wordlist, int minlength, int maxlength) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        serverLog.logConfig("CLEAN-WORDLIST", "START");

        String word;
        TreeSet wordset = new TreeSet();
        int count = 0;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wordlist)));
            String seps = "' .,:/-&";
            while ((word = br.readLine()) != null) {
                word = word.toLowerCase().trim();
                for (int i = 0; i < seps.length(); i++) {
                    if (word.indexOf(seps.charAt(i)) >= 0) word = word.substring(0, word.indexOf(seps.charAt(i)));
                }
                if ((word.length() >= minlength) && (word.length() <= maxlength)) wordset.add(word);
                count++;
            }
            br.close();

            if (wordset.size() != count) {
                count = count - wordset.size();
                BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(wordlist)));
                while (wordset.size() > 0) {
                    word = (String) wordset.first();
                    bw.write(word + "\n");
                    wordset.remove(word);
                }
                bw.close();
                serverLog.logInfo("CLEAN-WORDLIST", "shrinked wordlist by " + count + " words.");
            } else {
                serverLog.logInfo("CLEAN-WORDLIST", "not necessary to change wordlist");
            }
        } catch (IOException e) {
            serverLog.logSevere("CLEAN-WORDLIST", "ERROR: " + e.getMessage());
            System.exit(-1);
        }

        // finished
        serverLog.logConfig("CLEAN-WORDLIST", "FINISHED");
    }

    /**
    * Gets all words from the stopword-list and removes them in the databases.
    * FIXME: Really? Don't know if I read this correctly.
    *
    * @param homePath Root-Path where all information is to be found.
    */
    private static void deleteStopwords(String homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        serverLog.logConfig("DELETE-STOPWORDS", "START");

        Properties config = configuration("DELETE-STOPWORDS", homePath);
        File dbRoot = new File(homePath, config.getProperty("dbPath"));

        // load stopwords
        HashSet stopwords = loadWordSet(new File(homePath, "yacy.stopwords"));
        serverLog.logInfo("DELETE-STOPWORDS", "loaded stopwords, " + stopwords.size() + " entries in list, starting scanning");

        // find all hashes
        File f;
        String w;
        int count = 0;
        long thisamount, totalamount = 0;
        Iterator i = stopwords.iterator();
        while (i.hasNext()) {
            w = (String) i.next();
            f = plasmaWordIndexEntity.wordHash2path(dbRoot, indexEntryAttribute.word2hash(w));
            if (f.exists()) {
                thisamount = f.length();
                if (f.delete()) {
                    count++;
                    totalamount += thisamount;
                    serverLog.logInfo("DELETE-STOPWORDS", "deleted index for word '" + w + "', " + thisamount + " bytes");
                }
            }
        }

        serverLog.logInfo("DELETE-STOPWORDS", "TOTALS: deleted " + count + " indexes; " + (totalamount / 1024) + " kbytes");

        // finished
        serverLog.logConfig("DELETE-STOPWORDS", "FINISHED");
    }

    private static void transferCR(String targetaddress, String crfile) {
        File f = new File(crfile);
        try {
            byte[] b = serverFileUtils.read(f);
            String result = yacyClient.transfer(targetaddress, f.getName(), b);
            if (result == null)
                serverLog.logInfo("TRANSFER-CR", "transmitted file " + crfile + " to " + targetaddress + " successfully");
            else
                serverLog.logInfo("TRANSFER-CR", "error transmitting file " + crfile + " to " + targetaddress + ": " + result);
        } catch (IOException e) {
            serverLog.logInfo("TRANSFER-CR", "could not read file " + crfile);
        }
    }
        /**
    * Generates a text file containing all domains in this peer's DB.
    *
    * @param format String which determines format of the text file. Possible values: "html", "zip", "gzip" or "plain"
    */
    private static void domlist(String homePath, String format, String targetName) {
    	
    	File root = new File(homePath);
        try {
            plasmaURLPool pool = new plasmaURLPool(new File(root, "DATA/PLASMADB"), 16000, 1000, 1000);
            Iterator eiter = pool.loadedURL.entries(true, false);
            HashSet doms = new HashSet();
            plasmaCrawlLURL.Entry entry;
            while (eiter.hasNext()) {
                entry = (plasmaCrawlLURL.Entry) eiter.next();
                if ((entry != null) && (entry.url() != null)) doms.add(entry.url().getHost());
            }
            
            // output file in HTML format
            if (format.equals("html")) {
                File file = new File(root, targetName + ".html");
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                Iterator i = doms.iterator();
                String key;
                bos.write(("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">").getBytes());
                bos.write(serverCore.crlf);
                bos.write(("<html><head><title>YaCy domainlist</title></head><body>").getBytes());
                bos.write(serverCore.crlf);
                while (i.hasNext()) {
                    key = i.next().toString();
                    bos.write(("<a href=\"http://" + key + "\">" + key + "</a><br>").getBytes());
                    bos.write(serverCore.crlf);
                }
                bos.write(("</body></html>").getBytes());
                bos.close();
            //output file in plain text but compressed with ZIP
            } else if (format.equals("zip")) {
                ZipEntry zipEntry = new ZipEntry(targetName + ".txt");
                File file = new File(root, targetName + ".zip");
                ZipOutputStream bos = new ZipOutputStream(new FileOutputStream(file));
                bos.putNextEntry(zipEntry);
                Iterator i = doms.iterator();
                String key;
                while (i.hasNext()) {
                    key = i.next().toString();
                    bos.write((key).getBytes());
                    bos.write(serverCore.crlf);
                }
                bos.close();
            //output file in plain text but compressed with GZIP
            } else if (format.equals("gzip")) {
                File file = new File(root, targetName + ".txt.gz");
                GZIPOutputStream bos = new GZIPOutputStream(new FileOutputStream(file));
                Iterator i = doms.iterator();
                String key;
                while (i.hasNext()) {
                    key = i.next().toString();
                    bos.write((key).getBytes());
                    bos.write(serverCore.crlf);
                }
                bos.close();
            }
            else {
                // plain text list
                serverFileUtils.saveSet(new File(root, targetName + ".txt"), doms, new String(serverCore.crlf));
            }
            pool.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void urllist(String homePath, boolean html, String targetName) {
        File root = new File(homePath);
        try {
            plasmaURLPool pool = new plasmaURLPool(new File(root, "DATA/PLASMADB"), 16000, 1000, 1000);
            Iterator eiter = pool.loadedURL.entries(true, false);
            plasmaCrawlLURL.Entry entry;
            File file = new File(root, targetName);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            while (eiter.hasNext()) {
                entry = (plasmaCrawlLURL.Entry) eiter.next();
                if ((entry != null) && (entry.url() != null)) {
                    if (html) {
                        bos.write(("<a href=\"" + entry.url() + "\">" + entry.descr() + "</a><br>").getBytes("UTF-8"));
                        bos.write(serverCore.crlf);
                    } else {
                        bos.write(entry.url().toString().getBytes());
                        bos.write(serverCore.crlf);
                    }
                }
            }
            bos.close();
            pool.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static String[] shift(String[] args, int pos, int count) {
        String[] newargs = new String[args.length - count];
        System.arraycopy(args, 0, newargs, 0, pos);
        System.arraycopy(args, pos + count, newargs, pos, args.length - pos - count);
        return newargs;
    }
    
    /**
     * Uses an Iteration over urlHash.db to detect malformed URL-Entries.
     * Damaged URL-Entries will be marked in a HashSet and removed at the end of the function.
     *
     * @param homePath Root-Path where all information is to be found.
     */
    private static void urldbcleanup(String homePath) {
        File root = new File(homePath);
        File dbroot = new File(root, "DATA/PLASMADB");
        serverLog log = new serverLog("URLDBCLEANUP");
        HashSet damagedURLS = new HashSet();
        try {
            plasmaCrawlLURL currentUrlDB = new plasmaCrawlLURL(new File(dbroot, "urlHash.db"), 4194304);
            Iterator eiter = currentUrlDB.entries(true, false);
            int iteratorCount = 0;
            while (eiter.hasNext()) try {
                eiter.next();
                iteratorCount++;
            } catch (RuntimeException e) {
                String m = e.getMessage();
                damagedURLS.add(m.substring(m.length() - 12));
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            log.logInfo("URLs vorher: " + currentUrlDB.size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            Iterator eiter2 = damagedURLS.iterator();
            String urlHash;
            while (eiter2.hasNext()) {
                urlHash = (String) eiter2.next();

                // trying to fix the invalid URL
                httpc theHttpc = null;
                String oldUrlStr = null;
                try {
                    // getting the url data as byte array
                    byte[][] entry = currentUrlDB.urlHashCache.get(urlHash.getBytes());

                    // getting the wrong url string
                    oldUrlStr = new String(entry[1]).trim();

                    int pos = -1;
                    if ((pos = oldUrlStr.indexOf("://")) != -1) {
                        // trying to correct the url
                        String newUrlStr = "http://" + oldUrlStr.substring(pos + 3);
                        URL newUrl = new URL(newUrlStr);

                        // doing a http head request to test if the url is correct
                        theHttpc = httpc.getInstance(newUrl.getHost(), newUrl.getHost(), newUrl.getPort(), 30000, false);
                        response res = theHttpc.HEAD(newUrl.getPath(), null);

                        if (res.statusCode == 200) {
                            entry[1] = newUrl.toString().getBytes();
                            currentUrlDB.urlHashCache.put(entry);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' corrected\n\tURL: " + oldUrlStr + " -> " + newUrlStr);
                        } else {
                            currentUrlDB.remove(urlHash);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tConnection Status: " + res.status);
                        }
                    }
                } catch (Exception e) {
                    currentUrlDB.remove(urlHash);
                    log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                } finally {
                    if (theHttpc != null) try {
                        theHttpc.close();
                        httpc.returnInstance(theHttpc);
                    } catch (Exception e) { }
                }
            }

            log.logInfo("URLs nachher: " + currentUrlDB.size() + " kaputte URLs: " + damagedURLS.size());
            currentUrlDB.close();
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
    }
    
    private static void RWIHashList(String homePath, String targetName, String resource, String format) {
        plasmaWordIndex WordIndex = null;
        serverLog log = new serverLog("HASHLIST");
        File homeDBroot = new File(new File(homePath), "DATA/PLASMADB");
        String wordChunkStartHash = "------------";
        try {serverLog.configureLogging(new File(homePath, "yacy.logging"));} catch (Exception e) {}
        log.logInfo("STARTING CREATION OF RWI-HASHLIST");
        File root = new File(homePath);
        try {
            Iterator WordHashIterator = null;
            if (resource.equals("all")) {
                WordIndex = new plasmaWordIndex(homeDBroot, 8*1024*1024, log);
                WordHashIterator = WordIndex.wordHashes(wordChunkStartHash, plasmaWordIndex.RL_WORDFILES, false);
            } else if (resource.equals("assortments")) {
                plasmaWordIndexAssortmentCluster assortmentCluster = new plasmaWordIndexAssortmentCluster(new File(homeDBroot, "ACLUSTER"), 64, 16*1024*1024, log);
                WordHashIterator = assortmentCluster.hashConjunction(wordChunkStartHash, true, false);
            } else if (resource.startsWith("assortment")) {
                int a = Integer.parseInt(resource.substring(10));
                plasmaWordIndexAssortment assortment = new plasmaWordIndexAssortment(new File(homeDBroot, "ACLUSTER"), a, 8*1024*1024, null);
                WordHashIterator = assortment.hashes(wordChunkStartHash, true, false);
            } else if (resource.equals("words")) {
                plasmaWordIndexClassicDB fileDB = new plasmaWordIndexClassicDB(homeDBroot, log);
                WordHashIterator = fileDB.wordHashes(wordChunkStartHash, true, false);
            }
            int counter = 0;
            String wordHash = "";
            if (format.equals("zip")) {
                log.logInfo("Writing Hashlist to ZIP-file: " + targetName + ".zip");
                ZipEntry zipEntry = new ZipEntry(targetName + ".txt");
                File file = new File(root, targetName + ".zip");
                ZipOutputStream bos = new ZipOutputStream(new FileOutputStream(file));
                bos.putNextEntry(zipEntry);
                while (WordHashIterator.hasNext()) {
                    counter++;
                    wordHash = (String) WordHashIterator.next();
                    bos.write((wordHash).getBytes());
                    bos.write(serverCore.crlf);
                    if (counter % 500 == 0) {
                        log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + wordHash);
                    }
                }
                bos.close();
            }
            else {
                log.logInfo("Writing Hashlist to TXT-file: " + targetName + ".txt");
                File file = new File(root, targetName + ".txt");
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                while (WordHashIterator.hasNext()) {
                    counter++;
                    wordHash = (String) WordHashIterator.next();
                    bos.write((wordHash).getBytes());
                    bos.write(serverCore.crlf);
                    if (counter % 500 == 0) {
                        log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + wordHash);
                    }
                }
                bos.close();
            }
            log.logInfo("Total number of Hashs: " + counter + ". Last found Hash: " + wordHash);
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
        if (WordIndex != null) {
            WordIndex.close(60);
            WordIndex = null;
        }
    }
    
    /**
     * Searching for peers affected by Bug http://www.yacy-forum.de/viewtopic.php?p=16056
     * @param homePath
     * @see http://www.yacy-forum.de/viewtopic.php?p=16056
     */
    public static void testPeerDB(String homePath) {
        
        try {
            File yacyDBPath = new File(new File(homePath), "DATA/YACYDB");
            
            String[] dbFileNames = {"seed.new.db","seed.old.db","seed.pot.db"};
            for (int i=0; i < dbFileNames.length; i++) {
                File dbFile = new File(yacyDBPath,dbFileNames[i]);
                kelondroMap db = new kelondroMap(new kelondroDyn(dbFile, (1024 * 0x400) / 3, '#'), yacySeedDB.sortFields, yacySeedDB.accFields);
                
                kelondroMap.mapIterator it;
                it = db.maps(true, false);
                while (it.hasNext()) {
                    Map dna = (Map) it.next();
                    String peerHash = (String) dna.get("key");
                    if (peerHash.length() < yacySeedDB.commonHashLength) {
                        String peerName = (String) dna.get("Name");
                        String peerIP = (String) dna.get("IP");
                        String peerPort = (String) dna.get("Port");
                        
                        while (peerHash.length() < yacySeedDB.commonHashLength) { peerHash = peerHash + "_"; }                        
                        System.err.println("Invalid Peer-Hash found in '" + dbFileNames[i] + "': " + peerName + ":" +  peerHash + ", http://" + peerIP + ":" + peerPort);
                    }
                }
                db.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

 
    /**
     * Main-method which is started by java. Checks for special arguments or
     * starts up the application.
     * 
     * @param args
     *            Given arguments from the command line.
     */
    public static void main(String args[]) {

        // check memory amount
        System.gc();
        long startupMemFree  = Runtime.getRuntime().freeMemory(); // the
                                                                    // amount of
                                                                    // free
                                                                    // memory in
                                                                    // the Java
                                                                    // Virtual
                                                                    // Machine
        long startupMemTotal = Runtime.getRuntime().totalMemory(); // the total amount of memory in the Java virtual machine; may vary over time

        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        //which XML Parser?
        if(System.getProperty("javax.xml.parsers.DocumentBuilderFactory")==null){
            System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "org.apache.crimson.jaxp.DocumentBuilderFactoryImpl");
        }
        if(System.getProperty("javax.xml.parsers.SAXParserFactory")==null){
            System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.crimson.jaxp.SAXParserFactoryImpl");
        }
        
        String applicationRoot = System.getProperty("user.dir").replace('\\', '/');
        //System.out.println("args.length=" + args.length);
        //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
        if ((args.length >= 1) && ((args[0].equals("-startup")) || (args[0].equals("-start")))) {
            // normal start-up of yacy
            if (args.length == 2) applicationRoot= args[1];
            startup(applicationRoot, startupMemFree, startupMemTotal);
        } else if ((args.length >= 1) && ((args[0].equals("-shutdown")) || (args[0].equals("-stop")))) {
            // normal shutdown of yacy
            if (args.length == 2) applicationRoot= args[1];
            shutdown(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-migratewords"))) {
            // migrate words from DATA/PLASMADB/WORDS path to assortment cache, if possible
            // attention: this may run long and should not be interrupted!
            if (args.length == 2) applicationRoot= args[1];
            migrateWords(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-minimizeUrlDB"))) {
            // migrate words from DATA/PLASMADB/WORDS path to assortment cache, if possible
            // attention: this may run long and should not be interrupted!
            int dbcache = 4;
            if (args.length >= 3 && args[1].equals("-cache")) {
                dbcache = Integer.parseInt(args[2]);
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            minimizeUrlDB(applicationRoot, dbcache);
        } else if ((args.length >= 1) && (args[0].equals("-importDB"))) {
            // attention: this may run long and should not be interrupted!
            String importRoot = null;
            if (args.length == 3) {
                applicationRoot= args[1];
                importRoot = args[2];
            } else if (args.length == 2) {
                importRoot = args[1];
            } else {
                System.err.println("Usage: -importDB [homeDbRoot] importDbRoot");
                return;
            }
            importDB(applicationRoot, importRoot);
        } else if ((args.length >= 1) && (args[0].equals("-importAssortment"))) {
            // attention: this may run long and should not be interrupted!
            String assortmentFileName = null;
            if (args.length == 3) {
                applicationRoot= args[1];
                assortmentFileName = args[2];
            } else if (args.length == 2) {
                assortmentFileName = args[1];
            } else {
                System.err.println("Usage: -importAssortment [homeDbRoot] [AssortmentFileName]");
                return;
            }
            importAssortment(applicationRoot, assortmentFileName);
        } else if ((args.length >= 1) && (args[0].equals("-testPeerDB"))) {
            if (args.length == 2) {
                applicationRoot= args[1];
            } else if (args.length > 2) {
                System.err.println("Usage: -testPeerDB [homeDbRoot]");
            }
            testPeerDB(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-deletestopwords"))) {
            // delete those words in the index that are listed in the stopwords file
            if (args.length == 2) applicationRoot= args[1];
            deleteStopwords(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-genwordstat"))) {
            // this can help to create a stop-word list
            // to use this, you need a 'yacy.words' file in the root path
            // start this with "java -classpath classes yacy -genwordstat [<rootdir>]"
            if (args.length == 2) applicationRoot= args[1];
            genWordstat(applicationRoot);
        } else if ((args.length == 4) && (args[0].equals("-cleanwordlist"))) {
            // this can be used to organize and clean a word-list
            // start this with "java -classpath classes yacy -cleanwordlist <word-file> <minlength> <maxlength>"
            int minlength = Integer.parseInt(args[2]);
            int maxlength = Integer.parseInt(args[3]);
            cleanwordlist(args[1], minlength, maxlength);
        } else if ((args.length >= 1) && (args[0].equals("-transfercr"))) {
            // transfer a single cr file to a remote peer
            String targetaddress = args[1];
            String crfile = args[2];
            transferCR(targetaddress, crfile);
        } else if ((args.length >= 1) && (args[0].equals("-domlist"))) {
            // generate a url list and save it in a file
            String format = "txt";
            if (args.length >= 3 && args[1].equals("-format")) {
                if (args[2].equals("html")) format = args[2];
                if (args[2].equals("zip")) format = args[2];
                if (args[2].equals("gzip")) format = args[2];
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            String outfile = "domlist_" + System.currentTimeMillis();
            domlist(applicationRoot, format, outfile);
        } else if ((args.length >= 1) && (args[0].equals("-urllist"))) {
            // generate a url list and save it in a file
            boolean html = false;
            if (args.length >= 3 && args[1].equals("-format")) {
                if (args[2].equals("html")) html = true;
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            String outfile = "urllist_" + System.currentTimeMillis() + ((html) ? ".html" : ".txt");
            urllist(applicationRoot, html, outfile);
        } else if ((args.length >= 1) && (args[0].equals("-urldbcleanup"))) {
            // generate a url list and save it in a file
            if (args.length == 2) applicationRoot= args[1];
            urldbcleanup(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-rwihashlist"))) {
            // generate a url list and save it in a file
            String domain = "all";
            String format = "txt";
            if (args.length >= 2) domain= args[1];
            if (args.length >= 3) format= args[2];
            if (args.length == 4) applicationRoot= args[3];
            String outfile = "rwihashlist_" + System.currentTimeMillis();
            RWIHashList(applicationRoot, outfile, domain, format);
        } else {
            if (args.length == 1) applicationRoot= args[0];
            startup(applicationRoot, startupMemFree, startupMemTotal);
        }
    }
}

/**
* This class is a helper class whose instance is started, when the java virtual
* machine shuts down. Signals the plasmaSwitchboard to shut down.
*/
class shutdownHookThread extends Thread {
    private plasmaSwitchboard sb = null;
    private Thread mainThread = null;

    public shutdownHookThread(Thread mainThread, plasmaSwitchboard sb) {
        super();
        this.sb = sb;
        this.mainThread = mainThread;
    }

    public void run() {
        try {
            if (!this.sb.isTerminated()) {
                serverLog.logConfig("SHUTDOWN","Shutdown via shutdown hook.");

                // sending the yacy main thread a shutdown signal
                serverLog.logFine("SHUTDOWN","Signaling shutdown to the switchboard.");
                this.sb.terminate();

                // waiting for the yacy thread to finish execution
                serverLog.logFine("SHUTDOWN","Waiting for main thread to finish.");
                this.mainThread.join();
            }
        } catch (Exception e) {
            serverLog.logSevere("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
