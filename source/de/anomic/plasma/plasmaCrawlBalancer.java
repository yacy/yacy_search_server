// plasmaCrawlBalancer.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 24.09.2005
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public class plasmaCrawlBalancer {

    // a shared domainAccess map for all balancers
    private static final Map domainAccess = Collections.synchronizedMap(new HashMap());
    
    // definition of payload for fileStack
    private static final kelondroRow payload = new kelondroRow("byte[] urlhash-" + yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder, 0);
    
    // class variables
    private ArrayList     ramStack;     // a list that is flused first
    private kelondroStack fileStack;    // a file with url hashes
    private HashMap       domainStacks; // a map from domain name part to Lists with url hashs
    private HashSet       ramIndex;     // an index is needed externally, we provide that internally
    
    public plasmaCrawlBalancer(File stackFile) {
        fileStack    = kelondroStack.open(stackFile, payload);
        domainStacks = new HashMap();
        ramStack     = new ArrayList();
        ramIndex     = makeIndex();
    }

    public synchronized void close() {
        ramIndex = null;
        while (sizeDomainStacks() > 0) flushOnceDomStacks(true);
        try { flushAllRamStack(); } catch (IOException e) {}
        fileStack.close();
        fileStack = null;
    }
    
    public void finalize() {
        if (fileStack != null) close();
    }
    
    public synchronized void clear() {
        fileStack = kelondroStack.reset(fileStack);
        domainStacks.clear();
        ramStack.clear();
        ramIndex = new HashSet();
    }
    
    private HashSet makeIndex() {
        HashSet index = new HashSet(); // TODO: replace with kelondroIndex
        
        // take all elements from the file stack
        try {
            Iterator i = fileStack.keyIterator(); // iterates byte[] - objects
            while (i.hasNext()) index.add(new String((byte[]) i.next(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {}
        
        // take elements from the ram stack
        for (int i = 0; i < ramStack.size(); i++) index.add(ramStack.get(i));
        
        // take elememts from domain stacks
        Iterator i = domainStacks.entrySet().iterator();
        Map.Entry entry;
        LinkedList list;
        Iterator ii;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            list = (LinkedList) entry.getValue();
            ii = list.iterator();
            while (ii.hasNext()) index.add(ii.next());
        }
        
        return index;
    }
    
    public boolean has(String urlhash) {
        return ramIndex.contains(urlhash);
    }
    
    public Iterator iterator() {
        return ramIndex.iterator();
    }
    
    public synchronized int size() {
        int componentsize = fileStack.size() + ramStack.size() + sizeDomainStacks();
        if ((kelondroRecords.debugmode) && (componentsize != ramIndex.size())) {
            // hier ist ramIndex.size() immer grš§er. warum?
            serverLog.logWarning("PLASMA BALANCER", "size operation wrong - componentsize = " + componentsize + ", ramIndex.size() = " + ramIndex.size());
        }        
        return componentsize;
    }
    
    private int sizeDomainStacks() {
        if (domainStacks == null) return 0;
        int sum = 0;
        Iterator i = domainStacks.values().iterator();
        while (i.hasNext()) sum += ((LinkedList) i.next()).size();
        return sum;
    }
    
    private void flushOnceDomStacks(boolean ram) {
        // takes one entry from every domain stack and puts it on the file stack
        if (domainStacks.size() == 0) return;
        Iterator i = domainStacks.entrySet().iterator();
        Map.Entry entry;
        LinkedList list;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            list = (LinkedList) entry.getValue();
            if (list.size() != 0) {
                if (ram) {
                    ramStack.add(list.removeFirst());
                } else try {
                    fileStack.push(fileStack.row().newEntry(new byte[][]{((String) list.removeFirst()).getBytes()}));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (list.size() == 0) i.remove();
        }
    }
    
    private void flushAllRamStack() throws IOException {
        // this flushes only the ramStack to the fileStack, but does not flush the domainStacks
        for (int i = 0; i < ramStack.size() / 2; i++) {
            fileStack.push(fileStack.row().newEntry(new byte[][]{((String) ramStack.get(i)).getBytes()}));
            fileStack.push(fileStack.row().newEntry(new byte[][]{((String) ramStack.get(ramStack.size() - i - 1)).getBytes()}));
        }
        if (ramStack.size() % 2 == 1) 
            fileStack.push(fileStack.row().newEntry(new byte[][]{((String) ramStack.get(ramStack.size() / 2)).getBytes()}));
    }
    
    public synchronized void push(String urlhash) throws IOException {
        assert urlhash != null;
        if (ramIndex.contains(urlhash)) {
            serverLog.logWarning("PLASMA BALANCER", "double-check has failed for urlhash " + urlhash + " - fixed");
            return;
        }
        String dom = urlhash.substring(6);
        LinkedList domainList = (LinkedList) domainStacks.get(dom);
        if (domainList == null) {
            // create new list
            domainList = new LinkedList();
            domainList.addLast(urlhash);
            domainStacks.put(dom, domainList);
        } else {
            // extend existent domain list
            domainList.add(urlhash);
        }
        
        // add to index
        ramIndex.add(urlhash);
        
        // check size of domainStacks and flush
        if ((domainStacks.size() > 20) || (sizeDomainStacks() > 1000)) {
            flushOnceDomStacks(false);
        }
    }
    
    public synchronized String pop(long minimumDelta, long maximumAge) throws IOException {
        // returns an url-hash from the stack and ensures minimum delta times
        // we have 3 sources to choose from: the ramStack, the domainStacks and the fileStack
        
        String result = null; // the result
        
        // 1st: check ramStack
        if (ramStack.size() > 0) {
            result = (String) ramStack.remove(0);
        }
        
        // 2nd-a: check domainStacks for latest arrivals
        if (result == null) {
            // we select specific domains that have not been used for a long time
            // i.e. 60 seconds. Latest arrivals that have not yet been crawled
            // fit also in that scheme
            Iterator i = domainStacks.entrySet().iterator();
            Map.Entry entry;
            String domhash;
            long delta, maxdelta = 0;
            String maxhash = null;
            LinkedList domlist;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                domhash = (String) entry.getKey();
                delta = lastAccessDelta(domhash);
                if (delta == Integer.MAX_VALUE) {
                    // a brand new domain - we take it
                    domlist = (LinkedList) entry.getValue();
                    result = (String) domlist.removeFirst();
                    if (domlist.size() == 0) i.remove();
                    break;
                }
                if (delta > maxdelta) {
                    maxdelta = delta;
                    maxhash = domhash;
                }
            }
            if (maxdelta > maximumAge) {
                // success - we found an entry from a domain that has not been used for a long time
                domlist = (LinkedList) domainStacks.get(maxhash);
                result = (String) domlist.removeFirst();
                if (domlist.size() == 0) domainStacks.remove(maxhash);
            }
        }
        
        // 2nd-b: check domainStacks for best match between stack size and retrieval time
        if (result == null) {
            // we order all domains by the number of entries per domain
            // then we iterate through these domains in descending entry order
            // and that that one, that has a delta > minimumDelta
            Iterator i = domainStacks.entrySet().iterator();
            Map.Entry entry;
            String domhash;
            LinkedList domlist;
            TreeMap hitlist = new TreeMap();
            int count = 0;
            // first collect information about sizes of the domain lists
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                domhash = (String) entry.getKey();
                domlist = (LinkedList) entry.getValue();
                hitlist.put(new Integer(domlist.size() * 100 + count++), domhash);
            }
            
            // now iterate in descending order an fetch that one,
            // that is acceptable by the minimumDelta constraint
            long delta;
            String maxhash = null;
            while (hitlist.size() > 0) {
                domhash = (String) hitlist.remove(hitlist.lastKey());
                if (maxhash == null) maxhash = domhash; // remember first entry
                delta = lastAccessDelta(domhash);
                if (delta > minimumDelta) {
                    domlist = (LinkedList) domainStacks.get(domhash);
                    result = (String) domlist.removeFirst();
                    if (domlist.size() == 0) domainStacks.remove(domhash);
                    break;
                }
            }
            
            // if we did yet not choose any entry, we simply take that one with the most entries
            if ((result == null) && (maxhash != null)) {
                domlist = (LinkedList) domainStacks.get(maxhash);
                result = (String) domlist.removeFirst();
                if (domlist.size() == 0) domainStacks.remove(maxhash);
            }
        }
        
        // 3rd: take entry from file
        if ((result == null) && (fileStack.size() > 0)) {
            kelondroRow.Entry topentry = fileStack.top();
            if (topentry == null) {
                // emergency case: this means that something with the stack organization is wrong
                // the file appears to be broken. We kill the file.
                kelondroStack.reset(fileStack);
                serverLog.logSevere("PLASMA BALANCER", "get() failed to fetch entry from file stack. reset stack file.");
            } else {
                String top = new String(topentry.getColBytes(0));

                // check if the time after retrieval of last hash from same
                // domain is not shorter than the minimumDelta
                long delta = lastAccessDelta(top);
                if (delta > minimumDelta) {
                    // the entry from top is fine
                    result = new String(fileStack.pop().getColBytes(0));
                } else {
                    // try entry from bottom
                    result = new String(fileStack.pot().getColBytes(0));
                    delta = lastAccessDelta(result);
                }
            }
        }
        
        // check case where we did not found anything
        if (result == null) {
            serverLog.logSevere("PLASMA BALANCER", "get() was not able to find a valid urlhash - total size = " + size() + ", fileStack.size() = " + fileStack.size() + ", ramStack.size() = " + ramStack.size() + ", domainStacks.size() = " + domainStacks.size());
            return null;
        }
        
        // finally: check minimumDelta and if necessary force a sleep
        long delta = lastAccessDelta(result);
        if (delta < minimumDelta) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protect against the worst case, where the crawler could
            // behave in a DoS-manner
            long sleeptime = minimumDelta - delta;
            try {synchronized(this) { this.wait(sleeptime); }} catch (InterruptedException e) {}
        }
        
        // update statistical data
        domainAccess.put(result.substring(6), new Long(System.currentTimeMillis()));
        ramIndex.remove(result);
        return result;
    }
    
    private long lastAccessDelta(String hash) {
        assert hash != null;
        Long lastAccess = (Long) domainAccess.get((hash.length() > 6) ? hash.substring(6) : hash);
        if (lastAccess == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - lastAccess.longValue();
    }
    
    public synchronized String top(int dist) {
        int availableInRam = ramStack.size() + sizeDomainStacks();
        if ((availableInRam < dist) && (fileStack.size() > (dist - availableInRam))) {
            // flush some entries from disc to domain stacks
            try {
                for (int i = 0; i < (dist - availableInRam); i++) {
                    ramStack.add(new String(fileStack.pop().getColBytes(0)));
                }
            } catch (IOException e) {}
        }
        while ((sizeDomainStacks() > 0) && (ramStack.size() <= dist)) flushOnceDomStacks(true); // flush only that much as we need to display
        if (dist >= ramStack.size()) return null;
        return (String) ramStack.get(dist);
    }
    
}
