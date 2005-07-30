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
import java.util.Iterator;

import de.anomic.yacy.yacyCore;
import de.anomic.kelondro.kelondroTree;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroRecords;
import de.anomic.server.serverCodings;

public class yacyNewsDB {
    
    private File path;
    private int bufferkb;
    private kelondroTree news;
    
    public static final int attributesMaxLength = yacyNewsRecord.maxNewsRecordLength
                 - yacyNewsRecord.idLength()
                 - yacyNewsRecord.categoryStringLength
                 - yacyCore.universalDatePattern.length()
                 - 2;
    
    public yacyNewsDB(File path, int bufferkb) throws IOException {
        this.path = path;
        this.bufferkb = bufferkb;
        
        if (path.exists())
            news = new kelondroTree(path, bufferkb);
        else
            news = createDB(path,bufferkb);        
    }
    
    private static kelondroTree createDB(File path, int bufferkb) throws IOException {
        return new kelondroTree(path, bufferkb, new int[] {
                yacyNewsRecord.idLength(), // id = created + originator
                yacyNewsRecord.categoryStringLength,    // category
                yacyCore.universalDatePattern.length(), // received
                2,
                attributesMaxLength
            });
    }

    private void resetDB() throws IOException {
        try {close();} catch (Exception e) {}
        if (path.exists()) path.delete();
        news = createDB(path, bufferkb);
    }
    
    public void close() {
        if (news != null) try {news.close();} catch (IOException e) {}
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
        
        Iterator nodeIterator;
        
        public recordIterator() throws IOException {
            nodeIterator = news.rows(true, false);
        }
        
        public boolean hasNext() {
            return nodeIterator.hasNext();
        }
        
        public Object next() {
            try {
                return b2r((byte[][]) nodeIterator.next());
            } catch (IOException e) {
                return null;
            }
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

    private static yacyNewsRecord b2r(byte[][] b) throws IOException {
        if (b == null) return null;
        return new yacyNewsRecord(
            new String(b[0]),
            new String(b[1]),
            yacyCore.parseUniversalDate(new String(b[2])),
            (int) serverCodings.enhancedCoder.decodeBase64Long(new String(b[3])),
            serverCodings.string2map(new String(b[4]))
        );
    }

    private static byte[][] r2b(yacyNewsRecord r) throws IOException {
        if (r == null) return null;
        String attributes = r.attributes().toString();
        if (attributes.length() > attributesMaxLength) throw new IllegalArgumentException("attribute length=" + attributes.length() + " exceeds maximum size=" + attributesMaxLength);
        byte[][] b = new byte[5][];
        b[0] = r.id().getBytes();
        b[1] = r.category().getBytes();
        b[2] = yacyCore.universalDateShortString(r.created()).getBytes();
        b[3] = serverCodings.enhancedCoder.encodeBase64Long(r.distributed(), 2).getBytes();
        b[4] = attributes.getBytes();
        return b;
    }
    
}
