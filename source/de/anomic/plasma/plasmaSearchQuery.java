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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterAbstractScraper;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverCharBuffer;
import de.anomic.yacy.yacySeedDB;

public final class plasmaSearchQuery {
    
    public static final int SEARCHDOM_LOCAL = 0;
    public static final int SEARCHDOM_CLUSTERDHT = 1;
    public static final int SEARCHDOM_CLUSTERALL = 2;
    public static final int SEARCHDOM_GLOBALDHT = 3;
    public static final int SEARCHDOM_GLOBALALL = 4;
    
    public static final int CONTENTDOM_ALL   = -1;
    public static final int CONTENTDOM_TEXT  = 0;
    public static final int CONTENTDOM_IMAGE = 1;
    public static final int CONTENTDOM_AUDIO = 2;
    public static final int CONTENTDOM_VIDEO = 3;
    public static final int CONTENTDOM_APP   = 4;
    
    public static final kelondroBitfield empty_constraint    = new kelondroBitfield(4, "AAAAAA");
    public static final kelondroBitfield catchall_constraint = new kelondroBitfield(4, "______");
    
    public String queryString;
    public TreeSet queryHashes, excludeHashes;
    private int linesPerPage, offset;
    public String prefer;
    public int contentdom;
    public long maximumTime;
    public String urlMask;
    public int domType;
    public String domGroupName;
    public int domMaxTargets;
    public int maxDistance;
    public kelondroBitfield constraint;
    public boolean allofconstraint;
    public boolean onlineSnippetFetch;

    public plasmaSearchQuery(String queryString, int lines, kelondroBitfield constraint) {
    	if ((queryString.length() == 12) && (kelondroBase64Order.enhancedCoder.wellformed(queryString.getBytes()))) {
    		this.queryString = null;
            this.queryHashes = new TreeSet();
            this.excludeHashes = new TreeSet();
            this.queryHashes.add(queryString);
    	} else {
    		this.queryString = queryString;
    		TreeSet[] cq = cleanQuery(queryString);
    		this.queryHashes = plasmaCondenser.words2hashes(cq[0]);
    		this.excludeHashes = plasmaCondenser.words2hashes(cq[1]);
    	}
        this.maxDistance = Integer.MAX_VALUE;
        this.prefer = "";
        this.contentdom = CONTENTDOM_ALL;
        this.linesPerPage = lines;
        this.offset = 0;
        this.maximumTime = 10000;
        this.urlMask = ".*";
        this.domType = SEARCHDOM_LOCAL;
        this.domGroupName = "";
        this.domMaxTargets = 0;
        this.constraint = constraint;
        this.allofconstraint = false;
        this.onlineSnippetFetch = false;
    }
    
public plasmaSearchQuery(String queryString, TreeSet queryHashes, TreeSet excludeHashes, int maxDistance, String prefer, int contentdom,
        boolean onlineSnippetFetch,
        int lines, int offset, long maximumTime, String urlMask,
        int domType, String domGroupName, int domMaxTargets,
        kelondroBitfield constraint, boolean allofconstraint) {
		this.queryString = queryString;
		this.queryHashes = queryHashes;
		this.excludeHashes = excludeHashes;
		this.maxDistance = maxDistance;
		this.prefer = prefer;
		this.contentdom = contentdom;
		this.linesPerPage = lines;
		this.offset = offset;
		this.maximumTime = maximumTime;
		this.urlMask = urlMask;
		this.domType = domType;
		this.domGroupName = domGroupName;
		this.domMaxTargets = domMaxTargets;
		this.constraint = constraint;
		this.allofconstraint = allofconstraint;
		this.onlineSnippetFetch = onlineSnippetFetch;
    }
    
    public int neededResults() {
        // the number of result lines that must be computed
        return this.offset + this.linesPerPage;
    }
    
    public int displayResults() {
        // the number of result lines that are displayed at once (size of result page)
        return this.linesPerPage;
    }
    
    public void setOffset(int newOffset) {
        this.offset = newOffset;
    }
    
    public static int contentdomParser(String dom) {
        if (dom.equals("text")) return CONTENTDOM_TEXT;
        else if (dom.equals("image")) return CONTENTDOM_IMAGE;
        else if (dom.equals("audio")) return CONTENTDOM_AUDIO;
        else if (dom.equals("video")) return CONTENTDOM_VIDEO;
        else if (dom.equals("app")) return CONTENTDOM_APP;
        return CONTENTDOM_TEXT;
    }
    
    public String contentdom() {
        if (this.contentdom == CONTENTDOM_TEXT) return "text";
        else if (this.contentdom == CONTENTDOM_IMAGE) return "image";
        else if (this.contentdom == CONTENTDOM_AUDIO) return "audio";
        else if (this.contentdom == CONTENTDOM_VIDEO) return "video";
        else if (this.contentdom == CONTENTDOM_APP) return "app";
        return "text";
    }
    
