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
import java.util.Date;

import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroStack;
import de.anomic.kelondro.kelondroException;

public class yacyNewsQueue {

    private File path;
    private kelondroStack queueStack;
    private yacyNewsDB newsDB;

    public yacyNewsQueue(File path, yacyNewsDB newsDB) {
        this.path = path;
        this.newsDB = newsDB;

        if (path.exists()) try {
            queueStack = new kelondroStack(path);
        } catch (kelondroException e) {
            path.delete();
            queueStack = createStack(path);
        } catch (IOException e) {
            path.delete();
            queueStack = createStack(path);
        } else {
            queueStack = createStack(path);
        }
    }
    
    public static final kelondroRow rowdef = new kelondroRow(new kelondroColumn[]{
            new kelondroColumn("newsid", kelondroColumn.celltype_string, kelondroColumn.encoder_string, yacyNewsRecord.idLength(), "id = created + originator"),
            new kelondroColumn("last touched", kelondroColumn.celltype_string, kelondroColumn.encoder_string, yacyCore.universalDateShortPattern.length(), "")
    });

    private static kelondroStack createStack(File path) {
        return new kelondroStack(path, rowdef, true);
    }

    private void resetDB() {
        try {close();} catch (Exception e) {}
        if (path.exists()) path.delete();
        queueStack = createStack(path);
    }

    public void clear() {
        resetDB();
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

    public synchronized yacyNewsRecord remove(String id) throws IOException {
        yacyNewsRecord record;
        for (int i = 0; i < size(); i++) {
            record = top(i);
            if ((record != null) && (record.id().equals(id))) {
                pop(i);
                return record;
            }
        }
        return null;
    }

    private yacyNewsRecord b2r(kelondroRow.Entry b) throws IOException {
        if (b == null) return null;
        String id = b.getColString(0, null);
        //Date touched = yacyCore.parseUniversalDate(new String(b[1]));
        return newsDB.get(id);
    }

    private kelondroRow.Entry r2b(yacyNewsRecord r, boolean updateDB) throws IOException {
        if (r == null) return null;
        if (updateDB) {
            newsDB.put(r);
        } else {
            yacyNewsRecord r1 = newsDB.get(r.id());
            if (r1 == null) newsDB.put(r);
        }
        kelondroRow.Entry b = queueStack.row().newEntry(new byte[][]{
                r.id().getBytes(),
                yacyCore.universalDateShortString(new Date()).getBytes()});
        return b;
    }

}