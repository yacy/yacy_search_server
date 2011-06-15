package net.yacy;
// yacy.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
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


//import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
//import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.storage.OrderedScoreMap;
import net.yacy.cora.storage.ScoreMap;
import net.yacy.gui.YaCyApp;
import net.yacy.gui.framework.Browser;
import net.yacy.kelondro.blob.MapDataMining;
//import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;

//import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;

import de.anomic.data.Translator;
//import de.anomic.http.client.Client;
import de.anomic.http.server.HTTPDemon;
//import de.anomic.http.server.ResponseContainer;
import de.anomic.search.MetadataRepository;
import de.anomic.search.Segment;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.Tray;
import de.anomic.yacy.yacyBuildProperties;
import de.anomic.yacy.yacyRelease;
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
* <li>stop feeding of the crawling process because it otherwise fills the
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
    public static final String vString = yacyBuildProperties.getVersion();
    public static float version = 0.1f;
    
    public static final String vDATE   = yacyBuildProperties.getBuildDate();
    public static final String copyright = "[ YaCy v" + vString + ", build " + vDATE + " by Michael Christen / www.yacy.net ]";
    public static final String hline = "-------------------------------------------------------------------------------";
    public static final Semaphore shutdownSemaphore = new Semaphore(0);
    
    /**
     * a reference to the {@link Switchboard} created by the
     * {@link yacy#startup(String, long, long)} method.
     */
    private static Switchboard sb = null;

    /**
    * Starts up the whole application. Sets up all datastructures and starts
    * the main threads.
    *
    * @param homePath Root-path where all information is to be found.
    * @param startupFree free memory at startup time, to be used later for statistics
    */
    private static void startup(final File dataHome, final File appHome, final long startupMemFree, final long startupMemTotal, boolean gui) {
        try {
            // start up
            System.out.println(copyright);
            System.out.println(hline);

            // check java version
            try {
                "a".codePointAt(0); // needs at least Java 1.5
            } catch (final NoSuchMethodError e) {
                System.err.println("STARTUP: Java Version too low. You need at least Java 1.5 to run YaCy"); // TODO: is 1.6 now
                Thread.sleep(3000);
                System.exit(-1);
            }
            
            // ensure that there is a DATA directory, if not, create one and if that fails warn and die
            mkdirsIfNeseccary(dataHome);
            mkdirsIfNeseccary(appHome);
            File f = new File(dataHome, "DATA/");
            mkdirsIfNeseccary(f);
			if (!(f.exists())) { 
				System.err.println("Error creating DATA-directory in " + dataHome.toString() + " . Please check your write-permission for this folder. YaCy will now terminate."); 
				System.exit(-1); 
			}
            
            // setting up logging
			f = new File(dataHome, "DATA/LOG/");
            mkdirsIfNeseccary(f);
			f = new File(dataHome, "DATA/LOG/yacy.logging");
			//if (!f.exists()) try {
			    FileUtils.copy(new File(appHome, "yacy.logging"), f);
            //} catch (final IOException e){
            //    System.out.println("could not copy yacy.logging");
            //}
            try{
                Log.configureLogging(dataHome, appHome, new File(dataHome, "DATA/LOG/yacy.logging"));
            } catch (final IOException e) {
                System.out.println("could not find logging properties in homePath=" + dataHome);
                Log.logException(e);
            }
            Log.logConfig("STARTUP", "YaCy version: " + yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision());
            Log.logConfig("STARTUP", "Java version: " + System.getProperty("java.version", "no-java-version"));
            Log.logConfig("STARTUP", "Operation system: " + System.getProperty("os.name","unknown"));
            Log.logConfig("STARTUP", "Application root-path: " + appHome);
            Log.logConfig("STARTUP", "Data root-path: " + dataHome);
            Log.logConfig("STARTUP", "Time zone: UTC" + GenericFormatter.UTCDiffString() + "; UTC+0000 is " + System.currentTimeMillis());
            Log.logConfig("STARTUP", "Maximum file system path length: " + OS.maxPathLength);
            
            f = new File(dataHome, "DATA/yacy.running");
            if (f.exists()) {                // another instance running? VM crash? User will have to care about this
                Log.logSevere("STARTUP", "WARNING: the file " + f + " exists, this usually means that a YaCy instance is still running");
                delete(f);
            }
            if(!f.createNewFile())
                Log.logSevere("STARTUP", "WARNING: the file " + f + " can not be created!");
            try { new FileOutputStream(f).write(Integer.toString(OS.getPID()).getBytes()); } catch (Exception e) { } // write PID
            f.deleteOnExit();
            
            final String oldconf = "DATA/SETTINGS/httpProxy.conf".replace("/", File.separator);
            final String newconf = "DATA/SETTINGS/yacy.conf".replace("/", File.separator);
            final File oldconffile = new File(dataHome, oldconf);
            if (oldconffile.exists()) {
            	final File newconfFile = new File(dataHome, newconf);
                if(!oldconffile.renameTo(newconfFile))
                    Log.logSevere("STARTUP", "WARNING: the file " + oldconffile + " can not be renamed to "+ newconfFile +"!");
            }
            sb = new Switchboard(dataHome, appHome, "defaults/yacy.init".replace("/", File.separator), newconf);
            //sbSync.V(); // signal that the sb reference was set
            
            // save information about available memory at startup time
            sb.setConfig("memoryFreeAfterStartup", startupMemFree);
            sb.setConfig("memoryTotalAfterStartup", startupMemTotal);
            
            // start gui if wanted
            if (gui) YaCyApp.start("localhost", (int) sb.getConfigLong("port", 8090));
            
            // hardcoded, forced, temporary value-migration
            sb.setConfig("htTemplatePath", "htroot/env/templates");

            int oldRev;
    	    try {
                oldRev = Integer.parseInt(sb.getConfig("svnRevision", "0"));
            } catch (NumberFormatException e) {
                oldRev = 0;
    	    }
            int newRev = Integer.parseInt(yacyBuildProperties.getSVNRevision());
            sb.setConfig("svnRevision", yacyBuildProperties.getSVNRevision());
            sb.setConfig("applicationRoot", appHome.toString());
            sb.setConfig("dataRoot", dataHome.toString());
            yacyVersion.latestRelease = version;

            // read environment
            final int timeout = Math.max(5000, Integer.parseInt(sb.getConfig("httpdTimeout", "5000")));

            // create some directories
            final File htRootPath = new File(appHome, sb.getConfig("htRootPath", "htroot"));
            final File htDocsPath = sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
            mkdirIfNeseccary(htDocsPath);
            //final File htTemplatePath = new File(homePath, sb.getConfig("htTemplatePath","htdocs"));

            // create default notifier picture
            //TODO: Use templates instead of copying images ...
            if (!((new File(htDocsPath, "notifier.gif")).exists())) try {
                FileUtils.copy(new File(htRootPath, "env/grafics/empty.gif"),
                                     new File(htDocsPath, "notifier.gif"));
            } catch (final IOException e) {}

            final File htdocsReadme = new File(htDocsPath, "readme.txt");
            if (!(htdocsReadme.exists())) try {FileUtils.copy((
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
                    "\r\n").getBytes(), htdocsReadme);} catch (final IOException e) {
                        System.out.println("Error creating htdocs readme: " + e.getMessage());
                    }

            final File wwwDefaultPath = new File(htDocsPath, "www");
            mkdirIfNeseccary(wwwDefaultPath);


            final File shareDefaultPath = new File(htDocsPath, "share");
            mkdirIfNeseccary(shareDefaultPath);

            migration.migrate(sb, oldRev, newRev);
            
            // delete old release files
            final int deleteOldDownloadsAfterDays = (int) sb.getConfigLong("update.deleteOld", 30);
            yacyRelease.deleteOldDownloads(sb.releasePath, deleteOldDownloadsAfterDays );
            
            // set user-agent
            HTTPClient.setDefaultUserAgent(ClientIdentification.getUserAgent());
            
            // start main threads
            final String port = sb.getConfig("port", "8090");
            try {
                final HTTPDemon protocolHandler = new HTTPDemon(sb);
                final serverCore server = new serverCore(
                        timeout /*control socket timeout in milliseconds*/,
                        true /* block attacks (wrong protocol) */,
                        protocolHandler /*command class*/,
                        sb,
                        30000 /*command max length incl. GET args*/);
                server.setName("httpd:"+port);
                server.setPriority(Thread.MAX_PRIORITY);
                server.setObeyIntermission(false);
                
                // start the server
                sb.deployThread("10_httpd", "HTTPD Server/Proxy", "the HTTPD, used as web server and proxy", null, server, 0, 0, 0, 0);
                //server.start();

                // open the browser window
                final boolean browserPopUpTrigger = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_TRIGGER, "true").equals("true");
                if (browserPopUpTrigger) try {
                    final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
                    //boolean properPW = (sb.getConfig("adminAccount", "").length() == 0) && (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0);
                    //if (!properPW) browserPopUpPage = "ConfigBasic.html";
                    Browser.openBrowser((server.withSSL()?"https":"http") + "://localhost:" + serverCore.getPortNr(port) + "/" + browserPopUpPage);
                } catch (RuntimeException e) {
                    Log.logException(e);
                }
                
                // unlock yacyTray browser popup
                Tray.lockBrowserPopup = false;

                // Copy the shipped locales into DATA, existing files are overwritten
                final File locale_work   = sb.getDataPath("locale.work", "DATA/LOCALE/locales");
                final File locale_source = sb.getAppPath("locale.source", "locales");
                try{
                    final File[] locale_source_files = locale_source.listFiles();
                    mkdirsIfNeseccary(locale_work);
                    File target;
                    for (int i=0; i < locale_source_files.length; i++){
                    	target = new File(locale_work, locale_source_files[i].getName());
                        if (locale_source_files[i].getName().endsWith(".lng")) {
                        	if (target.exists()) delete(target);
                            FileUtils.copy(locale_source_files[i], target);
                        }
                    }
                    Log.logInfo("STARTUP", "Copied the default locales to " + locale_work.toString());
                }catch(final NullPointerException e){
                    Log.logSevere("STARTUP", "Nullpointer Exception while copying the default Locales");
                }

                //regenerate Locales from Translationlist, if needed
                final String lang = sb.getConfig("locale.language", "");
                if (!lang.equals("") && !lang.equals("default")) { //locale is used
                    String currentRev = "";
                    try{
                        final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), lang+"/version" ))));
                        currentRev = br.readLine();
                        br.close();
                    }catch(final IOException e){
                        //Error
                    }

                    if (!currentRev.equals(sb.getConfig("svnRevision", ""))) try { //is this another version?!
                        final File sourceDir = new File(sb.getConfig("htRootPath", "htroot"));
                        final File destDir = new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), lang);
                        if (Translator.translateFilesRecursive(sourceDir, destDir, new File(locale_work, lang + ".lng"), "html,template,inc", "locale")){ //translate it
                            //write the new Versionnumber
                            final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                            bw.write(sb.getConfig("svnRevision", "Error getting Version"));
                            bw.close();
                        }
                    } catch (final IOException e) {}
                }
                // initialize number formatter with this locale
                Formatter.setLocale(lang);
                
                // registering shutdown hook
                Log.logConfig("STARTUP", "Registering Shutdown Hook");
                final Runtime run = Runtime.getRuntime();
                run.addShutdownHook(new shutdownHookThread(Thread.currentThread(), sb));

                // save information about available memory after all initializations
                //try {
                    sb.setConfig("memoryFreeAfterInitBGC", MemoryControl.free());
                    sb.setConfig("memoryTotalAfterInitBGC", MemoryControl.total());
                    System.gc();
                    sb.setConfig("memoryFreeAfterInitAGC", MemoryControl.free());
                    sb.setConfig("memoryTotalAfterInitAGC", MemoryControl.total());
                //} catch (ConcurrentModificationException e) {}
                    
                // wait for server shutdown
                try {
                    sb.waitForShutdown();
                } catch (final Exception e) {
                    Log.logSevere("MAIN CONTROL LOOP", "PANIC: " + e.getMessage(),e);
                }
                // shut down
                if (RowCollection.sortingthreadexecutor != null) RowCollection.sortingthreadexecutor.shutdown();
                Log.logConfig("SHUTDOWN", "caught termination signal");
                server.terminate(false);
                server.interrupt();
                server.close();
                /*
                if (server.isAlive()) try {
                    // TODO only send request, don't read response (cause server is already down resulting in error)
                    final DigestURI u = new DigestURI((server.withSSL()?"https":"http")+"://localhost:" + serverCore.getPortNr(port), null);
                    Client.wget(u.toString(), null, 10000); // kick server
                    Log.logConfig("SHUTDOWN", "sent termination signal to server socket");
                } catch (final IOException ee) {
                    Log.logConfig("SHUTDOWN", "termination signal to server socket missed (server shutdown, ok)");
                }
                */
