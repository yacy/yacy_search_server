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

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import de.anomic.htmlFilter.*;
import de.anomic.server.*;
import de.anomic.tools.*;
import de.anomic.yacy.*;
import de.anomic.http.*;
import de.anomic.plasma.*;


public class httpdProxyHandler extends httpdAbstractHandler implements httpdHandler {
    
    // static variables
    // can only be instantiated upon first instantiation of this class object
    private static plasmaSwitchboard switchboard = null;
    private static plasmaHTCache  cacheManager = null;
    public  static serverLog log;
    public  static HashSet yellowList = null;
    public  static TreeMap blackListURLs = null;
    private static int timeout = 30000;
    private static boolean yacyTrigger = true;
    public static boolean remoteProxyUse = false;
    public static String remoteProxyHost = "";
    public static int remoteProxyPort = -1;
    public static String remoteProxyNoProxy = "";
    public static String[] remoteProxyNoProxyPatterns = null;
    private static HashSet remoteProxyAllowProxySet = new HashSet();
    private static HashSet remoteProxyDisallowProxySet = new HashSet();
    private static htmlFilterTransformer transformer = null;
    public  static String userAgent = "yacy (" + httpc.systemOST +") yacy.net";
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
            remoteProxyAllowProxySet = new HashSet();
            remoteProxyDisallowProxySet = new HashSet();
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
	    try {
		ClassLoader cp = new serverClassLoader(this.getClass().getClassLoader());
		Class transformerClass = cp.loadClass(switchboard.getConfig("pageTransformerClass", ""));
		transformer = (htmlFilterTransformer) transformerClass.newInstance();
		transformer.init(switchboard.getConfig("pageTransformerArg", "")); // this is usually the blueList
	    } catch (Exception e) {
		transformer = null;
	    }

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
	try {
	    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
	    String line;
	    while ((line = br.readLine()) != null) {
		line = line.trim();
		if ((line.length() > 0) && (!(line.startsWith("#")))) set.add(line.trim().toLowerCase());
	    }
	    br.close();
	    serverLog.logInfo("PROXY", "read " + setname + " set from file " + filename);
	} catch (IOException e) {}
	return set;
    }

    private static TreeMap loadMap(String mapname, String filename, String sep) {
	TreeMap map = new TreeMap();
	try {
	    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
	    String line;
	    int pos;
	    while ((line = br.readLine()) != null) {
		line = line.trim();
		if ((line.length() > 0) && (!(line.startsWith("#"))) && ((pos = line.indexOf(sep)) > 0))
		    map.put(line.substring(0, pos).trim().toLowerCase(), line.substring(pos + sep.length()).trim());
	    }
	    br.close();
	    serverLog.logInfo("PROXY", "read " + mapname + " map from file " + filename);
	} catch (IOException e) {}
	return map;
    }

    public static TreeMap loadBlacklist(String mapname, String filenames, String sep) {
	TreeMap map = new TreeMap();
        if (switchboard == null) return map; // not initialized yet
	File listsPath = new File(switchboard.getRootPath(), switchboard.getConfig("listsPath", "DATA/LISTS"));
        String filenamesarray[] = filenames.split(",");
	String filename = "";
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
        // request header may have double-entries: they are accumulated in one entry
        // by the httpd and separated by a "#" in the value field
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
        if (requestHeader.containsKey("Cookie")) {
            Object[] entry = new Object[]{new Date(), clienthost, requestHeader.get("Cookie")};
            switchboard.outgoingCookies.put(targethost, entry);
        }
    }
    
    public void handleIncomingCookies(httpHeader respondHeader, String serverhost, String targetclient) {
        // respond header may have double-entries: they are accumulated in one entry
        // by the httpc and separated by a "#" in the value field
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
        if (respondHeader.containsKey("Set-Cookie")) {
            Object[] entry = new Object[]{new Date(), targetclient, respondHeader.get("Set-Cookie")};
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
	    requestHeader.put("User-Agent", userAgent);
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
	plasmaHTCache.Entry hpc;
	if (cacheExists) {
	    // we respond on the request by using the cache

            hpc = cacheManager.newEntry(requestDate, 0, url, requestHeader, "200 OK", cachedResponseHeader, null, null, switchboard.defaultProxyProfile);

	    if (hpc.shallUseCache()) {
		// the cache is fresh

		try {
		    // replace date field in old header by actual date, this is according to RFC
		    cachedResponseHeader.put("Date", httpc.dateString(httpc.nowDate()));
		    
		    // maybe the content length is missing
		    if (!(cachedResponseHeader.containsKey("CONTENT-LENGTH")))
			cachedResponseHeader.put("CONTENT-LENGTH", (String) ("" + cacheFile.length()));

		    // check if we can send a 304 instead the complete content
		    if (requestHeader.containsKey("IF-MODIFIED-SINCE")) {
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
			if (((ext == null) || (!(switchboard.extensionBlack.contains(ext)))) &&
                            ((cachedResponseHeader == null) || (httpd.isTextMime(cachedResponseHeader.mime(), switchboard.mimeWhite)))) {
			    hfos = new htmlFilterOutputStream(respond, null, transformer, (ext.length() == 0));
			} else {
                            hfos = respond;
			}
			
			// send also the complete body now from the cache
			// simply read the file and transfer to out socket
			InputStream is = new FileInputStream(cacheFile);
			byte[] buffer = new byte[2048];
			int l;
			while ((l = is.read(buffer)) > 0) {hfos.write(buffer, 0, l);}
			is.close();
			if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
		    }
		    // that's it!
		} catch (SocketException e) {
		    // this happens if the client stops loading the file
		    // we do nothing here
                    respondError(respond, "111 socket error: " + e.getMessage(), 1, url.toString());
		}
	    } else {
		// the cache is (supposed to be) stale

		// delete the cache
		long sizeBeforeDelete = cacheFile.length();
		cacheFile.delete();

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

		    // make a scraper and transformer
                    if (((ext == null) || (!(switchboard.extensionBlack.contains(ext)))) &&
                        (httpd.isTextMime(res.responseHeader.mime(), switchboard.mimeWhite))) {
			scraper = new htmlFilterContentScraper(url);
			hfos = new htmlFilterOutputStream(respond, scraper, transformer, (ext.length() == 0));
                        if (((htmlFilterOutputStream) hfos).binarySuspect()) {
                            scraper = null; // forget it, may be rubbish
                            log.logDebug("Content of " + url + " is probably binary. deleted scraper.");
                        }
		    } else {
                        log.logDebug("Resource " + url + " has wrong extension (" + ext + ") or wrong mime-type (" + res.responseHeader.mime() + "). not scraped");
			scraper = null;
			hfos = respond;
		    }

		    // reserver cache entry
		    hpc = cacheManager.newEntry(requestDate, 0, url, requestHeader, res.status, res.responseHeader, scraper, null, switchboard.defaultProxyProfile);

                    // handle incoming cookies
                    handleIncomingCookies(res.responseHeader, host, ip);
                    
		    // request has been placed and result has been returned. work off response
		    try {
			respondHeader(respond, res.status, res.responseHeader);
                        String storeError;
			if ((storeError = hpc.shallStoreCache()) == null) {
			    // we write a new cache entry
			    if ((contentLength > 0) && // known
                                (contentLength < 1048576)) // 1 MB
                            {
				byte[] cacheArray = res.writeContent(hfos);
				if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
				// before we came here we deleted a cache entry
				if (sizeBeforeDelete == cacheArray.length) {
				    cacheArray = null;
                                    hpc.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
				    cacheManager.stackProcess(hpc); // unnecessary update
				} else {
                                    hpc.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
				    cacheManager.stackProcess(hpc, cacheArray); // necessary update, write response header to cache
				}
			    } else {
				cacheFile.getParentFile().mkdirs();
				res.writeContent(hfos, cacheFile);
				if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
				// before we came here we deleted a cache entry
				if (sizeBeforeDelete == cacheFile.length()) {
                                    hpc.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
				    cacheManager.stackProcess(hpc); // unnecessary update
				} else {
                                    hpc.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
				    cacheManager.stackProcess(hpc); // necessary update, write response header to cache
				}
			    }
			} else {
			    // no caching
                            log.logDebug(cacheFile.toString() + " not cached: " + storeError);
			    res.writeContent(hfos, null);
			    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
			    // before we came here we deleted a cache entry
                            hpc.status = plasmaHTCache.CACHE_STALE_NO_RELOAD;
			    cacheManager.stackProcess(hpc);
			}
		    } catch (SocketException e) {
			// this may happen if the client suddenly closes its connection
			// maybe the user has stopped loading
			// in that case, we are not responsible and just forget it
			// but we clean the cache also, since it may be only partial
			// and most possible corrupted
			if (cacheFile.exists()) cacheFile.delete();
		    }
		    remote.close();
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
		}
	    }
	} else {
	    // we take a new file from the net and respond with that
	    try {
		// open the connection
		//httpc remote = newhttpc(host, port, timeout);
                httpc remote;
                if (yAddress == null) {
                    remote = newhttpc(host, port, timeout);
                } else {
                    remote = newhttpc(yAddress, timeout);
                }
		//System.out.println("HEADER: CLIENT TO PROXY = " + requestHeader.toString()); // DEBUG
		
		// send request
		httpc.response res = remote.GET(remotePath, requestHeader);
		long contentLength = res.responseHeader.contentLength();

		// make a scraper and transformer
                if (((ext == null) || (!(switchboard.extensionBlack.contains(ext)))) &&
                    (httpd.isTextMime(res.responseHeader.mime(), switchboard.mimeWhite))) {
                    scraper = new htmlFilterContentScraper(url);
		    hfos = new htmlFilterOutputStream(respond, scraper, transformer, (ext.length() == 0));
                    if (((htmlFilterOutputStream) hfos).binarySuspect()) {
                        scraper = null; // forget it, may be rubbish
                        log.logDebug("Content of " + url + " is probably binary. deleted scraper.");
                    }
		} else {
                    log.logDebug("Resource " + url + " has wrong extension (" + ext + ") or wrong mime-type (" + res.responseHeader.mime() + "). not scraped");
		    scraper = null;
		    hfos = respond;
		}

		// reserve cache entry
		hpc = cacheManager.newEntry(requestDate, 0, url, requestHeader, res.status, res.responseHeader, scraper, null, switchboard.defaultProxyProfile);

                // handle incoming cookies
                handleIncomingCookies(res.responseHeader, host, ip);
                    
		// request has been placed and result has been returned. work off response
		try {
		    //System.out.println("HEADER: SERVER TO PROXY = [" + res.status + "] " + ((httpHeader) res.responseHeader).toString()); // DEBUG
		    respondHeader(respond, res.status, res.responseHeader);
		    String storeError;
                    if ((storeError = hpc.shallStoreCache()) == null) {
			// we write a new cache entry
			if ((contentLength > 0) && (contentLength < 1048576)) {
			    // write to buffer
			    byte[] cacheArray = res.writeContent(hfos);
			    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
			    // enQueue new entry with response header and file as byte[]
                            hpc.status = plasmaHTCache.CACHE_FILL;
			    cacheManager.stackProcess(hpc, cacheArray);
			} else try {
			    // write to file system directly
			    cacheFile.getParentFile().mkdirs();
			    res.writeContent(hfos, cacheFile);
			    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
			    // enQueue new entry with response header
			    hpc.status = plasmaHTCache.CACHE_FILL;
			    cacheManager.stackProcess(hpc);
			} catch (FileNotFoundException e) {
			    // this may happen if there are no write rights whatsoever
			    // (do nothing)
			    /*
			      Exception occurred:
			      java.io.FileNotFoundException: 
			      /opt/yacy_pre_v0.314_20041219/DATA/HTCACHE/www.spiegel.de/fotostrecke/0,5538,PB64-SUQ9NDYwNyZucj0z,00.html 
			      (Permission denied)
			    */
			}
		    } else {
			// no caching
			//System.out.println("DEBUG: " + res.status + " " + cacheFile.toString()); // debug
			log.logDebug(cacheFile.toString() + " not cached: " + storeError);
			res.writeContent(hfos, null);
			if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
			// no old file and no load. just data passing
                        hpc.status = plasmaHTCache.CACHE_PASSING;
			cacheManager.stackProcess(hpc);
		    }
		} catch (SocketException e) {
		    // this may happen if the client suddenly closes its connection
		    // maybe the user has stopped loading
		    // in that case, we are not responsible and just forget it
		    // but we clean the cache also, since it may be only partial
		    // and most possible corrupted
		    if (cacheFile.exists()) cacheFile.delete();
		    respondHeader(respond,"404 client unexpectedly closed connection", new httpHeader(null));
		}
		remote.close();
	    } catch (Exception e) {
		// this may happen if the targeted host does not exist or anything with the
		// remote server was wrong.
		// in any case, sending a 404 is appropriate
		try {
		    if ((e.toString().indexOf("unknown host")) > 0) {
			respondHeader(respond,"404 unknown host", new httpHeader(null));
		    } else {
			respondHeader(respond,"404 resource not available (generic exception: " + e.toString() + ")", new httpHeader(null));
			//respond.write(("Exception occurred:\r\n").getBytes());
			//respond.write((e.toString() + "\r\n").getBytes());
			//respond.write(("[TRACE: ").getBytes());
			//e.printStackTrace(new PrintStream(respond));
			//respond.write(("]\r\n").getBytes());
			/*	http://www.geocrawler.com/archives/3/201/1999/8/50/2505805/
				> java.net.ConnectException: Connection refused
			 */
			e.printStackTrace();
		    }
		} catch (Exception ee) {}
	    }
	}
	respond.flush();
    }

    
    private void respondError(OutputStream respond, String origerror, int errorcase, String url) {
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
            FileInputStream fis = new FileInputStream(file);
            httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes());
            o.close();
            result = o.toByteArray();
            
            // return header
            httpHeader header = new httpHeader();
            header.put("Date", httpc.dateString(httpc.nowDate()));
            header.put("Content-type", "text/html");
            header.put("Content-length", "" + o.size());
            header.put("Pragma", "no-cache");
            
            // write the array to the client
            respondHeader(respond, origerror, header);
            serverFileUtils.write(result, respond);
            respond.flush();
        } catch (IOException e) {
            
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
	    requestHeader.put("User-Agent", userAgent);
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
	    requestHeader.put("User-Agent", userAgent);
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
	    httpc remoteProxy = new httpc(host, port, timeout, false, remoteProxyHost, remoteProxyPort);
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
            return new httpc(server, port, timeout, false, remoteProxyHost, remoteProxyPort);
        } else {
	    return new httpc(server, port, timeout, false);
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
	String s;

	// prepare header
	//header.put("Server", "AnomicHTTPD (www.anomic.de)");
	if (!(header.containsKey("date"))) header.put("Date", httpc.dateString(httpc.nowDate()));
	if (!(header.containsKey("content-type"))) header.put("Content-type", "text/html"); // fix this

	// write status line
	respond.write(("HTTP/1.1 " + status + "\r\n").getBytes());

	//System.out.println("HEADER: PROXY TO CLIENT = " + header.toString()); // DEBUG

	// write header
	Iterator i = header.keySet().iterator();
	String key;
	String value;
	int pos;
	//System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
	while (i.hasNext()) {
	    key = (String) i.next();
	    if (!(key.startsWith("#"))) { // '#' in key is reserved for proxy attributes as artificial header values
		value = (String) header.get(key);
		if (!(key.equals("Location"))) while ((pos = value.lastIndexOf("#")) >= 0) {
		    // special handling is needed if a key appeared several times, which is valid.
		    // all lines with same key are combined in one value, separated by a "#"
		    respond.write((key + ": " + value.substring(pos + 1).trim() + "\r\n").getBytes());
		    //System.out.println("#" + key + ": " + value.substring(pos + 1).trim());
		    value = value.substring(0, pos).trim();
		}
		respond.write((key + ": " + value + "\r\n").getBytes());
		//System.out.println("#" + key + ": " + value);
	    }
	}

	// end header
	respond.write(("\r\n").getBytes());
	respond.flush();
    }


    private void textMessage(OutputStream out, String body) throws IOException {
	out.write(("HTTP/1.1 200 OK\r\n").getBytes());
	out.write(("Server: AnomicHTTPD (www.anomic.de)\r\n").getBytes());
	out.write(("Date: " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
	out.write(("Content-type: text/plain\r\n").getBytes());
	out.write(("Content-length: " + body.length() +"\r\n").getBytes());
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
