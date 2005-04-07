// plasmaCrawlerLoader.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 25.02.2004
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

package de.anomic.plasma;

import java.io.*;
import java.util.*;
import java.net.*;
import de.anomic.net.*;
import de.anomic.http.*;
import de.anomic.server.*;
import de.anomic.tools.*;
import de.anomic.htmlFilter.*;

public class plasmaCrawlLoader {

    private plasmaHTCache   cacheManager;
    private int             socketTimeout;
    private int             loadTimeout;
    private boolean         remoteProxyUse;
    private String          remoteProxyHost;
    private int             remoteProxyPort;
    private int             maxSlots;
    private List            slots;
    private serverLog       log;
    private HashSet         acceptMimeTypes;

    public plasmaCrawlLoader(plasmaHTCache cacheManager, serverLog log, int socketTimeout, int loadTimeout, int mslots, boolean proxyUse, String proxyHost, int proxyPort,
                             HashSet acceptMimeTypes) {
	this.cacheManager    = cacheManager;
	this.log             = log;
	this.socketTimeout   = socketTimeout;
	this.loadTimeout     = loadTimeout;
	this.remoteProxyUse  = proxyUse;
	this.remoteProxyHost = proxyHost;
	this.remoteProxyPort = proxyPort;
	this.maxSlots        = mslots;
	this.slots           = new LinkedList();
        this.acceptMimeTypes = acceptMimeTypes;
    }

    private void killTimeouts() {
        Exec thread;
        for (int i = slots.size() - 1; i >= 0; i--) {
            // check if thread is alive
            thread = (Exec) slots.get(i);
            if (thread.isAlive()) {
                // check the age of the thread
                if (System.currentTimeMillis() - thread.startdate > loadTimeout) {
                    // we kill that thread
                    thread.interrupt(); // hopefully this wakes him up.
                    slots.remove(i);
                    System.out.println("CRAWLER: IGNORING SLEEPING DOWNLOAD SLOT " + thread.url.toString());
                }
            } else {
                // thread i is dead, remove it
                slots.remove(i);
            }
        }
    }
    
    public synchronized void loadParallel(URL url, String referer, String initiator, int depth, plasmaCrawlProfile.entry profile) {

	// wait until there is space in the download slots
	Exec thread;
	while (slots.size() >= maxSlots) {
	    killTimeouts();

	    // wait a while
	    try {
		Thread.currentThread().sleep(1000);
	    } catch (InterruptedException e) {
		break;
	    }
	}

	// we found space in the download slots
	thread = new Exec(url, referer, initiator, depth, profile);
	thread.start();
	slots.add(thread);
    }

    public int size() {
        killTimeouts();
        return slots.size();
    }
    
    public Exec[] threadStatus() {
        killTimeouts();
        Exec[] result = new Exec[slots.size()];
        for (int i = 0; i < slots.size(); i++) result[i] = (Exec) slots.get(i);
        return result;
    }
    
    public class Exec extends Thread {

	public URL url;
        public String referer;
        public String initiator;
	public int depth;
	public long startdate;
        public plasmaCrawlProfile.entry profile;
        public String error;
        
	public Exec(URL url, String referer, String initiator, int depth, plasmaCrawlProfile.entry profile) {
	    this.url = url;               // the url to crawl
            this.referer = referer;       // the url that contained this url as link
            this.initiator = initiator;
            this.depth = depth;           // distance from start-url
	    this.startdate = System.currentTimeMillis();
            this.profile = profile;
            this.error = null;
        }

        public void run() {
	    try {
		load(url, referer, initiator, depth, profile);
	    } catch (IOException e) {
	    }
	}

	private httpc newhttpc(String server, int port, boolean ssl) throws IOException {
	    // a new httpc connection, combined with possible remote proxy
	    if (remoteProxyUse)
		return new httpc(server, port, socketTimeout, ssl, remoteProxyHost, remoteProxyPort);
	    else
		return new httpc(server, port, socketTimeout, ssl);
	}