    public String searchdom() {
        return (this.domType == SEARCHDOM_LOCAL) ? "local" : "global";
    }
    
    public static TreeSet hashes2Set(String query) {
        if (query == null) return new TreeSet(kelondroBase64Order.enhancedCoder);
        final TreeSet keyhashes = new TreeSet(kelondroBase64Order.enhancedCoder);
        for (int i = 0; i < (query.length() / yacySeedDB.commonHashLength); i++) {
            keyhashes.add(query.substring(i * yacySeedDB.commonHashLength, (i + 1) * yacySeedDB.commonHashLength));
        }
        return keyhashes;
    }
    
    public static String hashSet2hashString(Set hashes) {
        Iterator i = hashes.iterator();
        StringBuffer sb = new StringBuffer(hashes.size() * yacySeedDB.commonHashLength);
        while (i.hasNext()) sb.append((String) i.next());
        return new String(sb);
    }
    
    public static final boolean matches(String text, TreeSet keyhashes) {
    	// returns true if any of the word hashes in keyhashes appear in the String text
    	// to do this, all words in the string must be recognized and transcoded to word hashes
    	TreeSet wordhashes = plasmaCondenser.words2hashes(plasmaCondenser.getWords(text).keySet());
    	return kelondroMSetTools.anymatch(wordhashes, keyhashes);
    }
    
    public static TreeSet[] cleanQuery(String querystring) {
    	// returns two sets: a query set and a exclude set
    	if ((querystring == null) || (querystring.length() == 0)) return new TreeSet[]{new TreeSet(kelondroBase64Order.enhancedCoder), new TreeSet(kelondroBase64Order.enhancedCoder)};
        
        // convert Umlaute
        querystring = htmlFilterAbstractScraper.convertUmlaute(new serverCharBuffer(querystring.toCharArray())).toString();
        
        // remove funny symbols
        final String seps = "'.,:/&";
        querystring = querystring.toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = querystring.indexOf(seps.charAt(i))) >= 0) { querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (" " + querystring.substring(c + 1)) : ""); }
        }
        
        // the string is clean now, but we must generate a set out of it
        final TreeSet query = new TreeSet(kelondroNaturalOrder.naturalOrder);
        final TreeSet exclude = new TreeSet(kelondroNaturalOrder.naturalOrder);
        final String[] a = querystring.split(" ");
        for (int i = 0; i < a.length; i++) {
        	if (a[i].startsWith("-")) {
        		exclude.add(a[i].substring(1));
        	} else {
        		while ((c = a[i].indexOf('-')) >= 0) {
        			query.add(a[i].substring(0, c));
        			a[i] = a[i].substring(c + 1);
        		}
        		if (a[i].length() > 0) query.add(a[i]);
        	}
        }
        return new TreeSet[]{query, exclude};
    }
    
    public String queryString() {
    	return this.queryString;
    }
    
    public TreeSet[] queryWords() {
        return cleanQuery(this.queryString);
    }
    
    public void filterOut(Set blueList) {
        // filter out words that appear in this set
    	// this is applied to the queryHashes
    	TreeSet blues = plasmaCondenser.words2hashes(blueList);
    	kelondroMSetTools.excludeDestructive(queryHashes, blues);
    }

    public static String anonymizedQueryHashes(Set hashes) {
        // create a more anonymized representation of euqery hashes for logging
        StringBuffer sb = new StringBuffer(hashes.size() * 14 + 2);
        Iterator i = hashes.iterator();
        sb.append("[");
        String hash;
        if (i.hasNext()) {
            hash = (String) i.next();
            sb.append(hash.substring(0, 3)).append(".........");
        }
        while (i.hasNext()) {
            hash = (String) i.next();
            sb.append(", ").append(hash.substring(0, 3)).append(".........");
        }
        sb.append("]");
        return new String(sb);
    }
    
    public String id() {
        // generate a string that identifies a search so results can be re-used in a cache
        return hashSet2hashString(this.queryHashes) + "-" + hashSet2hashString(this.excludeHashes) + ":" + this.contentdom;
    }
    
    public HashMap resultProfile(int searchcount, long searchtime, long urlretrieval, long snippetcomputation) {
        // generate statistics about search: query, time, etc
        HashMap r = new HashMap();
        r.put("queryhashes", queryHashes);
        r.put("querystring", queryString);
        r.put("querycount", new Integer(linesPerPage));
        r.put("querytime", new Long(maximumTime));
        r.put("resultcount", new Integer(searchcount));
        r.put("resulttime", new Long(searchtime));
        r.put("resulturltime", new Long(urlretrieval));
        r.put("resultsnippettime", new Long(snippetcomputation));
        return r;
    }
}
