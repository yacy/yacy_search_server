// plasmaSearch.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.06.2004
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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;

public final class plasmaSearch {
    
    private final plasmaCrawlLURL urlStore;
    private final plasmaWordIndex wordIndex;

    public plasmaSearch(plasmaCrawlLURL urlStore, plasmaWordIndex wordIndex) {
	this.urlStore = urlStore;
        this.wordIndex = wordIndex;
    }

    public static int calcVirtualAge(Date modified) {
	// this calculates a virtual age from a given date
	// the purpose is to have an age in days of a given modified date
	// from a fixed standpoint in the past
	//if (modified == null) return 0;
	// this is milliseconds. we need days
	// one day has 60*60*24 seconds = 86400 seconds
	// we take mod 64**3 = 262144, this is the mask of the storage
	return (int) ((modified.getTime() / 86400000) % 262144);
    }
    
    public void addWords(plasmaWordIndexEntryContainer container) {
        wordIndex.addEntries(container, true);
    }
        
    public int addPageIndex(URL url, String urlHash, Date urlModified, plasmaCondenser condenser,
			 String language, char doctype) {
        // this is called by the switchboard to put in a new page into the index
	// use all the words in one condenser object to simultanous create index entries
	int age = calcVirtualAge(urlModified);
	int quality = 0;
	try {
	    quality = Integer.parseInt(condenser.getAnalysis().getProperty("INFORMATION_VALUE","0"), 16);
	} catch (NumberFormatException e) {
	    System.out.println("INTERNAL ERROR WITH CONDENSER.INFORMATION_VALUE: " + e.toString() + ": in URL " + url.toString());
	}

        // iterate over all words
	Iterator i = condenser.getWords().iterator();
	String word;
	int count;
	plasmaWordIndexEntry entry;
	String wordHash;
	int p = 0;
	while (i.hasNext()) {
	    word = (String) i.next();
	    count = condenser.wordCount(word);
	    //if ((s.length() > 4) && (c > 1)) System.out.println("# " + s + ": " + c);
	    wordHash = plasmaWordIndexEntry.word2hash(word);
	    entry = new plasmaWordIndexEntry(urlHash, count, p++, 0, 0,
                                         age, quality, language, doctype, true);
	    this.wordIndex.addEntries(plasmaWordIndexEntryContainer.instantContainer(wordHash, System.currentTimeMillis(), entry), false);
	}
	//System.out.println("DEBUG: plasmaSearch.addPageIndex: added " + condenser.getWords().size() + " words, flushed " + c + " entries");
        return condenser.getWords().size();
    }
    /*
    public plasmaWordIndexEntity searchWords(Set words, long time) throws IOException {

    }
    */
    /*
    public plasmaWordIndexEntity searchHashes(Set hashes, long time) throws IOException {

    }
    */
    /*
    public plasmaSearchResult order(plasmaWordIndexEntity searchResult, Set searchhashes, Set stopwords, char[] priority, long maxTime, int minEntries) throws IOException {

    }
    */
}
