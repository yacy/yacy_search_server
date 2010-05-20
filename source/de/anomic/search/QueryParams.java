// plasmaSearchQuery.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 10.10.2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.search;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.document.Condenser;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.SetTools;

import de.anomic.yacy.yacySeed;

public final class QueryParams {
    
    public static final int SEARCHDOM_LOCAL = 0;
    public static final int SEARCHDOM_CLUSTERDHT = 1;
    public static final int SEARCHDOM_CLUSTERALL = 2;
    public static final int SEARCHDOM_GLOBALDHT = 3;
    public static final int SEARCHDOM_GLOBALALL = 4;
    
    public static enum FetchMode {
    	NO_FETCH_NO_VERIFY,
    	FETCH_BUT_ACCEPT_OFFLINE_OR_USE_CACHE,
    	FETCH_AND_VERIFY_ONLINE;
    }
    
    public static final Bitfield empty_constraint    = new Bitfield(4, "AAAAAA");
    public static final Pattern catchall_pattern = Pattern.compile(".*");
    public static final Pattern matchnothing_pattern = Pattern.compile("");
    
    public final String queryString;
    public HandleSet fullqueryHashes, queryHashes, excludeHashes;
    public final int itemsPerPage;
    public int offset;
    public final Pattern urlMask, prefer;
    public final boolean urlMask_isCatchall, prefer_isMatchnothing;
    public final ContentDomain contentdom;
    public final String targetlang;
    public final String navigators;
    public final int domType;
    public final int zonecode;
    public final int domMaxTargets;
    public final int maxDistance;
    public final Bitfield constraint;
    public final boolean allofconstraint;
    public final boolean onlineSnippetFetch;
    public final RankingProfile ranking;
    private final Segment indexSegment;
    public final String host; // this is the client host that starts the query, not a site operator
    public final String sitehash; // this is a domain hash, 6 bytes long or null
    public final String authorhash;
    public final String tenant; 
    public yacySeed remotepeer;
    public final Long handle;
    // values that are set after a search:
    public int resultcount; // number of found results
    public long searchtime, urlretrievaltime, snippetcomputationtime; // time to perform the search, to get all the urls, and to compute the snippets
    public boolean specialRights; // is true if the user has a special authorization and my use more database-extensive options
    