//                Client.closeAllConnections();
//                MultiThreadedHttpConnectionManager.shutdownAll();
                
                // idle until the processes are down
                if (server.isAlive()) {
                    //Thread.sleep(2000); // wait a while
                    server.interrupt();
//                    MultiThreadedHttpConnectionManager.shutdownAll();
                }
//                MultiThreadedHttpConnectionManager.shutdownAll();
                Log.logConfig("SHUTDOWN", "server has terminated");
                sb.close();
            } catch (final Exception e) {
                Log.logSevere("STARTUP", "Unexpected Error: " + e.getClass().getName(),e);
                //System.exit(1);
            }
        } catch (final Exception ee) {
            Log.logSevere("STARTUP", "FATAL ERROR: " + ee.getMessage(),ee);
        } finally {
        }
        Log.logConfig("SHUTDOWN", "goodbye. (this is the last line)");
        Log.shutdown();
        shutdownSemaphore.release(1000);
        try {
            System.exit(0);
        } catch (Exception e) {} // was once stopped by de.anomic.net.ftpc$sm.checkExit(ftpc.java:1790)
    }

	/**
	 * @param f
	 */
	private static void delete(File f) {
		if(!f.delete())
		    Log.logSevere("STARTUP", "WARNING: the file " + f + " can not be deleted!");
	}

	/**
	 * @see File#mkdir()
	 * @param path
	 */
	private static void mkdirIfNeseccary(final File path) {
		if (!(path.exists()))
			if(!path.mkdir())
				Log.logWarning("STARTUP", "could not create directory "+ path.toString());
	}

	/**
	 * @see File#mkdirs()
	 * @param path
	 */
	private static void mkdirsIfNeseccary(final File path) {
		if (!(path.exists()))
			if(!path.mkdirs())
				Log.logWarning("STARTUP", "could not create directories "+ path.toString());
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
    private static Properties configuration(final String mes, final File homePath) {
        Log.logConfig(mes, "Application Root Path: " + homePath.toString());

        // read data folder
        final File dataFolder = new File(homePath, "DATA");
        if (!(dataFolder.exists())) {
            Log.logSevere(mes, "Application was never started or root path wrong.");
            System.exit(-1);
        }

        final Properties config = new Properties();
        FileInputStream fis = null;
		try {
        	fis  = new FileInputStream(new File(homePath, "DATA/SETTINGS/yacy.conf"));
            config.load(fis);
        } catch (final FileNotFoundException e) {
            Log.logSevere(mes, "could not find configuration file.");
            System.exit(-1);
        } catch (final IOException e) {
            Log.logSevere(mes, "could not read configuration file.");
            System.exit(-1);
        } finally {
        	if(fis != null) {
        		try {
					fis.close();
				} catch (IOException e) {
				    Log.logException(e);
				}
        	}
        }

        return config;
    }
    
    /**
    * Call the shutdown-page of YaCy to tell it to shut down. This method is
    * called if you start yacy with the argument -shutdown.
    *
    * @param homePath Root-path where all the information is to be found.
    */
    public static void shutdown(final File homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        submitURL(homePath, "Steering.html?shutdown=", "Terminate YaCy");
    }

    public static void update(final File homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        submitURL(homePath, "ConfigUpdate_p.html?autoUpdate=", "Update YaCy to most recent version");
    }

    private static void submitURL(final File homePath, String path, String processdescription) {
        final Properties config = configuration("COMMAND-STEERING", homePath);

        // read port
        final int port = serverCore.getPortNr(config.getProperty("port", "8090"));

        // read password
        String encodedPassword = (String) config.get(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5);
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(RequestHeader.AUTHORIZATION, "realm=" + encodedPassword); // for http-authentify
//        final Client con = new Client(10000, requestHeader);
        final HTTPClient con = new HTTPClient();
        con.setHeader(requestHeader.entrySet());
//        ResponseContainer res = null;
        try {
//            res = con.GET("http://localhost:"+ port +"/" + path);
            con.GETbytes("http://localhost:"+ port +"/" + path);

            // read response
//            if (res.getStatusLine().startsWith("2")) {
            if (con.getStatusCode() > 199 && con.getStatusCode() < 300) {
                Log.logConfig("COMMAND-STEERING", "YACY accepted steering command: " + processdescription);
//                final ByteArrayOutputStream bos = new ByteArrayOutputStream(); //This is stream is not used???
//                try {
//                    FileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), new BufferedOutputStream(bos));
//                } finally {
//                    res.closeStream();
//                }
            } else {
//                Log.logSevere("COMMAND-STEERING", "error response from YACY socket: " + res.getStatusLine());
            	Log.logSevere("COMMAND-STEERING", "error response from YACY socket: " + con.getHttpResponse().getStatusLine());
                System.exit(-1);
            }
        } catch (final IOException e) {
            Log.logSevere("COMMAND-STEERING", "could not establish connection to YACY socket: " + e.getMessage());
            System.exit(-1);
//        } finally {
//            // release connection
//            if(res != null) {
//                res.closeStream();
//            }
        }
        
        try {
			HTTPClient.closeConnectionManager();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

        // finished
        Log.logConfig("COMMAND-STEERING", "SUCCESSFULLY FINISHED COMMAND: " + processdescription);
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
    private static void genWordstat(final File homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);

        // load words
        Log.logInfo("GEN-WORDSTAT", "loading words...");
        final TreeMap<byte[], String> words = loadWordMap(new File(homePath, "yacy.words"));

        // find all hashes
        Log.logInfo("GEN-WORDSTAT", "searching all word-hash databases...");
        final File dbRoot = new File(homePath, "DATA/INDEX/freeworld/");
        final enumerateFiles ef = new enumerateFiles(new File(dbRoot, "WORDS"), true, false, true, true);
        File f;
        byte[] h;
        final ScoreMap<byte[]> hs = new OrderedScoreMap<byte[]>(Base64Order.standardCoder);
        while (ef.hasMoreElements()) {
            f = ef.nextElement();
            h = f.getName().substring(0, Word.commonHashLength).getBytes();
            hs.inc(h, (int) f.length());
        }

        // list the hashes in reverse order
        Log.logInfo("GEN-WORDSTAT", "listing words in reverse size order...");
        String w;
        final Iterator<byte[]> i = hs.keys(false);
        while (i.hasNext()) {
            h = i.next();
            w = words.get(h);
            if (w == null) System.out.print("# " + h); else System.out.print(w);
            System.out.println(" - " + hs.get(h));
        }

        // finished
        Log.logConfig("GEN-WORDSTAT", "FINISHED");
    }
    
    /**
     * @param homePath path to the YaCy directory
     * @param networkName 
     */
    public static void minimizeUrlDB(final File dataHome, final File appHome, final String networkName) {
        // run with "java -classpath classes yacy -minimizeUrlDB"
        try {Log.configureLogging(dataHome, appHome, new File(dataHome, "DATA/LOG/yacy.logging"));} catch (final Exception e) {}
        final File indexPrimaryRoot = new File(dataHome, "DATA/INDEX");
        final File indexRoot2 = new File(dataHome, "DATA/INDEX2");
        final Log log = new Log("URL-CLEANUP");
        try {
            log.logInfo("STARTING URL CLEANUP");
            
            // db containing all currently loades urls
            final MetadataRepository currentUrlDB = new MetadataRepository(new File(new File(indexPrimaryRoot, networkName), "TEXT"), "text.urlmd", false, false);
            
            // db used to hold all neede urls
            final MetadataRepository minimizedUrlDB = new MetadataRepository(new File(new File(indexRoot2, networkName), "TEXT"), "text.urlmd", false, false);
            
            final int cacheMem = (int)(MemoryControl.maxMemory - MemoryControl.total());
            if (cacheMem < 2048000) throw new OutOfMemoryError("Not enough memory available to start clean up.");
                
            final Segment wordIndex = new Segment(
                    log,
                    new File(new File(indexPrimaryRoot, "freeworld"), "TEXT"),
                    10000,
                    (long) Integer.MAX_VALUE, false, false);
            final Iterator<ReferenceContainer<WordReference>> indexContainerIterator = wordIndex.termIndex().references("AAAAAAAAAAAA".getBytes(), false, false);
            
            long urlCounter = 0, wordCounter = 0;
            long wordChunkStart = System.currentTimeMillis(), wordChunkEnd = 0;
            String wordChunkStartHash = "AAAAAAAAAAAA", wordChunkEndHash;
            
            while (indexContainerIterator.hasNext()) {
                ReferenceContainer<WordReference> wordIdxContainer = null;
                try {
                    wordCounter++;
                    wordIdxContainer = indexContainerIterator.next();
                    
                    // the combined container will fit, read the container
                    final Iterator<WordReference> wordIdxEntries = wordIdxContainer.entries();
                    Reference iEntry;
                    while (wordIdxEntries.hasNext()) {
                        iEntry = wordIdxEntries.next();
                        final byte[] urlHash = iEntry.urlhash();                    
                        if ((currentUrlDB.exists(urlHash)) && (!minimizedUrlDB.exists(urlHash))) try {
                            final URIMetadataRow urlEntry = currentUrlDB.load(urlHash);                       
                            urlCounter++;
                            minimizedUrlDB.store(urlEntry);
                            if (urlCounter % 500 == 0) {
                                log.logInfo(urlCounter + " URLs found so far.");
                            }
                        } catch (final IOException e) {}
                    }
                    
                    if (wordCounter%500 == 0) {
                        wordChunkEndHash = ASCII.String(wordIdxContainer.getTermHash());
                        wordChunkEnd = System.currentTimeMillis();
                        final long duration = wordChunkEnd - wordChunkStart;
                        log.logInfo(wordCounter + " words scanned " +
                                "[" + wordChunkStartHash + " .. " + wordChunkEndHash + "]\n" + 
                                "Duration: "+ 500*1000/duration + " words/s" +
                                " | Free memory: " + MemoryControl.free() + 
                                " | Total memory: " + MemoryControl.total());
                        wordChunkStart = wordChunkEnd;
                        wordChunkStartHash = wordChunkEndHash;
                    }
                    
                    // we have read all elements, now we can close it
                    wordIdxContainer = null;
                    
                } catch (final Exception e) {
                    log.logSevere("Exception", e);
                } finally {
                    if (wordIdxContainer != null) try { wordIdxContainer = null; } catch (final Exception e) {}
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
        } catch (final Exception e) {
            log.logSevere("Exception: " + e.getMessage(), e);
        } catch (final Error e) {
            log.logSevere("Error: " + e.getMessage(), e);
        }
    }

    /**
    * Reads all words from the given file and creates a treemap, where key is
    * the plasma word hash and value is the word itself.
    *
    * @param wordlist File where the words are stored.
    * @return HashMap with the hash-word - relation.
    */
    private static TreeMap<byte[], String> loadWordMap(final File wordlist) {
        // returns a hash-word - Relation
        final TreeMap<byte[], String> wordmap = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        try {
            String word;
            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wordlist)));
            while ((word = br.readLine()) != null) wordmap.put(Word.word2hash(word), word);
            br.close();
        } catch (final IOException e) {}
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
    private static void cleanwordlist(final String wordlist, final int minlength, final int maxlength) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        Log.logConfig("CLEAN-WORDLIST", "START");

        String word;
        final TreeSet<String> wordset = new TreeSet<String>();
        int count = 0;
        try {
            final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(wordlist)));
            final String seps = "' .,:/-&";
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
                final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(wordlist)));
                while (!wordset.isEmpty()) {
                    word = wordset.first();
                    bw.write(word + "\n");
                    wordset.remove(word);
                }
                bw.close();
                Log.logInfo("CLEAN-WORDLIST", "shrinked wordlist by " + count + " words.");
            } else {
                Log.logInfo("CLEAN-WORDLIST", "not necessary to change wordlist");
            }
        } catch (final IOException e) {
            Log.logSevere("CLEAN-WORDLIST", "ERROR: " + e.getMessage());
            System.exit(-1);
        }

        // finished
        Log.logConfig("CLEAN-WORDLIST", "FINISHED");
    }
    
    private static String[] shift(final String[] args, final int pos, final int count) {
        final String[] newargs = new String[args.length - count];
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
    private static void urldbcleanup(final File dataHome, final File appHome, final String networkName) {
        final File root = dataHome;
        final File indexroot = new File(root, "DATA/INDEX");
        try {Log.configureLogging(dataHome, appHome, new File(dataHome, "DATA/LOG/yacy.logging"));} catch (final Exception e) {}
        final MetadataRepository currentUrlDB = new MetadataRepository(new File(new File(indexroot, networkName), "TEXT"), "text.urlmd", false, false);
        currentUrlDB.deadlinkCleaner();
        currentUrlDB.close();
    }
    
    private static void RWIHashList(final File dataHome, final File appHome, final String targetName, final String resource, final String format) {
        Segment WordIndex = null;
        final Log log = new Log("HASHLIST");
        final File indexPrimaryRoot = new File(dataHome, "DATA/INDEX");
        final String wordChunkStartHash = "AAAAAAAAAAAA";
        try {Log.configureLogging(dataHome, appHome, new File(dataHome, "DATA/LOG/yacy.logging"));} catch (final Exception e) {}
        log.logInfo("STARTING CREATION OF RWI-HASHLIST");
        final File root = dataHome;
        try {
            Iterator<ReferenceContainer<WordReference>> indexContainerIterator = null;
            if (resource.equals("all")) {
                WordIndex = new Segment(
                        log,
                        new File(new File(indexPrimaryRoot, "freeworld"), "TEXT"),
                        10000,
                        (long) Integer.MAX_VALUE, false, false);
                indexContainerIterator = WordIndex.termIndex().references(wordChunkStartHash.getBytes(), false, false);
            }
            int counter = 0;
            ReferenceContainer<WordReference> container = null;
            if (format.equals("zip")) {
                log.logInfo("Writing Hashlist to ZIP-file: " + targetName + ".zip");
                final ZipEntry zipEntry = new ZipEntry(targetName + ".txt");
                final File file = new File(root, targetName + ".zip");
                final ZipOutputStream bos = new ZipOutputStream(new FileOutputStream(file));
                bos.putNextEntry(zipEntry);
                if(indexContainerIterator != null) {
                    while (indexContainerIterator.hasNext()) {
                        counter++;
                        container = indexContainerIterator.next();
                        bos.write(container.getTermHash());
                        bos.write(serverCore.CRLF);
                        if (counter % 500 == 0) {
                            log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + ASCII.String(container.getTermHash()));
                        }
                    }
                }
                bos.flush();
                bos.close();
            } else {
                log.logInfo("Writing Hashlist to TXT-file: " + targetName + ".txt");
                final File file = new File(root, targetName + ".txt");
                final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                if(indexContainerIterator != null) {
                    while (indexContainerIterator.hasNext()) {
                        counter++;
                        container = indexContainerIterator.next();
                        bos.write(container.getTermHash());
                        bos.write(serverCore.CRLF);
                        if (counter % 500 == 0) {
                            log.logInfo("Found " + counter + " Hashs until now. Last found Hash: " + ASCII.String(container.getTermHash()));
                        }
                    }
                }
                bos.flush();
                bos.close();
            }
            log.logInfo("Total number of Hashs: " + counter + ". Last found Hash: " + (container == null ? "null" : ASCII.String(container.getTermHash())));
        } catch (final IOException e) {
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
    public static void testPeerDB(final File homePath) {
        
        try {
            final File yacyDBPath = new File(homePath, "DATA/INDEX/freeworld/NETWORK");
            
            final String[] dbFileNames = {"seed.new.db","seed.old.db","seed.pot.db"};
            for (int i=0; i < dbFileNames.length; i++) {
                final File dbFile = new File(yacyDBPath,dbFileNames[i]);
                final MapDataMining db = new MapDataMining(dbFile, Word.commonHashLength, Base64Order.enhancedCoder, 1024 * 512, 500, yacySeedDB.sortFields, yacySeedDB.longaccFields, yacySeedDB.doubleaccFields, null);
                
                MapDataMining.mapIterator it;
                it = db.maps(true, false);
                while (it.hasNext()) {
                    final Map<String, String> dna = it.next();
                    String peerHash = dna.get("key");
                    if (peerHash.length() < Word.commonHashLength) {
                        final String peerName = dna.get("Name");
                        final String peerIP = dna.get("IP");
                        final String peerPort = dna.get("Port");
                        
                        while (peerHash.length() < Word.commonHashLength) { peerHash = peerHash + "_"; }                        
                        System.err.println("Invalid Peer-Hash found in '" + dbFileNames[i] + "': " + peerName + ":" +  peerHash + ", http://" + peerIP + ":" + peerPort);
                    }
                }
                db.close();
            }
        } catch (final Exception e) {
            Log.logException(e);
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
    	
    	try {
	
	        // check assertion status
	        //ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
	        boolean assertionenabled = false;
	        assert assertionenabled = true;
	        if (assertionenabled) System.out.println("Asserts are enabled");
	        
	        // check memory amount
	        System.gc();
	        final long startupMemFree  = MemoryControl.free();
	        final long startupMemTotal = MemoryControl.total();
	        
	        // maybe go into headless awt mode: we have three cases depending on OS and one exception:
	        // windows   : better do not go into headless mode
	        // mac       : go into headless mode because an application is shown in gui which may not be wanted
	        // linux     : go into headless mode because this does not need any head operation
	        // exception : if the -gui option is used then do not go into headless mode since that uses a gui
	        boolean headless = true;
	        if (OS.isWindows) headless = false;
	        if (args.length >= 1 && args[0].toLowerCase().equals("-gui")) headless = false;
	        System.setProperty("java.awt.headless", headless ? "true" : "false");
	        
	        String s = ""; for (String a: args) s += a + " ";
	        yacyRelease.startParameter = s.trim();
	        
	        File applicationRoot = new File(System.getProperty("user.dir").replace('\\', '/'));
	        File dataRoot = applicationRoot;
	        //System.out.println("args.length=" + args.length);
	        //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
	        if ((args.length >= 1) && (args[0].toLowerCase().equals("-startup") || args[0].equals("-start"))) {
	            // normal start-up of yacy
	            if (args.length > 1) dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
	            startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, false);
	        } else if (args.length >= 1 && args[0].toLowerCase().equals("-gui")) {
	            // start-up of yacy with gui
	            if (args.length > 1) dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
	            startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, true);
	        } else if ((args.length >= 1) && ((args[0].toLowerCase().equals("-shutdown")) || (args[0].equals("-stop")))) {
	            // normal shutdown of yacy
	            if (args.length == 2) applicationRoot= new File(args[1]);
	            shutdown(applicationRoot);
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-update"))) {
	            // aut-update yacy
	            if (args.length == 2) applicationRoot= new File(args[1]);
	            update(applicationRoot);
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-version"))) {
	            // show yacy version
	            System.out.println(copyright);
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-minimizeurldb"))) {
	            // migrate words from DATA/PLASMADB/WORDS path to assortment cache, if possible
	            // attention: this may run long and should not be interrupted!
	            if (args.length >= 3 && args[1].toLowerCase().equals("-cache")) {
	                args = shift(args, 1, 2);
	            }
	            if (args.length == 2) applicationRoot= new File(args[1]);
	            minimizeUrlDB(dataRoot, applicationRoot, "freeworld");
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-testpeerdb"))) {
	            if (args.length == 2) {
	                applicationRoot = new File(args[1]);
	            } else if (args.length > 2) {
	                System.err.println("Usage: -testPeerDB [homeDbRoot]");
	            }
	            testPeerDB(applicationRoot);
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-genwordstat"))) {
	            // this can help to create a stop-word list
	            // to use this, you need a 'yacy.words' file in the root path
	            // start this with "java -classpath classes yacy -genwordstat [<rootdir>]"
	            if (args.length == 2) applicationRoot= new File(args[1]);
	            genWordstat(applicationRoot);
	        } else if ((args.length == 4) && (args[0].toLowerCase().equals("-cleanwordlist"))) {
	            // this can be used to organize and clean a word-list
	            // start this with "java -classpath classes yacy -cleanwordlist <word-file> <minlength> <maxlength>"
	            final int minlength = Integer.parseInt(args[2]);
	            final int maxlength = Integer.parseInt(args[3]);
	            cleanwordlist(args[1], minlength, maxlength);
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-urldbcleanup"))) {
	            // generate a url list and save it in a file
	            if (args.length == 2) applicationRoot= new File(args[1]);
	            urldbcleanup(dataRoot, applicationRoot, "freeworld");
	        } else if ((args.length >= 1) && (args[0].toLowerCase().equals("-rwihashlist"))) {
	            // generate a url list and save it in a file
	            String domain = "all";
	            String format = "txt";
	            if (args.length >= 2) domain= args[1];
	            if (args.length >= 3) format= args[2];
	            if (args.length == 4) applicationRoot= new File(args[3]);
	            final String outfile = "rwihashlist_" + System.currentTimeMillis();
	            RWIHashList(dataRoot, applicationRoot, outfile, domain, format);
	        } else {
	            if (args.length == 1) applicationRoot= new File(args[0]);
	            startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, false);
	        }
    	} finally {
    		Log.shutdown();
    	}
    }
}

/**
* This class is a helper class whose instance is started, when the java virtual
* machine shuts down. Signals the plasmaSwitchboard to shut down.
*/
class shutdownHookThread extends Thread {
    private final Switchboard sb;
    private final Thread mainThread;

    public shutdownHookThread(final Thread mainThread, final Switchboard sb) {
        super();
        this.sb = sb;
        this.mainThread = mainThread;
    }

    public void run() {
        try {
            if (!this.sb.isTerminated()) {
                Log.logConfig("SHUTDOWN","Shutdown via shutdown hook.");

                // sending the yacy main thread a shutdown signal
                Log.logFine("SHUTDOWN","Signaling shutdown to the switchboard.");
                this.sb.terminate("shutdown hook");

                // waiting for the yacy thread to finish execution
                Log.logFine("SHUTDOWN","Waiting for main thread to finish.");
                if (this.mainThread.isAlive() && !this.sb.isTerminated()) {
                    this.mainThread.join();
                }
            }
        } catch (final Exception e) {
            Log.logSevere("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
