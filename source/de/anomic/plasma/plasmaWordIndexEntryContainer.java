// plasmaIndexEntryContainer.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 07.05.2005
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
import de.anomic.server.serverCodings;

public class plasmaWordIndexEntryContainer implements Comparable {

    private String wordHash;
    private HashMap container;
    
    public plasmaWordIndexEntryContainer(String wordHash) {
        this.wordHash = wordHash;
        container = new HashMap(); // a urlhash/plasmaWordIndexEntry - relation
    }
    
    public int size() {
        return container.size();
    }
    
    public String wordHash() {
        return wordHash;
    }

    public boolean add(plasmaWordIndexEntry entry) {
        // returns true if the new entry was added, false if it already existet
        String urlHash = entry.getUrlHash();
        if (container.containsKey(urlHash)) return false;
        container.put(urlHash, entry);
        return true;
    }
    
    public int add(plasmaWordIndexEntryContainer c) {
        // returns the number of new elements
        Iterator i = c.entries();
        int x = 0;
        while (i.hasNext()) {
            if (add((plasmaWordIndexEntry) i.next())) x++;
        }
        return x;
    }
    
    public boolean contains(String urlHash) {
        return container.containsKey(urlHash);
    }
    
    public plasmaWordIndexEntry getOne() {
        return (plasmaWordIndexEntry) container.values().toArray()[0];
    }
    
    public Iterator entries() {
        // returns an iterator of plasmaWordIndexEntry objects
        return container.values().iterator();
    }
    
    public static plasmaWordIndexEntryContainer instantContainer(String wordHash, plasmaWordIndexEntry entry) {
        plasmaWordIndexEntryContainer c = new plasmaWordIndexEntryContainer(wordHash);
        c.add(entry);
        return c;
    }
    
    public String toString() {
        return "C[" + wordHash + "] has " + container.size() + " entries";
    }
    
    public int compareTo(Object obj) {
        plasmaWordIndexEntryContainer other = (plasmaWordIndexEntryContainer) obj;
        return this.wordHash.compareTo(other.wordHash);
    }

    public int hashCode() {
        return (int) serverCodings.enhancedCoder.decodeBase64Long(this.wordHash.substring(0, 4));
    }
    
}
