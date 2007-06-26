// yacyNewsQueue.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;

public class yacyNewsDB {

    private File path;
    private long preloadTime;
    protected kelondroIndex news;

    public yacyNewsDB(File path, long preloadTime) {
        this.path = path;
        this.preloadTime = preloadTime;
        this.news = new kelondroCache(kelondroTree.open(path, true, preloadTime, yacyNewsRecord.rowdef), true, false);
    }

    private void resetDB() {
        try {close();} catch (Exception e) {}
        if (path.exists()) path.delete();
        this.news = new kelondroCache(kelondroTree.open(path, true, preloadTime, yacyNewsRecord.rowdef), true, false);
    }
    
    public void close() {
        if (news != null) news.close();
        news = null;
    }

    public void finalize() {
        close();
    }

    public int size() {
        return news.size();
    }

    public void remove(String id) throws IOException {
        news.remove(id.getBytes());
    }

    public synchronized yacyNewsRecord put(yacyNewsRecord record) throws IOException {
        try {
            return b2r(news.put(r2b(record)));
        } catch (kelondroException e) {
            resetDB();
            return b2r(news.put(r2b(record)));
        }
    }

    public synchronized Iterator news() throws IOException {
        // the iteration iterates yacyNewsRecord - type objects
        return new recordIterator();
    }

    public class recordIterator implements Iterator {

        Iterator rowIterator;

        public recordIterator() throws IOException {
            rowIterator = news.rows(true, null);
        }

        public boolean hasNext() {
            return rowIterator.hasNext();
        }

        public Object next() {
            return b2r((kelondroRow.Entry) rowIterator.next());
        }

        public void remove() {
        }

    }

    public synchronized yacyNewsRecord get(String id) throws IOException {
        try {
            return b2r(news.get(id.getBytes()));
        } catch (kelondroException e) {
            resetDB();
            return null;
        }
    }

    protected final static yacyNewsRecord b2r(kelondroRow.Entry b) {
        if (b == null) return null;
        return new yacyNewsRecord(
            b.getColString(0, null),
            b.getColString(1, "UTF-8"),
            (b.empty(2)) ? null : yacyCore.parseUniversalDate(b.getColString(2, null), serverDate.UTCDiffString()),
            (int) b.getColLong(3),
            serverCodings.string2map(b.getColString(4, "UTF-8"), ",")
        );
    }

    protected final kelondroRow.Entry r2b(yacyNewsRecord r) {
        try {
            if (r == null) return null;
            String attributes = r.attributes().toString();
            if (attributes.length() > yacyNewsRecord.attributesMaxLength) throw new IllegalArgumentException("attribute length=" + attributes.length() + " exceeds maximum size=" + yacyNewsRecord.attributesMaxLength);
            kelondroRow.Entry entry = this.news.row().newEntry();
            entry.setCol(0, r.id().getBytes());
            entry.setCol(1, r.category().getBytes("UTF-8"));
            entry.setCol(2, (r.received() == null) ? null : yacyCore.universalDateShortString(r.received()).getBytes());
            entry.setCol(3, kelondroBase64Order.enhancedCoder.encodeLong(r.distributed(), 2).getBytes());
            entry.setCol(4, attributes.getBytes("UTF-8"));
            return entry;
        } catch(UnsupportedEncodingException e) {
            // ignore this. this should never occure
            return null;
        }
    }

}