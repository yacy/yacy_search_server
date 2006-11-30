// yacyNewsRecord.java
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

import java.util.Date;
import java.util.Map;

import de.anomic.kelondro.kelondroRow;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;

public class yacyNewsRecord {

    public static final int maxNewsRecordLength  = 512;
    public static final int categoryStringLength = 8;
    public static final int idLength = yacyCore.universalDateShortPattern.length() + yacySeedDB.commonHashLength;

    private String originator;  // hash of originating peer
    private Date   created;     // Date when news was created by originator
    private Date   received;    // Date when news was received here at this peer
    private String category;    // keyword that adresses possible actions
    private int    distributed; // counter that counts number of distributions of this news record
    private Map    attributes;  // elemets of the news for a special category

    public static final int attributesMaxLength = maxNewsRecordLength
                                                  - idLength
                                                  - categoryStringLength
                                                  - yacyCore.universalDateShortPattern.length()
                                                  - 2;
    
    public static final kelondroRow rowdef = new kelondroRow(
            "String idx-" + idLength + " \"id = created + originator\"," +
            "String cat-" + categoryStringLength + "," +
            "String rec-" + yacyCore.universalDateShortPattern.length() + "," +
            "short  dis-2 {b64e}," +
            "String att-" + attributesMaxLength
    );
    
    public yacyNewsRecord(String newsString) {
        this.attributes = serverCodings.string2map(newsString, ",");
        this.received = (attributes.containsKey("rec")) ? yacyCore.parseUniversalDate((String) attributes.get("rec"), serverDate.UTCDiffString()) : new Date();
        this.created = (attributes.containsKey("cre")) ? yacyCore.parseUniversalDate((String) attributes.get("cre"), serverDate.UTCDiffString()) : new Date();
        this.category = (attributes.containsKey("cat")) ? (String) attributes.get("cat") : "";
        this.distributed = (attributes.containsKey("dis")) ? Integer.parseInt((String) attributes.get("dis")) : 0;
        this.originator = (attributes.containsKey("ori")) ? (String) attributes.get("ori") : "";
        removeStandards();
    }

    public yacyNewsRecord(String category, Map attributes) {
        if (category.length() > categoryStringLength) throw new IllegalArgumentException("category length exceeds maximum");
        this.attributes = attributes;
        this.received = null;
        this.created = new Date();
        this.category = category;
        this.distributed = 0;
        this.originator = yacyCore.seedDB.mySeed.hash;
        removeStandards();
    }

    protected yacyNewsRecord(String id, String category, Date received, int distributed, Map attributes) {
        this.attributes = attributes;
        this.received = received;
        this.created = yacyCore.parseUniversalDate(id.substring(0, yacyCore.universalDateShortPattern.length()), serverDate.UTCDiffString());
        this.category = category;
        this.distributed = distributed;
        this.originator = id.substring(yacyCore.universalDateShortPattern.length());
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
        if (this.created != null)    attributes.put("cre", yacyCore.universalDateShortString(this.created));
        if (this.received != null)   attributes.put("rec", yacyCore.universalDateShortString(this.received));
        attributes.put("dis", Integer.toString(this.distributed));
        String theString = attributes.toString();
        removeStandards();
        return theString;
    }

    public String id() {
        return yacyCore.universalDateShortString(created) + originator;
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

    public Map attributes() {
        return attributes;
    }
    
    public String attribute(String key, String dflt) {
        String s = (String) attributes.get(key);
        if ((s == null) || (s.length() == 0)) return dflt;
        return s;
    }

    public static void main(String[] args) {
        System.out.println((new yacyNewsRecord(args[0])).toString());
    }
}