// yacyNewsQueue.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.server.serverDate;

public class yacyNewsQueue {

    private final File path;
    kelondroStack queueStack;
    private final yacyNewsDB newsDB;
    
    public static final kelondroRow rowdef = new kelondroRow(new kelondroColumn[]{
            new kelondroColumn("newsid", kelondroColumn.celltype_string, kelondroColumn.encoder_bytes, yacyNewsRecord.idLength, "id = created + originator"),
            new kelondroColumn("last touched", kelondroColumn.celltype_string, kelondroColumn.encoder_bytes, serverDate.PATTERN_SHORT_SECOND.length(), "")
        },
        kelondroNaturalOrder.naturalOrder, 0
    );

    public yacyNewsQueue(final File path, final yacyNewsDB newsDB) {
        this.path = path;
        this.newsDB = newsDB;
        this.queueStack = kelondroStack.open(path, rowdef);
    }

    private void resetDB() {
        try {close();} catch (final Exception e) {}
        if (path.exists()) path.delete();
        queueStack = kelondroStack.open(path, rowdef);
    }

    public void clear() {
        resetDB();
    }

    public void close() {
        if (queueStack != null) queueStack.close();
        queueStack = null;
    }

    protected void finalize() {
        close();
    }

    public int size() {
        return queueStack.size();
    }

    public synchronized void push(final yacyNewsRecord entry) throws IOException {
        queueStack.push(r2b(entry, true));
    }

    public synchronized yacyNewsRecord pop() throws IOException {
        if (queueStack.size() == 0) return null;
        return b2r(queueStack.pop());
    }

    public synchronized yacyNewsRecord top() throws IOException {
        if (queueStack.size() == 0) return null;
        return b2r(queueStack.top());
    }

    public synchronized yacyNewsRecord topInc() throws IOException {
        if (queueStack.size() == 0) return null;
        final yacyNewsRecord entry = pop();
        if (entry != null) {
            entry.incDistribution();
            push(entry);
        }
        return entry;
    }

    public synchronized yacyNewsRecord get(final String id) {
        yacyNewsRecord record;
        final Iterator<yacyNewsRecord> i = records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) return record;
        }
        return null;
    }

    public synchronized yacyNewsRecord remove(final String id) {
        yacyNewsRecord record;
        final Iterator<yacyNewsRecord> i = records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) {
                i.remove();
                return record;
            }
        }
        return null;
    }

    yacyNewsRecord b2r(final kelondroRow.Entry b) throws IOException {
        if (b == null) return null;
        final String id = b.getColString(0, null);
        //Date touched = yacyCore.parseUniversalDate(new String(b[1]));
        return newsDB.get(id);
    }

    private kelondroRow.Entry r2b(final yacyNewsRecord r, final boolean updateDB) throws IOException {
        if (r == null) return null;
        if (updateDB) {
            newsDB.put(r);
        } else {
            final yacyNewsRecord r1 = newsDB.get(r.id());
            if (r1 == null) newsDB.put(r);
        }
        final kelondroRow.Entry b = queueStack.row().newEntry(new byte[][]{
                r.id().getBytes(),
                serverDate.formatShortSecond(new Date()).getBytes()});
        return b;
    }
    
    public Iterator<yacyNewsRecord> records(final boolean up) {
        // iterates yacyNewsRecord-type objects
        if (queueStack == null) return new HashSet<yacyNewsRecord>().iterator();
        return new newsIterator(up);
    }
    
    public class newsIterator implements Iterator<yacyNewsRecord> {
        // iterates yacyNewsRecord-type objects
        
        Iterator<kelondroRow.Entry> stackNodeIterator;
        
        public newsIterator(final boolean up) {
            stackNodeIterator = queueStack.stackIterator(up);
        }
        
        public boolean hasNext() {
            return stackNodeIterator.hasNext();
        }

        public yacyNewsRecord next() {
            final kelondroRow.Entry row = stackNodeIterator.next();
            try {
                return b2r(row);
            } catch (final IOException e) {
                return null;
            }
        }

        public void remove() {
            stackNodeIterator.remove();
        }
        
    }

}