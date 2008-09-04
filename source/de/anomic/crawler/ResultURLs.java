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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public final class ResultURLs {

    // result stacks;
    // these have all entries of form
    // strings: urlHash + initiatorHash + ExecutorHash
    private final LinkedList<String> externResultStack; // 1 - remote index: retrieved by other peer
    private final LinkedList<String> searchResultStack; // 2 - partly remote/local index: result of search queries
    private final LinkedList<String> transfResultStack; // 3 - partly remote/local index: result of index transfer
    private final LinkedList<String> proxyResultStack;  // 4 - local index: result of proxy fetch/prefetch
    private final LinkedList<String> lcrawlResultStack; // 5 - local index: result of local crawling
    private final LinkedList<String> gcrawlResultStack; // 6 - local index: triggered external

    private final kelondroMScoreCluster<String> externResultDomains;
    private final kelondroMScoreCluster<String> searchResultDomains;
    private final kelondroMScoreCluster<String> transfResultDomains;
    private final kelondroMScoreCluster<String> proxyResultDomains;
    private final kelondroMScoreCluster<String> lcrawlResultDomains;
    private final kelondroMScoreCluster<String> gcrawlResultDomains;

    public ResultURLs() {
        // init result stacks
        externResultStack = new LinkedList<String>();
        searchResultStack = new LinkedList<String>();
        transfResultStack = new LinkedList<String>();
        proxyResultStack  = new LinkedList<String>();
        lcrawlResultStack = new LinkedList<String>();
        gcrawlResultStack = new LinkedList<String>();
        // init result domain statistics
        externResultDomains = new kelondroMScoreCluster<String>();
        searchResultDomains = new kelondroMScoreCluster<String>();
        transfResultDomains = new kelondroMScoreCluster<String>();
        proxyResultDomains = new kelondroMScoreCluster<String>();
        lcrawlResultDomains = new kelondroMScoreCluster<String>();
        gcrawlResultDomains = new kelondroMScoreCluster<String>();
    }

    public synchronized void stack(final indexURLReference e, final String initiatorHash, final String executorHash, final int stackType) {
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
            final kelondroMScoreCluster<String> domains = getDomains(stackType);
            if (domains != null) {
                domains.incScore(e.comp().url().getHost());
            }
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/3: " + ex.toString());
            return;
        }
    }
    
    public synchronized int getStackSize(final int stack) {
        final List<String> resultStack = getStack(stack);
        if(resultStack == null) {
            return -1;
        }
        return resultStack.size();
    }

    public synchronized String getUrlHash(final int stack, final int pos) {
        return getHashNo(stack, pos, 0);
    }

    public synchronized String getInitiatorHash(final int stack, final int pos) {
        return getHashNo(stack, pos, 1);
    }

    public synchronized String getExecutorHash(final int stack, final int pos) {
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
    public synchronized String getHashNo(final int stack, final int pos, final int index) {
        final String result = getResultStackAt(stack, pos);
        if(result != null) {
            if(result.length() < yacySeedDB.commonHashLength * 3) {
                serverLog.logSevere("ResultURLs", "unexpected error: result of stack is too short: "+ result.length());
                if(result.length() <= yacySeedDB.commonHashLength * 2) {
                    return null;
                }
                // return what is there
                return result.substring(yacySeedDB.commonHashLength * 2);
            }
            return result.substring(yacySeedDB.commonHashLength * index, yacySeedDB.commonHashLength * (index + 1));
        } else if(isValidStack(stack)) {
            serverLog.logSevere("ResultURLs", "unexpected error: result of stack is null: "+ stack +","+ pos);
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
    private String getResultStackAt(final int stack, final int pos) {
        assert pos >= 0 : "precondition violated: " + pos + " >= 0";
        
        final List<String> resultStack = getStack(stack);
        
        if(resultStack == null) {
            return null;
        }
        assert pos < resultStack.size() : "pos = " + pos + ", resultStack.size() = " + resultStack.size();
        if(pos >= resultStack.size()) {
            serverLog.logSevere("ResultURLs", "unexpected error: Index out of Bounds "+ pos +" of "+ resultStack.size());
            return null;
        }
        
        return resultStack.get(pos);
    }

    /**
     * iterate all domains in the result domain statistic
     * @return iterator of domains in reverse order (downwards)
     */
    public Iterator<String> domains(final int stack) {
        return getDomains(stack).scores(false);
    }
    
    /**
     * return the count of the domain
     * @param stack type
     * @param domain name
     * @return the number of occurrences of the domain in the stack statistics
     */
    public int domainCount(final int stack, String domain) {
        return getDomains(stack).getScore(domain);
    }
    
    /**
     * returns the stack indentified by the id <em>stack</em>
     * 
     * @param stack id of resultStack
     * @return null if stack does not exist (id is unknown or stack is null (which should not occur and an error is logged))
     */
    private List<String> getStack(final int stack) {
        switch (stack) {
            case 1: return externResultStack;
            case 2: return searchResultStack;
            case 3: return transfResultStack;
            case 4: return proxyResultStack;
            case 5: return lcrawlResultStack;
            case 6: return gcrawlResultStack;
            default:
                return null;
        }
    }
    private kelondroMScoreCluster<String> getDomains(final int stack) {
        switch (stack) {
            case 1: return externResultDomains;
            case 2: return searchResultDomains;
            case 3: return transfResultDomains;
            case 4: return proxyResultDomains;
            case 5: return lcrawlResultDomains;
            case 6: return gcrawlResultDomains;
            default:
                return null;
        }
    }
    
    /**
     * tests if a stack with id <em>stack</em> exists
     * 
     * @param stack
     * @return
     */
    private boolean isValidStack(final int stack) {
        return getStack(stack) != null;
    }

    public synchronized boolean removeStack(final int stack, final int pos) {
        final List<String> resultStack = getStack(stack);
        if(resultStack == null) {
            return false;
        }
        return resultStack.remove(pos) != null;
    }

    public synchronized void clearStack(final int stack) {
        final List<String> resultStack = getStack(stack);
        if (resultStack != null) resultStack.clear();
        final kelondroMScoreCluster<String> resultDomains = getDomains(stack);
        if (resultDomains != null) resultDomains.clear();
        
    }

    public synchronized boolean remove(final String urlHash) {
        if (urlHash == null) return false;
        for (int stack = 1; stack <= 6; stack++) {
            for (int i = getStackSize(stack) - 1; i >= 0; i--) {
                if (getUrlHash(stack, i).equals(urlHash)) {
                    removeStack(stack, i);
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
        final ResultURLs results = new ResultURLs();
        try {
            final yacyURL url = new yacyURL("http", "www.yacy.net", 80, "/");
            final indexURLReference urlRef = new indexURLReference(url, "YaCy Homepage", "", "", "", new Date(), new Date(), new Date(), "", new byte[] {}, 123, 42, '?', new kelondroBitfield(), "de", 0, 0, 0, 0, 0, 0);
            int stackNo = 1;
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
            stackNo = 42;
            System.out.println("size of stack:\t"+ results.getStackSize(stackNo));
            // get
            System.out.println("url hash:\t"+ results.getUrlHash(stackNo, 0));
            System.out.println("executor hash:\t"+ results.getExecutorHash(stackNo, 0));
            System.out.println("initiator hash:\t"+ results.getInitiatorHash(stackNo, 0));
            
            // benchmark
            final long start = System.currentTimeMillis();
            for(int i = 0; i < 1000000; i++) {
                stackNo = i % 6;
                // add
                results.stack(urlRef, urlRef.hash(), url.hash(), stackNo);
                // size
                results.getStackSize(stackNo);
                // get
                for(int j = 0; j < 10; j++) {
                    results.getUrlHash(stackNo, i / 6);
                    results.getExecutorHash(stackNo, i / 6);
                    results.getInitiatorHash(stackNo, i / 6);
                }
            }
            System.out.println("benschmark: "+ (System.currentTimeMillis() - start) + " ms");
        } catch (final MalformedURLException e) {
            e.printStackTrace();
        }
    }

}
