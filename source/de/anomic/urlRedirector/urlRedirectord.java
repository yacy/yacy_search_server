package de.anomic.urlRedirector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Date;

import de.anomic.crawler.CrawlProfile;
import de.anomic.data.userDB;
import de.anomic.http.HttpClient;
import de.anomic.http.httpResponseHeader;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverHandler;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCore.Session;
import de.anomic.yacy.yacyURL;

public class urlRedirectord implements serverHandler, Cloneable {
    
    private serverCore.Session session;
    private static plasmaSwitchboard sb = null;
    private final serverLog theLogger = new serverLog("URL-REDIRECTOR");
    private static CrawlProfile.entry profile = null;
    private String nextURL;
    
    public urlRedirectord() {
        if (sb == null) {
            sb = plasmaSwitchboard.getSwitchboard();
        }
        
        if (profile == null) {
                profile = sb.webIndex.profilesActiveCrawls.newEntry(
                            // name
                            "URL Redirector",
                            // start URL
                            null, 
                            // crawling filter
                            ".*", 
                            ".*", 
                            // depth
                            0, 
                            0,
                            // recrawlIfOlder (minutes), if negative: do not re-crawl
                            -1,
                            // domFilterDepth, if negative: no auto-filter
                            -1,
                            // domMaxPages, if negative: no count restriction
                            -1,
                            // crawlDynamic
                            false,
                            // indexText
                            true,
                            // indexMedia
                            true,
                            // storeHTCache
                            false,
                            // storeTxCache
                            true, 
                            // remoteIndexing
                            false, 
                            // xsstopw
                            true, 
                            // xdstopw
                            true, 
                            // xpstopw
                            true
                    );
        }
    }
    
    public String getURL() {
        return this.nextURL;
    }
    
    public void initSession(final Session theSession){
        // getting current session
        this.session = theSession;   
    }
    
    public String greeting() {
        return null;
    }
    
    public String error(final Throwable e) {
        return null;
    }
    
    public urlRedirectord clone() {
        return null;
    }
    
    public void reset() {
        this.session = null;
    }
    
    public Boolean EMPTY(final String arg) throws IOException {
        return null;
    }
    
    public Boolean UNKNOWN(final String requestLine) throws IOException {
        return null;
    }
    
    public Boolean REDIRECTOR(final String requestLine) {
        try {
            
            boolean authenticated = false;
            String userName = null;
            String md5Pwd = null;
            
            // setting timeout
            this.session.controlSocket.setSoTimeout(0);                 
            
            String line = null;
            final BufferedReader inputReader = new BufferedReader(new InputStreamReader(this.session.in));
            final PrintWriter outputWriter = new PrintWriter(this.session.out);
            
            while ((line = inputReader.readLine()) != null) {
                if (line.equals("EXIT")) {
                    break;
                } else if (line.startsWith("#")) {
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                    continue;
                } else if (line.startsWith("USER")) {
                    userName = line.substring(line.indexOf(" ")).trim();
                } else if (line.startsWith("PWD")) {
                    if (userName != null) {
                        final userDB.Entry userEntry = sb.userDB.getEntry(userName);
                        if (userEntry != null) {
                            md5Pwd = line.substring(line.indexOf(" ")).trim();
                            if (userEntry.getMD5EncodedUserPwd().equals(md5Pwd)) {
                                authenticated = true;
                            }
                        }
                    }
                } else if (line.startsWith("MEDIAEXT")) {
                    String transferIgnoreList = plasmaParser.getMediaExtList();
                    transferIgnoreList = transferIgnoreList.substring(1,transferIgnoreList.length()-1);
                    
                    outputWriter.print(transferIgnoreList);
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                } else if (line.startsWith("DEPTH")) {
                    final int pos = line.indexOf(" ");
                    if (pos != -1) {
                        final String newDepth = line.substring(pos).trim();
                        this.theLogger.logFine("Changing crawling depth to '" + newDepth + "'.");
                        sb.webIndex.profilesActiveCrawls.changeEntry(profile, "generalDepth",newDepth);
                    }
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                } else if (line.startsWith("CRAWLDYNAMIC")) {
                    final int pos = line.indexOf(" ");
                    if (pos != -1) {
                        final String newValue = line.substring(pos).trim();
                        this.theLogger.logFine("Changing crawl dynamic setting to '" + newValue + "'");
                        sb.webIndex.profilesActiveCrawls.changeEntry(profile, "crawlingQ",newValue);
                    }
                    outputWriter.print("\r\n");
                    outputWriter.flush();                    
                } else {
                    if (!authenticated) {
                        return Boolean.FALSE;
                    }
                    
                    final int pos = line.indexOf(" ");
                    this.nextURL = (pos != -1) ? line.substring(0,pos):line; 
                    
                    this.theLogger.logFine("Receiving request " + line);
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                    
                    String reasonString = null;
                    try {
                        // generating URL Object
                        final yacyURL reqURL = new yacyURL(this.nextURL, null);
                        
                        // getting URL mimeType
                        final httpResponseHeader header = HttpClient.whead(reqURL.toString()); 
                        
                        if (plasmaParser.supportedContent(
                                plasmaParser.PARSER_MODE_URLREDIRECTOR,
                                reqURL,
                                header.mime())
                        ) {
                            // first delete old entry, if exists
                            final String urlhash = reqURL.hash();
                            sb.webIndex.removeURL(urlhash);
                            sb.crawlQueues.noticeURL.removeByURLHash(urlhash);
                            sb.crawlQueues.errorURL.remove(urlhash);                            
                            
                            // enqueuing URL for crawling
                            reasonString = sb.crawlStacker.stackCrawl(
                                    reqURL, 
                                    null, 
                                    sb.webIndex.seedDB.mySeed().hash, 
                                    "URL Redirector", 
                                    new Date(), 
                                    0, 
                                    profile
                            );   
                        } else {
                            reasonString = "Unsupporte file extension";
                        } 
                    } catch (final MalformedURLException badUrlEx) {
                        reasonString = "Malformed URL";
                    }
                        
                    if (reasonString != null) {
                        this.theLogger.logFine("URL " + nextURL + " rejected. Reason: " + reasonString);
                    }
                    nextURL = null;
                }
            }        
            
            this.theLogger.logFine("Connection terminated");
            
            // Terminating connection
            return serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            this.theLogger.logSevere("Unexpected Error: " + e.getMessage(),e);
            return serverCore.TERMINATE_CONNECTION;
        }
    }
    
    
    
}
