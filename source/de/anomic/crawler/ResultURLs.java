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
import java.util.LinkedList;
import java.util.List;

import de.anomic.index.indexURLReference;
import de.anomic.kelondro.kelondroBitfield;
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

    public ResultURLs() {
        // init result stacks
        externResultStack = new LinkedList<String>();
        searchResultStack = new LinkedList<String>();
        transfResultStack = new LinkedList<String>();
        proxyResultStack  = new LinkedList<String>();
        lcrawlResultStack = new LinkedList<String>();
        gcrawlResultStack = new LinkedList<String>();
    }

    public synchronized void stack(final indexURLReference e, final String initiatorHash, final String executorHash, final int stackType) {
        assert initiatorHash != null;
        assert executorHash != null;
        if (e == null) { return; }
        try {
            final List<String> resultStack = getStack(stackType);
            if(resultStack != null) {
                resultStack.add(e.hash() + initiatorHash + executorHash);
            }
            return;
        } catch (final Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
    }

    public synchronized void notifyGCrawl(final String urlHash, final String initiatorHash, final String executorHash) {
        gcrawlResultStack.add(urlHash + initiatorHash + executorHash);
    }
    
    public synchronized int getStackSize(final int stack) {
        final List<String> resultStack = getStack(stack);
        if(resultStack != null) {
            return resultStack.size();
        } else {
            return -1;
        }
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
            if(yacySeedDB.commonHashLength * 3 <= result.length()) {
                return result.substring(yacySeedDB.commonHashLength * index, yacySeedDB.commonHashLength * (index + 1));
            } else {
                serverLog.logSevere("ResultURLs", "unexpected error: result of stack is too short: "+ result.length());
                if(yacySeedDB.commonHashLength * 2 < result.length()) {
                    // return what is there
                    return result.substring(yacySeedDB.commonHashLength * 2);
                } else {
                    return null;
                }
            }
        } else if(isValidStack(stack)) {
            serverLog.logSevere("ResultURLs", "unexpected error: result of stack is null: "+ stack +","+ pos);
        }
        return null;
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
        if(resultStack != null) {
            assert pos < resultStack.size() : "pos = " + pos + ", resultStack.size() = " + resultStack.size();
            if(pos < resultStack.size()) {
                return resultStack.get(pos);
            } else {
                serverLog.logSevere("ResultURLs", "unexpected error: Index out of Bounds "+ pos +" of "+ resultStack.size());
            }
        }
        return null;
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
//        Object prevElement = null;
//        switch (stack) {
//            case 1: prevElement = externResultStack.remove(pos); break;
//            case 2: prevElement = searchResultStack.remove(pos); break;
//            case 3: prevElement = transfResultStack.remove(pos); break;
//            case 4: prevElement = proxyResultStack.remove(pos); break;
//            case 5: prevElement = lcrawlResultStack.remove(pos); break;
//            case 6: prevElement = gcrawlResultStack.remove(pos); break;
//        }
//        return prevElement != null;
        final List<String> resultStack = getStack(stack);
        if(resultStack != null) {
            return resultStack.remove(pos) != null;
        } else {
            return false;
        }
    }

    public synchronized void clearStack(final int stack) {
        final List<String> resultStack = getStack(stack);
        if(resultStack != null) {
            resultStack.clear();
        }
//        switch (stack) {
//            case 1: externResultStack.clear(); break;
//            case 2: searchResultStack.clear(); break;
//            case 3: transfResultStack.clear(); break;
//            case 4: proxyResultStack.clear(); break;
//            case 5: lcrawlResultStack.clear(); break;
//            case 6: gcrawlResultStack.clear(); break;
//        }
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
