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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MapTools;
import net.yacy.kelondro.util.kelondroException;


public class NewsDB {

    private final File path;
    private final Row rowdef;
    private final int attributesMaxLength;
    private Index news;

    private  static final int categoryStringLength = 8;
    public   static final int idLength = GenericFormatter.PATTERN_SHORT_SECOND.length() + Word.commonHashLength;

    public NewsDB(
    		final File path,
    		final int maxNewsRecordLength,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.path = path;
        this.attributesMaxLength = maxNewsRecordLength
            - idLength
            - categoryStringLength
            - GenericFormatter.PATTERN_SHORT_SECOND.length()
            - 2;
        this.rowdef = new Row(
                "String idx-" + idLength + " \"id = created + originator\"," +
                "String cat-" + categoryStringLength + "," +
                "String rec-" + GenericFormatter.PATTERN_SHORT_SECOND.length() + "," +
                "short  dis-2 {b64e}," +
                "String att-" + this.attributesMaxLength,
                NaturalOrder.naturalOrder
            );
        try {
            this.news = new Table(path, this.rowdef, 10, 0, useTailCache, exceed134217727, true);
        } catch (final SpaceExceededException e) {
            try {
                this.news = new Table(path, this.rowdef, 0, 0, false, exceed134217727, true);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
    }

    private void resetDB() {
        try {close();} catch (final Exception e) {}
        if (this.path.exists()) FileUtils.deletedelete(this.path);
        try {
            this.news = new Table(this.path, this.rowdef, 10, 0, false, false, true);
        } catch (final SpaceExceededException e) {
            try {
                this.news = new Table(this.path, this.rowdef, 0, 0, false, false, true);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
    }

    public synchronized void close() {
        if (this.news != null) this.news.close();
        this.news = null;
    }

    @Override
    protected void finalize() {
        close();
    }

    public int size() {
        return this.news.size();
    }

    public void remove(final String id) throws IOException {
        this.news.delete(UTF8.getBytes(id));
    }

    public synchronized Record put(final Record record) throws IOException, SpaceExceededException {
        try {
            return b2r(this.news.replace(r2b(record)));
        } catch (final Exception e) {
            resetDB();
            return b2r(this.news.replace(r2b(record)));
        }
    }

    public synchronized Record get(final String id) throws IOException {
        if (this.news == null) return null;
        try {
            return b2r(this.news.get(UTF8.getBytes(id), false));
        } catch (final kelondroException e) {
            resetDB();
            return null;
        }
    }

    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_SECOND_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second);

    private Record b2r(final Row.Entry b) {
        if (b == null) return null;
        return new NewsDB.Record(
            b.getPrimaryKeyASCII(),
            b.getColUTF8(1),
            (b.empty(2)) ? null : my_SHORT_SECOND_FORMATTER.parse(b.getColASCII(2), GenericFormatter.UTCDiffString()),
            (int) b.getColLong(3),
            MapTools.string2map(b.getColUTF8(4), ",")
        );
    }

    private final Row.Entry r2b(final Record r) {
        if (r == null) return null;
        String attributes = r.attributes().toString();
        if (attributes.length() > this.attributesMaxLength) {
            ConcurrentLog.warn("yacyNewsDB", "attribute length=" + attributes.length() + " exceeds maximum size=" + this.attributesMaxLength);
            attributes = new HashMap<String, String>().toString();
        }
        final Row.Entry entry = this.news.row().newEntry();
        entry.setCol(0, UTF8.getBytes(r.id()));
        entry.setCol(1, UTF8.getBytes(r.category()));
        entry.setCol(2, (r.received() == null) ? null : UTF8.getBytes(my_SHORT_SECOND_FORMATTER.format(r.received())));
        entry.setCol(3, Base64Order.enhancedCoder.encodeLongBA(r.distributed(), 2));
        entry.setCol(4, UTF8.getBytes(attributes));
        return entry;
    }

    public Record newRecord(final Seed mySeed, final String category, final Properties attributes) {
        try {
            final Map<String, String> m = new HashMap<String, String>();
            final Iterator<Entry<Object, Object>> e = attributes.entrySet().iterator();
            Map.Entry<Object, Object> entry;
            while (e.hasNext()) {
                entry = e.next();
                m.put((String) entry.getKey(), (String) entry.getValue());
            }
            return new Record(mySeed, category, m);
        } catch (final IllegalArgumentException e) {
            Network.log.warn("rejected bad yacy news record (1): " + e.getMessage());
            return null;
        }
    }

    public Record newRecord(final Seed mySeed, final String category, final Map<String, String> attributes) {
        try {
            return new Record(mySeed, category, attributes);
        } catch (final IllegalArgumentException e) {
            Network.log.warn("rejected bad yacy news record (2): " + e.getMessage());
            return null;
        }
    }

    public Record newRecord(final String external) {
        try {
            return new Record(external);
        } catch (final IllegalArgumentException e) {
            Network.log.warn("rejected bad yacy news record (3): " + e.getMessage());
            return null;
        }
    }

    public class Record {

        private final String originator;  // hash of originating peer
        private final Date   created;     // Date when news was created by originator
        private final Date   received;    // Date when news was received here at this peer
        private final String category;    // keyword that addresses possible actions
        private       int    distributed; // counter that counts number of distributions of this news record
        private final Map<String, String> attributes;  // elements of the news for a special category


        private Record(final String newsString) {
            this.attributes = MapTools.string2map(newsString, ",");
            if (this.attributes.toString().length() > NewsDB.this.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + this.attributes.toString().length() + ") exceeds maximum (" + NewsDB.this.attributesMaxLength + ")");
            this.category = (this.attributes.containsKey("cat")) ? this.attributes.get("cat") : "";
            if (this.category.length() > NewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + this.category.length() + ") exceeds maximum (" + NewsDB.categoryStringLength + ")");
            this.received = (this.attributes.containsKey("rec")) ? my_SHORT_SECOND_FORMATTER.parse(this.attributes.get("rec"), GenericFormatter.UTCDiffString()) : new Date();
            this.created = (this.attributes.containsKey("cre")) ? my_SHORT_SECOND_FORMATTER.parse(this.attributes.get("cre"), GenericFormatter.UTCDiffString()) : new Date();
            this.distributed = (this.attributes.containsKey("dis")) ? Integer.parseInt(this.attributes.get("dis")) : 0;
            this.originator = (this.attributes.containsKey("ori")) ? this.attributes.get("ori") : "";
            removeStandards();
        }

        private Record(final Seed mySeed, final String category, final Map<String, String> attributes) {
            if (category.length() > NewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + NewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > NewsDB.this.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + NewsDB.this.attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = null;
            this.created = new Date();
            this.category = category;
            this.distributed = 0;
            this.originator = mySeed.hash;
            removeStandards();
        }

        private Record(final String id, final String category, final Date received, final int distributed, final Map<String, String> attributes) {
            if (category.length() > NewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + NewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > NewsDB.this.attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + NewsDB.this.attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = received;
            this.created = my_SHORT_SECOND_FORMATTER.parse(id.substring(0, GenericFormatter.PATTERN_SHORT_SECOND.length()), GenericFormatter.UTCDiffString());
            this.category = category;
            this.distributed = distributed;
            this.originator = id.substring(GenericFormatter.PATTERN_SHORT_SECOND.length());
            removeStandards();
        }

        private void removeStandards() {
            this.attributes.remove("ori");
            this.attributes.remove("cat");
            this.attributes.remove("cre");
            this.attributes.remove("rec");
            this.attributes.remove("dis");
        }

        @Override
        public String toString() {
            // this creates the string that shall be distributed
            // attention: this has no additional encoding
            if (this.originator != null) this.attributes.put("ori", this.originator);
            if (this.category != null)   this.attributes.put("cat", this.category);
            if (this.created != null)    this.attributes.put("cre", my_SHORT_SECOND_FORMATTER.format(this.created));
            if (this.received != null)   this.attributes.put("rec", my_SHORT_SECOND_FORMATTER.format(this.received));
            this.attributes.put("dis", Integer.toString(this.distributed));
            final String theString = this.attributes.toString();
            removeStandards();
            return theString;
        }

        public String id() {
            return my_SHORT_SECOND_FORMATTER.format(this.created) + this.originator;
        }

        public String originator() {
            return this.originator;
        }

        public Date created() {
            return this.created;
        }

        public Date received() {
            return this.received;
        }

        public String category() {
            return this.category;
        }

        public int distributed() {
            return this.distributed;
        }

        public void incDistribution() {
            this.distributed++;
        }

        public Map<String, String> attributes() {
            return this.attributes;
        }

        public String attribute(final String key, final String dflt) {
            final String s = this.attributes.get(key);
            if ((s == null) || (s.isEmpty())) return dflt;
            return s;
        }
    }

}
