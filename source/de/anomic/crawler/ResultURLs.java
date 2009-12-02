// plasmaCrawlLURL.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2004
//
// $LastChangedDate: 2008-03-16 23:31:54 +0100 (So, 16 Mrz 2008) $
// $LastChangedRevision: 4575 $
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

/*
   This class provides storage functions for the plasma search engine.
   - the url-specific properties, including condenser results
   - the text content of the url
   Both entities are accessed with a hash, which is based on the MD5
   algorithm. The MD5 is not encoded as a hex value, but a b64 value.
*/

package de.anomic.crawler;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.ScoreCluster;

import de.anomic.crawler.retrieval.EventOrigin;
import de.anomic.yacy.yacySeedDB;

public final class ResultURLs {

    // result stacks;
    // these have all entries of form
    // strings: urlHash + initiatorHash + ExecutorHash
    private final Map<EventOrigin, LinkedList<String>> resultStacks;
    private final Map<EventOrigin, ScoreCluster<String>> resultDomains;

    public ResultURLs(int initialStackCapacity) {
        // init result stacks
        resultStacks = new HashMap<EventOrigin, LinkedList<String>>(initialStackCapacity);
        resultDomains = new HashMap<EventOrigin, ScoreCluster<String>>(initialStackCapacity);
        for (EventOrigin origin: EventOrigin.values()) {
            resultStacks.put(origin, new LinkedList<String>());
            resultDomains.put(origin, new ScoreCluster<String>());
        }
    }

    public synchronized void stack(final URIMetadataRow e, final String initiatorHash, final String executorHash, final EventOrigin stackType) {
        assert initiatorHash != null;
        assert executorHash != null;
        if (e == null) { return; }
        try {
            final List<String> resultStack = getStack(stackType);
            if (resultStack != null) {
                resultStack.add(e.hash() + initiatorHash + executorHash);
            }
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
        try {
            final ScoreCluster<String> domains = getDomains(stackType);
            if (domains != null) {
                domains.incScore(e.metadata().url().getHost());
            }
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/3: " + ex.toString());
            return;
        }
    }
    
    public synchronized int getStackSize(final EventOrigin stack) {
        final List<String> resultStack = getStack(stack);
        if (resultStack == null) return 0;
        return resultStack.size();
    }
    
    public synchronized int getDomainListSize(final EventOrigin stack) {
        final ScoreCluster<String> domains = getDomains(stack);
        if (domains == null) return 0;
        return domains.size();
    }

    public synchronized String getUrlHash(final EventOrigin stack, final int pos) {
        return getHashNo(stack, pos, 0);
    }

    public synchronized String getInitiatorHash(final EventOrigin stack, final int pos) {
        return getHashNo(stack, pos, 1);
    }

    public synchronized String getExecutorHash(final EventOrigin stack, final int pos) {
        return getHashNo(stack, pos, 2);
    }
    
    /**
     * gets the hash at <em>index</em> in element at <em>pos</em> in <em>stack</em> (based on {@link yacySeedDB#commonHashLength}) 
     * 
     * <p>simplified example with {@link yacySeedDB#commonHashLength} = 3:</p>
     * <code>String[][] stacks[1][0] = "123456789";
     * System.out.println(getHashNo(1, 0, 0));
     * System.out.println(getHashNo(1, 0, 0));
     * System.out.println(getHashNo(1, 0, 0));</code>
     * <p>Output:
     * 123<br/>
     * 456<br/>
     * 789</p>
     * 
     * @param stack
     * @param pos
     * @param index starting at 0
     * @return
     */
    public synchronized String getHashNo(final EventOrigin stack, final int pos, final int index) {
        final String result = getResultStackAt(stack, pos);
        if(result != null) {
            if(result.length() < Word.commonHashLength * 3) {
                Log.logSevere("ResultURLs", "unexpected error: result of stack is too short: "+ result.length());
                if(result.length() <= Word.commonHashLength * 2) {
                    return null;
                }
                // return what is there
                return result.substring(Word.commonHashLength * 2);
            }
            return result.substring(Word.commonHashLength * index, Word.commonHashLength * (index + 1));
        } else if(isValidStack(stack)) {
            Log.logSevere("ResultURLs", "unexpected error: result of stack is null: "+ stack +","+ pos);
        }
        return result;
    }

    /**
     * gets the element at pos in stack
     * 
     * @param stack
     * @param pos
     * @return null if either stack or element do not exist
     */
    private String getResultStackAt(final EventOrigin stack, final int pos) {
        assert pos >= 0 : "precondition violated: " + pos + " >= 0";
        
        final List<String> resultStack = getStack(stack);
        
        if(resultStack == null) {
            return null;
        }
        assert pos < resultStack.size() : "pos = " + pos + ", resultStack.size() = " + resultStack.size();
        if(pos >= resultStack.size()) {
            Log.logSevere("ResultURLs", "unexpected error: Index out of Bounds "+ pos +" of "+ resultStack.size());
            return null;
        }
        
        return resultStack.get(pos);
    }

    /**
     * iterate all domains in the result domain statistic
     * @return iterator of domains in reverse order (downwards)
     */
    public Iterator<String> domains(final EventOrigin stack) {
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).scores(false);
    }
    