    public QueryParams(final String queryString,
    						 final int itemsPerPage,
    		                 final Bitfield constraint,
    		                 final Segment indexSegment,
                             final RankingProfile ranking) {
    	if ((queryString.length() == 12) && (Base64Order.enhancedCoder.wellformed(queryString.getBytes()))) {
    		this.queryString = null;
            this.queryHashes = new HandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.excludeHashes = new HandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            try {
                this.queryHashes.put(queryString.getBytes());
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
    	} else {
    		this.queryString = queryString;
    		final TreeSet<String>[] cq = cleanQuery(queryString);
    		this.queryHashes = Word.words2hashesHandles(cq[0]);
    		this.excludeHashes = Word.words2hashesHandles(cq[1]);
    		this.fullqueryHashes = Word.words2hashesHandles(cq[2]);
    	}
    	this.ranking = ranking;
    	this.tenant = null;
        this.maxDistance = Integer.MAX_VALUE;
        this.urlMask = catchall_pattern;
        this.urlMask_isCatchall = true;
        this.prefer = matchnothing_pattern;
        this.prefer_isMatchnothing = true;
        this.contentdom = ContentDomain.ALL;
        this.itemsPerPage = itemsPerPage;
        this.offset = 0;
        this.targetlang = "en";
        this.domType = SEARCHDOM_LOCAL;
        this.zonecode = DigestURI.TLD_any_zone_filter;
        this.domMaxTargets = 0;
        this.constraint = constraint;
        this.allofconstraint = false;
        this.onlineSnippetFetch = false;
        this.host = null;
        this.sitehash = null;
        this.authorhash = null;
        this.remotepeer = null;
        this.handle = Long.valueOf(System.currentTimeMillis());
        this.specialRights = false;
        this.navigators = "all";
        this.indexSegment = indexSegment;
    }
    
    public QueryParams(
		final String queryString, final HandleSet queryHashes,
		final HandleSet excludeHashes, 
        final HandleSet fullqueryHashes,
        final String tenant,
        final int maxDistance, final String prefer, final ContentDomain contentdom,
        final String language,
        final String navigators,
        final boolean onlineSnippetFetch,
        final int itemsPerPage, final int offset, final String urlMask,
        final int domType, final int domMaxTargets,
        final Bitfield constraint, final boolean allofconstraint,
        final String site,
        final String authorhash,
        final int domainzone,
        final String host,
        final boolean specialRights,
        final Segment indexSegment,
        final RankingProfile ranking) {
		this.queryString = queryString;
		this.queryHashes = queryHashes;
		this.excludeHashes = excludeHashes;
		this.fullqueryHashes = fullqueryHashes;
		this.tenant = (tenant != null && tenant.length() == 0) ? null : tenant;
		this.ranking = ranking;
		this.maxDistance = maxDistance;
		this.contentdom = contentdom;
		this.itemsPerPage = Math.min((specialRights) ? 1000 : 100, itemsPerPage);
		this.offset = Math.min((specialRights) ? 10000 : 1000, offset);
		this.urlMask = Pattern.compile(urlMask);
        this.urlMask_isCatchall = this.urlMask.toString().equals(catchall_pattern.toString());
		this.prefer = Pattern.compile(prefer);
        this.prefer_isMatchnothing = this.prefer.toString().equals(matchnothing_pattern.toString());;
		assert language != null;
        this.targetlang = language;
        this.navigators = navigators;
        this.domType = domType;
        this.zonecode = domainzone;
		this.domMaxTargets = domMaxTargets;
		this.constraint = constraint;
		this.allofconstraint = allofconstraint;
		this.sitehash = site; assert site == null || site.length() == 6;
		this.authorhash = authorhash; assert authorhash == null || authorhash.length() > 0;
		this.onlineSnippetFetch = onlineSnippetFetch;
		this.host = host;
        this.remotepeer = null;
		this.handle = Long.valueOf(System.currentTimeMillis());
		this.specialRights = specialRights;
        this.indexSegment = indexSegment;
    }
    
    public Segment getSegment() {
        return this.indexSegment;
    }
    
    public int neededResults() {
        // the number of result lines that must be computed
        return this.offset + this.itemsPerPage;
    }
    
    public int displayResults() {
        // the number of result lines that are displayed at once (size of result page)
        return this.itemsPerPage;
    }
    
    public void setOffset(final int newOffset) {
        this.offset = newOffset;
    }
    
    public String contentdom() {
        return this.contentdom.toString();
    }
    
    public boolean isGlobal() {
        return this.domType != SEARCHDOM_LOCAL;
    }
    
    public boolean isLocal() {
        return this.domType == SEARCHDOM_LOCAL;
    }
    
    public static HandleSet hashes2Set(final String query) {
        final HandleSet keyhashes = new HandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        if (query == null) return keyhashes;
        for (int i = 0; i < (query.length() / Word.commonHashLength); i++) try {
            keyhashes.put(query.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength).getBytes());
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return keyhashes;
    }
    
    public static HandleSet hashes2Handles(final String query) {
        final HandleSet keyhashes = new HandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        if (query == null) return keyhashes;
        for (int i = 0; i < (query.length() / Word.commonHashLength); i++) try {
            keyhashes.put(query.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength).getBytes());
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return keyhashes;
    }
    
    public static String hashSet2hashString(final HandleSet hashes) {
        final byte[] bb = new byte[hashes.size() * Word.commonHashLength];
        int p = 0;
        for (byte[] b : hashes) {
            assert b.length == Word.commonHashLength : "hash = " + new String(b);
            System.arraycopy(b, 0, bb, p, Word.commonHashLength);
            p += Word.commonHashLength;
        }
        return new String(bb);
    }

    public static String anonymizedQueryHashes(final HandleSet hashes) {
        // create a more anonymized representation of a query hashes for logging
        final Iterator<byte[]> i = hashes.iterator();
        final StringBuilder sb = new StringBuilder(hashes.size() * (Word.commonHashLength + 2) + 2);
        sb.append("[");
        byte[] hash;
        if (i.hasNext()) {
            hash = i.next();
            sb.append(new String(hash).substring(0, 3)).append(".........");
        }
        while (i.hasNext()) {
            hash = i.next();
            sb.append(", ").append(new String(hash).substring(0, 3)).append(".........");
        }
        sb.append("]");
        return new String(sb);
    }
    
    protected static final boolean matches(final String text, final HandleSet keyhashes) {
    	// returns true if any of the word hashes in keyhashes appear in the String text
    	// to do this, all words in the string must be recognized and transcoded to word hashes
    	final HandleSet wordhashes = Word.words2hashesHandles(Condenser.getWords(text).keySet());
    	return SetTools.anymatch(wordhashes, keyhashes);
    }
    
    private static String seps = "'.,/&_"; static {seps += '"';}
    
    @SuppressWarnings("unchecked")
    public static TreeSet<String>[] cleanQuery(String querystring) {
        // returns three sets: a query set, a exclude set and a full query set
        final TreeSet<String> query = new TreeSet<String>(NaturalOrder.naturalComparator);
        final TreeSet<String> exclude = new TreeSet<String>(NaturalOrder.naturalComparator);
        final TreeSet<String> fullquery = new TreeSet<String>(NaturalOrder.naturalComparator);
        
        if ((querystring == null) || (querystring.length() == 0)) return new TreeSet[]{query, exclude, fullquery};
        
        // convert Umlaute
        querystring = AbstractScraper.stripAll(querystring).toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = querystring.indexOf(seps.charAt(i))) >= 0) { querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (" " + querystring.substring(c + 1)) : ""); }
        }
        
        String s;
        int l;
        // the string is clean now, but we must generate a set out of it
        final String[] a = querystring.split(" ");
        for (int i = 0; i < a.length; i++) {
        	if (a[i].startsWith("-")) {
        		exclude.add(a[i].substring(1));
        	} else {
        		while ((c = a[i].indexOf('-')) >= 0) {
        			s = a[i].substring(0, c);
        			l = s.length();
					if(l > 2) query.add(s);
        			if(l > 0) fullquery.add(s);
        			a[i] = a[i].substring(c + 1);
        		}
        		l = a[i].length();
				if (l > 2) query.add(a[i]);
        		if (l > 0) fullquery.add(a[i]);
        	}
        }
        return new TreeSet[]{query, exclude, fullquery};
    }
    
    public String queryString(final boolean encodeHTML) {
    	if(encodeHTML){
    		return CharacterCoding.unicode2html(this.queryString, true);
    	}
    	return this.queryString;
    }
    
    public TreeSet<String>[] queryWords() {
        return cleanQuery(this.queryString);
    }
    
    public void filterOut(final TreeSet<String> blueList) {
        // filter out words that appear in this set
    	// this is applied to the queryHashes
    	final HandleSet blues = Word.words2hashesHandles(blueList);
    	for (byte[] b: blues) queryHashes.remove(b);
    }

    public String id(final boolean anonymized) {
        // generate a string that identifies a search so results can be re-used in a cache
        String context =
            "*" + this.domType + 
            "*" + this.contentdom +
            "*" + this.zonecode +
            "*" + new String(Word.word2hash(this.ranking.toExternalString())) +
            "*" + this.prefer +
            "*" + this.urlMask +
            "*" + this.sitehash +
            "*" + this.authorhash +
            "*" + this.targetlang +
            "*" + this.constraint +
            "*" + this.maxDistance;
        if (anonymized) 
            return anonymizedQueryHashes(this.queryHashes) + "-" + anonymizedQueryHashes(this.excludeHashes) + context;
        else
            return hashSet2hashString(this.queryHashes) + "-" + hashSet2hashString(this.excludeHashes) + context;
    }
    
    /**
     * make a query anchor tag
     * @param page
     * @param display
     * @param theQuery
     * @param originalUrlMask
     * @param addToQuery
     * @return
     */
    public static String navurl(String ext, final int page, final int display, final QueryParams theQuery, final String originalUrlMask, String addToQuery, String nav) {
        return
        "/yacysearch." + ext + "?display=" + display +
        "&query=" + theQuery.queryString(true).replace(' ', '+') + ((addToQuery == null) ? "" : "+" + addToQuery) +
        "&maximumRecords="+ theQuery.displayResults() +
        "&startRecord=" + (page * theQuery.displayResults()) +
        "&resource=" + ((theQuery.isLocal()) ? "local" : "global") +
        "&verify=" + ((theQuery.onlineSnippetFetch) ? "true" : "false") +
        "&nav=" + nav +
        "&urlmaskfilter=" + originalUrlMask +
        "&prefermaskfilter=" + theQuery.prefer +
        "&cat=href&amp;constraint=" + ((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64()) +
        "&contentdom=" + theQuery.contentdom() +
        "&former=" + theQuery.queryString(true);
    }
}
