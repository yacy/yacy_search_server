//yacy.java
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.yacy.net
//Frankfurt, Germany, 2004, 2005
//last major change: 24.03.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.data.translator;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpd;
import de.anomic.http.httpdFileHandler;
import de.anomic.http.httpdProxyHandler;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroTree;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.plasma.plasmaWordIndexEntity;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.plasma.plasmaWordIndexEntryContainer;
import de.anomic.plasma.plasmaWordIndexClassicDB;
import de.anomic.plasma.plasmaWordIndexCache;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSystem;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacyCore;

/**
* This is the main class of the proxy. Several threads are started from here:
* <ul>
* <li>one single instance of the plasmaSwitchboard is generated, which itself
* starts a thread with a plasmaHTMLCache object. This object simply counts
* files sizes in the cache and terminates them. It also generates a
* plasmaCrawlerLoader object, which may itself start some more httpc-calling
* threads to load web pages. They terminate automatically when a page has
* loaded.
* <li>one serverCore - thread is started, which implements a multi-threaded
* server. The process may start itself many more processes that handle
* connections.
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
    private static final String copyright = "[ YACY Proxy v" + vString + ", build " + vDATE + " by Michael Christen / www.yacy.net ]";
    private static final String hline = "-------------------------------------------------------------------------------";

    /**
    * Convert the combined versionstring into a pretty string.
    * FIXME: Why is this so complicated?
    *
    * @param s Combined version string
    * @return Pretty string where version and svn-Version are separated by an
    * slash
    */
    public static String combinedVersionString2PrettyString(String s) {
        long svn;
        try {svn = (long) (100000000.0 * Double.parseDouble(s));} catch (NumberFormatException ee) {svn = 0;}
        double version = (Math.floor((double) svn / (double) 100000) / (double) 1000);
        String vStr = (version < 0.11) ? "dev" : "" + version;
        //while (vStr.length() < 5) vStr = vStr + "0";
        svn = svn % 100000;
        if (svn > 4000) svn=svn / 10; // fix a previous bug online
        String svnStr = "" + svn;
        while (svnStr.length() < 5) svnStr = "0" + svnStr;
        return vStr + "/" + svnStr;
    }

    /**
    * Combines the version of the proxy with the versionnumber from svn to a
    * combined Version
    *
    * @param version Current given version for this proxy.
    * @param svn Current version given from svn.
    * @return String with the combined version
    */
    public static float versvn2combinedVersion(float version, int svn) {
        return (float) (((double) version * 100000000.0 + ((double) svn)) / 100000000.0);
    }

    /**
    * Starts up the whole application. Sets up all datastructures and starts
    * the main threads.
    *
    * @param homePath Root-path where all information is to be found.
    */
    private static void startup(String homePath) {
        long startup = yacyCore.universalTime();
        try {
            // start up
            System.out.println(copyright);
            System.out.println(hline);

            // check java version
            try {
                String[] check = "a,b".split(","); // split needs java 1.4
            } catch (NoSuchMethodError e) {
                System.err.println("STARTUP: Java Version too low. You need at least Java 1.4.2 to run YACY");
                Thread.currentThread().sleep(3000);
                System.exit(-1);
            }

            // setting up logging
            try {
                serverLog.configureLogging(new File(homePath, "yacy.logging"));
            } catch (IOException e) {
                System.out.println("could not find logging properties in homePath=" + homePath);
                e.printStackTrace();
            }
            serverLog.logSystem("STARTUP", copyright);
            serverLog.logSystem("STARTUP", hline);

            serverLog.logSystem("STARTUP", "java version " + System.getProperty("java.version", "no-java-version"));
            serverLog.logSystem("STARTUP", "Application Root Path: " + homePath.toString());

            // create data folder
            File dataFolder = new File(homePath, "DATA");
            if (!(dataFolder.exists())) dataFolder.mkdir();

            plasmaSwitchboard sb = new plasmaSwitchboard(homePath, "yacy.init", "DATA/SETTINGS/httpProxy.conf");

            // hardcoded, forced, temporary value-migration
            sb.setConfig("htTemplatePath", "htroot/env/templates");
            sb.setConfig("parseableExt", "html,htm,txt,php,shtml,asp");

            // if we are running an SVN version, we try to detect the used svn revision now ...
            Properties buildProp = new Properties();
            File buildPropFile = null;
            try {
                buildPropFile = new File(homePath,"build.properties");
                buildProp.load(new FileInputStream(buildPropFile));
            } catch (Exception e) {
                System.err.println("ERROR: " + buildPropFile.toString() + " not found in settings path");
            }

            try {
                if (buildProp.containsKey("releaseNr")) {
                    // this normally looks like this: $Revision: 181 $
                    String svnReleaseNrStr = buildProp.getProperty("releaseNr");
                    Pattern pattern = Pattern.compile("\\$Revision:\\s(.*)\\s\\$",Pattern.DOTALL+Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(svnReleaseNrStr);
                    if (matcher.find()) {
                        String svrReleaseNr = matcher.group(1);
                        try {
                            try {version = Float.parseFloat(vString);} catch (NumberFormatException e) {version = (float) 0.1;}
                            version = versvn2combinedVersion(version, Integer.parseInt(svrReleaseNr));
                        } catch (NumberFormatException e) {}
                        sb.setConfig("svnRevision", svrReleaseNr);
                    }
                }
            } catch (Exception e) {
                System.err.println("Unable to determine the currently used SVN revision number.");
            }

            sb.setConfig("version", "" + version);
            sb.setConfig("vdate", vDATE);
            sb.setConfig("applicationRoot", homePath);
            sb.setConfig("startupTime", "" + startup);
            serverLog.logSystem("STARTUP", "YACY Version: " + version + ", Built " + vDATE);
            yacyCore.latestVersion = (float) version;

            // read environment
            //new
            int port          = Integer.parseInt(sb.getConfig("port", "8080"));
            int timeout       = Integer.parseInt(sb.getConfig("httpdTimeout", "60000"));
            if (timeout < 60000) timeout = 60000;
            int maxSessions   = Integer.parseInt(sb.getConfig("httpdMaxSessions", "100"));

            // create some directories
            File htRootPath = new File(sb.getRootPath(), sb.getConfig("htRootPath", "htroot"));
            File htDocsPath = new File(sb.getRootPath(), sb.getConfig("htDocsPath", "DATA/HTDOCS"));
            File htTemplatePath = new File(sb.getRootPath(), sb.getConfig("htTemplatePath","htdocs"));

            // create default notifier picture
            if (!((new File(htRootPath, "env/pictures/notifier.gif")).exists())) try {
                serverFileUtils.copy(new File(htRootPath, "env/pictures/empty.gif"), 
                                     new File(htRootPath, "env/pictures/notifier.gif"));
            } catch (IOException e) {}

            if (!(htDocsPath.exists())) htDocsPath.mkdir();
            File htdocsDefaultReadme = new File(htDocsPath, "readme.txt");
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

            File wwwDefaultPath = new File(htDocsPath, "www");
            if (!(wwwDefaultPath.exists())) wwwDefaultPath.mkdir();

            File wwwDefaultClass = new File(wwwDefaultPath, "welcome.class");
            //if ((!(wwwDefaultClass.exists())) || (wwwDefaultClass.length() != (new File(htRootPath, "htdocsdefault/welcome.class")).length())) try {
            if((new File(htRootPath, "htdocsdefault/welcome.java")).exists())
                serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.java"), new File(wwwDefaultPath, "welcome.java"));
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.class"), wwwDefaultClass);
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/welcome.html"), new File(wwwDefaultPath, "welcome.html"));
            //} catch (IOException e) {}

            File shareDefaultPath = new File(htDocsPath, "share");
            if (!(shareDefaultPath.exists())) shareDefaultPath.mkdir();

            File shareDefaultClass = new File(shareDefaultPath, "dir.class");
            //if ((!(shareDefaultClass.exists())) || (shareDefaultClass.length() != (new File(htRootPath, "htdocsdefault/dir.class")).length())) try {
            if((new File(htRootPath, "htdocsdefault/dir.java")).exists())
                serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.java"), new File(shareDefaultPath, "dir.java"));
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.class"), shareDefaultClass);
            serverFileUtils.copy(new File(htRootPath, "htdocsdefault/dir.html"), new File(shareDefaultPath, "dir.html"));
            //} catch (IOException e) {}


            // set preset accounts/passwords
            String acc;
            if ((acc = sb.getConfig("proxyAccount", "")).length() > 0) {
                sb.setConfig("proxyAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(acc)));
                sb.setConfig("proxyAccount", "");
            }
            if ((acc = sb.getConfig("serverAccount", "")).length() > 0) {
                sb.setConfig("serverAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(acc)));
                sb.setConfig("serverAccount", "");
            }
            if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
                sb.setConfig("adminAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(serverCodings.standardCoder.encodeBase64String(acc)));
                sb.setConfig("adminAccount", "");
            }

            // fix unsafe old passwords
            if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
                sb.setConfig("proxyAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(acc));
                sb.setConfig("proxyAccountBase64", "");
            }
            if ((acc = sb.getConfig("serverAccountBase64", "")).length() > 0) {
                sb.setConfig("serverAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(acc));
                sb.setConfig("serverAccountBase64", "");
            }
            if ((acc = sb.getConfig("adminAccountBase64", "")).length() > 0) {
                sb.setConfig("adminAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(acc));
                sb.setConfig("adminAccountBase64", "");
            }
            if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
                sb.setConfig("uploadAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(acc));
                sb.setConfig("uploadAccountBase64", "");
            }
            if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
                sb.setConfig("downloadAccountBase64MD5", serverCodings.standardCoder.encodeMD5Hex(acc));
                sb.setConfig("downloadAccountBase64", "");
            }

            // start main threads
            try {
                httpd protocolHandler = new httpd(sb, new httpdFileHandler(sb), new httpdProxyHandler(sb));
                serverCore server = new serverCore(port,
                        maxSessions /*sessions*/,
                        timeout /*control socket timeout in milliseconds*/,
                        true /* terminate sleeping threads */,
                        true /* block attacks (wrong protocol) */,
                        protocolHandler /*command class*/,
                        sb,
                        30000 /*command max length incl. GET args*/);
                server.setName("httpd:"+port);
                server.setPriority(Thread.MAX_PRIORITY);
                if (server == null) {
                    serverLog.logFailure("STARTUP", "Failed to start server. Probably port " + port + " already in use.");
                } else {
                    // first start the server
                    sb.deployThread("10_httpd", "HTTPD Server/Proxy", "the HTTPD, used as web server and proxy", null, server, 0, 0, 0, 0);
                    //server.start();

                    // open the browser window
                    boolean browserPopUpTrigger = sb.getConfig("browserPopUpTrigger", "true").equals("true");
                    if (browserPopUpTrigger) {
                        String  browserPopUpPage        = sb.getConfig("browserPopUpPage", "Status.html");
                        String  browserPopUpApplication = sb.getConfig("browserPopUpApplication", "netscape");
                        serverSystem.openBrowser("http://localhost:" + port + "/" + browserPopUpPage, browserPopUpApplication);
                    }

                    //Copy the shipped locales into DATA
                    File localesPath = new File(sb.getRootPath(), sb.getConfig("localesPath", "DATA/LOCALE"));
                    File defaultLocalesPath = new File(sb.getRootPath(), "locales");

                    try{
                        File[] defaultLocales = defaultLocalesPath.listFiles();
                        localesPath.mkdirs();
                        for(int i=0;i < defaultLocales.length; i++){
                            if(defaultLocales[i].getName().endsWith(".lng"))
                                serverFileUtils.copy(defaultLocales[i], new File(localesPath, defaultLocales[i].getName()));
                        }
                        serverLog.logInfo("STARTUP", "Copied the default lokales to DATA/LOCALE");
                    }catch(NullPointerException e){
                        serverLog.logError("STARTUP", "Nullpointer Exception while copying the default Locales");
                    }

                    //regenerate Locales from Translationlist, if needed
                    String lang = sb.getConfig("htLocaleSelection", "");
                    if(! lang.equals("") && ! lang.equals("default") ){ //locale is used
                        String currentRev = "";
                        try{
                            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File( sb.getConfig("htRootPath", "htroot"), "locale/"+lang+"/version" ))));
                            currentRev = br.readLine();
                            br.close();
                        }catch(IOException e){
                            //Error
                        }

                        try{ //seperate try, because we want this, even when the File "version2 does not exist.
                            if(! currentRev.equals(sb.getConfig("svnRevision", "")) ){ //is this another version?!
                                File sourceDir = new File(sb.getConfig("htRootPath", "htroot"));
                                File destDir = new File(sourceDir, "locale/"+lang);
                                if(translator.translateFiles(sourceDir, destDir, new File("DATA/LOCALE/"+lang+".lng"), "html")){ //translate it
                                    //write the new Versionnumber
                                    BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                                    bw.write(sb.getConfig("svnRevision", "Error getting Version"));
                                    bw.close();
                                }
                            }
                        }catch(IOException e){
                            //Error
                        }
                    }

                    // registering shutdown hook
                    serverLog.logSystem("STARTUP", "Registering Shutdown Hook");
                    Runtime run = Runtime.getRuntime();
                    run.addShutdownHook(new shutdownHookThread(Thread.currentThread(), sb));

                    // wait for server shutdown
                    try {
                        sb.waitForShutdown();
                    } catch (Exception e) {
                        serverLog.logError("MAIN CONTROL LOOP", "PANIK: " + e.getMessage(),e);
                    }

                    // shut down
                    serverLog.logSystem("SHUTDOWN", "caught termination signal");
                    server.terminate(false);
                    server.interrupt();
                    if (server.isAlive()) try {
                        httpc.wget(new URL("http://localhost:" + port), 1000, null, null, null, 0); // kick server
                        serverLog.logSystem("SHUTDOWN", "sent termination signal to server socket");
                    } catch (IOException ee) {
                        serverLog.logSystem("SHUTDOWN", "termination signal to server socket missed (server shutdown, ok)");
                    }

                    // idle until the processes are down
                    while (server.isAlive()) {
                        Thread.currentThread().sleep(2000); // wait a while
                    }
                    serverLog.logSystem("SHUTDOWN", "server has terminated");
                    sb.close();
                }
            } catch (Exception e) {
                serverLog.logError("STARTUP", "Unexpected Error: " + e.getClass().getName(),e);
                //System.exit(1);
            }
        } catch (Exception ee) {
            serverLog.logFailure("STARTUP", "FATAL ERROR: " + ee.getMessage(),ee);
            ee.printStackTrace();
        }
        serverLog.logSystem("SHUTDOWN", "goodbye. (this is the last line)");
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
        serverLog.logSystem(mes, "Application Root Path: " + homePath.toString());

        // read data folder
        File dataFolder = new File(homePath, "DATA");
        if (!(dataFolder.exists())) {
            serverLog.logError(mes, "Application was never started or root path wrong.");
            System.exit(-1);
        }

        Properties config = new Properties();
        try {
            config.load(new FileInputStream(new File(homePath, "DATA/SETTINGS/httpProxy.conf")));
        } catch (FileNotFoundException e) {
            serverLog.logError(mes, "could not find configuration file.");
            System.exit(-1);
        } catch (IOException e) {
            serverLog.logError(mes, "could not read configuration file.");
            System.exit(-1);
        }

        return config;
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
        int port = Integer.parseInt((String) config.get("port"));

        // read password
        String encodedPassword = (String) config.get("adminAccountBase64MD5");
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        httpHeader requestHeader = new httpHeader();
        requestHeader.put("Authorization", "realm=" + encodedPassword); // for http-authentify
        try {
            httpc con = httpc.getInstance("localhost", port, 10000, false);
            httpc.response res = con.GET("Steering.html?shutdown=", requestHeader);

            // read response
            if (res.status.startsWith("2")) {
                serverLog.logSystem("REMOTE-SHUTDOWN", "YACY accepted shutdown command.");
                serverLog.logSystem("REMOTE-SHUTDOWN", "Stand by for termination, which may last some seconds.");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                res.writeContent(bos, null);
                con.close();
            } else {
                serverLog.logError("REMOTE-SHUTDOWN", "error response from YACY socket: " + res.status);
                System.exit(-1);
            }
        } catch (IOException e) {
            serverLog.logError("REMOTE-SHUTDOWN", "could not establish connection to YACY socket: " + e.getMessage());
            System.exit(-1);
        }

        // finished
        serverLog.logSystem("REMOTE-SHUTDOWN", "SUCCESSFULLY FINISHED remote-shutdown:");
        serverLog.logSystem("REMOTE-SHUTDOWN", "YACY will terminate after working off all enqueued tasks.");
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
        serverLog.logSystem("GEN-WORDSTAT", "FINISHED");
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
        try {
            serverLog log = new serverLog("WORDMIGRATION");
            log.logInfo("STARTING MIGRATION");
            plasmaWordIndexCache wordIndexCache = new plasmaWordIndexCache(dbroot, new plasmaWordIndexClassicDB(dbroot, log), 20000, log);
            enumerateFiles words = new enumerateFiles(new File(dbroot, "WORDS"), true, false, true, true);
            String wordhash;
            File wordfile;
            int migration;
            while (words.hasMoreElements()) try {
                wordfile = (File) words.nextElement();
                wordhash = wordfile.getName().substring(0, 12);
                migration = wordIndexCache.migrateWords2Assortment(wordhash);
                if (migration == 0)
                    log.logInfo("SKIPPED  " + wordhash + ": too big");
                else if (migration > 0)
                    log.logInfo("MIGRATED " + wordhash + ": " + migration + " entries");
                else
                    log.logInfo("REVERSED " + wordhash + ": " + (-migration) + " entries");
            } catch (Exception e) {
                e.printStackTrace();
            }
            log.logInfo("FINISHED MIGRATION JOB, WAIT FOR DUMP");
            wordIndexCache.close(60);
            log.logInfo("TERMINATED MIGRATION");
        } catch (IOException e) {
            e.printStackTrace();
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
            while ((word = br.readLine()) != null) wordmap.put(plasmaWordIndexEntry.word2hash(word),word);
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
        serverLog.logSystem("CLEAN-WORDLIST", "START");

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
            serverLog.logError("CLEAN-WORDLIST", "ERROR: " + e.getMessage());
            System.exit(-1);
        }

        // finished
        serverLog.logSystem("CLEAN-WORDLIST", "FINISHED");
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
        serverLog.logSystem("DELETE-STOPWORDS", "START");

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
            f = plasmaWordIndexEntity.wordHash2path(dbRoot, plasmaWordIndexEntry.word2hash(w));
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
        serverLog.logSystem("DELETE-STOPWORDS", "FINISHED");
    }

    /**
    * Main-method which is started by java. Checks for special arguments or
    * starts up the application.
    *
    * @param args Given arguments from the command line.
    */
    public static void main(String args[]) {
        String applicationRoot = System.getProperty("user.dir");
        //System.out.println("args.length=" + args.length);
        //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
        if ((args.length >= 1) && ((args[0].equals("-startup")) || (args[0].equals("-start")))) {
            // normal start-up of yacy
            if (args.length == 2) applicationRoot= args[1];
            startup(applicationRoot);
        } else if ((args.length >= 1) && ((args[0].equals("-shutdown")) || (args[0].equals("-stop")))) {
            // normal shutdown of yacy
            if (args.length == 2) applicationRoot= args[1];
            shutdown(applicationRoot);
        } else if ((args.length >= 1) && (args[0].equals("-migratewords"))) {
            // migrate words from DATA/PLASMADB/WORDS path to assortment cache, if possible
            // attention: this may run long and should not be interrupted!
            if (args.length == 2) applicationRoot= args[1];
            migrateWords(applicationRoot);
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
        } else {
            if (args.length == 1) applicationRoot= args[0];
            startup(applicationRoot);
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
        this.sb = sb;
        this.mainThread = mainThread;
    }

    public void run() {

        try {
            if (!this.sb.isTerminated()) {
                serverLog.logSystem("SHUTDOWN","Shutdown via shutdown hook.");

                // sending the yacy main thread a shutdown signal
                this.sb.terminate();

                // waiting for the yacy thread to finish execution
                this.mainThread.join();
            }
        } catch (Exception e) {
            serverLog.logFailure("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
