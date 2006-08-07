// plasmaEURL.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.08.2004
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

// EURL - noticed (known but not loaded) URL's

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import de.anomic.net.URL;

import java.util.Date;
import java.util.LinkedList;
import java.util.Iterator;

import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.tools.bitfield;

public class plasmaCrawlEURL extends indexURL {

    /* =======================================================================
     * Failure reason constants
     * ======================================================================= */
    // invalid urls
    public static final String DENIED_URL_NULL = "denied_(url_null)";
    public static final String DENIED_MALFORMED_URL = "denied_(malformed_url)";
    public static final String DENIED_PRIVATE_IP_ADDRESS = "denied_(private_ip_address)";
    public static final String DENIED_LOOPBACK_IP_ADDRESS = "denied_(loopback_ip_address)";
    public static final String DENIED_CACHEFILE_PATH_TOO_LONG = "denied_(cachefile_path_too_long)";
    public static final String DENIED_INVALID_CACHEFILE_PATH = "denied_(invalid_cachefile_path)";    
    
    // blacklisted/blocked urls
    public static final String DENIED_URL_IN_BLACKLIST = "denied_(url_in_blacklist)";
    public static final String DENIED_URL_DOES_NOT_MATCH_FILTER = "denied_(does_not_match_filter)";
    public static final String DENIED_CGI_URL = "denied_(cgi_url)";
    public static final String DENIED_POST_URL = "denied_(post_url)";
    public static final String DENIED_NO_MATCH_WITH_DOMAIN_FILTER = "denied_(no_match_with_domain_filter)";
    public static final String DENIED_DOMAIN_COUNT_EXCEEDED = "denied_(domain_count_exceeded)";    
    public static final String DENIED_ROBOTS_TXT = "denied_(robots.txt)";
    
    // wrong content
    public static final String DENIED_WRONG_MIMETYPE_OR_EXT = "denied_(wrong_mimetype_or_extension)";
    public static final String DENIED_REDIRECTION_HEADER_EMPTY = "denied_(redirection_header_empty)";
    public static final String DENIED_REDIRECTION_COUNTER_EXCEEDED = "denied_(redirection_counter_exceeded)";
    public static final String DENIED_WRONG_HTTP_STATUSCODE = "denied_(wrong_http_status_code_";
    public static final String DENIED_CONTENT_DECODING_ERROR = "denied_(content_decoding_error)";
    
    // network errors
    public static final String DENIED_UNKNOWN_HOST = "denied_(unknown_host)";
    public static final String DENIED_NO_ROUTE_TO_HOST = "denied_(no_route_to_host)"; 
    public static final String DENIED_NETWORK_IS_UNREACHABLE = "denied_(Network_is_unreachable)";    
    
    // connection errors
    public static final String DENIED_CONNECTION_ERROR = "denied_(socket_connection_error)";
    public static final String DENIED_CONNECTION_BIND_EXCEPTION = "denied_(connection_bind_exception)";
    public static final String DENIED_CONNECTION_TIMEOUT = "denied_(connection_timeout)";
    public static final String DENIED_CONNECTION_REFUSED = "denied_(connection_refused)";    
    public static final String DENIED_SSL_UNTRUSTED_CERT = "denied_(No_trusted_ssl_certificate_found)";

    // double registered errors
    public static final String DOUBLE_REGISTERED = "double_(registered_in_";
    
    // server errors
    public static final String DENIED_OUT_OF_DISK_SPACE = "denied_(out_of_disk_space)";
    public static final String DENIED_SERVER_SHUTDOWN = "denied_(server_shutdown)";
    
    // Parser errors
    public static final String DENIED_PARSER_ERROR = "denied_(parser_error)";
    public static final String DENIED_NOT_PARSEABLE_NO_CONTENT = "denied_(not_parseabel_no_content)";
    
    // indexing errors
    public static final String DENIED_UNSPECIFIED_INDEXING_ERROR = "denied_(unspecified_indexing_error)";
    public static final String DENIED_UNKNOWN_INDEXING_PROCESS_CASE = "denied_(unknown_indexing_process_case)";
    

