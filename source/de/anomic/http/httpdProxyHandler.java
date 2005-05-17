// httpdProxyHandler.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 10.05.2004
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

// Contributions:
// [AS] Alexander Schier: Blacklist (404 response for AGIS hosts)
// [TL] Timo Leise: url-wildcards for blacklists

/*
   Class documentation:
   This class is a servlet to the httpd daemon. It is accessed each time
   an URL in a GET, HEAD or POST command contains the whole host information
   or a host is given in the header host field of an HTTP/1.0 / HTTP/1.1
   command.
   Transparency is maintained, whenever appropriate. We change header
   atributes if necessary for the indexing mechanism; i.e. we do not
   support gzip-ed encoding. We also do not support unrealistic
   'expires' values that would force a cache to be flushed immediately
   pragma non-cache attributes are supported
*/


package de.anomic.http;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterContentTransformer;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.htmlFilter.htmlFilterTransformer;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverLog;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;


public final class httpdProxyHandler extends httpdAbstractHandler implements httpdHandler {
    
    // static variables
    // can only be instantiated upon first instantiation of this class object
    private static plasmaSwitchboard switchboard = null;
    private static plasmaHTCache  cacheManager = null;
    public  static serverLog log;
    public  static HashSet yellowList = null;
    public  static TreeMap blackListURLs = null;
    private static int timeout = 30000;
    private static boolean yacyTrigger = true;
    
    public static boolean isTransparentProxy = false;
    
    public static boolean remoteProxyUse = false;
    public static String remoteProxyHost = "";
    public static int remoteProxyPort = -1;
    public static String remoteProxyNoProxy = "";
    public static String[] remoteProxyNoProxyPatterns = null;
    private static final HashSet remoteProxyAllowProxySet = new HashSet();
    private static final HashSet remoteProxyDisallowProxySet = new HashSet();
    
    
    private static htmlFilterTransformer transformer = null;
    public  static final String userAgent = "yacy (" + httpc.systemOST +") yacy.net";
    private File   htRootPath = null;
        
    // class methods
    public httpdProxyHandler(serverSwitch sb) {
	if (switchboard == null) {
	    switchboard = (plasmaSwitchboard) sb;
	    cacheManager = switchboard.getCacheManager();

	    // load remote proxy data
	    remoteProxyHost    = switchboard.getConfig("remoteProxyHost","");
            try {
                remoteProxyPort    = Integer.parseInt(switchboard.getConfig("remoteProxyPort","3128"));
            } catch (NumberFormatException e) {
                remoteProxyPort = 3128;
            }
	    remoteProxyUse     = switchboard.getConfig("remoteProxyUse","false").equals("true");
            remoteProxyNoProxy = switchboard.getConfig("remoteProxyNoProxy","");
            remoteProxyNoProxyPatterns = remoteProxyNoProxy.split(",");
            
	    // set loglevel
	    int loglevel = Integer.parseInt(switchboard.getConfig("proxyLoglevel", "2"));
	    log = new serverLog("HTTPDProxy", loglevel);
            
	    // set timeout
	    timeout = Integer.parseInt(switchboard.getConfig("clientTimeout", "10000"));

            // create a htRootPath: system pages
            if (htRootPath == null) {
                htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
                if (!(htRootPath.exists())) htRootPath.mkdir();
            }
            
            // load a transformer
            transformer = new htmlFilterContentTransformer();
            transformer.init(new File(switchboard.getRootPath(), switchboard.getConfig("plasmaBlueList", "")).toString());

	    String f;
	    // load the yellow-list
	    f = switchboard.getConfig("proxyYellowList", null);
	    if (f != null) yellowList = loadSet("yellow", f); else yellowList = new HashSet();

	    // load the black-list / inspired by [AS]
	    f = switchboard.getConfig("proxyBlackListsActive", null);
	    if (f != null) blackListURLs = loadBlacklist("black", f, "/"); else blackListURLs = new TreeMap();
            log.logSystem("Proxy Handler Initialized");
	}
    }


