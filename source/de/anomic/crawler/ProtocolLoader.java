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

import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

public final class ProtocolLoader {

    private plasmaSwitchboard sb;
    private serverLog log;
    private HashSet<String> supportedProtocols;
    private HTTPLoader httpLoader;
    private FTPLoader ftpLoader;
    
    public ProtocolLoader(plasmaSwitchboard sb, serverLog log) {
        this.sb = sb;
        this.log = log;
        this.supportedProtocols = new HashSet<String>(Arrays.asList(new String[]{"http","https","ftp"}));
        
        // initiate loader objects
        httpLoader = new HTTPLoader(sb, log);
        ftpLoader = new FTPLoader(sb, log);
    }
    
    public boolean isSupportedProtocol(String protocol) {
        if ((protocol == null) || (protocol.length() == 0)) return false;
        return this.supportedProtocols.contains(protocol.trim().toLowerCase());
    }
    
    @SuppressWarnings("unchecked")
    public HashSet<String> getSupportedProtocols() {
        return (HashSet<String>) this.supportedProtocols.clone();
    }
    
    public plasmaHTCache.Entry load(CrawlEntry entry, String parserMode) {
        // getting the protocol of the next URL                
        String protocol = entry.url().getProtocol();
        
        if ((protocol.equals("http") || (protocol.equals("https")))) return httpLoader.load(entry, parserMode);
        if (protocol.equals("ftp")) return ftpLoader.load(entry);
        
        this.log.logWarning("Unsupported protocol '" + protocol + "' in url " + entry.url());
        return null;
    }
    
    public String process(CrawlEntry entry, String parserMode) {
        // load a resource, store it to htcache and push queue entry to switchboard queue
        // returns null if everything went fine, a fail reason string if a problem occurred
        plasmaHTCache.Entry h;
        try {
            h = load(entry, parserMode);
            entry.setStatus("loaded");
            if (h == null) return "load failed";
            boolean stored = sb.htEntryStoreProcess(h);
            entry.setStatus("stored-" + ((stored) ? "ok" : "fail"));
            return (stored) ? null : "not stored";
        } catch (Exception e) {
            log.logWarning("problem loading " + entry.url().toString(), e);
            return "load error - " + e.getMessage();
        }
    }

}