    public int deleteDomain(final EventOrigin stack, String host, String hosthash) {
        assert hosthash.length() == 6;
        int i = 0;
        while (i < getStackSize(stack)) {
            if (getUrlHash(stack, i).substring(6).equals(hosthash)) getStack(stack).remove(i); else i++;
        }
        assert host != null : "host = null";
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).deleteScore(host);
    }
    
    /**
     * return the count of the domain
     * @param stack type
     * @param domain name
     * @return the number of occurrences of the domain in the stack statistics
     */
    public int domainCount(final EventOrigin stack, String domain) {
        assert domain != null : "domain = null";
        assert getDomains(stack) != null : "getDomains(" + stack + ") = null";
        return getDomains(stack).getScore(domain);
    }
    
    /**
     * returns the stack identified by the id <em>stack</em>
     * 
     * @param stack id of resultStack
     * @return null if stack does not exist (id is unknown or stack is null (which should not occur and an error is logged))
     */
    private List<String> getStack(final EventOrigin stack) {
        return resultStacks.get(stack);
    }
    private ScoreCluster<String> getDomains(final EventOrigin stack) {
        return resultDomains.get(stack);
    }
    
    /**
     * tests if a stack with id <em>stack</em> exists
     * 
     * @param stack
     * @return
     */
    private boolean isValidStack(final EventOrigin stack) {
        return getStack(stack) != null;
    }

    public synchronized boolean removeStack(final EventOrigin stack, final int pos) {
        final List<String> resultStack = getStack(stack);
        if (resultStack == null) {
            return false;
        }
        return resultStack.remove(pos) != null;
    }

    public synchronized void clearStack(final EventOrigin stack) {
        final List<String> resultStack = getStack(stack);
        if (resultStack != null) resultStack.clear();
        final ScoreCluster<String> resultDomains = getDomains(stack);
        if (resultDomains != null) {
            // we do not clear this completely, just remove most of the less important entries
            resultDomains.shrinkToMaxSize(100);
            resultDomains.shrinkToMinScore(2);
        }
    }

    public synchronized boolean remove(final String urlHash) {
        if (urlHash == null) return false;
        String hash;
        for (EventOrigin origin: EventOrigin.values()) {
            for (int i = getStackSize(origin) - 1; i >= 0; i--) {
                hash = getUrlHash(origin, i);
                if (hash != null && hash.equals(urlHash)) {
                    removeStack(origin, i);
                    return true;
                }
            }
        }
        return true;
    }
    
    /**
     * test and benchmark
     * @param args
     */
    public static void main(final String[] args) {
        final ResultURLs results = new ResultURLs(10);
        try {
            final DigestURI url = new DigestURI("http", "www.yacy.net", 80, "/");
            final URIMetadataRow urlRef = new URIMetadataRow(url, "YaCy Homepage", "", "", "", new Date(), new Date(), new Date(), "", new byte[] {}, 123, 42, '?', new Bitfield(), "de", 0, 0, 0, 0, 0, 0);
            EventOrigin stackNo = EventOrigin.LOCAL_CRAWLING;
            System.out.println("valid test:\n=======");
            // add
            results.stack(urlRef, urlRef.hash(), url.hash(), stackNo);
            // size
            System.out.println("size of stack:\t"+ results.getStackSize(stackNo));
            // get
            System.out.println("url hash:\t"+ results.getUrlHash(stackNo, 0));
            System.out.println("executor hash:\t"+ results.getExecutorHash(stackNo, 0));
            System.out.println("initiator hash:\t"+ results.getInitiatorHash(stackNo, 0));
            // test errors
            System.out.println("invalid test:\n=======");
            // get
            System.out.println("url hash:\t"+ results.getUrlHash(stackNo, 1));
            System.out.println("executor hash:\t"+ results.getExecutorHash(stackNo, 1));
            System.out.println("initiator hash:\t"+ results.getInitiatorHash(stackNo, 1));
        } catch (final MalformedURLException e) {
            Log.logException(e);
        }
    }

}