    private static HashSet loadSet(String setname, String filename) {
		HashSet set = new HashSet();
        BufferedReader br = null;
		try {
		    br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
		    String line;
		    while ((line = br.readLine()) != null) {
				line = line.trim();
				if ((line.length() > 0) && (!(line.startsWith("#")))) set.add(line.trim().toLowerCase());
		    }
		    br.close();
		    serverLog.logInfo("PROXY", "read " + setname + " set from file " + filename);
		} catch (IOException e) {
		} finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
		}
		return set;
    }

    private static TreeMap loadMap(String mapname, String filename, String sep) {
		TreeMap map = new TreeMap();
        BufferedReader br = null;
		try {
		    br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
		    String line;
		    int pos;
		    while ((line = br.readLine()) != null) {
				line = line.trim();
				if ((line.length() > 0) && (!(line.startsWith("#"))) && ((pos = line.indexOf(sep)) > 0))
				    map.put(line.substring(0, pos).trim().toLowerCase(), line.substring(pos + sep.length()).trim());
		    }
		    serverLog.logInfo("PROXY", "read " + mapname + " map from file " + filename);
		} catch (IOException e) {            
		} finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
		}
		return map;
    }

    public static TreeMap loadBlacklist(String mapname, String filenames, String sep) {
		TreeMap map = new TreeMap();
		if (switchboard == null) return map; // not initialized yet
		File listsPath = new File(switchboard.getRootPath(), switchboard.getConfig("listsPath", "DATA/LISTS"));
		String filenamesarray[] = filenames.split(",");

		if(filenamesarray.length >0)
			for(int i = 0; i < filenamesarray.length; i++)
				map.putAll(loadMap(mapname, (new File(listsPath, filenamesarray[i])).toString(), sep));
		return map;
    }

    private static String domain(String host) {
	String domain = host;
	int pos = domain.lastIndexOf(".");
	if (pos >= 0) {
	    // truncate from last part
	    domain = domain.substring(0, pos);
	    pos = domain.lastIndexOf(".");
	    if (pos >= 0) {
		// truncate from first part
		domain = domain.substring(pos + 1);
	    }
	}
	return domain;
    }
    
    private boolean blacklistedURL(String hostlow, String path) {
        if (blackListURLs == null) return false;
        
        int index = 0;
        
        // [TL] While "." are found within the string
        while ((index = hostlow.indexOf(".", index + 1)) != -1) {
            if (blackListURLs.get(hostlow.substring(0, index + 1) + "*") != null) {
                //System.out.println("Host blocked: " + hostlow.substring(0, index+1) + "*");
                return true;
            }
        }
        
        index = hostlow.length();
        while ((index = hostlow.lastIndexOf(".", index - 1)) != -1) {
            if (blackListURLs.get("*" + hostlow.substring(index, hostlow.length())) != null) {
                //System.out.println("Host blocked: " + "*" + hostlow.substring(index, host.length()));
                return true;
            }
        }

        String pp = ""; // path-pattern
        return (((pp = (String) blackListURLs.get(hostlow)) != null) &&
                ((pp.equals("*")) || (path.substring(1).matches(pp))));
    }
    
    public void handleOutgoingCookies(httpHeader requestHeader, String targethost, String clienthost) {
        /*
        The syntax for the header is:
 
        cookie          =       "Cookie:" cookie-version
                                1*((";" | ",") cookie-value)
        cookie-value    =       NAME "=" VALUE [";" path] [";" domain]
        cookie-version  =       "$Version" "=" value
        NAME            =       attr
        VALUE           =       value
        path            =       "$Path" "=" value
        domain          =       "$Domain" "=" value
        */
        if (requestHeader.containsKey(httpHeader.COOKIE)) {
            Object[] entry = new Object[]{new Date(), clienthost, requestHeader.getMultiple(httpHeader.COOKIE)};
            switchboard.outgoingCookies.put(targethost, entry);
        }
    }
    
    public void handleIncomingCookies(httpHeader respondHeader, String serverhost, String targetclient) {
        /*
        The syntax for the Set-Cookie response header is 

        set-cookie      =       "Set-Cookie:" cookies
        cookies         =       1#cookie
        cookie          =       NAME "=" VALUE *(";" cookie-av)
        NAME            =       attr
        VALUE           =       value
        cookie-av       =       "Comment" "=" value
                        |       "Domain" "=" value
                        |       "Max-Age" "=" value
                        |       "Path" "=" value
                        |       "Secure"
                        |       "Version" "=" 1*DIGIT
        */
        if (respondHeader.containsKey(httpHeader.SET_COOKIE)) {
            Object[] entry = new Object[]{new Date(), targetclient, respondHeader.getMultiple(httpHeader.SET_COOKIE)};
            switchboard.incomingCookies.put(serverhost, entry);
        }
    }

    public void doGet(Properties conProp, httpHeader requestHeader, OutputStream respond) throws IOException {
	// prepare response
	// conProp      : a collection of properties about the connection, like URL
	// requestHeader : The header lines of the connection from the request
	// args         : the argument values of a connection, like &-values in GET and values within boundaries in POST
	// files        : files within POST boundaries, same key as in args

	if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();

	Date requestDate = new Date(); // remember the time...
	String method = conProp.getProperty("METHOD");
	String host = conProp.getProperty("HOST");
	String path = conProp.getProperty("PATH"); // always starts with leading '/'
	String args = conProp.getProperty("ARGS"); // may be null if no args were given
	String ip = conProp.getProperty("CLIENTIP"); // the ip from the connecting peer

	int port;
	int pos;

	if ((pos = host.indexOf(":")) < 0) {
	    port = 80;
	} else {
	    port = Integer.parseInt(host.substring(pos + 1));
	    host = host.substring(0, pos);
	}
        
        String ext;
        if ((pos = path.lastIndexOf('.')) < 0) {
            ext = "";
        } else {
            ext = path.substring(pos + 1).toLowerCase();
        }

	URL url = null;
	try {
	    if (args == null)
		url = new URL("http", host, port, path);
	    else
		url = new URL("http", host, port, path + "?" + args);
	} catch (MalformedURLException e) {
	    serverLog.logError("PROXY", "ERROR: internal error with url generation: host=" +
			       host + ", port=" + port + ", path=" + path + ", args=" + args);
	    url = null;
	}
	//System.out.println("GENERATED URL:" + url.toString()); // debug

	// check the blacklist
	// blacklist idea inspired by [AS]:
	// respond a 404 for all AGIS ("all you get is shit") servers
	String hostlow = host.toLowerCase();
        if (blacklistedURL(hostlow, path)) {
            try {
                respondHeader(respond,"404 Not Found (AGIS)", new httpHeader(null));
                respond.write(("404 (generated): URL '" + hostlow + "' blocked by yacy proxy (blacklisted)\r\n").getBytes());
                respond.flush();
                serverLog.logInfo("PROXY", "AGIS blocking of host '" + hostlow + "'"); // debug
                return;
            } catch (Exception ee) {}
        }
        
        // handle outgoing cookies
        handleOutgoingCookies(requestHeader, host, ip);
        
        // set another userAgent, if not yellowlisted
        if ((yellowList != null) && (!(yellowList.contains(domain(hostlow))))) {
            // change the User-Agent
            requestHeader.put(httpHeader.USER_AGENT, userAgent);
        }
        
        // set a scraper and a htmlFilter
        OutputStream hfos = null;
        htmlFilterContentScraper scraper = null;
        
        // resolve yacy and yacyh domains
        String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
        
        // re-calc the url path
        String remotePath = (args == null) ? path : (path + "?" + args); // with leading '/'
        
        // attach possible yacy-sublevel-domain
        if ((yAddress != null) &&
	    ((pos = yAddress.indexOf("/")) >= 0) &&
	    (!(remotePath.startsWith("/env"))) // this is the special path, staying always at root-level
	    ) remotePath = yAddress.substring(pos) + remotePath;
        
        // decide wether to use a cache entry or connect to the network
        File cacheFile = cacheManager.getCachePath(url);
        String urlHash = plasmaCrawlLURL.urlHash(url);
        httpHeader cachedResponseHeader = null;
        boolean cacheExists = ((cacheFile.exists()) && (cacheFile.isFile()) &&
			       ((cachedResponseHeader = cacheManager.getCachedResponse(urlHash)) != null));
        
        // why are files unzipped upon arrival? why not zip all files in cache?
        // This follows from the following premises
        // (a) no file shall be unzip-ed more than once to prevent unnessesary computing time
        // (b) old cache entries shall be comparable with refill-entries to detect/distiguish case 3+4
        // (c) the indexing mechanism needs files unzip-ed, a schedule could do that later
        // case b and c contradicts, if we use a scheduler, because files in a stale cache would be unzipped
        // and the newly arrival would be zipped and would have to be unzipped upon load. But then the
        // scheduler is superfluous. Therefore the only reminding case is
        // (d) cached files shall be either all zipped or unzipped
        // case d contradicts with a, because files need to be unzipped for indexing. Therefore
        // the only remaining case is to unzip files right upon load. Thats what we do here.
        
        // finally use existing cache if appropriate
        // here we must decide weather or not to save the data
        // to a cache
        // we distinguish four CACHE STATE cases:
        // 1. cache fill
        // 2. cache fresh - no refill
        // 3. cache stale - refill - necessary
        // 4. cache stale - refill - superfluous
        // in two of these cases we trigger a scheduler to handle newly arrived files:
        // case 1 and case 3
        plasmaHTCache.Entry cacheEntry;
        if ((cacheExists) &&
            ((cacheEntry = cacheManager.newEntry(requestDate, 0, url, requestHeader, "200 OK",
                                          cachedResponseHeader, null,
                                          switchboard.defaultProxyProfile)).shallUseCache())) {
            // we respond on the request by using the cache, the cache is fresh
            
            try {
                // replace date field in old header by actual date, this is according to RFC
                cachedResponseHeader.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
                
                // maybe the content length is missing
                if (!(cachedResponseHeader.containsKey(httpHeader.CONTENT_LENGTH)))
                    cachedResponseHeader.put(httpHeader.CONTENT_LENGTH, Long.toString(cacheFile.length()));
                
                // check if we can send a 304 instead the complete content
                if (requestHeader.containsKey(httpHeader.IF_MODIFIED_SINCE)) {
                    // conditional request: freshness of cache for that condition was already
                    // checked within shallUseCache(). Now send only a 304 response
                    log.logInfo("CACHE HIT/304 " + cacheFile.toString());
                    
                    // send cached header with replaced date and added length
                    respondHeader(respond, "304 OK", cachedResponseHeader); // respond with 'not modified'
                    
                } else {
                    // unconditional request: send content of cache
                    log.logInfo("CACHE HIT/203 " + cacheFile.toString());
                    
                    // send cached header with replaced date and added length
                    respondHeader(respond, "203 OK", cachedResponseHeader); // respond with 'non-authoritative'
                    
                    // make a transformer
                    if ((!(transformer.isIdentityTransformer())) &&
                        ((ext == null) || (!(plasmaParser.mediaExtContains(ext)))) &&
                        ((cachedResponseHeader == null) || (plasmaParser.realtimeParsableMimeTypesContains(cachedResponseHeader.mime())))) {
                        hfos = new htmlFilterOutputStream(respond, null, transformer, (ext.length() == 0));
                    } else {
                        hfos = respond;
                    }
                    
                    // send also the complete body now from the cache
                    // simply read the file and transfer to out socket
                    InputStream is = null;
                    try {
	                    is = new FileInputStream(cacheFile);
	                    byte[] buffer = new byte[2048];
	                    int l;
	                    while ((l = is.read(buffer)) > 0) {hfos.write(buffer, 0, l);}
                    } finally {
                        if (is != null) try { is.close(); } catch (Exception e) {}
                    }
                    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                }
                // that's it!
            } catch (SocketException e) {
                // this happens if the client stops loading the file
                // we do nothing here
                respondError(respond, "111 socket error: " + e.getMessage(), 1, url.toString());
            }
            respond.flush();
            return;
        }
        
        // the cache does either not exist or is (supposed to be) stale
        long sizeBeforeDelete = -1;
        if (cacheExists) {
            // delete the cache
            sizeBeforeDelete = cacheFile.length();
            cacheFile.delete();
        }
        
        // take a new file from the server
        httpc remote = null;
        httpc.response res = null;
        
        try {
            // open the connection
            if (yAddress == null) {
                remote = newhttpc(host, port, timeout);
            } else {
                remote = newhttpc(yAddress, timeout);
            }
            //System.out.println("HEADER: CLIENT TO PROXY = " + requestHeader.toString()); // DEBUG
            
            // send request
            res = remote.GET(remotePath, requestHeader);
            long contentLength = res.responseHeader.contentLength();
            
            // reserver cache entry
            cacheEntry = cacheManager.newEntry(requestDate, 0, url, requestHeader, res.status, res.responseHeader, null, switchboard.defaultProxyProfile);
            
            // handle file types
            if (((ext == null) || (!(plasmaParser.mediaExtContains(ext)))) &&
                (plasmaParser.realtimeParsableMimeTypesContains(res.responseHeader.mime()))) {
				// this is a file that is a possible candidate for parsing by the indexer
                if (transformer.isIdentityTransformer()) {
					log.logDebug("create passthrough (parse candidate) for url " + url);
					// no transformation, only passthrough
					// this is especially the case if the bluelist is empty
					// in that case, the content is not scraped here but later
                    hfos = respond;
                } else {
                    // make a scraper and transformer
					log.logDebug("create scraper for url " + url);
                    scraper = new htmlFilterContentScraper(url);
                    hfos = new htmlFilterOutputStream(respond, scraper, transformer, (ext.length() == 0));
                    if (((htmlFilterOutputStream) hfos).binarySuspect()) {
                        scraper = null; // forget it, may be rubbish
                        log.logDebug("Content of " + url + " is probably binary. deleted scraper.");
                    }
                    cacheEntry.scraper = scraper;
                }
            } else {
                log.logDebug("Resource " + url + " has wrong extension (" + ext + ") or wrong mime-type (" + res.responseHeader.mime() + "). not scraped");
                scraper = null;
                hfos = respond;
                cacheEntry.scraper = scraper;
            }
            
            // handle incoming cookies
            handleIncomingCookies(res.responseHeader, host, ip);
            
            // request has been placed and result has been returned. work off response
            try {
                respondHeader(respond, res.status, res.responseHeader);
                String storeError;
                if ((storeError = cacheEntry.shallStoreCache()) == null) {
                    // we write a new cache entry
                    if ((contentLength > 0) && // known
                        (contentLength < 1048576)) {// 1 MB
                        // ok, we don't write actually into a file, only to RAM, and schedule writing the file.
                        byte[] cacheArray = res.writeContent(hfos);
						log.logDebug("writeContent of " + url + " produced cacheArray = " + ((cacheArray == null) ? "null" : ("size=" + cacheArray.length)));

                        if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                        
                        if (sizeBeforeDelete == -1) {
                            // totally fresh file
                            cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                            cacheManager.stackProcess(cacheEntry, cacheArray);
                        } else if (sizeBeforeDelete == cacheArray.length) {
                            // before we came here we deleted a cache entry
                            cacheArray = null;
                            cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                            cacheManager.stackProcess(cacheEntry); // unnecessary update
                        } else {
                            // before we came here we deleted a cache entry
                            cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                            cacheManager.stackProcess(cacheEntry, cacheArray); // necessary update, write response header to cache
                        }
                    } else {
                        // the file is too big to cache it in the ram, or the size is unknown
						// write to file right here.
                        cacheFile.getParentFile().mkdirs();
                        res.writeContent(hfos, cacheFile);
                        if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
						log.logDebug("for write-file of " + url + ": contentLength = " + contentLength + ", sizeBeforeDelete = " + sizeBeforeDelete);
                        if (sizeBeforeDelete == -1) {
                            // totally fresh file
                            cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                            cacheManager.stackProcess(cacheEntry);
                        } else if (sizeBeforeDelete == cacheFile.length()) {
                            // before we came here we deleted a cache entry
                            cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                            cacheManager.stackProcess(cacheEntry); // unnecessary update
                        } else {
                            // before we came here we deleted a cache entry
                            cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                            cacheManager.stackProcess(cacheEntry); // necessary update, write response header to cache
                        }
						// beware! all these writings will not fill the cacheEntry.cacheArray
						// that means they are not available for the indexer (except they are scraped before)
                    }
                } else {
                    // no caching
                    log.logDebug(cacheFile.toString() + " not cached: " + storeError);
                    res.writeContent(hfos, null);
                    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                    if (sizeBeforeDelete == -1) {
                        // no old file and no load. just data passing
                        cacheEntry.status = plasmaHTCache.CACHE_PASSING;
                        cacheManager.stackProcess(cacheEntry);
                    } else {
                        // before we came here we deleted a cache entry
                        cacheEntry.status = plasmaHTCache.CACHE_STALE_NO_RELOAD;
                        cacheManager.stackProcess(cacheEntry);
                    }
                }
            } catch (SocketException e) {
                // this may happen if the client suddenly closes its connection
                // maybe the user has stopped loading
                // in that case, we are not responsible and just forget it
                // but we clean the cache also, since it may be only partial
                // and most possible corrupted
                if (cacheFile.exists()) cacheFile.delete();
                respondHeader(respond,"404 client unexpectedly closed connection", new httpHeader(null));
            } catch (IOException e) {
				// can have various reasons
                if (cacheFile.exists()) cacheFile.delete();
				if (e.getMessage().indexOf("Corrupt GZIP trailer") >= 0) {
				    // just do nothing, we leave it this way
					log.logDebug("ignoring bad gzip trail for URL " + url + " (" + e.getMessage() + ")");
				} else {
				    respondHeader(respond,"404 client unexpectedly closed connection", new httpHeader(null));
					log.logDebug("IOError for URL " + url + " (" + e.getMessage() + ") - responded 404");
				    e.printStackTrace();
				}
            }
        } catch (Exception e) {
            // this may happen if the targeted host does not exist or anything with the
            // remote server was wrong.
            // in any case, sending a 404 is appropriate
            try {
                if ((e.toString().indexOf("unknown host")) > 0) {
                    respondHeader(respond,"404 unknown host", new httpHeader(null));
                } else {
                    respondHeader(respond,"404 Not Found", new httpHeader(null));
                    respond.write(("Exception occurred:\r\n").getBytes());
                    respond.write((e.toString() + "\r\n").getBytes());
                    respond.write(("[TRACE: ").getBytes());
                    e.printStackTrace(new PrintStream(respond));
                    respond.write(("]\r\n").getBytes());
                }
            } catch (Exception ee) {}
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
        respond.flush();
    }

    
    private void respondError(OutputStream respond, String origerror, int errorcase, String url) {
        FileInputStream fis = null;
        try {
            // set rewrite values
            serverObjects tp = new serverObjects();
            tp.put("errormessage", errorcase);
            tp.put("httperror", origerror);
            tp.put("url", url);
            
            // rewrite the file
            File file = new File(htRootPath, "/proxymsg/error.html");
            byte[] result;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            fis = new FileInputStream(file);
            httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes());
            o.close();
            result = o.toByteArray();
            
            // return header
            httpHeader header = new httpHeader();
            header.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
            header.put(httpHeader.CONTENT_TYPE, "text/html");
            header.put(httpHeader.CONTENT_LENGTH, "" + o.size());
            header.put(httpHeader.PRAGMA, "no-cache");
            
            // write the array to the client
            respondHeader(respond, origerror, header);
            serverFileUtils.write(result, respond);
            respond.flush();
        } catch (IOException e) {            
        } finally {
			if (fis != null) try { fis.close(); } catch (Exception e) {}
        }
    }

    public void doHead(Properties conProp, httpHeader requestHeader, OutputStream respond) throws IOException {
	String method = conProp.getProperty("METHOD");
	String host = conProp.getProperty("HOST");
	String path = conProp.getProperty("PATH");
	String args = conProp.getProperty("ARGS"); // may be null if no args were given
	int port;
	int pos;
	if ((pos = host.indexOf(":")) < 0) {
	    port = 80;
	} else {
	    port = Integer.parseInt(host.substring(pos + 1));
	    host = host.substring(0, pos);
	}

	// check the blacklist, inspired by [AS]: respond a 404 for all AGIS (all you get is shit) servers
	String hostlow = host.toLowerCase();
        if (blacklistedURL(hostlow, path)) {
            try {
                respondHeader(respond,"404 Not Found (AGIS)", new httpHeader(null));
                respond.write(("404 (generated): URL '" + hostlow + "' blocked by yacy proxy (blacklisted)\r\n").getBytes());
                respond.flush();
                serverLog.logInfo("PROXY", "AGIS blocking of host '" + hostlow + "'"); // debug
                return;
            } catch (Exception ee) {}
        }

	// set another userAgent, if not yellowlisted
	if (!(yellowList.contains(domain(hostlow)))) {
	    // change the User-Agent
	    requestHeader.put(httpHeader.USER_AGENT, userAgent);
	}
	
        // resolve yacy and yacyh domains
        String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
        
        // re-calc the url path
	String remotePath = (args == null) ? path : (path + "?" + args);
        
        // attach possible yacy-sublevel-domain
        if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;

	httpc remote = null;
	httpc.response res = null;

	try {
	    // open the connection
            if (yAddress == null) {
                remote = newhttpc(host, port, timeout);
            } else {
                remote = newhttpc(yAddress, timeout); // with [AS] patch
            }
	    res = remote.HEAD(remotePath, requestHeader);
	    respondHeader(respond, res.status, res.responseHeader);
	} catch (Exception e) {
	    try {
		respondHeader(respond,"404 Not Found", new httpHeader(null));
		respond.write(("Exception occurred:\r\n").getBytes());
		respond.write((e.toString() + "\r\n").getBytes());
		respond.write(("[TRACE: ").getBytes());
		e.printStackTrace(new PrintStream(respond));
		respond.write(("]\r\n").getBytes());
	    } catch (Exception ee) {}
	} finally {
        if (remote != null) httpc.returnInstance(remote);
    }
    
	respond.flush();
    }

    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream respond, PushbackInputStream body) throws IOException {
	String host = conProp.getProperty("HOST");
	String path = conProp.getProperty("PATH");
	String args = conProp.getProperty("ARGS"); // may be null if no args were given
	int port;
	int pos;
	if ((pos = host.indexOf(":")) < 0) {
	    port = 80;
	} else {
	    port = Integer.parseInt(host.substring(pos + 1));
	    host = host.substring(0, pos);
	}

	// set another userAgent, if not yellowlisted
	if (!(yellowList.contains(domain(host).toLowerCase()))) {
	    // change the User-Agent
	    requestHeader.put(httpHeader.USER_AGENT, userAgent);
	}
	
        // resolve yacy and yacyh domains
        String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
        
        // re-calc the url path
        String remotePath = (args == null) ? path : (path + "?" + args);
        
        // attach possible yacy-sublevel-domain
        if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;

	httpc remote = null;
	httpc.response res = null;

	try {
            if (yAddress == null) {
                remote = newhttpc(host, port, timeout);
            } else {
                remote = newhttpc(yAddress, timeout);
            }
	    res = remote.POST(remotePath, requestHeader, body);
	    respondHeader(respond, res.status, res.responseHeader);
	    res.writeContent(respond, null);
	    remote.close();
	} catch (Exception e) {
	    try {
		respondHeader(respond,"404 Not Found", new httpHeader(null));
		respond.write(("Exception occurred:\r\n").getBytes());
		respond.write((e.toString() + "\r\n").getBytes());
		respond.write(("[TRACE: ").getBytes());
		e.printStackTrace(new PrintStream(respond));
		respond.write(("]\r\n").getBytes());
		} catch (Exception ee) {}
	} finally {
        if (remote != null) httpc.returnInstance(remote);
    }
	respond.flush();
    }

    public void doConnect(Properties conProp, de.anomic.http.httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) throws IOException {
        String host = conProp.getProperty("HOST");
    	int    port = Integer.parseInt(conProp.getProperty("PORT"));
    	String httpVersion = conProp.getProperty("HTTP");
    	int timeout = Integer.parseInt(switchboard.getConfig("clientTimeout", "10000"));
    
    	// possibly branch into PROXY-PROXY connection
    	if (remoteProxyUse) {
            httpc remoteProxy = null;
            try {
        	    remoteProxy = httpc.getInstance(host, port, timeout, false, remoteProxyHost, remoteProxyPort);
        	    httpc.response response = remoteProxy.CONNECT(host, port, requestHeader);
        	    response.print();
        	    if (response.success()) {
            		// replace connection details
            		host = remoteProxyHost;
            		port = remoteProxyPort;
            		// go on (see below)
                } else {
            		// pass error response back to client
            		respondHeader(clientOut, response.status, response.responseHeader);
            		return;
                }
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            } finally {
                if (remoteProxy != null) httpc.returnInstance(remoteProxy);
            }
    	} 
    
    	// try to establish connection to remote host
    	Socket sslSocket = new Socket(host, port);
    	sslSocket.setSoTimeout(timeout); // waiting time for write
    	sslSocket.setSoLinger(true, timeout); // waiting time for read
    	InputStream promiscuousIn  = sslSocket.getInputStream();
    	OutputStream promiscuousOut = sslSocket.getOutputStream();
    	
    	// now then we can return a success message
    	clientOut.write((httpVersion + " 200 Connection established" + serverCore.crlfString +
    			 "Proxy-agent: YACY" + serverCore.crlfString +
    			 serverCore.crlfString).getBytes());
    	
    	log.logInfo("SSL CONNECTION TO " + host + ":" + port + " ESTABLISHED");
    	
    	// start stream passing with mediate processes
    	try {
    	    Mediate cs = new Mediate(sslSocket, clientIn, promiscuousOut);
    	    Mediate sc = new Mediate(sslSocket, promiscuousIn, clientOut);
    	    cs.start();
    	    sc.start();
    	    while ((sslSocket != null) &&
    		   (sslSocket.isBound()) &&
    		   (!(sslSocket.isClosed())) &&
    		   (sslSocket.isConnected()) &&
    		   ((cs.isAlive()) || (sc.isAlive()))) {
    		// idle
    		try {Thread.currentThread().sleep(1000);} catch (InterruptedException e) {} // wait a while
    	    }
    	    // set stop mode
    	    cs.pleaseTerminate();
    	    sc.pleaseTerminate();
    	    // wake up thread
    	    cs.interrupt();
    	    sc.interrupt();
    	    // ...hope they have terminated...
    	} catch (IOException e) {
    	    //System.out.println("promiscuous termination: " + e.getMessage());
    	}
	
    }

    public class Mediate extends Thread {

	boolean terminate;
	Socket socket;
	InputStream in;
	OutputStream out;

	public Mediate(Socket socket, InputStream in, OutputStream out) throws IOException {
	    this.terminate = false;
	    this.in = in;
	    this.out = out;
	    this.socket = socket;
	}
	
	public void run() {
	    byte[] buffer = new byte[512];
	    int len;
	    try {
		while ((socket != null) &&
		       (socket.isBound()) &&
		       (!(socket.isClosed())) &&
		       (socket.isConnected()) &&
		       (!(terminate)) &&
		       (in != null) &&
		       (out != null) &&
		       ((len = in.read(buffer)) >= 0)
		       ) {
		    out.write(buffer, 0, len); 
		}
	    } catch (IOException e) {}
	}
	
	public void pleaseTerminate() {
	    terminate = true;
	}
    }

    private httpc newhttpc(String server, int port, int timeout) throws IOException {
	// a new httpc connection, combined with possible remote proxy
        boolean useProxy = remoteProxyUse;
        // check no-proxy rule
        if ((useProxy) && (!(remoteProxyAllowProxySet.contains(server)))) {
            if (remoteProxyDisallowProxySet.contains(server)) {
                useProxy = false;
            } else {
                // analyse remoteProxyNoProxy;
                // set either remoteProxyAllowProxySet or remoteProxyDisallowProxySet accordingly
                int i = 0;
                while (i < remoteProxyNoProxyPatterns.length) {
                    if (server.matches(remoteProxyNoProxyPatterns[i])) {
                        // disallow proxy for this server
                        remoteProxyDisallowProxySet.add(server);
                        useProxy = false;
                        break;
                    }
                    i++;
                }
                if (i == remoteProxyNoProxyPatterns.length) {
                    // no pattern matches: allow server
                    remoteProxyAllowProxySet.add(server);
                }
            }
        }
        // branch to server/proxy
        if (useProxy) {
            return httpc.getInstance(server, port, timeout, false, remoteProxyHost, remoteProxyPort);
        } else {
	    return httpc.getInstance(server, port, timeout, false);
        }
    }

    private httpc newhttpc(String address, int timeout) throws IOException {
	// a new httpc connection for <host>:<port>/<path> syntax
        // this is called when a '.yacy'-domain is used
        int p = address.indexOf(":");
        if (p < 0) return null;
        String server = address.substring(0, p);
        address = address.substring(p + 1);
        // remove possible path elements (may occur for 'virtual' subdomains
        p = address.indexOf("/");
        if (p >= 0) address = address.substring(0, p); // cut it off
        int port = Integer.parseInt(address);
        // normal creation of httpc object
        return newhttpc(server, port, timeout);
    }
    
    private void respondHeader(OutputStream respond, String status, httpHeader header) throws IOException, SocketException {

	// prepare header
	//header.put("Server", "AnomicHTTPD (www.anomic.de)");
	if (!(header.containsKey(httpHeader.DATE))) header.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
	if (!(header.containsKey(httpHeader.CONTENT_TYPE))) header.put(httpHeader.CONTENT_TYPE, "text/html"); // fix this

	StringBuffer headerStringBuffer = new StringBuffer(200);
    
	// write status line
	headerStringBuffer.append("HTTP/1.1 ").append(status).append("\r\n");

	//System.out.println("HEADER: PROXY TO CLIENT = " + header.toString()); // DEBUG

	// write header
	Iterator i = header.keySet().iterator();
	String key;
	String value;
        char tag;
        int count;
	//System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
	while (i.hasNext()) {
	    key = (String) i.next();
            tag = key.charAt(0);
	    if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                count = header.keyCount(key);
                for (int j = 0; j < count; j++) {
                    headerStringBuffer.append(key).append(": ").append((String) header.getSingle(key, j)).append("\r\n");  
                }
                //System.out.println("#" + key + ": " + value);
	    }
	}
        headerStringBuffer.append("\r\n");

	// end header
	respond.write(headerStringBuffer.toString().getBytes());
	respond.flush();
    }


    private void textMessage(OutputStream out, String body) throws IOException {
	out.write(("HTTP/1.1 200 OK\r\n").getBytes());
	out.write((httpHeader.SERVER + ": AnomicHTTPD (www.anomic.de)\r\n").getBytes());
	out.write((httpHeader.DATE + ": " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
	out.write((httpHeader.CONTENT_TYPE + ": text/plain\r\n").getBytes());
	out.write((httpHeader.CONTENT_LENGTH + ": " + body.length() +"\r\n").getBytes());
	out.write(("\r\n").getBytes());
	out.flush();
	out.write(body.getBytes());
	out.flush();
    }
    
    private void transferFile(OutputStream out, File f) throws IOException {
	InputStream source = new FileInputStream(f);
	byte[] buffer = new byte[4096];
	int bytes_read;
	while ((bytes_read = source.read(buffer)) > 0) out.write(buffer, 0, bytes_read);
	out.flush();
	source.close();
    }

}

/*
proxy test:

http://www.chipchapin.com/WebTools/cookietest.php?
http://xlists.aza.org/moderator/cookietest/cookietest1.php
http://vancouver-webpages.com/proxy/cache-test.html

*/
