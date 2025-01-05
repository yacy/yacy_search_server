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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.entity.mime.content.ContentBody;

import com.google.common.io.Files;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.TransactionManager;
import net.yacy.data.Translator;
import net.yacy.gui.YaCyApp;
import net.yacy.gui.framework.Browser;
import net.yacy.http.YaCyHttpServer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.OS;
import net.yacy.peers.Seed;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.peers.operation.yacyRelease;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverSwitch;
import net.yacy.utils.translation.TranslatorXliff;


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

    public static final String copyright = "[ YaCy v" + vString + " by Michael Christen / www.yacy.net ]";
    public static final String hline = "-------------------------------------------------------------------------------";
    public static final Semaphore shutdownSemaphore = new Semaphore(0);

    public static File htDocsPath = null;
    public static File shareDefaultPath = null;
    public static File shareDumpDefaultPath = null;

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
        String tmpdir=null;
        try {
            // start up
            System.out.println(copyright);
            System.out.println(hline);

            // ensure that there is a DATA directory, if not, create one and if that fails warn and die
            mkdirsIfNeseccary(dataHome);
            mkdirsIfNeseccary(appHome);
            File f = new File(dataHome, "DATA/");
            mkdirsIfNeseccary(f);
            if (!(f.exists())) {
                System.err.println("Error creating DATA-directory in " + dataHome.toString() + " . Please check your write-permission for this folder. YaCy will now terminate.");
                System.exit(-1);
            }

            // set jvm tmpdir to a subdir for easy cleanup (as extensive use file.deleteonexit waists memory during long runs, as todelete files names are collected and never cleaned up during runtime)
            // keep this as earlier as possible, as any other class can use the "java.io.tmpdir" property, even the log manager, when the log file pattern uses "%t" as an alias for the tmp directory
            try {
                tmpdir = java.nio.file.Files.createTempDirectory("yacy-tmp-").toString(); // creates sub dir in jvm's temp (see System.property "java.io.tempdir")
                System.setProperty("java.io.tmpdir", tmpdir);
            } catch (final IOException ex) { }

            // setting up logging
            f = new File(dataHome, "DATA/LOG/");
            mkdirsIfNeseccary(f);
            f = new File(f, "yacy.logging");
            final File f0 = new File(appHome, "defaults/yacy.logging");
            if (!f.exists() || f0.lastModified() > f.lastModified()) try {
                Files.copy(f0, f);
            } catch (final IOException e){
                System.out.println("could not copy yacy.logging: " + e.getMessage());
            }
            try{
                ConcurrentLog.configureLogging(dataHome, new File(dataHome, "DATA/LOG/yacy.logging"));
            } catch (final IOException e) {
                System.out.println("could not find logging properties in homePath=" + dataHome);
                ConcurrentLog.logException(e);
            }
            ConcurrentLog.config("STARTUP", "YaCy version: " + yacyBuildProperties.getReleaseStub());
            ConcurrentLog.config("STARTUP", "Java version: " + System.getProperty("java.version", "no-java-version"));
            ConcurrentLog.config("STARTUP", "Operation system: " + System.getProperty("os.name","unknown"));
            ConcurrentLog.config("STARTUP", "Application root-path: " + appHome);
            ConcurrentLog.config("STARTUP", "Data root-path: " + dataHome);
            ConcurrentLog.config("STARTUP", "Time zone: UTC" + GenericFormatter.UTCDiffString() + "; UTC+0000 is " + System.currentTimeMillis());
            ConcurrentLog.config("STARTUP", "Maximum file system path length: " + OS.maxPathLength);

            f = new File(dataHome, "DATA/yacy.running");
            if (!f.createNewFile()) ConcurrentLog.severe("STARTUP", "WARNING: the file " + f + " can not be created!");
            try {
            	final FileOutputStream fos = new FileOutputStream(f);
            	fos.write(Integer.toString(OS.getPID()).getBytes());
            	fos.close();
            } catch (final Exception e) { } // write PID
            f.deleteOnExit();
            FileChannel channel = null;
            FileLock lock = null;
            try {
            	final RandomAccessFile raf = new RandomAccessFile(f,"rw");
                channel = raf.getChannel();
                lock = channel.tryLock(); // lock yacy.running
                raf.close();
            } catch (final Exception e) { }

            final String conf = "DATA/SETTINGS/yacy.conf".replace("/", File.separator);
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
            if (gui) YaCyApp.start("localhost", sb.getLocalPort());

            // hardcoded, forced, temporary value-migration
            sb.setConfig("htTemplatePath", "htroot/env/templates");

            double oldVer;
            try {
                String tmpversion = sb.getConfig(Seed.VERSION, "");
                if (tmpversion.isEmpty()) { // before 1.83009737 only the svnRevision nr was in config (like 9737)
                    tmpversion = yacyBuildProperties.getVersion();
                    final int oldRev = Integer.parseInt(sb.getConfig("svnRevision", "0"));
                    if (oldRev > 1) {
                        oldVer = Double.parseDouble(tmpversion) + oldRev / 100000000.0;
                    } else {
                        oldVer = Double.parseDouble(yacyBuildProperties.getVersion()); // failsafe (assume current version = no migration)
                    }
                } else {
                    oldVer = Double.parseDouble(tmpversion);
                }
            } catch (final NumberFormatException e) {
                oldVer = 0.0d;
            }
            final double newRev = Double.parseDouble(yacyBuildProperties.getVersion());
            sb.setConfig(Seed.VERSION, yacyBuildProperties.getVersion());
            sb.setConfig("applicationRoot", appHome.toString());
            sb.setConfig("dataRoot", dataHome.toString());

            // create some directories
            final File htRootPath = new File(appHome, sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
            mkdirIfNeseccary(htRootPath);
            htDocsPath = sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
            mkdirIfNeseccary(htDocsPath);
            //final File htTemplatePath = new File(homePath, sb.getConfig("htTemplatePath","htdocs"));

            // create default notifier picture
            final File notifierFile = new File(htDocsPath, "notifier.gif");
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

            shareDefaultPath = new File(htDocsPath, "share");
            mkdirIfNeseccary(shareDefaultPath);
            shareDumpDefaultPath = new File(shareDefaultPath, "dump");
            mkdirIfNeseccary(shareDumpDefaultPath);

            migration.migrate(sb, oldVer, newRev);

            // delete old release files
            final int deleteOldDownloadsAfterDays = (int) sb.getConfigLong("update.deleteOld", 30);
            yacyRelease.deleteOldDownloads(sb.releasePath, deleteOldDownloadsAfterDays );

            // start main threads
            final int port = sb.getLocalPort();
            try {
                // start http server
                YaCyHttpServer httpServer;
                httpServer = new YaCyHttpServer(port, "0.0.0.0");
                httpServer.startupServer();
                sb.setHttpServer(httpServer);
                // TODO: this has no effect on Jetty (but needed to reflect configured value and limit is still used)
                ConnectionInfo.setServerMaxcount(sb.getConfigInt("connectionsMax", ConnectionInfo.getMaxcount()));

                ConcurrentLog.info("STARTUP",httpServer.getVersion());

                // open the browser window
                final boolean browserPopUpTrigger = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_TRIGGER, "true").equals("true");
                if (browserPopUpTrigger) try {
                    final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
                    //boolean properPW = (sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT, "").isEmpty()) && (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0);
                    //if (!properPW) browserPopUpPage = "ConfigBasic.html";
                    /* YaCy main startup process must not hang because browser opening is long or fails.
                     * Let's open try opening the browser in a separate thread */
                    new Thread("Browser opening") {
                        @Override
                        public void run() {
                            Browser.openBrowser(("http://localhost:"+port) + "/" + browserPopUpPage);
                        }
                    }.start();
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
                // on lang=browser all active translation should be checked (because any could be requested by client)
                List<String> langlist;
                if (lang.endsWith("browser"))
                    langlist = Translator.activeTranslations(); // get all translated languages
                else {
                    langlist = new ArrayList<>();
                    langlist.add(lang);
                }
                for (final String tmplang : langlist) {
                    if (!tmplang.equals("") && !tmplang.equals("default") && !tmplang.equals("browser")) { //locale is used
                        String currentRev = null;
                        BufferedReader br = null;
                        try {
                            br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), tmplang + "/version"))));
                            currentRev = br.readLine(); // may return null
                        } catch (final IOException e) {
                            //Error
                        } finally {
                            try {
                                br.close();
                            } catch(final IOException ioe) {
                                ConcurrentLog.warn("STARTUP", "Could not close " + tmplang + " version file");
                            }
                        }

                        if (currentRev == null || !currentRev.equals(sb.getConfig(Seed.VERSION, ""))) {
                            try { //is this another version?!
                                final File sourceDir = new File(sb.getConfig(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT));
                                final File destDir = new File(sb.getDataPath("locale.translated_html", "DATA/LOCALE/htroot"), tmplang);
                                if (new TranslatorXliff().translateFilesRecursive(sourceDir, destDir, new File(locale_source, tmplang + ".lng"), "html,template,inc", "locale")) { //translate it
                                    //write the new Versionnumber
                                    final BufferedWriter bw = new BufferedWriter(new PrintWriter(new FileWriter(new File(destDir, "version"))));
                                    bw.write(sb.getConfig(Seed.VERSION, "Error getting Version"));
                                    bw.close();
                                }
                            } catch (final IOException e) {
                            }
                        }
                    }
                }
                // initialize number formatter with this locale
                if (!lang.equals("browser")) // "default" is handled by .setLocale()
                    Formatter.setLocale(lang);

                // registering shutdown hook
                ConcurrentLog.config("STARTUP", "Registering Shutdown Hook");
                final Runtime run = Runtime.getRuntime();
                run.addShutdownHook(new shutdownHookThread(sb, shutdownSemaphore));

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
                ConcurrentLog.config("SHUTDOWN", "caught termination signal");
                httpServer.stop();

                ConcurrentLog.config("SHUTDOWN", "server has terminated");
                sb.close();
            } catch (final Exception e) {
                ConcurrentLog.severe("STARTUP", "Unexpected Error: " + e.getClass().getName(),e);
                //System.exit(1);
            }
            if (lock != null && lock.isValid()) lock.release();
            if (channel != null && channel.isOpen()) channel.close();
        } catch (final Exception ee) {
            ConcurrentLog.severe("STARTUP", "FATAL ERROR: " + ee.getMessage(),ee);
        } finally {
        }

        if (tmpdir != null) FileUtils.deletedelete(new File(tmpdir)); // clean up temp dir (set at startup as subdir of system.propery "java.io.tmpdir")

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
    public static void mkdirIfNeseccary(final File path) {
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

        final LinkedHashMap<String,ContentBody> post = new LinkedHashMap<>();
        post.put("shutdown", UTF8.StringBody(""));
        submitPostURL(homePath, "Steering.html", "Terminate YaCy", post);
    }

    public static void update(final File homePath) {
        // start up
        System.out.println(copyright);
        System.out.println(hline);
        submitURL(homePath, "ConfigUpdate_p.html?autoUpdate=", "Update YaCy to most recent version");
    }

    /**
     * Submits post data to the local peer URL, authenticating as administrator
     * @param homePath directory containing YaCy DATA folder
     * @param path url relative path part
     * @param processdescription description of the operation for logging purpose
     * @param post data to post
     */
    private static void submitPostURL(final File homePath, final String path, final String processdescription, final Map<String, ContentBody> post) {
        final Properties config = configuration("COMMAND-STEERING", homePath);

        // read port
        final int port = Integer.parseInt(config.getProperty(SwitchboardConstants.SERVER_PORT, "8090"));

        // read password
        final String encodedPassword = config.getProperty(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "");
        final String adminUser = config.getProperty(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");

        // send 'wget' to web interface
        try (final HTTPClient con = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
            /* First get a valid transaction token using HTTP GET */
            con.GETbytes("http://localhost:"+ port +"/" + path, adminUser, encodedPassword, false);

            if (con.getStatusCode() != HttpStatus.SC_OK) {
                throw new IOException("Error response from YACY socket: " + con.getHttpResponse().getStatusLine());
            }

            final Header transactionTokenHeader = con.getHttpResponse().getFirstHeader(HeaderFramework.X_YACY_TRANSACTION_TOKEN);
            if(transactionTokenHeader == null) {
                throw new IOException("Could not retrieve a valid transaction token");
            }

            /* Then POST the request */
            post.put(TransactionManager.TRANSACTION_TOKEN_PARAM, UTF8.StringBody(transactionTokenHeader.getValue()));
            con.POSTbytes(new MultiProtocolURL("http://localhost:"+ port +"/" + path), null, post, adminUser, encodedPassword, false, false);
            if (con.getStatusCode() >= HttpStatus.SC_OK && con.getStatusCode() < HttpStatus.SC_MULTIPLE_CHOICES) {
                ConcurrentLog.config("COMMAND-STEERING", "YACY accepted steering command: " + processdescription);
            } else {
                ConcurrentLog.severe("COMMAND-STEERING", "error response from YACY socket: " + con.getHttpResponse().getStatusLine());

                try {
                    HTTPClient.closeConnectionManager();
                } catch (final InterruptedException e1) {
                    e1.printStackTrace();
                }

                RemoteInstance.closeConnectionManager();

                System.exit(-1);
            }
        } catch (final IOException e) {
            ConcurrentLog.severe("COMMAND-STEERING", "could not establish connection to YACY socket: " + e.getMessage());

            try {
                HTTPClient.closeConnectionManager();
            } catch (final InterruptedException e1) {
                e1.printStackTrace();
            }
            RemoteInstance.closeConnectionManager();

            System.exit(-1);
        }

        try {
            HTTPClient.closeConnectionManager();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        RemoteInstance.closeConnectionManager();

        // finished
        ConcurrentLog.config("COMMAND-STEERING", "SUCCESSFULLY FINISHED COMMAND: " + processdescription);
    }

    private static void submitURL(final File homePath, final String path, final String processdescription) {
        final Properties config = configuration("COMMAND-STEERING", homePath);

        // read port
        final int port = Integer.parseInt(config.getProperty(SwitchboardConstants.SERVER_PORT, "8090"));

        // read password
        String encodedPassword = (String) config.get(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5);
        if (encodedPassword == null) encodedPassword = ""; // not defined

        // send 'wget' to web interface
        try (final HTTPClient con = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
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
        RemoteInstance.closeConnectionManager();

        // finished
        ConcurrentLog.config("COMMAND-STEERING", "SUCCESSFULLY FINISHED COMMAND: " + processdescription);
    }

    /**
     * read saved config file and perform action which need to be done before main task starts
     * - like check on system alrady running etc.
     *
     * @param dataHome data directory
     */
    static private void preReadSavedConfigandInit(final File dataHome) {
        final File lockFile = new File(dataHome, "DATA/yacy.running");
        final String conf = "DATA/SETTINGS/yacy.conf";

        // If YaCy is actually running, then we check if the server port is open.
        // If yes, then we consider that a restart is a user mistake and then we just respond
        // as the user expects and tell the browser to open the start page.
        // That will especially happen if Windows Users double-Click the YaCy Icon on the desktop to simply
        // open the web interface. (They don't think of 'servers' they just want to get to the search page).
        // We need to parse the configuration file for that to get the host port
        final File configFile = new File(dataHome, conf);

        if (configFile.exists()) {
            final Properties p = new Properties();
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(configFile);
                p.load(fis);

                // test for yacy already running
                if (lockFile.exists()) {  // another instance running? VM crash? User will have to care about this
                    //standard log system not up yet - use simply stdout
                    // prevents also creation of a log file while just opening browser
                    System.out.println("WARNING: the file " + lockFile + " exists, this usually means that a YaCy instance is still running. If you want to restart YaCy, try first ./stopYACY.sh, then ./startYACY.sh. If ./stopYACY.sh fails, try ./killYACY.sh");

                    final int port = Integer.parseInt(p.getProperty(SwitchboardConstants.SERVER_PORT, "8090"));
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
                }
            } catch (final IOException ex) {
                System.err.println("ERROR: config file seems to be corrupt");
                System.err.println("ERROR: if problem persists, delete file");
                System.err.println(configFile.getAbsolutePath());
                ConcurrentLog.logException(ex);
                ConcurrentLog.severe("Startup", "cannot read " + configFile.toString() + ", please delete the corrupted file if problem persits");
            } finally {
                try {
                    fis.close();
                } catch (final IOException e) {
                    ConcurrentLog.warn("Startup", "Could not close file " + configFile);
                }
            }
        }
    }

    /**
     * Main-method which is started by java. Checks for special arguments or
     * starts up the application.
     *
     * @param args
     *            Given arguments from the command line.
     */
    public static void main(final String args[]) {

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
            if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("-gui")) headless = false;
            System.setProperty("java.awt.headless", headless ? "true" : "false");

            final StringBuilder s = new StringBuilder(); for (final String a: args) s.append(a).append(" ");
            yacyRelease.startParameter = s.toString().trim();

            // case for the application path if started normally with a jre command
            File applicationRoot = new File(System.getProperty("user.dir").replace('\\', '/'));
            File dataRoot = applicationRoot;
            //System.out.println("args.length=" + args.length);
            //System.out.print("args=["); for (int i = 0; i < args.length; i++) System.out.print(args[i] + ", "); System.out.println("]");
            if ((args.length >= 1) && (args[0].toLowerCase(Locale.ROOT).equals("-startup") || args[0].equals("-start"))) {
                // normal start-up of yacy
                if (args.length > 1) {
                    dataRoot = new File(args[1]);
                    if(!dataRoot.isAbsolute()) {
                        /* data root folder provided as a path relative to the user home folder */
                        dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
                    }
                }
                preReadSavedConfigandInit(dataRoot);
                startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, false);
            } else if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("-gui")) {
                // start-up of yacy with gui
                if (args.length > 1) {
                    dataRoot = new File(args[1]);
                    if(!dataRoot.isAbsolute()) {
                        /* data root folder provided as a path relative to the user home folder */
                        dataRoot = new File(System.getProperty("user.home").replace('\\', '/'), args[1]);
                    }
                }
                preReadSavedConfigandInit(dataRoot);
                startup(dataRoot, applicationRoot, startupMemFree, startupMemTotal, true);
            } else if ((args.length >= 1) && ((args[0].toLowerCase(Locale.ROOT).equals("-shutdown")) || (args[0].equals("-stop")))) {
                // normal shutdown of yacy
                if (args.length == 2) applicationRoot= new File(args[1]);
                shutdown(applicationRoot);
            } else if ((args.length >= 1) && (args[0].toLowerCase(Locale.ROOT).equals("-update"))) {
                // aut-update yacy
                if (args.length == 2) applicationRoot= new File(args[1]);
                update(applicationRoot);
            } else if ((args.length >= 1) && (args[0].toLowerCase(Locale.ROOT).equals("-version"))) {
                // show yacy version
                System.out.println(copyright);
            } else if ((args.length > 1) && (args[0].toLowerCase(Locale.ROOT).equals("-config"))) {
                // set config parameter. Special handling of adminAccount=user:pwd (generates md5 encoded password)
                // on Windows parameter should be enclosed in doublequotes to accept = sign (e.g. -config "port=8090" "port.ssl=8043")
                final File f = new File (dataRoot,"DATA/SETTINGS/");
                if (!f.exists()) {
                    mkdirsIfNeseccary(f);
                } else {
                    if (new File(dataRoot, "DATA/yacy.running").exists()) {
                        System.out.println("please restart YaCy");
                    }
                }
                // use serverSwitch to read config properties (including init values from yacy.init
                final serverSwitch ss = new serverSwitch(dataRoot,applicationRoot,"defaults/yacy.init","DATA/SETTINGS/yacy.conf");

                for (int icnt=1; icnt < args.length ; icnt++) {
                    final String cfg = args[icnt];
                    final int pos = cfg.indexOf('=');
                    if (pos > 0) {
                        final String cmd = cfg.substring(0, pos);
                        final String val = cfg.substring(pos + 1);

                        if (!val.isEmpty()) {
                            if (cmd.equalsIgnoreCase(SwitchboardConstants.ADMIN_ACCOUNT)) { // special command to set adminusername and md5-pwd
                                final int cpos = val.indexOf(':');  //format adminAccount=adminname:adminpwd
                                if (cpos >= 0) {
                                    String username = val.substring(0, cpos);
                                    final String pwdtxt = val.substring(cpos + 1);
                                    if (!username.isEmpty()) {
                                        ss.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, username);
                                        System.out.println("Set property " + SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME + " = " + username);
                                    } else {
                                        username = ss.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin");
                                    }
                                    ss.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, sb.encodeDigestAuth(username, pwdtxt));
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
                if (args.length == 1) {
                    applicationRoot= new File(args[0]);
                }
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
    private final Semaphore shutdownSemaphore;

    public shutdownHookThread(final Switchboard sb, final Semaphore shutdownSemaphore) {
        super("yacy.shutdownHookThread");
        this.sb = sb;
        this.shutdownSemaphore = shutdownSemaphore;
    }

    @Override
    public void run() {
        try {
            if (!this.sb.isTerminated()) {
                ConcurrentLog.config("SHUTDOWN","Shutdown via shutdown hook.");
                /* Print also to the standard output, as the LogManager is likely to have
                 * been concurrently reset by its own shutdown hook thread */
                System.out.println("SHUTDOWN Starting shutdown via shutdown hook.");

                // sending the yacy main thread a shutdown signal
                ConcurrentLog.fine("SHUTDOWN","Signaling shutdown to the switchboard.");
                this.sb.terminate("shutdown hook");

                // waiting for the yacy thread to finish execution
                ConcurrentLog.fine("SHUTDOWN","Waiting for main thread to finish.");

                /* Main thread will release the shutdownSemaphore once completely terminated.
                 * We do not wait indefinitely as the application is supposed here to quickly terminate */
                final int maxWaitTime = 30;
                if(!this.shutdownSemaphore.tryAcquire(maxWaitTime, TimeUnit.SECONDS)) {
                    System.out.println("Shutting down JVM. Main thread did not completely finish within " + maxWaitTime + " seconds.");
                }
            }
        } catch (final Exception e) {
            ConcurrentLog.severe("SHUTDOWN","Unexpected error. " + e.getClass().getName(),e);
        }
    }
}
