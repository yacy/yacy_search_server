package de.anomic.urlRedirector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.Date;

import de.anomic.data.userDB;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlProfile;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverHandler;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCore.Session;
import de.anomic.yacy.yacyCore;

public class urlRedirectord implements serverHandler {
    
    private serverCore.Session session;
    private static plasmaSwitchboard switchboard = null;
    private serverLog theLogger = new serverLog("URL-REDIRECTOR");
    private static plasmaCrawlProfile.entry profile = null;
    private String nextURL;
    
    public urlRedirectord() {
        if (switchboard == null) {
            switchboard = plasmaSwitchboard.getSwitchboard();
        }
        
        if (profile == null) {
                profile = switchboard.profiles.newEntry(
                            // name
                            "URL Redirector",
                            // start URL
                            "", 
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
                            // storeHTCache
                            false,
                            // storeTxCache
                            true, 
                            //localIndexing
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
    
    public void initSession(Session theSession){
        // getting current session
        this.session = theSession;   
    }
    
    public String greeting() {
        return null;
    }
    
    public String error(Throwable e) {
        return null;
    }
    
    public Object clone() {
        return null;
    }
    
    public void reset() {
        this.session = null;
    }
    
    public Boolean EMPTY(String arg) throws IOException {
        return null;
    }
    
    public Boolean UNKNOWN(String requestLine) throws IOException {
        return null;
    }
    
    public Boolean REDIRECTOR(String requestLine) {
        try {
            
            boolean authenticated = false;
            String userName = null;
            String md5Pwd = null;
            
            // setting timeout
            this.session.controlSocket.setSoTimeout(0);                 
            
            String line = null;
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(this.session.in));
            PrintWriter outputWriter = new PrintWriter(this.session.out);
            
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
                        userDB.Entry userEntry = switchboard.userDB.getEntry(userName);
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
                    int pos = line.indexOf(" ");
                    if (pos != -1) {
                        String newDepth = line.substring(pos).trim();
                        this.theLogger.logFine("Changing crawling depth to '" + newDepth + "'.");
                        profile.changeEntry("generalDepth",newDepth);
                    }
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                } else if (line.startsWith("CRAWLDYNAMIC")) {
                    int pos = line.indexOf(" ");
                    if (pos != -1) {
                        String newValue = line.substring(pos).trim();
                        this.theLogger.logFine("Changing crawl dynamic setting to '" + newValue + "'");
                        profile.changeEntry("crawlingQ",newValue);
                    }
                    outputWriter.print("\r\n");
                    outputWriter.flush();                    
                } else {
                    if (!authenticated) {
                        return Boolean.FALSE;
                    }
                    
                    int pos = line.indexOf(" ");
                    this.nextURL = (pos != -1) ? line.substring(0,pos):line; 
                    
                    this.theLogger.logFine("Receiving request " + line);
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                    
                    String reasonString = null;
                    try {
                        // generating URL Object
                        URL reqURL = new URL(this.nextURL);
                        
                        // getting URL mimeType
                        httpHeader header = httpc.whead(reqURL, reqURL.getHost(), 10000, null, null, switchboard.remoteProxyConfig);                        
                        
                        if (plasmaParser.supportedContent(
                                plasmaParser.PARSER_MODE_URLREDIRECTOR,
                                reqURL,
                                header.mime())
                        ) {
                            // first delete old entry, if exists
                            String urlhash = plasmaURL.urlHash(this.nextURL);
                            switchboard.urlPool.loadedURL.remove(urlhash);
                            switchboard.urlPool.noticeURL.remove(urlhash);
                            switchboard.urlPool.errorURL.remove(urlhash);                            
                            
                            // enqueuing URL for crawling
                            reasonString = switchboard.sbStackCrawlThread.stackCrawl(
                                    this.nextURL, 
                                    null, 
                                    yacyCore.seedDB.mySeed.hash, 
                                    "URL Redirector", 
                                    new Date(), 
                                    0, 
                                    profile
                            );   
                        } else {
                            reasonString = "Unsupporte file extension";
                        } 
                    } catch (MalformedURLException badUrlEx) {
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
        } catch (Exception e) {
            this.theLogger.logSevere("Unexpected Error: " + e.getMessage(),e);
            return serverCore.TERMINATE_CONNECTION;
        }
    }
    
    
    
}
