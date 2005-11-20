// plasmaRCIEvaluation.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 18.11.2005
//
// $LastChangedDate: 2005-10-22 15:28:04 +0200 (Sat, 22 Oct 2005) $
// $LastChangedRevision: 968 $
// $LastChangedBy: theli $
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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;

import de.anomic.kelondro.kelondroAttrSeq;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.tools.bitfield;

public class plasmaRankingRCIEvaluation {
    
    public static int[] rcieval(File rci_file) throws IOException {
        // collect information about which entry has how many references
        // the output is a reference-count:occurrences relation
        if (!(rci_file.exists())) return null;
        final kelondroAttrSeq rci = new kelondroAttrSeq(rci_file, false);
        HashMap counts = new HashMap();
        Iterator i = rci.keys();
        String key;
        kelondroAttrSeq.Entry entry;
        Integer count_key, count_count;
        int c, maxcount = 0;
        while (i.hasNext()) {
            key = (String) i.next();
            entry = rci.getEntry(key);
            c = entry.getSeq().size();
            if (c > maxcount) maxcount = c;
            count_key = new Integer(c);
            count_count = (Integer) counts.get(count_key);
            if (count_count == null) {
                count_count = new Integer(1);
            } else {
                count_count = new Integer(count_count.intValue() + 1);
            }
            counts.put(count_key, count_count);
        }
        int[] ctable = new int[maxcount + 1];
        for (int j = 0; j <= maxcount; j++) {
            count_count = (Integer) counts.get(new Integer(j));
            if (count_count == null) {
                ctable[j] = 0;
            } else {
                ctable[j] = count_count.intValue();
            }
        }
        return ctable;
    }
    
    public static long sum(int[] c) {
        long s = 0;
        for (int i = 0; i < c.length; i++) s += (long) c[i];
        return s;
    }
    
    public static int[] interval(int[] counts, int parts) {
        long limit = sum(counts) / 2;
        int[] pos = new int[parts];
        int s = 0, p = parts - 1;
        for (int i = 0; i < counts.length; i++) {
            s += counts[i];
            if ((s > limit) && (p >= 0)) {
                pos[p--] = i - 1;
                limit = (2 * limit - s + counts[i]) / 2;
                s = counts[i];
            }
        }
        pos[0] = counts.length - 1;
        return pos;
    }
    
    public static void main(String[] args) {
        try {
            if ((args.length == 2) && (args[0].equals("-rcieval"))) {
                File root_path = new File(args[1]);
                File rci_file = new File(root_path, "DATA/RANKING/GLOBAL/030_rci0/RCI-0.rci.gz");
                long start = System.currentTimeMillis();
                int count[] = rcieval(rci_file);
                long seconds = java.lang.Math.max(1, (System.currentTimeMillis() - start) / 1000);
                System.out.println("Finished RCI evaluation in " + seconds + " seconds");
                /*
                System.out.println("count table:");
                for (int i = 0; i < count.length; i++) {
                    System.out.println(i + " references: " + count[i] + " times");
                }
                */
                int[] pos = interval(count, 16);
                System.out.println("partition position table:");
                for (int i = 0; i < pos.length; i++) {
                    System.out.println("position " + i + ": " + pos[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
