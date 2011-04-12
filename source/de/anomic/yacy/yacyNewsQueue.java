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
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;


public class yacyNewsQueue {

    private final File path;
    private Table queueStack;
    private final yacyNewsDB newsDB;
    
    private static final Row rowdef = new Row(new Column[]{
            new Column("newsid", Column.celltype_string, Column.encoder_bytes, yacyNewsDB.idLength, "id = created + originator"),
            new Column("last touched", Column.celltype_string, Column.encoder_bytes, GenericFormatter.PATTERN_SHORT_SECOND.length(), "")
        },
        NaturalOrder.naturalOrder
    );

    public yacyNewsQueue(final File path, final yacyNewsDB newsDB) {
        this.path = path;
        this.newsDB = newsDB;
        try {
            this.queueStack = new Table(path, rowdef, 10, 0, false, false);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            this.queueStack = null;
        }
    }
    
    public void clear() {
        try {
            this.queueStack.clear();
        } catch (IOException e) {
            try {close();} catch (final Exception ee) {}
            if (path.exists()) FileUtils.deletedelete(path);
            try {
                this.queueStack = new Table(path, rowdef, 10, 0, false, false);
            } catch (RowSpaceExceededException ee) {
                Log.logException(e);
                this.queueStack = null;
            }
        }
    }

    public void close() {
        if (queueStack != null) queueStack.close();
        queueStack = null;
    }

    @Override
    protected void finalize() {
        close();
    }

    public int size() {
        return queueStack.size();
    }
    
    public boolean isEmpty() {
        return queueStack.isEmpty();
    }

    public synchronized void push(final yacyNewsDB.Record entry) throws IOException, RowSpaceExceededException {
        if (!queueStack.consistencyCheck()) {
            Log.logSevere("yacyNewsQueue", "reset of table " + this.path);
            queueStack.clear();
        }
        queueStack.addUnique(r2b(entry));
    }

    public synchronized yacyNewsDB.Record pop() throws IOException {
        if (queueStack.isEmpty()) return null;
        return b2r(queueStack.removeOne());
    }

    public synchronized yacyNewsDB.Record get(final String id) {
        yacyNewsDB.Record record;
        final Iterator<yacyNewsDB.Record> i = records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) return record;
        }
        return null;
    }

    public synchronized yacyNewsDB.Record remove(final String id) {
        yacyNewsDB.Record record;
        final Iterator<yacyNewsDB.Record> i = records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) {
                try {
                    this.queueStack.remove(UTF8.getBytes(id));
                } catch (IOException e) {
                    Log.logException(e);
                }
                return record;
            }
        }
        return null;
    }

    yacyNewsDB.Record b2r(final Row.Entry b) throws IOException {
        if (b == null) return null;
        final String id = b.getColString(0);
        //Date touched = yacyCore.parseUniversalDate(UTF8.String(b[1]));
        return newsDB.get(id);
    }

    private Row.Entry r2b(final yacyNewsDB.Record r) throws IOException, RowSpaceExceededException {
        if (r == null) return null;
        newsDB.put(r);
        final Row.Entry b = queueStack.row().newEntry(new byte[][]{
                UTF8.getBytes(r.id()),
                        UTF8.getBytes(GenericFormatter.SHORT_SECOND_FORMATTER.format())});
        return b;
    }
    
    public Iterator<yacyNewsDB.Record> records(final boolean up) {
        // iterates yacyNewsRecord-type objects
        if (queueStack == null) return new HashSet<yacyNewsDB.Record>().iterator();
        return new newsIterator(up);
    }
    
    private class newsIterator implements Iterator<yacyNewsDB.Record> {
        // iterates yacyNewsRecord-type objects
        
        Iterator<Row.Entry> stackNodeIterator;
        
        private newsIterator(final boolean up) {
            try {
                stackNodeIterator = queueStack.rows();
            } catch (IOException e) {
                Log.logException(e);
                stackNodeIterator = null;
            }
        }
        
        public boolean hasNext() {
            return stackNodeIterator != null && stackNodeIterator.hasNext();
        }

        public yacyNewsDB.Record next() {
            if (stackNodeIterator == null) return null;
            final Row.Entry row = stackNodeIterator.next();
            try {
                return b2r(row);
            } catch (final IOException e) {
                return null;
            }
        }

        public void remove() {
            if (stackNodeIterator != null) stackNodeIterator.remove();
        }
        
    }

}