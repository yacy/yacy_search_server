// plasmaSearchQuery.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.data.htmlTools;
import de.anomic.htmlFilter.htmlFilterAbstractScraper;
import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverCharBuffer;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

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
    public TreeSet<String> queryHashes, excludeHashes;
    public int linesPerPage, offset;
    public String prefer;
    public int contentdom;
    public String urlMask;
    public String targetlang;
    public int domType;
    public int zonecode;
    public int domMaxTargets;
    public int maxDistance;
    public kelondroBitfield constraint;
    public boolean allofconstraint;
    public boolean onlineSnippetFetch;
    public plasmaSearchRankingProfile ranking;
    public String host;
    public yacySeed remotepeer;
    public Long handle;
    // values that are set after a search:
    public int resultcount; // number of found results
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public boolean specialRights; // is true if the user has a special authorization and my use more database-extensive options
    
    public plasmaSearchQuery(final String queryString,
    						 final int lines,
    		                 final plasmaSearchRankingProfile ranking,
    		                 final kelondroBitfield constraint) {
    	if ((queryString.length() == 12) && (kelondroBase64Order.enhancedCoder.wellformed(queryString.getBytes()))) {
    		this.queryString = null;
            this.queryHashes = new TreeSet<String>();
            this.excludeHashes = new TreeSet<String>();
            this.queryHashes.add(queryString);
    	} else {
    		this.queryString = queryString;
    		final TreeSet<String>[] cq = cleanQuery(queryString);
    		this.queryHashes = indexWord.words2hashes(cq[0]);
    		this.excludeHashes = indexWord.words2hashes(cq[1]);
    	}
    	this.ranking = ranking;
        this.maxDistance = Integer.MAX_VALUE;
        this.prefer = "";
        this.contentdom = CONTENTDOM_ALL;
        this.linesPerPage = lines;
        this.offset = 0;
        this.urlMask = ".*";
        this.targetlang = "en";
        this.domType = SEARCHDOM_LOCAL;
        this.zonecode = yacyURL.TLD_any_zone_filter;
        this.domMaxTargets = 0;
        this.constraint = constraint;
        this.allofconstraint = false;
        this.onlineSnippetFetch = false;
        this.host = null;
        this.remotepeer = null;
        this.handle = Long.valueOf(System.currentTimeMillis());
        this.specialRights = false;
    }
    
    public plasmaSearchQuery(
		final String queryString, final TreeSet<String> queryHashes,
		final TreeSet<String> excludeHashes, 
        final plasmaSearchRankingProfile ranking,
        final int maxDistance, final String prefer, final int contentdom,
        final String language,
        final boolean onlineSnippetFetch,
        final int lines, final int offset, final String urlMask,
        final int domType, final String domGroupName, final int domMaxTargets,
        final kelondroBitfield constraint, final boolean allofconstraint,
        final int domainzone,
        final String host,
        final boolean specialRights) {
		this.queryString = queryString;
		this.queryHashes = queryHashes;
		this.excludeHashes = excludeHashes;
		this.ranking = ranking;
		this.maxDistance = maxDistance;
		this.prefer = prefer;
		this.contentdom = contentdom;
		this.linesPerPage = Math.min((specialRights) ? 1000 : 10, lines);
		this.offset = Math.min((specialRights) ? 10000 : 100, offset);
		this.urlMask = urlMask;
        this.targetlang = language;
        this.domType = domType;
        this.zonecode = domainzone;
		this.domMaxTargets = domMaxTargets;
		this.constraint = constraint;
		this.allofconstraint = allofconstraint;
		this.onlineSnippetFetch = onlineSnippetFetch;
		this.host = host;
        this.remotepeer = null;
		this.handle = Long.valueOf(System.currentTimeMillis());
		this.specialRights = specialRights;
    }
    
    public int neededResults() {
        // the number of result lines that must be computed
        return this.offset + this.linesPerPage;
    }
    
    public int displayResults() {
        // the number of result lines that are displayed at once (size of result page)
        return this.linesPerPage;
    }
    
    public void setOffset(final int newOffset) {
        this.offset = newOffset;
    }
    
    public static int contentdomParser(final String dom) {
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
    
    public boolean isGlobal() {
        return this.domType != SEARCHDOM_LOCAL;
    }
    
    public boolean isLocal() {
        return this.domType == SEARCHDOM_LOCAL;
    }
    
    public static TreeSet<String> hashes2Set(final String query) {
        if (query == null) return new TreeSet<String>(kelondroBase64Order.enhancedComparator);
        final TreeSet<String> keyhashes = new TreeSet<String>(kelondroBase64Order.enhancedComparator);
        for (int i = 0; i < (query.length() / yacySeedDB.commonHashLength); i++) {
            keyhashes.add(query.substring(i * yacySeedDB.commonHashLength, (i + 1) * yacySeedDB.commonHashLength));
        }
        return keyhashes;
    }
    
    public static String hashSet2hashString(final Set<String> hashes) {
        final Iterator<String> i = hashes.iterator();
        final StringBuffer sb = new StringBuffer(hashes.size() * yacySeedDB.commonHashLength);
        while (i.hasNext()) sb.append(i.next());
        return new String(sb);
    }

    public static String anonymizedQueryHashes(final Set<String> hashes) {
        // create a more anonymized representation of a query hashes for logging
        final Iterator<String> i = hashes.iterator();
        final StringBuffer sb = new StringBuffer(hashes.size() * (yacySeedDB.commonHashLength + 2) + 2);
        sb.append("[");
        String hash;
        if (i.hasNext()) {
            hash = i.next();
            sb.append(hash.substring(0, 3)).append(".........");
        }
        while (i.hasNext()) {
            hash = i.next();
            sb.append(", ").append(hash.substring(0, 3)).append(".........");
        }
        sb.append("]");
        return new String(sb);
    }
    
    public static final boolean matches(final String text, final TreeSet<String> keyhashes) {
    	// returns true if any of the word hashes in keyhashes appear in the String text
    	// to do this, all words in the string must be recognized and transcoded to word hashes
    	final TreeSet<String> wordhashes = indexWord.words2hashes(plasmaCondenser.getWords(text).keySet());
    	return kelondroMSetTools.anymatch(wordhashes, keyhashes);
    }
    
    private static String seps = "'.,/&-_"; static {seps += '"';}
    
    @SuppressWarnings("unchecked")
    public static TreeSet<String>[] cleanQuery(String querystring) {
    	// returns two sets: a query set and a exclude set
    	if ((querystring == null) || (querystring.length() == 0)) return new TreeSet[]{new TreeSet<String>(kelondroNaturalOrder.naturalComparator), new TreeSet<String>(kelondroNaturalOrder.naturalComparator)};
        
        // convert Umlaute
        querystring = htmlFilterAbstractScraper.convertUmlaute(new serverCharBuffer(querystring.toCharArray())).toString().toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = querystring.indexOf(seps.charAt(i))) >= 0) { querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (" " + querystring.substring(c + 1)) : ""); }
        }
        
        // the string is clean now, but we must generate a set out of it
        final TreeSet<String> query = new TreeSet<String>(kelondroNaturalOrder.naturalComparator);
        final TreeSet<String> exclude = new TreeSet<String>(kelondroNaturalOrder.naturalComparator);
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
    
    public String queryString(final boolean encodeHTML) {
    	if(encodeHTML){
    		return htmlTools.encodeUnicode2html(this.queryString, true);
    	}
    	return this.queryString;
    }
    
    public TreeSet<String>[] queryWords() {
        return cleanQuery(this.queryString);
    }
    
    public void filterOut(final Set<String> blueList) {
        // filter out words that appear in this set
    	// this is applied to the queryHashes
    	final TreeSet<String> blues = indexWord.words2hashes(blueList);
    	kelondroMSetTools.excludeDestructive(queryHashes, blues);
    }

    public String id(final boolean anonymized) {
        // generate a string that identifies a search so results can be re-used in a cache
        String context =
            "*" + this.domType + 
            "*" + this.contentdom +
            "*" + this.zonecode +
            "*" + indexWord.word2hash(this.ranking.toExternalString()) +
            "*" + this.prefer +
            "*" + this.urlMask +
            "*" + this.targetlang +
            "*" + this.constraint +
            "*" + this.maxDistance;
        if (anonymized) 
            return anonymizedQueryHashes(this.queryHashes) + "-" + anonymizedQueryHashes(this.excludeHashes) + context;
        else
            return hashSet2hashString(this.queryHashes) + "-" + hashSet2hashString(this.excludeHashes) + context;
    }
    
}
