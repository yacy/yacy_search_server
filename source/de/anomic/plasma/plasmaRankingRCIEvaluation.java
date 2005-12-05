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
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import de.anomic.kelondro.kelondroAttrSeq;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;

public class plasmaRankingRCIEvaluation {
    
    public static int[] rcieval(kelondroAttrSeq rci) throws IOException {
        // collect information about which entry has how many references
        // the output is a reference-count:occurrences relation
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
        int[] partition = new int[parts];
        int s = 0, p = parts - 1;
        for (int i = 1; i < counts.length; i++) {
            s += counts[i];
            if ((s > limit) && (p >= 0)) {
                partition[p--] = i;
                limit = (2 * limit - s) / 2;
                s = 0;
            }
        }
        partition[0] = counts.length - 1;
        for (int i = 1; i < 10; i++) partition[i] = (partition[i - 1] + 4 * partition[i]) / 5;
        return partition;
    }
    
    public static void checkPartitionTable0(int[] counts, int[] partition) {
        int sumsum = 0;
        int sum;
        int j = 0;
        for (int i = partition.length - 1; i >= 0; i--) {
            sum = 0;
            while (j <= partition[i]) {
                sum += counts[j++];
            }
            System.out.println("sum of YBR-" + i + " entries: " + sum);
            sumsum += sum;
        }
        System.out.println("complete sum = " + sumsum);
    }
    
    public static void checkPartitionTable1(int[] counts, int[] partition) {
        int sumsum = 0;
        int[] sum = new int[partition.length];
        for (int i = 0; i < partition.length; i++) sum[i] = 0;
        for (int i = 0; i < counts.length; i++) sum[orderIntoYBI(partition, i)] += counts[i];
        for (int i = partition.length - 1; i >= 0; i--) {
            System.out.println("sum of YBR-" + i + " entries: " + sum[i]);
            sumsum += sum[i];
        }
        System.out.println("complete sum = " + sumsum);
    }
    
    public static int orderIntoYBI(int[] partition, int count) {
        for (int i = 0; i < partition.length - 1; i++) {
            if ((count >= (partition[i + 1] + 1)) && (count <= partition[i])) return i;
        }
        return partition.length - 1;
    }
    
    public static TreeSet[] genRankingTable(kelondroAttrSeq rci, int[] partition) {
        TreeSet[] ranked = new TreeSet[partition.length];
        for (int i = 0; i < partition.length; i++) ranked[i] = new TreeSet();
        Iterator i = rci.keys();
        String key;
        kelondroAttrSeq.Entry entry;
        while (i.hasNext()) {
            key = (String) i.next();
            entry = rci.getEntry(key);
            ranked[orderIntoYBI(partition, entry.getSeq().size())].add(key);
        }
        return ranked;
    }

    public static HashMap genReverseDomHash(File domlist) {
        HashSet domset = serverFileUtils.loadList(domlist);
        HashMap dommap = new HashMap();
        Iterator i = domset.iterator();
        String dom;
        while (i.hasNext()) {
            dom = (String) i.next();
            if (dom.startsWith("www.")) dom = dom.substring(4);
            try {
                dommap.put(plasmaURL.urlHash(new URL("http://" + dom)).substring(6), dom);
                dommap.put(plasmaURL.urlHash(new URL("http://www." + dom)).substring(6), "www." + dom);
            } catch (MalformedURLException e) {}
        }
        return dommap;
    }

    public static void storeRankingTable(TreeSet[] ranking, File tablePath) throws IOException {
        String filename;
        if (!(tablePath.exists())) tablePath.mkdirs();
        for (int i = 0; i < ranking.length - 1; i++) {
            filename = "YBR-4-" + serverCodings.encodeHex(i, 2) + ".idx";
            serverFileUtils.saveSet(new File(tablePath, filename), ranking[i], "");
        }
    }
    
    public static void main(String[] args) {
        try {
            if ((args.length == 2) && (args[0].equals("-genybr"))) {
                File root_path = new File(args[1]);
                File rci_file = new File(root_path, "DATA/RANKING/GLOBAL/030_rci0/RCI-0.rci.gz");
                long start = System.currentTimeMillis();
                if (!(rci_file.exists())) return;
                
                // create partition table
                final kelondroAttrSeq rci = new kelondroAttrSeq(rci_file, false);
                int counts[] = rcieval(rci);
                int[] partition = interval(counts, 16);
                
                // check the table
                System.out.println("partition position table:");
                for (int i = 0; i < partition.length - 1; i++) {
                    System.out.println("YBR-" + i + ": " + (partition[i + 1] + 1) + " - " + partition[i] + " references");
                }
                System.out.println("YBR-" + (partition.length - 1) + ": 0 - " + partition[partition.length - 1] + " references");
                checkPartitionTable0(counts, partition);
                checkPartitionTable1(counts, partition);
                int sum = 0;
                for (int i = 0; i < counts.length; i++) sum += counts[i];
                System.out.println("sum of all references: " + sum);
                
                // create ranking
                TreeSet[] ranked = genRankingTable(rci, partition);
                storeRankingTable(ranked, new File(root_path, "ranking/YBR"));
                long seconds = java.lang.Math.max(1, (System.currentTimeMillis() - start) / 1000);
                System.out.println("Finished YBR generation in " + seconds + " seconds.");
            }
            if ((args.length == 2) && (args[0].equals("-rcieval"))) {
                File root_path = new File(args[1]);
                
                // load a partition table
                plasmaSearchPreOrder.loadYBR(new File(root_path, "ranking/YBR"), 16);
                
                // load domain list and generate hash index for domains
                HashMap dommap = genReverseDomHash(new File(root_path, "domlist.txt"));
                
                // print out the table
                String hash, dom;
                for (int i = 0; i < 9; i++) {
                    System.out.print("YBR-" + i + ": ");
                    for (int j = 0; j < plasmaSearchPreOrder.ybrTables[i].size(); j++) {
                        hash = new String(plasmaSearchPreOrder.ybrTables[i].get(j));
                        dom = (String) dommap.get(hash);
                        if (dom == null) System.out.print("[" + hash + "], "); else System.out.print(dom + ", ");
                    }
                    System.out.println();
                }
                
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