	private void load(URL url, String referer, String initiator, int depth, plasmaCrawlProfile.entry profile) throws IOException {
            if (url == null) return;
            Date requestDate = new Date(); // remember the time...
	    String host = url.getHost();
	    String path = url.getPath();
	    int port = url.getPort();
            boolean ssl = url.getProtocol().equals("https");
	    if (port < 0) port = (ssl) ? 443 : 80;
	    
            // set referrer; in some case advertise a little bit:
            referer = referer.trim();
            if (referer.length() == 0) referer = "http://www.yacy.net/yacy/";
            
	    // take a file from the net
	    try {
		// create a request header
		httpHeader requestHeader = new httpHeader();
		requestHeader.put("User-Agent", httpdProxyHandler.userAgent);
		requestHeader.put("Referer", referer);
		requestHeader.put("Accept-Encoding", "gzip,deflate");

                //System.out.println("CRAWLER_REQUEST_HEADER=" + requestHeader.toString()); // DEBUG
                
		// open the connection
		httpc remote = newhttpc(host, port, ssl);
		
		// send request
		httpc.response res = remote.GET(path, requestHeader);
                
                if (res.status.startsWith("200")) {
                    // the transfer is ok
                    long contentLength = res.responseHeader.contentLength();
                    
                    // make a scraper and transformer
                    htmlFilterContentScraper scraper = new htmlFilterContentScraper(url);
                    OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
                    
                    // reserve cache entry
                    plasmaHTCache.Entry htCache = cacheManager.newEntry(requestDate, depth, url, requestHeader, res.status, res.responseHeader, scraper, initiator, profile);
                    
                    // request has been placed and result has been returned. work off response
                    File cacheFile = cacheManager.getCachePath(url);
                    try {
                        if (!(httpd.isTextMime(res.responseHeader.mime().toLowerCase(), acceptMimeTypes))) {
                            // if the response has not the right file type then reject file
                            hfos.close();
                            remote.close();
                            System.out.println("REJECTED WRONG MIME TYPE " + res.responseHeader.mime() + " for url " + url.toString());
                            htCache.status = plasmaHTCache.CACHE_UNFILLED;
                        } else if ((profile.storeHTCache()) && ((error = htCache.shallStoreCache()) == null)) {
                            // we write the new cache entry to file system directly
                            cacheFile.getParentFile().mkdirs();
                            res.writeContent(hfos, cacheFile); // writes in content scraper and cache file
                            htCache.status = plasmaHTCache.CACHE_FILL;
                        } else {
                            if (error != null) log.logDebug("CRAWLER NOT STORED RESOURCE " + url.toString() + ": " + error);
                            // anyway, the content still lives in the content scraper
                            res.writeContent(hfos, null); // writes only into content scraper
                            htCache.status = plasmaHTCache.CACHE_PASSING;
                        }
                        // enQueue new entry with response header
                        if ((initiator == null) || (initiator.length() == 0)) {
                            // enqueued for proxy writings
                            cacheManager.stackProcess(htCache);
                        } else {
                            // direct processing for crawling
                            cacheManager.process(htCache);
                        }
                    } catch (SocketException e) {
                        // this may happen if the client suddenly closes its connection
                        // maybe the user has stopped loading
                        // in that case, we are not responsible and just forget it
                        // but we clean the cache also, since it may be only partial
                        // and most possible corrupted
                        if (cacheFile.exists()) cacheFile.delete();
                        System.out.println("CRAWLER LOADER ERROR1: with url=" + url.toString() + ": " + e.toString());
                    }
                } else {
                    // if the response has not the right response type then reject file
                    System.out.println("REJECTED WRONG STATUS TYPE '" + res.status + "' for url " + url.toString());
                    // not processed any further
                }
                remote.close();
            } catch (Exception e) {
                // this may happen if the targeted host does not exist or anything with the
                // remote server was wrong.
                System.out.println("CRAWLER LOADER ERROR2 with url=" + url.toString() + ": " + e.toString());
                e.printStackTrace();
            }
        }
	
    }

    
}
