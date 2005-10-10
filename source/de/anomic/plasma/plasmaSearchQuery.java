// plasmaSearchQuery.java 
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

import java.util.Set;
import java.util.TreeSet;
import java.util.Iterator;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.server.serverByteBuffer;

public final class plasmaSearchQuery {
    
    public static final int SEARCHDOM_LOCAL = 0;
    public static final int SEARCHDOM_GROUPDHT = 1;
    public static final int SEARCHDOM_GROUPALL = 2;
    public static final int SEARCHDOM_GLOBALDHT = 3;
    public static final int SEARCHDOM_GLOBALALL = 4;
    
    public Set queryWords;
    public Set queryHashes;
    public String referrer;
    public String[] order;
    public int wantedResults;
    public long maximumTime;
    public String urlMask;
    public int domType;
    public String domGroupName;
    public int domMaxTargets;

    public plasmaSearchQuery(Set queryWords, String referrer,
                             String[] order, int wantedResults, long maximumTime, String urlMask,
                             int domType, String domGroupName, int domMaxTargets) {
        this.queryWords = queryWords;
        this.queryHashes = words2hashes(queryWords);
        this.referrer = referrer;
        this.order = order;
        this.wantedResults = wantedResults;
        this.maximumTime = maximumTime;
        this.urlMask = urlMask;
        this.domType = domType;
        this.domGroupName = domGroupName;
        this.domMaxTargets = domMaxTargets;
    }
    

    public static Set words2hashes(String[] words) {
	TreeSet hashes = new TreeSet();
        for (int i = 0; i < words.length; i++) hashes.add(plasmaWordIndexEntry.word2hash(words[i]));
        return hashes;
    }

    public static Set words2hashes(Set words) {
	Iterator i = words.iterator();
	TreeSet hashes = new TreeSet();
	while (i.hasNext()) hashes.add(plasmaWordIndexEntry.word2hash((String) i.next()));
        return hashes;
    }
    
    public static TreeSet cleanQuery(String words) {
        // convert Umlaute
        words = htmlFilterContentScraper.convertUmlaute(new serverByteBuffer(words.getBytes())).toString();
        
        // remove funny symbols
        final String seps = "' .,:/-&";
        words = words.toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            if ((c = words.indexOf(seps.charAt(i))) >= 0) { words = words.substring(0, c) + (((c + 1) < words.length()) ? (" " + words.substring(c + 1)) : ""); }
        }
        
        // the string is clean now, but we must generate a set out of it
        final String[] a = words.split(" ");
        final TreeSet query = new TreeSet(kelondroMSetTools.fastStringComparator);
        for (int i = 0; i < a.length; i++) { query.add(a[i]); }
        return query;
    }
    
}
