// plasmaProtocolLoader.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.10.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.crawler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.http.httpdProxyCacheEntry;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.logging.serverLog;

public final class ProtocolLoader {

    private static final long minDelay = 250; // milliseconds; 4 accesses per second
    private static final ConcurrentHashMap<String, Long> accessTime = new ConcurrentHashMap<String, Long>(); // to protect targets from DDoS
    
    private final plasmaSwitchboard sb;
    private final serverLog log;
    private final HashSet<String> supportedProtocols;
    private final HTTPLoader httpLoader;
    private final FTPLoader ftpLoader;
    
    public ProtocolLoader(final plasmaSwitchboard sb, final serverLog log) {
        this.sb = sb;
        this.log = log;
        this.supportedProtocols = new HashSet<String>(Arrays.asList(new String[]{"http","https","ftp"}));
        
        // initiate loader objects
        httpLoader = new HTTPLoader(sb, log);
        ftpLoader = new FTPLoader(sb, log);
    }
    
    public boolean isSupportedProtocol(final String protocol) {
        if ((protocol == null) || (protocol.length() == 0)) return false;
        return this.supportedProtocols.contains(protocol.trim().toLowerCase());
    }
    
    @SuppressWarnings("unchecked")
    public HashSet<String> getSupportedProtocols() {
        return (HashSet<String>) this.supportedProtocols.clone();
    }
    
    public httpdProxyCacheEntry load(final CrawlEntry entry, final String parserMode) {
        // getting the protocol of the next URL                
        final String protocol = entry.url().getProtocol();
        final String host = entry.url().getHost();
        
        // check if this loads a page from localhost, which must be prevented to protect the server
        // against attacks to the administration interface when localhost access is granted
        if (serverCore.isLocalhost(host) && sb.getConfigBool("adminAccountForLocalhost", false)) return null;
        
        // check access time
        if (!entry.url().isLocal()) {
            final Long lastAccess = accessTime.get(host);
            long wait = 0;
            if (lastAccess != null) wait = Math.max(0, minDelay + lastAccess.longValue() - System.currentTimeMillis());
            if (wait > 0) {
                // force a sleep here. Instead just sleep we clean up the accessTime map
                final long untilTime = System.currentTimeMillis() + wait;
                final Iterator<Map.Entry<String, Long>> i = accessTime.entrySet().iterator();
                Map.Entry<String, Long> e;
                while (i.hasNext()) {
                    e = i.next();
                    if (System.currentTimeMillis() > untilTime) break;
                    if (System.currentTimeMillis() - e.getValue().longValue() > minDelay) i.remove();
                }
                if (System.currentTimeMillis() < untilTime)
                    try {Thread.sleep(untilTime - System.currentTimeMillis());} catch (final InterruptedException ee) {}
            }
        }
        accessTime.put(host, System.currentTimeMillis());
        
        // load resource
        if ((protocol.equals("http") || (protocol.equals("https")))) return httpLoader.load(entry, parserMode);
        if (protocol.equals("ftp")) return ftpLoader.load(entry);
        
        this.log.logWarning("Unsupported protocol '" + protocol + "' in url " + entry.url());
        return null;
    }
    
    public String process(final CrawlEntry entry, final String parserMode) {
        // load a resource, store it to htcache and push queue entry to switchboard queue
        // returns null if everything went fine, a fail reason string if a problem occurred
        httpdProxyCacheEntry h;
        try {
            h = load(entry, parserMode);
            entry.setStatus("loaded");
            if (h == null) return "load failed";
            final boolean stored = sb.htEntryStoreProcess(h);
            entry.setStatus("stored-" + ((stored) ? "ok" : "fail"));
            return (stored) ? null : "not stored";
        } catch (final Exception e) {
            log.logWarning("problem loading " + entry.url().toString(), e);
            return "load error - " + e.getMessage();
        }
    }

}





