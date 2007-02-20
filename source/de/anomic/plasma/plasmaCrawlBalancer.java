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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.yacy.yacySeedDB;

public class plasmaCrawlBalancer {

    private kelondroStack stack;
    private HashMap domainStacks;
    private HashMap domainAccess;
    
    public plasmaCrawlBalancer(File stackFile) {
        stack = kelondroStack.open(stackFile, new kelondroRow("byte[] urlhash-" + yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder, 0));
        domainStacks = new HashMap();
        domainAccess = new HashMap();
    }

    public void close() {
        try { flushSome(domainStacks.size()); } catch (IOException e) {}
        try { stack.close(); } catch (IOException e) {}
        stack = null;
    }
    
    public void reset() {
        synchronized (domainStacks) {
            stack = kelondroStack.reset(stack);
            domainStacks.clear();
        }
    }
    
    public Iterator iterator() {
        // iterates byte[] - objects
        return new KeyIterator(stack.iterator());
    }
    
    public int size() {
        return stack.size() + sizeDomainStacks();
    }
    
    private int sizeDomainStacks() {
        if (domainStacks == null) return 0;
        int sum = 0;
        synchronized (domainStacks) {
            Iterator i = domainStacks.values().iterator();
            while (i.hasNext()) sum += ((ArrayList) i.next()).size();
        }
        return sum;
    }
    
    private void flushOnce() throws IOException {
        // takes one entry from every domain stack and puts it on the file stack
        synchronized (domainStacks) {
            Iterator i = domainStacks.entrySet().iterator();
            Map.Entry entry;
            ArrayList list;
            while (i.hasNext()) {
                entry = (Map.Entry) i.next();
                list = (ArrayList) entry.getValue();
                if (list.size() != 0) {
                    stack.push(stack.row().newEntry(new byte[][]{(byte[]) list.remove(0)}));
                }
                if (list.size() == 0) i.remove();
            }
        }
    }
    
    private void flushSome(int count) throws IOException {
        while ((domainStacks.size() > 0) && (count-- > 0)) flushOnce();
    }
    
    public void add(String domain, byte[] hash) throws IOException {
        synchronized (domainStacks) {
            ArrayList domainList = (ArrayList) domainStacks.get(domain);
            if (domainList == null) {
                // create new list
                domainList = new ArrayList();
                domainList.add(hash);
                domainStacks.put(domain, domainList);
            } else {
                // extend existent domain list
                domainList.add(hash);
            }
        }
        
        // check size of domainStacks and flush
        if ((domainStacks.size() > 20) || (sizeDomainStacks() > 400)) {
            flushOnce();
        }
    }
    
    public String get(long minimumDelta) throws IOException {
        // returns an url-hash from the stack and ensures minimum delta times
        synchronized (domainStacks) {
            if ((stack.size() == 0) && (domainStacks.size() > 0)) flushOnce();
            if (stack.size() == 0) return null;
            
            String entry = null;
            kelondroRow.Entry topentry = stack.top();
            if (topentry == null) return null;
            String top = new String(topentry.getColBytes(0));

            // check if the time after retrieval of last hash from same
            // domain is not shorter than the minimumDelta
            long delta = lastAccessDelta(top);
            if (delta > minimumDelta) {
                // the entry from top is fine
                entry = new String(stack.pop().getColBytes(0));
            } else {
                // try entry from bottom
                entry = new String(stack.pot().getColBytes(0));
                delta = lastAccessDelta(entry);
            }
            
            if (delta < minimumDelta) {
                // force a busy waiting here
                // in best case, this should never happen if the balancer works propertly
                // this is only to protect against the worst case, where the crawler could
                // behave in a DoS-manner
                long sleeptime = minimumDelta - delta;
                try {synchronized(this) { this.wait(sleeptime); }} catch (InterruptedException e) {}
            }
            domainAccess.put(entry.substring(6), new Long(System.currentTimeMillis()));
            return entry;
        }
    }
    
    private long lastAccessDelta(String urlhash) {
        assert urlhash != null;
        Long lastAccess = (Long) domainAccess.get(urlhash.substring(6));
        if (lastAccess == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - lastAccess.longValue();
    }
    
    public byte[] top(int dist) throws IOException {
        flushSome(1 + dist - stack.size()); // flush only that much as we need to display
        synchronized (domainStacks) {
            return stack.top(dist).getColBytes(0);
        }
    }
    
    public void clear() throws IOException {
        synchronized (domainStacks) {
            domainStacks.clear();
            stack = kelondroStack.reset(stack);
        }
    }
    
    public class KeyIterator implements Iterator {

        Iterator ni;
        
        public KeyIterator(Iterator i) {
            ni = i;
        }
        
        public boolean hasNext() {
            return ni.hasNext();
        }
        
        public Object next() {
            return ((kelondroRecords.Node) ni.next()).getKey();
        }
        
        public void remove() {
        }
        
    }
    
}
