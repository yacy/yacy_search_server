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

package net.yacy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutionException;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.sorting.Array;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.Translator;
import net.yacy.gui.YaCyApp;
import net.yacy.gui.framework.Browser;
import net.yacy.http.YaCyHttpServer;
import net.yacy.http.Jetty8HttpServerImpl;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.peers.operation.yacyRelease;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import com.google.common.io.Files;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Digest;
import net.yacy.crawler.retrieval.Response;
import net.yacy.server.serverSwitch;


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
    private static void startup(final File dataHome, final File appHome, final long startupMemFree, final long startupMemTotal, final boolean gui) {
        try {
            // start up
            System.out.println(copyright);
            System.out.println(hline);

            // check java version
            try {
                "a".isEmpty(); // needs at least Java 1.6
            } catch (final NoSuchMethodError e) {
                System.err.println("STARTUP: Java Version too low. You need at least Java 1.6 to run YaCy");
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
			final File f0 = new File(appHome, "defaults/yacy.logging");
			if (!f.exists() || f0.lastModified() > f.lastModified()) try {
			    Files.copy(f0, f);
            } catch (final IOException e){
                System.out.println("could not copy yacy.logging");
            }
            try{
                ConcurrentLog.configureLogging(dataHome, appHome, new File(dataHome, "DATA/LOG/yacy.logging"));
            } catch (final IOException e) {
                System.out.println("could not find logging properties in homePath=" + dataHome);
                ConcurrentLog.logException(e);
            }
            ConcurrentLog.config("STARTUP", "YaCy version: " + yacyBuildProperties.getVersion() + "/" + yacyBuildProperties.getSVNRevision());
            ConcurrentLog.config("STARTUP", "Java version: " + System.getProperty("java.version", "no-java-version"));
            ConcurrentLog.config("STARTUP", "Operation system: " + System.getProperty("os.name","unknown"));
            ConcurrentLog.config("STARTUP", "Application root-path: " + appHome);
            ConcurrentLog.config("STARTUP", "Data root-path: " + dataHome);
            ConcurrentLog.config("STARTUP", "Time zone: UTC" + GenericFormatter.UTCDiffString() + "; UTC+0000 is " + System.currentTimeMillis());
            ConcurrentLog.config("STARTUP", "Maximum file system path length: " + OS.maxPathLength);

            f = new File(dataHome, "DATA/yacy.running");
            final String conf = "DATA/SETTINGS/yacy.conf".replace("/", File.separator);
            if (!f.createNewFile()) ConcurrentLog.severe("STARTUP", "WARNING: the file " + f + " can not be created!");
            try { new FileOutputStream(f).write(Integer.toString(OS.getPID()).getBytes()); } catch (final Exception e) { } // write PID
            f.deleteOnExit();
            FileChannel channel = null;
            FileLock lock = null;
            try {
            	channel = new RandomAccessFile(f,"rw").getChannel();
            	lock = channel.tryLock(); // lock yacy.running
            } catch (final Exception e) { }

            try {
                sb = new Switchboard(dataHome, appHome, "defaults/yacy.init".replace("/", File.separator), conf);
            } catch (final RuntimeException e) {
                ConcurrentLog.severe("STARTUP", "YaCy cannot start: " + e.getMessage(), e);
                System.exit(-1);
            }
            //sbSync.V(); // signal that the sb reference was set

            // switch the memory strategy
            MemoryControl.setStandardStrategy(sb.getConfigBool("memory.standardStrategy", true));

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
            } catch (final NumberFormatException e) {
                oldRev = 0;
    	    }
            final int newRev = Integer.parseInt(yacyBuildProperties.getSVNRevision());
            sb.setConfig("svnRevision", yacyBuildProperties.getSVNRevision());
            sb.setConfig("applicationRoot", appHome.toString());
            sb.setConfig("dataRoot", dataHome.toString());

            // create some directories
            final File htRootPath = new File(appHome, sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
            mkdirIfNeseccary(htRootPath);
            final File htDocsPath = sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
            mkdirIfNeseccary(htDocsPath);
            //final File htTemplatePath = new File(homePath, sb.getConfig("htTemplatePath","htdocs"));

            // copy the donate iframe (better to copy this once here instead of doing this in an actual iframe in the search result)
            final File wwwEnvPath = new File(htDocsPath, "env");
            mkdirIfNeseccary(wwwEnvPath);
            final String iframesource = sb.getConfig("donation.iframesource", "");
            final String iframetarget = sb.getConfig("donation.iframetarget", "");
            final File iframefile = new File(htDocsPath, iframetarget);
            if (!iframefile.exists()) new Thread() {
                @Override
                public void run() {
                    final ClientIdentification.Agent agent = ClientIdentification.getAgent(ClientIdentification.yacyInternetCrawlerAgentName);
                    Response response;
                    try {
                        response = sb.loader == null ? null : sb.loader.load(sb.loader.request(new DigestURL(iframesource), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, agent);
                        if (response != null) FileUtils.copy(response.getContent(), iframefile);
                    } catch (Throwable e) {}
                }
            }.start();
            
            // create default notifier picture
            File notifierFile = new File(htDocsPath, "notifier.gif");
            if (!notifierFile.exists()) try {Files.copy(new File(htRootPath, "env/grafics/empty.gif"), notifierFile);} catch (final IOException e) {}

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
            HTTPClient.setDefaultUserAgent(ClientIdentification.yacyInternetCrawlerAgent.userAgent);

            // start main threads
            final int port = sb.getConfigInt("port", 8090);
            try {
                // start http server
            	YaCyHttpServer httpServer;
                httpServer = new Jetty8HttpServerImpl(port);
                httpServer.startupServer();
                sb.setHttpServer(httpServer);
                ConcurrentLog.info("STARTUP",httpServer.getVersion());
                
                // open the browser window
                final boolean browserPopUpTrigger = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_TRIGGER, "true").equals("true");
                if (browserPopUpTrigger) try {
                    final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
                    //boolean properPW = (sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT, "").isEmpty()) && (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0);
                    //if (!properPW) browserPopUpPage = "ConfigBasic.html";
                    Browser.openBrowser(("http://localhost:"+port) + "/" + browserPopUpPage);
                   // Browser.openBrowser((server.withSSL()?"https":"http") + "://localhost:" + serverCore.getPortNr(port) + "/" + browserPopUpPage);
                } catch (final Throwable e) {
                    // cannot open browser. This may be normal in headless environments
                    //Log.logException(e);
                }

                // enable browser popup, http server is ready now
                sb.tray.setReady();

                //regenerate Locales from Translationlist, if needed
                final File locale_source = sb.getAppPath("locale.source", "locales");
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
                        final File sourceDir = new File(sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
                        final File destDir = new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), lang);
                        if (Translator.translateFilesRecursive(sourceDir, destDir, new File(locale_source, lang + ".lng"), "html,template,inc", "locale")){ //translate it
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
                ConcurrentLog.config("STARTUP", "Registering Shutdown Hook");
                final Runtime run = Runtime.getRuntime();
                run.addShutdownHook(new shutdownHookThread(Thread.currentThread(), sb));

                // save information about available memory after all initializations
                //try {
                    sb.setConfig("memoryFreeAfterInitBGC", MemoryControl.free());
                    sb.setConfig("memoryTotalAfterInitBGC", MemoryControl.total());
                    System.gc();
                    sb.setConfig("memoryFreeAfterInitAGC", MemoryControl.free());
                    sb.setConfig("memoryTotalAfterInitAGC", MemoryControl.total());
                //} catch (final ConcurrentModificationException e) {}

                // wait for server shutdown
                try {
                    sb.waitForShutdown();
                } catch (final Exception e) {
                    ConcurrentLog.severe("MAIN CONTROL LOOP", "PANIC: " + e.getMessage(),e);
                }
                // shut down
                Array.terminate();
                ConcurrentLog.config("SHUTDOWN", "caught termination signal");
                httpServer.stop();

                ConcurrentLog.config("SHUTDOWN", "server has terminated");
                sb.close();
            } catch (final Exception e) {
                ConcurrentLog.severe("STARTUP", "Unexpected Error: " + e.getClass().getName(),e);
                //System.exit(1);
            }
            if(lock != null) lock.release();
            if(channel != null) channel.close();
        } catch (final Exception ee) {
            ConcurrentLog.severe("STARTUP", "FATAL ERROR: " + ee.getMessage(),ee);
        } finally {
        }

        ConcurrentLog.config("SHUTDOWN", "goodbye. (this is the last line)");
        ConcurrentLog.shutdown();
        shutdownSemaphore.release(1000);
        try {
            System.exit(0);
        } catch (final Exception e) {} // was once stopped by de.anomic.net.ftpc$sm.checkExit(ftpc.java:1790)
    }

	/**
	 * @param f
	 */
	private static void delete(final File f) {
		if(!f.delete())
		    ConcurrentLog.severe("STARTUP", "WARNING: the file " + f + " can not be deleted!");
	}

	/**
	 * @see File#mkdir()
	 * @param path
	 */
	private static void mkdirIfNeseccary(final File path) {
		if (!(path.exists()))
			if(!path.mkdir())
				ConcurrentLog.warn("STARTUP", "could not create directory "+ path.toString());
	}

	/**
	 * @see File#mkdirs()
	 * @param path
	 */
	public static void mkdirsIfNeseccary(final File path) {
		if (!(path.exists()))
			if(!path.mkdirs())
				ConcurrentLog.warn("STARTUP", "could not create directories "+ path.toString());
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
        ConcurrentLog.config(mes, "Application Root Path: " + homePath.toString());

        // read data folder
        final File dataFolder = new File(homePath, "DATA");
        if (!(dataFolder.exists())) {
            ConcurrentLog.severe(mes, "Application was never started or root path wrong.");
            System.exit(-1);
        }

        final Properties config = new Properties();
        FileInputStream fis = null;
		try {
        	fis  = new FileInputStream(new File(homePath, "DATA/SETTINGS/yacy.conf"));
            config.load(fis);
        } catch (final FileNotFoundException e) {
            ConcurrentLog.severe(mes, "could not find configuration file.");
            System.exit(-1);
        } catch (final IOException e) {
            ConcurrentLog.severe(mes, "could not read configuration file.");
            System.exit(-1);
        } finally {
        	if(fis != null) {
        		try {
					fis.close();
				} catch (final IOException e) {
				    ConcurrentLog.logException(e);
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

    private static void submitURL(final File homePath, final String path, final String processdescription) {
        final Properties config = configuration("COMMAND-STEERING", homePath);

        // read port
        final int port = Integer.parseInt(config.getProperty("port", "8090"));

        // read password
        String encodedPassword = (String) config.get(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5);
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        final RequestHeader requestHeader = new RequestHeader();
        final HTTPClient con = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        con.setHeader(requestHeader.entrySet());
        try {
            con.GETbytes("http://localhost:"+ port +"/" + path, config.getProperty(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME,"admin"), encodedPassword, false);
            if (con.getStatusCode() > 199 && con.getStatusCode() < 300) {
                ConcurrentLog.config("COMMAND-STEERING", "YACY accepted steering command: " + processdescription);

            } else {
            	ConcurrentLog.severe("COMMAND-STEERING", "error response from YACY socket: " + con.getHttpResponse().getStatusLine());
                System.exit(-1);
            }
        } catch (final IOException e) {
            ConcurrentLog.severe("COMMAND-STEERING", "could not establish connection to YACY socket: " + e.getMessage());
            System.exit(-1);
        }

        try {
			HTTPClient.closeConnectionManager();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}

        // finished
        ConcurrentLog.config("COMMAND-STEERING", "SUCCESSFULLY FINISHED COMMAND: " + processdescription);
    }
    
    /**
     * read saved config file and perform action which need to be done before main task starts
     * - like check on system alrady running etc.
     * 
     * @param dataHome data directory
     */
    static private void preReadSavedConfigandInit(File dataHome) {
        File lockFile = new File(dataHome, "DATA/yacy.running");
        final String conf = "DATA/SETTINGS/yacy.conf";
        
        // If YaCy is actually running, then we check if the server port is open.
        // If yes, then we consider that a restart is a user mistake and then we just respond
        // as the user expects and tell the browser to open the start page.
        // That will especially happen if Windows Users double-Click the YaCy Icon on the desktop to simply
        // open the web interface. (They don't think of 'servers' they just want to get to the search page).
        // We need to parse the configuration file for that to get the host port
        File configFile = new File(dataHome, conf);
        
        if (configFile.exists()) {
            Properties p = new Properties();
            try {
                FileInputStream fis = new FileInputStream(configFile);
                p.load(fis);
                fis.close();
                // Test for server access restriction (is implemented using Jetty IPaccessHandler which does not support IPv6
                // try to disavle IPv6 
                String teststr = p.getProperty("serverClient", "*");
                if (!teststr.equals("*")) {
                    // testing on Win-8 showed this property has to be set befor Switchboard starts 
                    // and seems to be sensitive (or time critical) if other code had been executed before this (don't know why ... ?)
                    System.setProperty("java.net.preferIPv6Addresses", "false");
                    System.setProperty("java.net.preferIPv4Stack", "true"); // DO NOT PREFER IPv6, i.e. freifunk uses ipv6 only and host resolving does not work
                    teststr = System.getProperty("java.net.preferIPv4Stack");
                    System.out.println("set system property java.net.preferIP4Stack=" + teststr);
                }   
                
                // test for yacy already running
                if (lockFile.exists()) {  // another instance running? VM crash? User will have to care about this
                    //standard log system not up yet - use simply stdout
                    // prevents also creation of a log file while just opening browser
                    System.out.println("WARNING: the file " + lockFile + " exists, this usually means that a YaCy instance is still running. If you want to restart YaCy, try first ./stopYACY.sh, then ./startYACY.sh. If ./stopYACY.sh fails, try ./killYACY.sh");

                    int port = Integer.parseInt(p.getProperty("port", "8090"));
                    try {
                        if (TimeoutRequest.ping("127.0.0.1", port, 1000)) {
                            Browser.openBrowser("http://localhost:" + port + "/" + p.getProperty(SwitchboardConstants.BROWSER_POP_UP_PAGE, "index.html"));
                            // Thats it; YaCy was running, the user is happy, we can stop now.
                            System.out.println("WARNING: YaCy instance was still running; just opening the browser and exit.");
                            System.exit(0);
                        } else {
                            // YaCy is not running; thus delete the file an go on as nothing was wrong.
                            System.err.println("INFO: delete old yacy.running file; likely previous YaCy session was not orderly shutdown!");
                            delete(lockFile);
                        }
                    } catch (final ExecutionException ex) { }                                    
                }
            } catch (IOException ex) { }
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
	        assert (assertionenabled = true) == true; // compare to true to remove warning: "Possible accidental assignement"
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
            // System.setProperty("java.net.preferIPv4Stack", "true"); // DO NOT PREFER IPv6, i.e. freifunk uses ipv6 only and host resolving does not work
           
	        String s = ""; for (final String a: args) s += a + " ";
	        yacyRelease.startParameter = s.trim();

	        File applicationRoot = new File(System.getProperty("user.dir").replace('\\', '/'));
	        File dataRoot = applicationRoot;
	        //System.out.println("args.length=" + args.length);
	        //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
	        if ((args.length >= 1) && (args[0].toLowerCase().equals("-startup") || args[0].equals("-start"))) {
	            // normal start-up of yacy
	            if (args.length > 1) dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
                    preReadSavedConfigandInit(dataRoot);
	            startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, false);
	        } else if (args.length >= 1 && args[0].toLowerCase().equals("-gui")) {
	            // start-up of yacy with gui
	            if (args.length > 1) dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
                    preReadSavedConfigandInit(dataRoot);
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
                } else if ((args.length > 1) && (args[0].toLowerCase().equals("-config"))) {
                    // set config parameter. Special handling of adminAccount=user:pwd (generates md5 encoded password)
                    // on Windows parameter should be enclosed in doublequotes to accept = sign (e.g. -config "port=8090" "port.ssl=8043")
                    File f = new File (dataRoot,"DATA/SETTINGS/");
                    if (!f.exists()) {
                        mkdirsIfNeseccary(f);
                    } else {
                        if (new File(dataRoot, "DATA/yacy.running").exists()) {
                            System.out.println("please restart YaCy");
                        }
                    }
                    // use serverSwitch to read config properties (including init values from yacy.init
                    serverSwitch ss = new serverSwitch(dataRoot,applicationRoot,"defaults/yacy.init","DATA/SETTINGS/yacy.conf");

                    for (int icnt=1; icnt < args.length ; icnt++) {
                        String cfg = args[icnt];
                        int pos = cfg.indexOf('=');
                        if (pos > 0) {
                            String cmd = cfg.substring(0, pos);
                            String val = cfg.substring(pos + 1);

                            if (!val.isEmpty()) {
                                if (cmd.equalsIgnoreCase(SwitchboardConstants.ADMIN_ACCOUNT)) { // special command to set adminusername and md5-pwd
                                    int cpos = val.indexOf(':');  //format adminAccount=adminname:adminpwd
                                    if (cpos >= 0) {
                                        String username = val.substring(0, cpos);
                                        String pwdtxt = val.substring(cpos + 1);
                                        if (!username.isEmpty()) {
                                            ss.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, username);
                                            System.out.println("Set property " + SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME + " = " + username);
                                        } else {
                                            username = ss.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");
                                        }
                                        ss.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "MD5:" + Digest.encodeMD5Hex(username + ":" + ss.getConfig(SwitchboardConstants.ADMIN_REALM, "YaCy") + ":" + pwdtxt));
                                        System.out.println("Set property " + SwitchboardConstants.ADMIN_ACCOUNT_B64MD5 + " = " + ss.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""));
                                    }
                                } else {
                                    ss.setConfig(cmd, val);
                                    System.out.println("Set property " + cmd + " = " + val);
                                }
                            }
                        } else {
                            System.out.println("skip parameter " + cfg + " (equal sign missing, put parameter in doublequotes)");
                        }
                        System.out.println();
                    }
                } else {
	            if (args.length == 1) applicationRoot= new File(args[0]);
                    preReadSavedConfigandInit(dataRoot);
	            startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, false);
	        }
    	} finally {
    		ConcurrentLog.shutdown();
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

    @Override
    public void run() {
        try {
            if (!this.sb.isTerminated()) {
                ConcurrentLog.config("SHUTDOWN","Shutdown via shutdown hook.");

                // sending the yacy main thread a shutdown signal
                ConcurrentLog.fine("SHUTDOWN","Signaling shutdown to the switchboard.");
                this.sb.terminate("shutdown hook");

                // waiting for the yacy thread to finish execution
                ConcurrentLog.fine("SHUTDOWN","Waiting for main thread to finish.");
                if (this.mainThread.isAlive() && !this.sb.isTerminated()) {
                    this.mainThread.join();
                }
            }
        } catch (final Exception e) {
            ConcurrentLog.severe("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
