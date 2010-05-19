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
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.ObjectIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.kelondro.util.MapTools;


public class yacyNewsDB {

    private final File path;
    protected ObjectIndex news;

    private static final int maxNewsRecordLength  = 512;
    private  static final int categoryStringLength = 8;
    public  static final int idLength = DateFormatter.PATTERN_SHORT_SECOND.length() + Word.commonHashLength;

    private static final int attributesMaxLength =
        maxNewsRecordLength
            - idLength
            - categoryStringLength
            - DateFormatter.PATTERN_SHORT_SECOND.length()
            - 2;

    private static final Row rowdef = new Row(
            "String idx-" + idLength + " \"id = created + originator\"," +
            "String cat-" + categoryStringLength + "," +
            "String rec-" + DateFormatter.PATTERN_SHORT_SECOND.length() + "," +
            "short  dis-2 {b64e}," +
            "String att-" + attributesMaxLength,
            NaturalOrder.naturalOrder
    );

    public yacyNewsDB(
    		final File path,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.path = path;
        try {
            this.news = new Table(path, rowdef, 10, 0, useTailCache, exceed134217727);
        } catch (RowSpaceExceededException e) {
            try {
                this.news = new Table(path, rowdef, 0, 0, false, exceed134217727);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
    }

    private void resetDB() {
        try {close();} catch (final Exception e) {}
        if (path.exists()) FileUtils.deletedelete(path);
        try {
            this.news = new Table(path, rowdef, 10, 0, false, false);
        } catch (RowSpaceExceededException e) {
            try {
                this.news = new Table(path, rowdef, 0, 0, false, false);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
    }
    
    public void close() {
        if (news != null) news.close();
        news = null;
    }

    protected void finalize() {
        close();
    }

    public int size() {
        return news.size();
    }

    public void remove(final String id) throws IOException {
        news.delete(id.getBytes());
    }

    public synchronized Record put(final Record record) throws IOException, RowSpaceExceededException {
        try {
            return b2r(news.replace(r2b(record)));
        } catch (final Exception e) {
            resetDB();
            return b2r(news.replace(r2b(record)));
        }
    }

    public synchronized Iterator<Record> news() throws IOException {
        // the iteration iterates yacyNewsRecord - type objects
        return new recordIterator();
    }

    public class recordIterator implements Iterator<Record> {

        Iterator<Row.Entry> rowIterator;

        public recordIterator() throws IOException {
            rowIterator = news.rows();
        }

        public boolean hasNext() {
            return rowIterator.hasNext();
        }

        public Record next() {
            return b2r(rowIterator.next());
        }

        public void remove() {
            rowIterator.remove();
        }

    }

    public synchronized Record get(final String id) throws IOException {
        try {
            return b2r(news.get(id.getBytes()));
        } catch (final kelondroException e) {
            resetDB();
            return null;
        }
    }

    protected final static Record b2r(final Row.Entry b) {
        if (b == null) return null;
        return new yacyNewsDB.Record(
            b.getColString(0, null),
            b.getColString(1, "UTF-8"),
            (b.empty(2)) ? null : DateFormatter.parseShortSecond(b.getColString(2, null), DateFormatter.UTCDiffString()),
            (int) b.getColLong(3),
            MapTools.string2map(b.getColString(4, "UTF-8"), ",")
        );
    }

    protected final Row.Entry r2b(final Record r) {
        if (r == null) return null;
        try {
            final String attributes = r.attributes().toString();
            if (attributes.length() > attributesMaxLength) throw new IllegalArgumentException("attribute length=" + attributes.length() + " exceeds maximum size=" + attributesMaxLength);
            final Row.Entry entry = this.news.row().newEntry();
            entry.setCol(0, r.id().getBytes());
            entry.setCol(1, r.category().getBytes("UTF-8"));
            entry.setCol(2, (r.received() == null) ? null : DateFormatter.formatShortSecond(r.received()).getBytes());
            entry.setCol(3, Base64Order.enhancedCoder.encodeLong(r.distributed(), 2).getBytes());
            entry.setCol(4, attributes.getBytes("UTF-8"));
            return entry;
        } catch(final UnsupportedEncodingException e) {
            // ignore this. this should never occure
            return null;
        }
    }
    
    public static Record newRecord(final yacySeed mySeed, final String category, final Properties attributes) {
        try {
            final HashMap<String, String> m = new HashMap<String, String>();
            final Iterator<Entry<Object, Object>> e = attributes.entrySet().iterator();
            Map.Entry<Object, Object> entry;
            while (e.hasNext()) {
                entry = e.next();
                m.put((String) entry.getKey(), (String) entry.getValue());
            }
            return new Record(mySeed, category, m);
        } catch (final IllegalArgumentException e) {
            yacyCore.log.logWarning("rejected bad yacy news record: " + e.getMessage());
            return null;
        }
    }

    public static class Record {

        private final String originator;  // hash of originating peer
        private final Date   created;     // Date when news was created by originator
        private final Date   received;    // Date when news was received here at this peer
        private final String category;    // keyword that adresses possible actions
        private       int    distributed; // counter that counts number of distributions of this news record
        private final Map<String, String> attributes;  // elemets of the news for a special category

        
        public Record(final String newsString) {
            this.attributes = MapTools.string2map(newsString, ",");
            if (attributes.toString().length() > yacyNewsDB.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + yacyNewsDB.attributesMaxLength + ")");
            this.category = (attributes.containsKey("cat")) ? attributes.get("cat") : "";
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            this.received = (attributes.containsKey("rec")) ? DateFormatter.parseShortSecond(attributes.get("rec"), DateFormatter.UTCDiffString()) : new Date();
            this.created = (attributes.containsKey("cre")) ? DateFormatter.parseShortSecond(attributes.get("cre"), DateFormatter.UTCDiffString()) : new Date();
            this.distributed = (attributes.containsKey("dis")) ? Integer.parseInt(attributes.get("dis")) : 0;
            this.originator = (attributes.containsKey("ori")) ? attributes.get("ori") : "";
            removeStandards();
        }

        public Record(final yacySeed mySeed, final String category, final Map<String, String> attributes) {
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > yacyNewsDB.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + yacyNewsDB.attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = null;
            this.created = new Date();
            this.category = category;
            this.distributed = 0;
            this.originator = mySeed.hash;
            removeStandards();
        }

        protected Record(final String id, final String category, final Date received, final int distributed, final Map<String, String> attributes) {
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > yacyNewsDB.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + yacyNewsDB.attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = received;
            this.created = DateFormatter.parseShortSecond(id.substring(0, DateFormatter.PATTERN_SHORT_SECOND.length()), DateFormatter.UTCDiffString());
            this.category = category;
            this.distributed = distributed;
            this.originator = id.substring(DateFormatter.PATTERN_SHORT_SECOND.length());
            removeStandards();
        }

        private void removeStandards() {
            attributes.remove("ori");
            attributes.remove("cat");
            attributes.remove("cre");
            attributes.remove("rec");
            attributes.remove("dis");
        }
        
        public String toString() {
            // this creates the string that shall be distributed
            // attention: this has no additional encoding
            if (this.originator != null) attributes.put("ori", this.originator);
            if (this.category != null)   attributes.put("cat", this.category);
            if (this.created != null)    attributes.put("cre", DateFormatter.formatShortSecond(this.created));
            if (this.received != null)   attributes.put("rec", DateFormatter.formatShortSecond(this.received));
            attributes.put("dis", Integer.toString(this.distributed));
            final String theString = attributes.toString();
            removeStandards();
            return theString;
        }

        public String id() {
            return DateFormatter.formatShortSecond(created) + originator;
        }

        public String originator() {
            return originator;
        }

        public Date created() {
            return created;
        }

        public Date received() {
            return received;
        }

        public String category() {
            return category;
        }

        public int distributed() {
            return distributed;
        }

        public void incDistribution() {
            distributed++;
        }

        public Map<String, String> attributes() {
            return attributes;
        }
        
        public String attribute(final String key, final String dflt) {
            final String s = attributes.get(key);
            if ((s == null) || (s.length() == 0)) return dflt;
            return s;
        }

        public static void main(final String[] args) {
            System.out.println((new yacyNewsDB.Record(args[0])).toString());
        }
    }

}