    /* =======================================================================
     * Other object variables
     * ======================================================================= */        
    private LinkedList rejectedStack = new LinkedList(); // strings: url
    
    public plasmaCrawlEURL(File cachePath, int bufferkb, long preloadTime) {
        super();
        int[] ce = {
            urlHashLength,           // the url's hash
            urlHashLength,           // the url's referrer hash
            urlHashLength,           // the crawling initiator
            urlHashLength,           // the crawling executor
            urlStringLength,         // the url as string
            urlNameLength,           // the name of the url, from anchor tag <a>name</a>
            urlDateLength,           // the time when the url was first time appeared
            urlDateLength,           // the time when the url was last time tried to load
            urlRetryLength,          // number of load retries
            urlErrorLength,          // string describing load failure
            urlFlagLength            // extra space
        };
        if (cachePath.exists()) try {
            // open existing cache
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent);
        } catch (IOException e) {
            cachePath.delete();
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent, new kelondroRow(ce), true);
        } else {
            // create new cache
            cachePath.getParentFile().mkdirs();
            urlHashCache = new kelondroTree(cachePath, bufferkb * 0x400, preloadTime, kelondroTree.defaultObjectCachePercent, new kelondroRow(ce), true);
        }
    }

    public synchronized Entry newEntry(URL url, String referrer, String initiator, String executor,
				       String name, String failreason, bitfield flags) {
        if ((referrer == null) || (referrer.length() < urlHashLength)) referrer = dummyHash;
        if ((initiator == null) || (initiator.length() < urlHashLength)) initiator = dummyHash;
        if ((executor == null) || (executor.length() < urlHashLength)) executor = dummyHash;
        if (failreason == null) failreason = "unknown";
        return new Entry(url, referrer, initiator, executor, name, failreason, flags);
    }

    public synchronized void stackPushEntry(Entry e) {
        rejectedStack.add(e.hash);
    }
    
    public Entry stackPopEntry(int pos) throws IOException {
        String urlhash = (String) rejectedStack.get(pos);
        if (urlhash == null) return null;
        return new Entry(urlhash);
    }
   
    public synchronized Entry getEntry(String hash) throws IOException {
        return new Entry(hash);
    }

    public boolean exists(String urlHash) {
        try {
            return (urlHashCache.get(urlHash.getBytes()) != null);
        } catch (IOException e) {
            return false;
        }
    }
    
    public void clearStack() {
        rejectedStack.clear();
    }
    
    public int stackSize() {
        return rejectedStack.size();
    }
    
    public class Entry {

        private String   hash;       // the url's hash
        private String   referrer;   // the url's referrer hash
        private String   initiator;  // the crawling initiator
        private String   executor;  // the crawling initiator
        private URL      url;        // the url as string
        private String   name;       // the name of the url, from anchor tag <a>name</a>     
        private Date     initdate;   // the time when the url was first time appeared
        private Date     trydate;    // the time when the url was last time tried to load
        private int      trycount;   // number of tryings
        private String   failreason; // string describing reason for load fail
        private bitfield flags;      // extra space
        private boolean  stored;

        public Entry(URL url, String referrer, String initiator,
                     String executor, String name, String failreason, bitfield flags) {
            // create new entry
            this.hash = urlHash(url);
            this.referrer = (referrer == null) ? dummyHash : referrer;
            this.initiator = initiator;
            this.executor = executor;
            this.url = url;
            this.name = name;
            this.initdate = new Date();
            this.trydate = new Date();
            this.trycount = 0;
            this.failreason = failreason;
            this.flags = flags;
            this.stored = false;
        }

	    public Entry(String hash) throws IOException {
            // generates an plasmaEURLEntry using the url hash
            // to speed up the access, the url-hashes are buffered
            // in the hash cache.
            // we have two options to find the url:
            // - look into the hash cache
            // - look into the filed properties
            // if the url cannot be found, this returns null
            this.hash = hash;
            kelondroRow.Entry entry = urlHashCache.get(hash.getBytes());
            if (entry != null) {
                insertEntry(entry);
            }
            this.stored = true;
        }

        public Entry(kelondroRow.Entry entry) throws IOException {
            insertEntry(entry);
            this.stored = false;
        }
        
        private void insertEntry(kelondroRow.Entry entry) throws IOException {
            assert (entry != null);
            this.hash = entry.getColString(0, null);
            this.referrer = entry.getColString(1, "UTF-8");
            this.initiator = entry.getColString(2, "UTF-8");
            this.executor = entry.getColString(3, "UTF-8");
            this.url = new URL(entry.getColString(4, "UTF-8").trim());
            String n = entry.getColString(5, "UTF-8");
            this.name = (n == null) ? "" : n.trim();
            this.initdate = new Date(86400000 * entry.getColLongB64E(6));
            this.trydate = new Date(86400000 * entry.getColLongB64E(7));
            this.trycount = (int) entry.getColLongB64E(8);
            this.failreason = entry.getColString(9, "UTF-8");
            this.flags = new bitfield(entry.getColBytes(10));
            return;
        }
        
        public void store() {
	        // stores the values from the object variables into the database
            if (this.stored) return;
            String initdatestr = kelondroBase64Order.enhancedCoder.encodeLong(initdate.getTime() / 86400000, urlDateLength);
            String trydatestr = kelondroBase64Order.enhancedCoder.encodeLong(trydate.getTime() / 86400000, urlDateLength);

            // store the hash in the hash cache
            try {
                // even if the entry exists, we simply overwrite it
                byte[][] entry = new byte[][] {
                    this.hash.getBytes(),
                    this.referrer.getBytes(),
                    this.initiator.getBytes(),
                    this.executor.getBytes(),
                    this.url.toString().getBytes(),
                    this.name.getBytes(),
                    initdatestr.getBytes(),
                    trydatestr.getBytes(),
                    kelondroBase64Order.enhancedCoder.encodeLong(this.trycount, urlRetryLength).getBytes(),
                    this.failreason.getBytes(),
                    this.flags.getBytes()
                };
                urlHashCache.put(urlHashCache.row().newEntry(entry));
                this.stored = true;
            } catch (IOException e) {
                System.out.println("INTERNAL ERROR AT plasmaEURL:url2hash:" + e.toString());
            }
	    }

	public String hash() {
	    // return a url-hash, based on the md5 algorithm
	    // the result is a String of 12 bytes within a 72-bit space
	    // (each byte has an 6-bit range)
	    // that should be enough for all web pages on the world
	    return this.hash;
	}

        public String referrer() {
	    return this.referrer;
	}
        
	public URL url() {
	    return url;
	}

	public Date initdate() {
	    return trydate;
	}

	public Date trydate() {
	    return trydate;
	}

	public String initiator() {
	    // return the creator's hash
	    return initiator;
	}
        
        public String executor() {
	    // return the creator's hash
	    return executor;
	}
        
        public String name() {
	    // return the creator's hash
	    return name;
	}
        
        public String failreason() {
            return failreason;
        }

    }

    public class kiter implements Iterator {
        // enumerates entry elements
        Iterator i;
        boolean error = false;
        
        public kiter(boolean up, boolean rotating, String firstHash) throws IOException {
            i = urlHashCache.rows(up, rotating, (firstHash == null) ? null : firstHash.getBytes());
            error = false;
        }

        public boolean hasNext() {
            if (error) return false;
            return i.hasNext();
        }

        public Object next() throws RuntimeException {
            kelondroRow.Entry e = (kelondroRow.Entry) i.next();
            if (e == null) return null;
            try {
                return new Entry(e);
            } catch (IOException ex) {
                throw new RuntimeException("error '" + ex.getMessage() + "' for hash " + e.getColString(0, null));
            }
        }
        
        public void remove() {
            i.remove();
        }
        
    }

    public Iterator entries(boolean up, boolean rotating, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, rotating, firstHash);
    }
}
