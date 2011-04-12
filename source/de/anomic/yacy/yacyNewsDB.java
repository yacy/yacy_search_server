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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.kelondro.util.MapTools;


public class yacyNewsDB {

    private final File path;
    private final Row rowdef;
    private final int attributesMaxLength;
    private Index news;

    private  static final int categoryStringLength = 8;
    public   static final int idLength = GenericFormatter.PATTERN_SHORT_SECOND.length() + Word.commonHashLength;

    public yacyNewsDB(
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
                "String att-" + attributesMaxLength,
                NaturalOrder.naturalOrder
            );
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

    @Override
    protected void finalize() {
        close();
    }

    public int size() {
        return news.size();
    }

    public void remove(final String id) throws IOException {
        news.delete(UTF8.getBytes(id));
    }

    public synchronized Record put(final Record record) throws IOException, RowSpaceExceededException {
        try {
            return b2r(news.replace(r2b(record)));
        } catch (final Exception e) {
            resetDB();
            return b2r(news.replace(r2b(record)));
        }
    }

    public synchronized Record get(final String id) throws IOException {
        try {
            return b2r(news.get(UTF8.getBytes(id)));
        } catch (final kelondroException e) {
            resetDB();
            return null;
        }
    }
    
    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_SECOND_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second);

    private Record b2r(final Row.Entry b) {
        if (b == null) return null;
        return new yacyNewsDB.Record(
            b.getColString(0),
            b.getColString(1),
            (b.empty(2)) ? null : my_SHORT_SECOND_FORMATTER.parse(b.getColString(2), GenericFormatter.UTCDiffString()),
            (int) b.getColLong(3),
            MapTools.string2map(b.getColString(4), ",")
        );
    }

    private final Row.Entry r2b(final Record r) {
        if (r == null) return null;
        String attributes = r.attributes().toString();
        if (attributes.length() > attributesMaxLength) {
            Log.logWarning("yacyNewsDB", "attribute length=" + attributes.length() + " exceeds maximum size=" + attributesMaxLength);
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
    
    public Record newRecord(final yacySeed mySeed, final String category, final Properties attributes) {
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
            yacyCore.log.logWarning("rejected bad yacy news record (1): " + e.getMessage());
            return null;
        }
    }

    public Record newRecord(final yacySeed mySeed, final String category, final Map<String, String> attributes) {
        try {
            return new Record(mySeed, category, attributes);
        } catch (final IllegalArgumentException e) {
            yacyCore.log.logWarning("rejected bad yacy news record (2): " + e.getMessage());
            return null;
        }
    }

    public Record newRecord(String external) {
        try {
            return new Record(external);
        } catch (final IllegalArgumentException e) {
            yacyCore.log.logWarning("rejected bad yacy news record (3): " + e.getMessage());
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
            if (attributes.toString().length() > attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + attributesMaxLength + ")");
            this.category = (attributes.containsKey("cat")) ? attributes.get("cat") : "";
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            this.received = (attributes.containsKey("rec")) ? my_SHORT_SECOND_FORMATTER.parse(attributes.get("rec"), GenericFormatter.UTCDiffString()) : new Date();
            this.created = (attributes.containsKey("cre")) ? my_SHORT_SECOND_FORMATTER.parse(attributes.get("cre"), GenericFormatter.UTCDiffString()) : new Date();
            this.distributed = (attributes.containsKey("dis")) ? Integer.parseInt(attributes.get("dis")) : 0;
            this.originator = (attributes.containsKey("ori")) ? attributes.get("ori") : "";
            removeStandards();
        }

        private Record(final yacySeed mySeed, final String category, final Map<String, String> attributes) {
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = null;
            this.created = new Date();
            this.category = category;
            this.distributed = 0;
            this.originator = mySeed.hash;
            removeStandards();
        }

        private Record(final String id, final String category, final Date received, final int distributed, final Map<String, String> attributes) {
            if (category.length() > yacyNewsDB.categoryStringLength) throw new IllegalArgumentException("category length (" + category.length() + ") exceeds maximum (" + yacyNewsDB.categoryStringLength + ")");
            if (attributes.toString().length() > attributesMaxLength) throw new IllegalArgumentException("attributes length (" + attributes.toString().length() + ") exceeds maximum (" + attributesMaxLength + ")");
            this.attributes = attributes;
            this.received = received;
            this.created = my_SHORT_SECOND_FORMATTER.parse(id.substring(0, GenericFormatter.PATTERN_SHORT_SECOND.length()), GenericFormatter.UTCDiffString());
            this.category = category;
            this.distributed = distributed;
            this.originator = id.substring(GenericFormatter.PATTERN_SHORT_SECOND.length());
            removeStandards();
        }

        private void removeStandards() {
            attributes.remove("ori");
            attributes.remove("cat");
            attributes.remove("cre");
            attributes.remove("rec");
            attributes.remove("dis");
        }
        
        @Override
        public String toString() {
            // this creates the string that shall be distributed
            // attention: this has no additional encoding
            if (this.originator != null) attributes.put("ori", this.originator);
            if (this.category != null)   attributes.put("cat", this.category);
            if (this.created != null)    attributes.put("cre", my_SHORT_SECOND_FORMATTER.format(this.created));
            if (this.received != null)   attributes.put("rec", my_SHORT_SECOND_FORMATTER.format(this.received));
            attributes.put("dis", Integer.toString(this.distributed));
            final String theString = attributes.toString();
            removeStandards();
            return theString;
        }

        public String id() {
            return my_SHORT_SECOND_FORMATTER.format(created) + originator;
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
    }

}