// plasmaSearchPreOder.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created: 23.10.2005
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
import java.util.TreeMap;
import java.util.Iterator;

import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.kelondro.kelondroBinSearch;

public final class plasmaSearchPreOrder {
    
    private static kelondroBinSearch[] ybrTables = null; // block-rank tables
    private static boolean useYBR = true;
    
    private TreeMap pageAcc; // key = order hash; value = plasmaLURL.entry
    private plasmaSearchQuery query;
    
    public static void loadYBR(File rankingPath, int count) {
        // load ranking tables
        if (rankingPath.exists()) {
            ybrTables = new kelondroBinSearch[count];
            String ybrName;
            File f;
            try {
                for (int i = 0; i < count; i++) {
                    ybrName = "YBR-4-" + serverCodings.encodeHex(i, 2) + ".idx";
                    f = new File(rankingPath, ybrName);
                    if (f.exists()) {
                        ybrTables[i] = new kelondroBinSearch(serverFileUtils.read(f), 6);
                    } else {
                        ybrTables[i] = null;
                    }
                }
            } catch (IOException e) {
                ybrTables = null;
            }
        } else {
            ybrTables = null;
        }
    }
    
    public static boolean canUseYBR() {
        return ybrTables != null;
    }
    
    public static boolean isUsingYBR() {
        return useYBR;
    }
    
    public static void switchYBR(boolean usage) {
        useYBR = usage;
    }
    
    public plasmaSearchPreOrder(plasmaSearchQuery query) {
        this.pageAcc = new TreeMap();
        this.query = query;
    }
    
    public plasmaSearchPreOrder cloneSmart() {
        // clones only the top structure
        plasmaSearchPreOrder theClone = new plasmaSearchPreOrder(query);
        theClone.pageAcc = (TreeMap) this.pageAcc.clone();
        return theClone;
    }
    
    public boolean hasNext() {
        return pageAcc.size() > 0;
    }
    
    public plasmaWordIndexEntry next() {
        Object top = pageAcc.lastKey();
        return (plasmaWordIndexEntry) pageAcc.remove(top);
    }
    
    public void addEntity(plasmaWordIndexEntity entity, long maxTime) {
        Iterator i = entity.elements(true);
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        plasmaWordIndexEntry entry;
        while (i.hasNext()) {
            if (System.currentTimeMillis() > limitTime) break;
            entry = (plasmaWordIndexEntry) i.next();
            addEntry(entry);
        }
    }
    
    public void addEntry(plasmaWordIndexEntry indexEntry) {
        long ranking = 0;
        long factor = 4096L*4096L;
        
        for (int i = 0; i < 3; i++) {
            if (query.order[i].equals(plasmaSearchQuery.ORDER_QUALITY))  ranking  = factor * indexEntry.getQuality() / 64L;
            else if (query.order[i].equals(plasmaSearchQuery.ORDER_DATE)) ranking  = factor * indexEntry.getVirtualAge() / 64L;
            else if (query.order[i].equals(plasmaSearchQuery.ORDER_YBR))  ranking  = factor * ybr_p(indexEntry.getUrlHash());
            factor = factor / 4096L;
        }

        pageAcc.put(serverCodings.encodeHex(ranking, 16) + indexEntry.getUrlHash(), indexEntry);
    }

    public static int ybr_p(String urlHash) {
        return 16 - ybr(urlHash);
    }
    
    public static int ybr(String urlHash) {
        if (ybrTables == null) return 16;
        if (!(useYBR)) return 16;
        final String domHash = urlHash.substring(6);
        for (int i = 0; i < ybrTables.length; i++) {
            if ((ybrTables[i] != null) && (ybrTables[i].contains(domHash.getBytes()))) {
                //System.out.println("YBR FOUND: " + urlHash + " (" + i + ")");
                return i;
            }
        }
        //System.out.println("NOT FOUND: " + urlHash);
        return 16;
    }
    
}
