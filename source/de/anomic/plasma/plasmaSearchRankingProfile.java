// plasmaSearchRankingProfile.java 
// -------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created: 05.02.2006
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
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

public class plasmaSearchRankingProfile {

    public String[] order;
    
    public plasmaSearchRankingProfile(String[] order) {
        this.order = order;
    }
    
    public String orderString() {
        return order[0] + "-" + order[1] + "-" + order[2];
    }

    public long ranking(plasmaWordIndexEntry normalizedEntry) {
        long ranking = 0;
        
        for (int i = 0; i < 3; i++) {
            if (this.order[i].equals(plasmaSearchQuery.ORDER_QUALITY))   ranking  += normalizedEntry.getQuality() << (4 * (3 - i));
            else if (this.order[i].equals(plasmaSearchQuery.ORDER_DATE)) ranking  += normalizedEntry.getVirtualAge() << (4 * (3 - i));
            else if (this.order[i].equals(plasmaSearchQuery.ORDER_YBR))  ranking  += plasmaSearchPreOrder.ybr_p(normalizedEntry.getUrlHash()) << (4 * (3 - i));
        }
        ranking += (normalizedEntry.posintext()    == 0) ? 0 : (255 - normalizedEntry.posintext()) << 11;
        ranking += (normalizedEntry.worddistance() == 0) ? 0 : (255 - normalizedEntry.worddistance()) << 10;
        ranking += (normalizedEntry.hitcount()     == 0) ? 0 : normalizedEntry.hitcount() << 9;
        ranking += (255 - normalizedEntry.domlengthNormalized()) << 8;
        return ranking;
    }
}
