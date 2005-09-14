// yacyNewsQueue.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 13.07.2005
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroException;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;

public class yacyNewsQueue {
    
    private File path;
    private kelondroStack queueStack;
    private yacyNewsDB newsDB;
    
    public yacyNewsQueue(File path, yacyNewsDB newsDB) throws IOException {
        this.path = path;
        this.newsDB = newsDB;
        
        if (path.exists()) {
            try {
                queueStack = new kelondroStack(path, 0);
            } catch (kelondroException e) {
                path.delete();
                queueStack = createStack(path);
            }
        } else
            queueStack = createStack(path);
    }
    
    private static kelondroStack createStack(File path) throws IOException {
        return new kelondroStack(path, 0, new int[] {
                yacyNewsRecord.idLength(), // id = created + originator
                yacyCore.universalDateShortPattern.length() // last touched
            });
    }
    
    private void resetDB() throws IOException {
        try {close();} catch (Exception e) {}
        if (path.exists()) path.delete();
        queueStack = createStack(path);
    }
    
    public void close() {
        if (queueStack != null) try {queueStack.close();} catch (IOException e) {}
        queueStack = null;
    }

    public void finalize() {
        close();
    }
    
    public int size() {
        return queueStack.size();
    }
    
    public synchronized void push(yacyNewsRecord entry) throws IOException {
        queueStack.push(r2b(entry, true));
    }
    
    public synchronized yacyNewsRecord pop(int dist) throws IOException {
        if (queueStack.size() == 0) return null;
        return b2r(queueStack.pop(dist));
    }

    public synchronized yacyNewsRecord top(int dist) throws IOException {
        if (queueStack.size() == 0) return null;
        return b2r(queueStack.top(dist));
    }
    
    public synchronized yacyNewsRecord topInc() throws IOException {
        if (queueStack.size() == 0) return null;
        yacyNewsRecord entry = pop(0);
        if (entry != null) {
            entry.incDistribution();
            push(entry);
        }
        return entry;
    }
    
    /*
    public synchronized void incDistributedCounter(yacyNewsRecord entry) throws IOException {
        // this works only if the entry element lies ontop of the stack
        yacyNewsRecord topEntry = top();
        if (!(topEntry.id().equals(entry.id()))) throw new IllegalArgumentException("entry is not ontop of the stack");
        pop();
        entry.incDistribution();
        push(entry);
    }
    */
    
    private yacyNewsRecord b2r(byte[][] b) throws IOException {
        if (b == null) return null;
        String id = new String(b[0]);
        //Date touched = yacyCore.parseUniversalDate(new String(b[1]));
        return newsDB.get(id);
    }

    private byte[][] r2b(yacyNewsRecord r, boolean updateDB) throws IOException {
        if (r == null) return null;
        if (updateDB) {
            newsDB.put(r);
        } else {
            yacyNewsRecord r1 = newsDB.get(r.id());
            if (r1 == null) newsDB.put(r);
        }
        byte[][] b = new byte[2][];
        b[0] = r.id().getBytes();
        b[1] = yacyCore.universalDateShortString(new Date()).getBytes();
        return b;
    }
    
}
