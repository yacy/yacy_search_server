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

/*
   This class provides storage functions for the plasma search engine.
   - the url-specific properties, including condenser results
   - the text content of the url
   Both entities are accessed with a hash, which is based on the MD5
   algorithm. The MD5 is not encoded as a hex value, but a b64 value.
*/

package de.anomic.crawler;

import java.util.LinkedList;

import de.anomic.index.indexURLReference;
import de.anomic.yacy.yacySeedDB;

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

    public synchronized void stack(indexURLReference e, String initiatorHash, String executorHash, int stackType) {
        assert initiatorHash != null;
        assert executorHash != null;
        if (e == null) { return; }
        try {
            switch (stackType) {
                case 0: break;
                case 1: externResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 2: searchResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 3: transfResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 4: proxyResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 5: lcrawlResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 6: gcrawlResultStack.add(e.hash() + initiatorHash + executorHash); break;
            }
            return;
        } catch (Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
    }

    public synchronized void notifyGCrawl(String urlHash, String initiatorHash, String executorHash) {
        gcrawlResultStack.add(urlHash + initiatorHash + executorHash);
    }
    
    public synchronized int getStackSize(int stack) {
        switch (stack) {
            case 1: return externResultStack.size();
            case 2: return searchResultStack.size();
            case 3: return transfResultStack.size();
            case 4: return proxyResultStack.size();
            case 5: return lcrawlResultStack.size();
            case 6: return gcrawlResultStack.size();
        }
        return -1;
    }

    public synchronized String getUrlHash(int stack, int pos) {
        switch (stack) {
            case 1: return (externResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 2: return (searchResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 3: return (transfResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 4: return (proxyResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 5: return (lcrawlResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 6: return (gcrawlResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
        }
        return null;
    }

    public synchronized String getInitiatorHash(int stack, int pos) {
        switch (stack) {
            case 1: return (externResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 2: return (searchResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 3: return (transfResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 4: return (proxyResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 5: return (lcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 6: return (gcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
        }
        return null;
    }

    public synchronized String getExecutorHash(int stack, int pos) {
        switch (stack) {
            case 1: return (externResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 2: return (searchResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 3: return (transfResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 4: return (proxyResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 5: return (lcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 6: return (gcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
        }
        return null;
    }

    public synchronized boolean removeStack(int stack, int pos) {
        Object prevElement = null;
        switch (stack) {
            case 1: prevElement = externResultStack.remove(pos); break;
            case 2: prevElement = searchResultStack.remove(pos); break;
            case 3: prevElement = transfResultStack.remove(pos); break;
            case 4: prevElement = proxyResultStack.remove(pos); break;
            case 5: prevElement = lcrawlResultStack.remove(pos); break;
            case 6: prevElement = gcrawlResultStack.remove(pos); break;
        }
        return prevElement != null;
    }

    public synchronized void clearStack(int stack) {
        switch (stack) {
            case 1: externResultStack.clear(); break;
            case 2: searchResultStack.clear(); break;
            case 3: transfResultStack.clear(); break;
            case 4: proxyResultStack.clear(); break;
            case 5: lcrawlResultStack.clear(); break;
            case 6: gcrawlResultStack.clear(); break;
        }
    }

    public synchronized boolean remove(String urlHash) {
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

}
