// plasmaSearchEvent.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 10.10.2005
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

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Enumeration;
import java.io.IOException;

import de.anomic.kelondro.kelondroException;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCodings;

public final class plasmaSearchEvent {
    
    private serverLog log;
    private plasmaSearchQuery query;
    private plasmaWordIndex wordIndex;
    private plasmaCrawlLURL urlStore;
    private plasmaSnippetCache snippetCache;
    
    public plasmaSearchEvent(plasmaSearchQuery query, serverLog log, plasmaWordIndex wordIndex, plasmaCrawlLURL urlStore, plasmaSnippetCache snippetCache) {
        this.log = log;
        this.wordIndex = wordIndex;
        this.query = query;
        this.urlStore = urlStore;
        this.snippetCache = snippetCache;
    }
    
    public plasmaWordIndexEntity search(long time) throws IOException {
        // search for the set of hashes and return an array of urlEntry elements
        
        long stamp = System.currentTimeMillis();
        
        // retrieve entities that belong to the hashes
        Set entities = wordIndex.getEntities(query.queryHashes, true, true);
        
        // since this is a conjunction we return an empty entity if any word is not known
        if (entities == null) return new plasmaWordIndexEntity(null);
        
        // join the result
        return plasmaWordIndexEntity.joinEntities(entities, time - (System.currentTimeMillis() - stamp));
    }
    
    public plasmaSearchResult order(plasmaWordIndexEntity searchResult, long maxTime, int minEntries) throws IOException {
	// we collect the urlhashes from it and construct a List with urlEntry objects
	// attention: if minEntries is too high, this method will not terminate within the maxTime

	plasmaSearchResult acc = new plasmaSearchResult(query);
	if (searchResult == null) return acc; // strange case where searchResult is not proper: acc is then empty
        if (searchResult.size() == 0) return acc; // case that we have nothing to do
        
	Enumeration e = searchResult.elements(true);
	plasmaWordIndexEntry entry;
        long startCreateTime = System.currentTimeMillis();
        plasmaCrawlLURL.Entry page;
	try {
	    while (e.hasMoreElements()) {
                if ((acc.sizeFetched() >= minEntries) &&
                    (System.currentTimeMillis() - startCreateTime >= maxTime)) break;
                entry = (plasmaWordIndexEntry) e.nextElement();
                // find the url entry
                page = urlStore.getEntry(entry.getUrlHash());
                // add a result
		acc.addResult(entry, page);
	    }
	} catch (kelondroException ee) {
	    serverLog.logSevere("PLASMA", "Database Failure during plasmaSearch.order: " + ee.getMessage(), ee);
	}
        long startSortTime = System.currentTimeMillis();
        acc.sortResults();
        serverLog.logFine("PLASMA", "plasmaSearch.order: minEntries = " + minEntries + ", effectiveEntries = " + acc.sizeOrdered() + ", demanded Time = " + maxTime + ", effectiveTime = " + (System.currentTimeMillis() - startCreateTime) + ", createTime = " + (startSortTime - startCreateTime) + ", sortTime = " + (System.currentTimeMillis() - startSortTime));
	return acc;
    }
    
    /*
    public void preSearch() {
        plasmaWordIndexEntity idx = null;
        try {
            // search the database locally
            log.logFine("presearch: started job");
            idx = searchHashes(query.queryHashes, time);
            log.logFine("presearch: found " + idx.size() + " results");
            plasmaSearchResult acc = order(idx, queryhashes, order, time, searchcount);
            if (acc == null) return;
            log.logFine("presearch: ordered results, now " + acc.sizeOrdered() + " URLs ready for fetch");
            
            // take some elements and fetch the snippets
            snippetCache.fetch(acc, queryhashes, urlmask, fetchcount);
        } catch (IOException e) {
            log.logSevere("presearch: failed", e);
        } finally {
            if (idx != null) try { idx.close(); } catch (Exception e){}
        }
        log.logFine("presearch: job terminated");
    }
    */
}
