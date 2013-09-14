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

package net.yacy.peers;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;

public class NewsQueue implements Iterable<NewsDB.Record> {

    private final File path;
    private Table queueStack;
    private final NewsDB newsDB;

    private static final Row rowdef = new Row(new Column[]{
            new Column("newsid", Column.celltype_string, Column.encoder_bytes, NewsDB.idLength, "id = created + originator"),
            new Column("last touched", Column.celltype_string, Column.encoder_bytes, GenericFormatter.PATTERN_SHORT_SECOND.length(), "")
        },
        NaturalOrder.naturalOrder
    );

    public NewsQueue(final File path, final NewsDB newsDB) {
        this.path = path;
        this.newsDB = newsDB;
        try {
            this.queueStack = new Table(path, rowdef, 10, 0, false, false, true);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            this.queueStack = null;
        }
    }

    public void clear() {
        try {
            this.queueStack.clear();
        } catch (final IOException e) {
            try {close();} catch (final Exception ee) {}
            if (this.path.exists()) FileUtils.deletedelete(this.path);
            try {
                this.queueStack = new Table(this.path, rowdef, 10, 0, false, false, true);
            } catch (final SpaceExceededException ee) {
                ConcurrentLog.logException(e);
                this.queueStack = null;
            }
        }
    }

    public synchronized void close() {
        if (this.queueStack != null) this.queueStack.close();
        this.queueStack = null;
    }

    @Override
    protected void finalize() {
        close();
    }

    public int size() {
        return this.queueStack.size();
    }

    public boolean isEmpty() {
        return this.queueStack.isEmpty();
    }

    public synchronized void push(final NewsDB.Record entry) throws IOException, SpaceExceededException {
        if (!this.queueStack.consistencyCheck()) {
            ConcurrentLog.severe("yacyNewsQueue", "reset of table " + this.path);
            this.queueStack.clear();
        }
        this.queueStack.addUnique(r2b(entry));
    }

    public synchronized NewsDB.Record pop() throws IOException {
        if (this.queueStack.isEmpty()) return null;
        return b2r(this.queueStack.removeOne());
    }

    public synchronized NewsDB.Record get(final String id) {
        NewsDB.Record record;
        final Iterator<NewsDB.Record> i = iterator();
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) return record;
        }
        return null;
    }

    public synchronized NewsDB.Record remove(final String id) {
        NewsDB.Record record;
        final Iterator<NewsDB.Record> i = iterator();
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.id().equals(id))) {
                try {
                    this.queueStack.remove(UTF8.getBytes(id));
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
                return record;
            }
        }
        return null;
    }

    NewsDB.Record b2r(final Row.Entry b) throws IOException {
        if (b == null) return null;
        final String id = b.getPrimaryKeyASCII();
        //Date touched = yacyCore.parseUniversalDate(UTF8.String(b[1]));
        return this.newsDB.get(id);
    }

    private Row.Entry r2b(final NewsDB.Record r) throws IOException, SpaceExceededException {
        if (r == null) return null;
        this.newsDB.put(r);
        final Row.Entry b = this.queueStack.row().newEntry(new byte[][]{
                UTF8.getBytes(r.id()),
                        UTF8.getBytes(GenericFormatter.SHORT_SECOND_FORMATTER.format())});
        return b;
    }

    @Override
    public Iterator<NewsDB.Record> iterator() {
        // iterates yacyNewsRecord-type objects
        if (this.queueStack == null) return new HashSet<NewsDB.Record>().iterator();
        return new newsIterator();
    }

    private class newsIterator implements Iterator<NewsDB.Record> {
        // iterates yacyNewsRecord-type objects

        Iterator<Row.Entry> stackNodeIterator;

        private newsIterator() {
            try {
                this.stackNodeIterator = NewsQueue.this.queueStack.rows();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                this.stackNodeIterator = null;
            }
        }

        @Override
        public boolean hasNext() {
            return this.stackNodeIterator != null && this.stackNodeIterator.hasNext();
        }

        @Override
        public NewsDB.Record next() {
            if (this.stackNodeIterator == null) return null;
            Row.Entry row;
            try {
                row = this.stackNodeIterator.next();
            } catch (final IndexOutOfBoundsException e) {
                e.printStackTrace();
                return null;
            }
            try {
                return b2r(row);
            } catch (final IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public void remove() {
            if (this.stackNodeIterator != null) this.stackNodeIterator.remove();
        }

    }

}
