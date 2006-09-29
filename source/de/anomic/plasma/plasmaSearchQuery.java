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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterAbstractScraper;
import de.anomic.index.indexEntryAttribute;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverCharBuffer;

public final class plasmaSearchQuery {
    
    public static final int SEARCHDOM_LOCAL = 0;
    public static final int SEARCHDOM_GROUPDHT = 1;
    public static final int SEARCHDOM_GROUPALL = 2;
    public static final int SEARCHDOM_GLOBALDHT = 3;
    public static final int SEARCHDOM_GLOBALALL = 4;
    
    public Set queryWords, queryHashes;
    public int wantedResults;
    public String prefer;
    public long maximumTime;
    public String urlMask;
    public int domType;
    public String domGroupName;
    public int domMaxTargets;
    public int maxDistance;

    public plasmaSearchQuery(Set queryWords, int maxDistance, String prefer,
                             int wantedResults, long maximumTime, String urlMask,
                             int domType, String domGroupName, int domMaxTargets) {
        this.queryWords = queryWords;
        this.maxDistance = maxDistance;
        this.prefer = prefer;
        this.queryHashes = words2hashes(queryWords);
        this.wantedResults = wantedResults;
        this.maximumTime = maximumTime;
        this.urlMask = urlMask;
        this.domType = domType;
        this.domGroupName = domGroupName;
        this.domMaxTargets = domMaxTargets;
    }
    
    public plasmaSearchQuery(Set queryHashes, int maxDistance, String prefer,
                             int wantedResults, long maximumTime, String urlMask) {
        this.queryWords = null;
        this.maxDistance = maxDistance;
        this.prefer = prefer;
        this.queryHashes = queryHashes;
        this.wantedResults = wantedResults;
        this.maximumTime = maximumTime;
        this.urlMask = urlMask;
        this.domType = -1;
        this.domGroupName = null;
        this.domMaxTargets = -1;
    }

    public static Set words2hashSet(String[] words) {
        TreeSet hashes = new TreeSet();
        for (int i = 0; i < words.length; i++) hashes.add(indexEntryAttribute.word2hash(words[i]));
        return hashes;
    }

    public static String words2hashString(String[] words) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < words.length; i++) sb.append(indexEntryAttribute.word2hash(words[i]));
        return new String(sb);
    }

    public static Set words2hashes(Set words) {
        Iterator i = words.iterator();
        TreeSet hashes = new TreeSet();
        while (i.hasNext()) hashes.add(indexEntryAttribute.word2hash((String) i.next()));
        return hashes;
    }
    
    public static Set hashes2Set(String query) {
        if (query == null) return new HashSet();
        final HashSet keyhashes = new HashSet(query.length() / indexEntryAttribute.wordHashLength);
        for (int i = 0; i < (query.length() / indexEntryAttribute.wordHashLength); i++) {
            keyhashes.add(query.substring(i * indexEntryAttribute.wordHashLength, (i + 1) * indexEntryAttribute.wordHashLength));
        }
        return keyhashes;
    }
    
    public static String hashSet2hashString(Set words) {
        Iterator i = words.iterator();
        StringBuffer sb = new StringBuffer(words.size() * indexEntryAttribute.wordHashLength);
        while (i.hasNext()) sb.append((String) i.next());
        return new String(sb);
    }
    
    public static TreeSet cleanQuery(String words) {
        // convert Umlaute
        words = htmlFilterAbstractScraper.convertUmlaute(new serverCharBuffer(words.toCharArray())).toString();
        
        // remove funny symbols
        final String seps = "' .,:/-&";
        words = words.toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            if ((c = words.indexOf(seps.charAt(i))) >= 0) { words = words.substring(0, c) + (((c + 1) < words.length()) ? (" " + words.substring(c + 1)) : ""); }
        }
        
        // the string is clean now, but we must generate a set out of it
        final TreeSet query = new TreeSet(kelondroNaturalOrder.naturalOrder);
        if (words.length() == 0) return query; // split returns always one element
        final String[] a = words.split(" ");
        for (int i = 0; i < a.length; i++) { query.add(a[i]); }
        return query;
    }
    
    public int size() {
    		return queryHashes.size();
    }
    
    public String words(String separator) {
        if (queryWords == null) return "";
    		StringBuffer result = new StringBuffer(8 * queryWords.size());
    		Iterator i = queryWords.iterator();
    		if (i.hasNext()) result.append((String) i.next());
    		while (i.hasNext()) {
    			result.append(separator);
    			result.append((String) i.next());
    		}
    		return result.toString();
    }
    
    /*
    public String hashes(String separator) {
		StringBuffer result = new StringBuffer(8 * queryHashes.size());
		Iterator i = queryHashes.iterator();
		if (i.hasNext()) result.append((String) i.next());
		while (i.hasNext()) {
			result.append(separator);
			result.append((String) i.next());
		}
		return result.toString();
    }
   */
    
    public void filterOut(Set blueList) {
        // filter out words that appear in this set
        Iterator it = queryWords.iterator();
        String word;
        while (it.hasNext()) {
            word = (String) it.next();
            if (blueList.contains(word)) it.remove();
        }
    }

}
