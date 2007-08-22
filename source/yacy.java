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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.anomic.data.translator;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaCrawlEntry;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaCrawlZURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.serverSemaphore;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyVersion;

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
    public static final String vString = "@REPL_VERSION@";
    public static double version = 0.1;
    public static boolean pro;
    
    public static final String vDATE   = "@REPL_DATE@";
    public static final String copyright = "[ YaCy v" + vString + ", build " + vDATE + " by Michael Christen / www.yacy.net ]";
    public static final String hline = "-------------------------------------------------------------------------------";
   
    /**
     * a reference to the {@link plasmaSwitchboard} created by the
     * {@link yacy#startup(String, long, long)} method.
     */
    private static plasmaSwitchboard sb = null;
    
    /**
     * Semaphore needed by {@link yacy#setUpdaterCallback(serverUpdaterCallback)} to block 
     * until the {@link plasmaSwitchboard }object was created.
     */
    private static serverSemaphore sbSync = new serverSemaphore(0);
    
    /**
     * Semaphore needed by {@link yacy#waitForFinishedStartup()} to block 
     * until startup has finished
     */
    private static serverSemaphore startupFinishedSync = new serverSemaphore(0);

    /**
    * Starts up the whole application. Sets up all datastructures and starts
    * the main threads.
    *
    * @param homePath Root-path where all information is to be found.
    * @param startupFree free memory at startup time, to be used later for statistics
    */
    private static void startup(String homePath, long startupMemFree, long startupMemTotal) {
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
            
            // ensure that there is a DATA directory, if not, create one and if that fails warn and die
            File f = new File(homePath); if (!(f.exists())) f.mkdirs();
            f = new File(homePath, "DATA/"); if (!(f.exists())) f.mkdirs();
			if (!(f.exists())) { 
				System.err.println("Error creating DATA-directory in " + homePath.toString() + " . Please check your write-permission for this folder. YaCy will now terminate."); 
				System.exit(-1); 
			}
            
            // setting up logging
            f = new File(homePath, "DATA/LOG/"); if (!(f.exists())) f.mkdirs();
            if (!((new File(homePath, "DATA/LOG/yacy.logging")).exists())) try {
                serverFileUtils.copy(new File(homePath, "yacy.logging"), new File(homePath, "DATA/LOG/yacy.logging"));
            }catch (IOException e){
                System.out.println("could not copy yacy.logging");
            }
            try{
                serverLog.configureLogging(homePath,new File(homePath, "DATA/LOG/yacy.logging"));
            } catch (IOException e) {
                System.out.println("could not find logging properties in homePath=" + homePath);
                e.printStackTrace();
            }
            serverLog.logConfig("STARTUP", "Java version: " + System.getProperty("java.version", "no-java-version"));
            serverLog.logConfig("STARTUP", "Operation system: " + System.getProperty("os.name","unknown"));
            serverLog.logConfig("STARTUP", "Application root-ath: " + homePath);
            serverLog.logConfig("STARTUP", "Time zone: UTC" + serverDate.UTCDiffString() + "; UTC+0000 is " + System.currentTimeMillis());
            serverLog.logConfig("STARTUP", "Maximum file system path length: " + serverSystem.maxPathLength);
            
            f = new File(homePath, "DATA/yacy.running");
            if (f.exists()) {                // another instance running? VM crash? User will have to care about this
                serverLog.logSevere("STARTUP", "WARNING: the file " + f + " exists, this usually means that a YaCy instance is still running");
                f.delete();
            }
            f.createNewFile();
            f.deleteOnExit();
            
            pro = new File(homePath, "libx").exists();
            sb = new plasmaSwitchboard(homePath, "yacy.init", "DATA/SETTINGS/httpProxy.conf", pro);
            sbSync.V(); // signal that the sb reference was set
            
            // save information about available memory at startup time
            sb.setConfig("memoryFreeAfterStartup", startupMemFree);
            sb.setConfig("memoryTotalAfterStartup", startupMemTotal);
            
            // hardcoded, forced, temporary value-migration
            sb.setConfig("htTemplatePath", "htroot/env/templates");
            sb.setConfig("parseableExt", "html,htm,txt,php,shtml,asp");

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
                            try {version = Double.parseDouble(vString);} catch (NumberFormatException e) {version = (float) 0.1;}
                            version = yacyVersion.versvn2combinedVersion(version, Integer.parseInt(svrReleaseNr));
                        } catch (NumberFormatException e) {}
                        sb.setConfig("svnRevision", svrReleaseNr);
                    }
                }
                newRev=Integer.parseInt(sb.getConfig("svnRevision", "0"));
            } catch (Exception e) {
                System.err.println("Unable to determine the currently used SVN revision number.");
            }

            sb.setConfig("version", Double.toString(version));
            sb.setConfig("vString", yacyVersion.combined2prettyVersion(Double.toString(version)));
            sb.setConfig("vdate", (vDATE.startsWith("@")) ? serverDate.shortDayTime() : vDATE);
            sb.setConfig("applicationRoot", homePath);
            serverLog.logConfig("STARTUP", "YACY Version: " + version + ", Built " + sb.getConfig("vdate", "00000000"));
            yacyVersion.latestRelease = version;

            // read environment
            int timeout = Math.max(20000, Integer.parseInt(sb.getConfig("httpdTimeout", "20000")));

            // create some directories
            final File htRootPath = new File(homePath, sb.getConfig("htRootPath", "htroot"));
            final File htDocsPath = new File(homePath, sb.getConfig("htDocsPath", "DATA/HTDOCS"));
            if (!(htDocsPath.exists())) htDocsPath.mkdir();
            //final File htTemplatePath = new File(homePath, sb.getConfig("htTemplatePath","htdocs"));

            // create default notifier picture
            //TODO: Use templates instead of copying images ...
            if (!((new File(htDocsPath, "notifier.gif")).exists())) try {
                serverFileUtils.copy(new File(htRootPath, "env/grafics/empty.gif"),
                                     new File(htDocsPath, "notifier.gif"));
            } catch (IOException e) {}

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


            final File shareDefaultPath = new File(htDocsPath, "share");
            if (!(shareDefaultPath.exists())) shareDefaultPath.mkdir();

            migration.migrate(sb, oldRev, newRev);
            
            // start main threads
            final String port = sb.getConfig("port", "8080");
            try {
                final httpd protocolHandler = new httpd(sb);
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
                        boolean properPW = (sb.getConfig("adminAccount", "").length() == 0) && (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0);
                        if (!properPW) browserPopUpPage = "ConfigBasic.html";
                        final String  browserPopUpApplication = sb.getConfig("browserPopUpApplication", "netscape");
                        serverSystem.openBrowser((server.withSSL()?"https":"http") + "://localhost:" + serverCore.getPortNr(port) + "/" + browserPopUpPage, browserPopUpApplication);
                    }

                    // Copy the shipped locales into DATA, existing files are overwritten
                    final File locale_work   = new File(homePath, sb.getConfig("locale.work", "DATA/LOCALE/locales"));
                    final File locale_source = new File(homePath, sb.getConfig("locale.source", "locales"));
                    try{
                        final File[] locale_source_files = locale_source.listFiles();
                        locale_work.mkdirs();
                        File target;
                        for (int i=0; i < locale_source_files.length; i++){
                        	target = new File(locale_work, locale_source_files[i].getName());
                            if (locale_source_files[i].getName().endsWith(".lng")) {
                            	if (target.exists()) target.delete();
                                serverFileUtils.copy(locale_source_files[i], target);
                            }
                        }
                        serverLog.logInfo("STARTUP", "Copied the default locales to " + locale_work.toString());
                    }catch(NullPointerException e){
                        serverLog.logSevere("STARTUP", "Nullpointer Exception while copying the default Locales");
                    }

                    //regenerate Locales from Translationlist, if needed
                    final String lang = sb.getConfig("locale.language", "");
                    if (!lang.equals("") && !lang.equals("default")) { //locale is used
                        String currentRev = "";
                        try{
                            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(sb.getConfig("locale.translated_html", "DATA/LOCALE/htroot"), lang+"/version" ))));
                            currentRev = br.readLine();
                            br.close();
                        }catch(IOException e){
                            //Error
                        }

                        if (!currentRev.equals(sb.getConfig("svnRevision", ""))) try { //is this another version?!
                            final File sourceDir = new File(sb.getConfig("htRootPath", "htroot"));
                            final File destDir = new File(sb.getConfig("locale.translated_html", "DATA/LOCALE/htroot"), lang);
                            if (translator.translateFilesRecursive(sourceDir, destDir, new File(locale_work, lang + ".lng"), "html,template,inc", "locale")){ //translate it
                                //write the new Versionnumber
                                final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                                bw.write(sb.getConfig("svnRevision", "Error getting Version"));
                                bw.close();
                            }
                        } catch (IOException e) {}
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
                    
                    // signal finished startup
                    startupFinishedSync.V();
                        
                    // wait for server shutdown
                    try {
                        sb.waitForShutdown();
                    } catch (Exception e) {
                        serverLog.logSevere("MAIN CONTROL LOOP", "PANIC: " + e.getMessage(),e);
                    }
                    // shut down
                    serverLog.logConfig("SHUTDOWN", "caught termination signal");
                    server.terminate(false);
                    server.interrupt();
                    if (server.isAlive()) try {
                        URL u = new URL((server.withSSL()?"https":"http")+"://localhost:" + serverCore.getPortNr(port));
                        httpc.wget(u, u.getHost(), 1000, null, null, null, null, null); // kick server
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
        } finally {
        	startupFinishedSync.V();
        }
        serverLog.logConfig("SHUTDOWN", "goodbye. (this is the last line)");
        //try {
        //    System.exit(0);
        //} catch (Exception e) {} // was once stopped by de.anomic.net.ftpc$sm.checkExit(ftpc.java:1790)
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
    
    public static void shutdown() {
    	if (sb != null) {
    		// YaCy is running in the same runtime. we can shutdown via interrupt
    		sb.terminate();
    	} else {    	
    		String applicationRoot = System.getProperty("user.dir").replace('\\', '/');
    		shutdown(applicationRoot);
    	}
    }
    
    /**
    * Call the shutdown-page of YaCy to tell it to shut down. This method is
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
        String encodedPassword = (String) config.get(httpd.ADMIN_ACCOUNT_B64MD5);
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        httpHeader requestHeader = new httpHeader();
        requestHeader.put("Authorization", "realm=" + encodedPassword); // for http-authentify
        try {
            httpc con = httpc.getInstance("localhost", "localhost", port, 10000, false, null);
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
            h = f.getName().substring(0, yacySeedDB.commonHashLength);
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
     * @param homePath path to the YaCy directory
     * @param dbcache cache size in MB
     */
    public static void minimizeUrlDB(String homePath) {
        // run with "java -classpath classes yacy -minimizeUrlDB"
        try {serverLog.configureLogging(homePath,new File(homePath, "DATA/LOG/yacy.logging"));} catch (Exception e) {}
        File indexPrimaryRoot = new File(new File(homePath), "DATA/INDEX");
        File indexSecondaryRoot = new File(new File(homePath), "DATA/INDEX");
        File indexRoot2 = new File(new File(homePath), "DATA/INDEX2");
        serverLog log = new serverLog("URL-CLEANUP");
        try {
            log.logInfo("STARTING URL CLEANUP");
            
            // db containing all currently loades urls
            plasmaCrawlLURL currentUrlDB = new plasmaCrawlLURL(indexSecondaryRoot, 10000);
            
            // db used to hold all neede urls
            plasmaCrawlLURL minimizedUrlDB = new plasmaCrawlLURL(indexRoot2, 10000);
            
            Runtime rt = Runtime.getRuntime();
            int cacheMem = (int)(serverMemory.max-rt.totalMemory());
            if (cacheMem < 2048000) throw new OutOfMemoryError("Not enough memory available to start clean up.");
                
            plasmaWordIndex wordIndex = new plasmaWordIndex(indexPrimaryRoot, indexSecondaryRoot, 10000, log);
            Iterator indexContainerIterator = wordIndex.wordContainers("AAAAAAAAAAAA", false, false);
            
            long urlCounter = 0, wordCounter = 0;
            long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = 0;
            String wordChunkStartHash = "AAAAAAAAAAAA", wordChunkEndHash;
            
            while (indexContainerIterator.hasNext()) {
                indexContainer wordIdxContainer = null;
                try {
                    wordCounter++;
                    wordIdxContainer  = (indexContainer) indexContainerIterator.next();
                    
                    // the combined container will fit, read the container
                    Iterator wordIdxEntries = wordIdxContainer.entries();
                    indexRWIEntry iEntry;
                    while (wordIdxEntries.hasNext()) {
                        iEntry = (indexRWIEntry) wordIdxEntries.next();
                        String urlHash = iEntry.urlHash();                    
                        if ((currentUrlDB.exists(urlHash)) && (!minimizedUrlDB.exists(urlHash))) try {
                            indexURLEntry urlEntry = currentUrlDB.load(urlHash, null);                       
                            urlCounter++;
                            minimizedUrlDB.store(urlEntry);
                            if (urlCounter % 500 == 0) {
                                log.logInfo(urlCounter + " URLs found so far.");
                            }
                        } catch (IOException e) {}
                    }
                    
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = wordIdxContainer.getWordHash();
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
                    
                    // we have read all elements, now we can close it
                    wordIdxContainer = null;
                    
                } catch (Exception e) {
                    log.logSevere("Exception", e);
                } finally {
                    if (wordIdxContainer != null) try { wordIdxContainer = null; } catch (Exception e) {}
                }
            }
            log.logInfo("current LURL DB contains " + currentUrlDB.size() + " entries.");
            log.logInfo("mimimized LURL DB contains " + minimizedUrlDB.size() + " entries.");
            
            currentUrlDB.close();
            minimizedUrlDB.close();
            wordIndex.close();
            
            // TODO: rename the mimimized UrlDB to the name of the previous UrlDB            
            
            log.logInfo("FINISHED URL CLEANUP, WAIT FOR DUMP");
            log.logInfo("You can now backup your old URL DB and rename minimized/urlHash.db to urlHash.db");
            
            log.logInfo("TERMINATED URL CLEANUP");
        } catch (Exception e) {
            log.logSevere("Exception: " + e.getMessage(), e);
        } catch (Error e) {
            log.logSevere("Error: " + e.getMessage(), e);
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
            while ((word = br.readLine()) != null) wordmap.put(plasmaCondenser.word2hash(word),word);
            br.close();
        } catch (IOException e) {}
        return wordmap;
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
    * This may be useful to calculate the YaCy-Blockrank.
    *
    * @param format String which determines the format of the file. Possible values: "html", "zip", "gzip" or "plain"
    * @see urllist
    */
    private static void domlist(String homePath, String source, String format, String targetName) {
    	
    	File root = new File(homePath);
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(homePath, "yacy.init", "DATA/SETTINGS/httpProxy.conf", false);
            HashMap doms = new HashMap();
            System.out.println("Started domain list extraction from " + sb.wordIndex.loadedURL.size() + " url entries.");
            System.out.println("a dump will be written after double-check of all extracted domains.");
            System.out.println("This process may fail in case of too less memory. To increase memory, start with");
            System.out.println("java -Xmx<megabytes>m -classpath classes yacy -domlist [ -source { nurl | lurl | eurl } ] [ -format { text  | zip | gzip | html } ] [ <path to DATA folder> ]");
            int c = 0;
            long start = System.currentTimeMillis();
            if (source.equals("lurl")) {
                Iterator eiter = sb.wordIndex.loadedURL.entries(true, null);
                indexURLEntry entry;
                while (eiter.hasNext()) {
                    try {
                        entry = (indexURLEntry) eiter.next();
                        indexURLEntry.Components comp = entry.comp();
                        if ((entry != null) && (comp.url() != null)) doms.put(comp.url().getHost(), null);
                    } catch (Exception e) {
                        // here a MalformedURLException may occur
                        // just ignore
                    }
                    c++;
                    if (c % 10000 == 0) System.out.println(
                            c + " urls checked, " +
                            doms.size() + " domains collected, " +
                            ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory()) / 1024 / 1024) + " MB available, " + 
                            ((System.currentTimeMillis() - start) * (sb.wordIndex.loadedURL.size() - c) / c / 60000) + " minutes remaining.");
                }
            }
            if (source.equals("eurl")) {
                Iterator eiter = sb.errorURL.entries(true, null);
                plasmaCrawlZURL.Entry entry;
                while (eiter.hasNext()) {
                    try {
                        entry = (plasmaCrawlZURL.Entry) eiter.next();
                        if ((entry != null) && (entry.url() != null)) doms.put(entry.url().getHost(), entry.anycause());
                    } catch (Exception e) {
                        // here a MalformedURLException may occur
                        // just ignore
                    }
                    c++;
                    if (c % 10000 == 0) System.out.println(
                            c + " urls checked, " +
                            doms.size() + " domains collected, " +
                            ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory()) / 1024 / 1024) + " MB available, " + 
                            ((System.currentTimeMillis() - start) * (sb.wordIndex.loadedURL.size() - c) / c / 60000) + " minutes remaining.");
                }
            }
            if (source.equals("nurl")) {
                Iterator eiter = sb.noticeURL.iterator(plasmaCrawlNURL.STACK_TYPE_CORE);
                plasmaCrawlEntry entry;
                while (eiter.hasNext()) {
                    try {
                        entry = (plasmaCrawlEntry) eiter.next();
                        if ((entry != null) && (entry.url() != null)) doms.put(entry.url().getHost(), "profile=" + entry.profileHandle() + ", depth=" + entry.depth());
                    } catch (Exception e) {
                        // here a MalformedURLException may occur
                        // just ignore
                    }
                    c++;
                    if (c % 10000 == 0) System.out.println(
                            c + " urls checked, " +
                            doms.size() + " domains collected, " +
                            ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory()) / 1024 / 1024) + " MB available, " + 
                            ((System.currentTimeMillis() - start) * (sb.wordIndex.loadedURL.size() - c) / c / 60000) + " minutes remaining.");
                }
            }
            
            if (format.equals("html")) {
                // output file in HTML format
                File file = new File(root, targetName + ".html");
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                System.out.println("Started domain list dump to file " + file);
                Iterator i = doms.entrySet().iterator();
                Map.Entry entry;
                String key;
                bos.write(("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">").getBytes());
                bos.write(serverCore.crlf);
                bos.write(("<html><head><title>YaCy " + source + " domainlist</title></head><body>").getBytes());
                bos.write(serverCore.crlf);
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    key = (String) entry.getKey();
                    bos.write(("<a href=\"http://" + key + "\">" + key + "</a>" +
                              ((entry.getValue() == null) ? "" : (" " + ((String) entry.getValue()))) + "<br>"
                             ).getBytes());
                    bos.write(serverCore.crlf);
                }
                bos.write(("</body></html>").getBytes());
                bos.close();
            
            } else if (format.equals("zip")) {
                // output file in plain text but compressed with ZIP
                File file = new File(root, targetName + ".zip");
                System.out.println("Started domain list dump to file " + file);
                serverFileUtils.saveSet(file, "zip", doms.keySet(), new String(serverCore.crlf));
            
            } else if (format.equals("gzip")) {
                // output file in plain text but compressed with GZIP
                File file = new File(root, targetName + ".txt.gz");
                System.out.println("Started domain list dump to file " + file);
                serverFileUtils.saveSet(file, "gzip", doms.keySet(), new String(serverCore.crlf));
            } else {
                // plain text list
                File file = new File(root, targetName + ".txt");
                System.out.println("Started domain list dump to file " + file);
                serverFileUtils.saveSet(file, "plain", doms.keySet(), new String(serverCore.crlf));
            }
            sb.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void urllist(String homePath, String source, boolean html, String targetName) {
        File root = new File(homePath);
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(homePath, "yacy.init", "DATA/SETTINGS/httpProxy.conf", false);
            File file = new File(root, targetName);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            
            if (source.equals("lurl")) {
                Iterator eiter = sb.wordIndex.loadedURL.entries(true, null);
                indexURLEntry entry;
                while (eiter.hasNext()) {
                    entry = (indexURLEntry) eiter.next();
                    indexURLEntry.Components comp = entry.comp();
                    if ((entry != null) && (comp.url() != null)) {
                        if (html) {
                            bos.write(("<a href=\"" + comp.url().toNormalform(false, true) + "\">" + comp.title() + "</a><br>").getBytes("UTF-8"));
                            bos.write(serverCore.crlf);
                        } else {
                            bos.write(comp.url().toNormalform(false, true).getBytes());
                            bos.write(serverCore.crlf);
                        }
                    }
                }
            }
            if (source.equals("eurl")) {
                Iterator eiter = sb.errorURL.entries(true, null);
                plasmaCrawlZURL.Entry entry;
                while (eiter.hasNext()) {
                    entry = (plasmaCrawlZURL.Entry) eiter.next();
                    if ((entry != null) && (entry.url() != null)) {
                        if (html) {
                            bos.write(("<a href=\"" + entry.url() + "\">" + entry.url() + "</a> " + entry.anycause() + "<br>").getBytes("UTF-8"));
                            bos.write(serverCore.crlf);
                        } else {
                            bos.write(entry.url().toString().getBytes());
                            bos.write(serverCore.crlf);
                        }
                    }
                }
            }
            if (source.equals("nurl")) {
                Iterator eiter = sb.noticeURL.iterator(plasmaCrawlNURL.STACK_TYPE_CORE);
                plasmaCrawlEntry entry;
                while (eiter.hasNext()) {
                    entry = (plasmaCrawlEntry) eiter.next();
                    if ((entry != null) && (entry.url() != null)) {
                        if (html) {
                            bos.write(("<a href=\"" + entry.url() + "\">" + entry.url() + "</a> " + "profile=" + entry.profileHandle() + ", depth=" + entry.depth() + "<br>").getBytes("UTF-8"));
                            bos.write(serverCore.crlf);
                        } else {
                            bos.write(entry.url().toString().getBytes());
                            bos.write(serverCore.crlf);
                        }
                    }
                }
            }
            bos.close();
            sb.close();
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
        File indexroot = new File(root, "DATA/INDEX");
        try {serverLog.configureLogging(homePath,new File(homePath, "DATA/LOG/yacy.logging"));} catch (Exception e) {}
        plasmaCrawlLURL currentUrlDB = new plasmaCrawlLURL(indexroot, 10000);
        currentUrlDB.urldbcleanup();
        currentUrlDB.close();
    }
    
    private static void RWIHashList(String homePath, String targetName, String resource, String format) {
        plasmaWordIndex WordIndex = null;
        serverLog log = new serverLog("HASHLIST");
        File indexPrimaryRoot = new File(new File(homePath), "DATA/INDEX");
        File indexSecondaryRoot = new File(new File(homePath), "DATA/INDEX");
        String wordChunkStartHash = "AAAAAAAAAAAA";
        try {serverLog.configureLogging(homePath,new File(homePath, "DATA/LOG/yacy.logging"));} catch (Exception e) {}
        log.logInfo("STARTING CREATION OF RWI-HASHLIST");
        File root = new File(homePath);
        try {
            Iterator indexContainerIterator = null;
            if (resource.equals("all")) {
                WordIndex = new plasmaWordIndex(indexPrimaryRoot, indexSecondaryRoot, 3000, log);
                indexContainerIterator = WordIndex.wordContainers(wordChunkStartHash, false, false);
            }
            int counter = 0;
            indexContainer container = null;
            if (format.equals("zip")) {
                log.logInfo("Writing Hashlist to ZIP-file: " + targetName + ".zip");
                ZipEntry zipEntry = new ZipEntry(targetName + ".txt");
                File file = new File(root, targetName + ".zip");
                ZipOutputStream bos = new ZipOutputStream(new FileOutputStream(file));
                bos.putNextEntry(zipEntry);
                while (indexContainerIterator.hasNext()) {
                    counter++;
                    container = (indexContainer) indexContainerIterator.next();
                    bos.write((container.getWordHash()).getBytes());
                    bos.write(serverCore.crlf);
                    if (counter % 500 == 0) {
                        log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + container.getWordHash());
                    }
                }
                bos.flush();
                bos.close();
            } else {
                log.logInfo("Writing Hashlist to TXT-file: " + targetName + ".txt");
                File file = new File(root, targetName + ".txt");
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                while (indexContainerIterator.hasNext()) {
                    counter++;
                    container = (indexContainer) indexContainerIterator.next();
                    bos.write((container.getWordHash()).getBytes());
                    bos.write(serverCore.crlf);
                    if (counter % 500 == 0) {
                        log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + container.getWordHash());
                    }
                }
                bos.flush();
                bos.close();
            }
            log.logInfo("Total number of Hashs: " + counter + ". Last found Hash: " + container.getWordHash());
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
        if (WordIndex != null) {
            WordIndex.close();
            WordIndex = null;
        }
    }
    
    /**
     * Searching for peers affected by Bug
     * @param homePath
     */
    public static void testPeerDB(String homePath) {
        
        try {
            File yacyDBPath = new File(new File(homePath), "DATA/YACYDB");
            
            String[] dbFileNames = {"seed.new.db","seed.old.db","seed.pot.db"};
            for (int i=0; i < dbFileNames.length; i++) {
                File dbFile = new File(yacyDBPath,dbFileNames[i]);
                kelondroMapObjects db = new kelondroMapObjects(new kelondroDyn(dbFile, true, true, 3000, yacySeedDB.commonHashLength, 480, '#', kelondroBase64Order.enhancedCoder, true, false, true), 500, yacySeedDB.sortFields, yacySeedDB.longaccFields, yacySeedDB.doubleaccFields, null, null);
                
                kelondroMapObjects.mapIterator it;
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

        // check assertion status
        //ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        boolean assertionenabled = false;
        assert assertionenabled = true;
        if (assertionenabled) System.out.println("Asserts are enabled");
        
        // check memory amount
        System.gc();
        long startupMemFree  = Runtime.getRuntime().freeMemory(); // the amount of free memory in the Java Virtual Machine
        long startupMemTotal = Runtime.getRuntime().totalMemory(); // the total amount of memory in the Java virtual machine; may vary over time
        serverMemory.available(); // force initialization of class serverMemory
        
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        //which XML Parser?
//        if(System.getProperty("javax.xml.parsers.DocumentBuilderFactory")==null){
//            System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "org.apache.crimson.jaxp.DocumentBuilderFactoryImpl");
//        }
//        if(System.getProperty("javax.xml.parsers.SAXParserFactory")==null){
//            System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.crimson.jaxp.SAXParserFactoryImpl");
//        }
        
        String applicationRoot = System.getProperty("user.dir").replace('\\', '/');
        //System.out.println("args.length=" + args.length);
        //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
        if ((args.length >= 1) && ((args[0].toLowerCase().equals("-startup")) || (args[0].equals("-start")))) {
            // normal start-up of yacy
            if (args.length == 2) applicationRoot= args[1];
            startup(applicationRoot, startupMemFree, startupMemTotal);
        } else if ((args.length >= 1) && ((args[0].toLowerCase().equals("-shutdown")) || (args[0].equals("-stop")))) {
            // normal shutdown of yacy
            if (args.length == 2) applicationRoot= args[1];
            shutdown(applicationRoot);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-minimizeurldb"))) {
            // migrate words from DATA/PLASMADB/WORDS path to assortment cache, if possible
            // attention: this may run long and should not be interrupted!
            if (args.length >= 3 && args[1].toLowerCase().equals("-cache")) {
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            minimizeUrlDB(applicationRoot);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-testpeerdb"))) {
            if (args.length == 2) {
                applicationRoot= args[1];
            } else if (args.length > 2) {
                System.err.println("Usage: -testPeerDB [homeDbRoot]");
            }
            testPeerDB(applicationRoot);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-genwordstat"))) {
            // this can help to create a stop-word list
            // to use this, you need a 'yacy.words' file in the root path
            // start this with "java -classpath classes yacy -genwordstat [<rootdir>]"
            if (args.length == 2) applicationRoot= args[1];
            genWordstat(applicationRoot);
        } else if ((args.length == 4) && (args[0].toLowerCase().equals("-cleanwordlist"))) {
            // this can be used to organize and clean a word-list
            // start this with "java -classpath classes yacy -cleanwordlist <word-file> <minlength> <maxlength>"
            int minlength = Integer.parseInt(args[2]);
            int maxlength = Integer.parseInt(args[3]);
            cleanwordlist(args[1], minlength, maxlength);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-transfercr"))) {
            // transfer a single cr file to a remote peer
            String targetaddress = args[1];
            String crfile = args[2];
            transferCR(targetaddress, crfile);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-domlist"))) {
            // generate a url list and save it in a file
            String source = "lurl";
            if (args.length >= 3 && args[1].toLowerCase().equals("-source")) {
                if ((args[2].equals("nurl")) ||
                    (args[2].equals("lurl")) ||
                    (args[2].equals("eurl")))
                    source = args[2];
                args = shift(args, 1, 2);
            }
            String format = "txt";
            if (args.length >= 3 && args[1].toLowerCase().equals("-format")) {
                if ((args[2].equals("html")) ||
                    (args[2].equals("zip")) ||
                    (args[2].equals("gzip")))
                    format = args[2];
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            String outfile = "domlist_" + source + "_" + System.currentTimeMillis();
            domlist(applicationRoot, source, format, outfile);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-urllist"))) {
            // generate a url list and save it in a file
            String source = "lurl";
            if (args.length >= 3 && args[1].toLowerCase().equals("-source")) {
                if ((args[2].equals("nurl")) ||
                    (args[2].equals("lurl")) ||
                    (args[2].equals("eurl")))
                    source = args[2];
                args = shift(args, 1, 2);
            }
            boolean html = false;
            if (args.length >= 3 && args[1].toLowerCase().equals("-format")) {
                if (args[2].equals("html")) html = true;
                args = shift(args, 1, 2);
            }
            if (args.length == 2) applicationRoot= args[1];
            String outfile = "urllist_" + source + "_" + System.currentTimeMillis() + ((html) ? ".html" : ".txt");
            urllist(applicationRoot, source, html, outfile);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-urldbcleanup"))) {
            // generate a url list and save it in a file
            if (args.length == 2) applicationRoot= args[1];
            urldbcleanup(applicationRoot);
        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-rwihashlist"))) {
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
                if (this.mainThread.isAlive() && !this.sb.isTerminated()) {
                    this.mainThread.join();
                }
            }
        } catch (Exception e) {
            serverLog.logSevere("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
