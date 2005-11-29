package de.anomic.urlRedirector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import de.anomic.data.userDB;
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
    
    public urlRedirectord() {
        if (switchboard == null) {
            switchboard = plasmaSwitchboard.getSwitchboard();
        }
        
        if (profile == null) {
            try {
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
            } catch (IOException e) {
                this.theLogger.logSevere("Unable to create a crawling profile for the URL-Redirector",e);
            }
        }
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
    
    public Boolean REDIRECTOR(String requestLine) throws IOException {
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
                } else {
                    if (!authenticated) {
                        return Boolean.FALSE;
                    }
                    
                    int pos = line.indexOf(" ");
                    String nextURL = (pos != -1) ? line.substring(0,pos):line; 
                    
                    this.theLogger.logFine("Receiving request " + line);
                    outputWriter.print("\r\n");
                    outputWriter.flush();
                    
                    String reasonString = null;
                    try {
                        if (plasmaParser.supportedFileExt(new URL(nextURL))) {
                            // enqueuing URL for crawling
                            reasonString = switchboard.sbStackCrawlThread.stackCrawl(
                                    nextURL, 
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
