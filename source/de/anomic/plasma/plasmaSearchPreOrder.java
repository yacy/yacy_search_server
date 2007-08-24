// plasmaSearchPreOrder.java 
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.plasma.plasmaURL;
import de.anomic.kelondro.kelondroBinSearch;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;

public final class plasmaSearchPreOrder {
    
    public  static kelondroBinSearch[] ybrTables = null; // block-rank tables
    private static boolean useYBR = true;
    
    private indexRWIEntry entryMin, entryMax;
    private TreeMap pageAcc; // key = order hash; value = plasmaLURL.entry
    private plasmaSearchQuery query;
    private plasmaSearchRankingProfile ranking;
    private int filteredCount;
    
    public plasmaSearchPreOrder() {
        this.entryMin = null;
        this.entryMax = null;
        this.pageAcc = new TreeMap();
        this.query = null;
        this.ranking = null;
    }
    
    public plasmaSearchPreOrder(plasmaSearchQuery query, plasmaSearchProcessing process, plasmaSearchRankingProfile ranking, indexContainer container) {
        // we collect the urlhashes and construct a list with urlEntry objects
        // attention: if minEntries is too high, this method will not terminate within the maxTime

        this.query = query;
        this.ranking = ranking;
        
        assert (container != null);
        
        long maxTime = process.getTargetTime(plasmaSearchProcessing.PROCESS_PRESORT);
        process.startTimer();
        
        // set limit time for interruption
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        
        // first pass: find min/max to obtain limits for normalization
        Iterator i = container.entries();
        int count = 0;
        this.entryMin = null;
        this.entryMax = null;
        indexRWIEntry iEntry;
        while (i.hasNext()) {
            if (System.currentTimeMillis() > limitTime) break;
            iEntry = (indexRWIEntry) i.next();
            if (this.entryMin == null) this.entryMin = (indexRWIEntry) iEntry.clone(); else this.entryMin.min(iEntry);
            if (this.entryMax == null) this.entryMax = (indexRWIEntry) iEntry.clone(); else this.entryMax.max(iEntry);
            count++;
        }
        
        // second pass: normalize entries and get ranking
        i = container.entries();
        this.pageAcc = new TreeMap();
        TreeSet searchWords = plasmaSearchQuery.cleanQuery(query.queryString)[0];
        for (int j = 0; j < count; j++) {
            if (System.currentTimeMillis() > limitTime) break;
            iEntry = (indexRWIEntry) i.next();
            if (iEntry.urlHash().length() != container.row().width(container.row().primaryKey())) continue;
            if ((!(query.constraint.equals(plasmaSearchQuery.catchall_constraint))) && (!(iEntry.flags().allOf(query.constraint)))) continue; // filter out entries that do not match the search constraint
            if (query.contentdom != plasmaSearchQuery.CONTENTDOM_TEXT) {
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_AUDIO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasaudio)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_VIDEO) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasvideo)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasimage)))) continue;
                if ((query.contentdom == plasmaSearchQuery.CONTENTDOM_APP  ) && (!(iEntry.flags().get(plasmaCondenser.flag_cat_hasapp  )))) continue;
            }
            pageAcc.put(serverCodings.encodeHex(Long.MAX_VALUE - this.ranking.preRanking(iEntry, this.entryMin, this.entryMax, searchWords), 16) + iEntry.urlHash(), iEntry);
        }
        this.filteredCount = pageAcc.size();
        
        if (container.size() > query.wantedResults) remove(true, true);

        process.setYieldTime(plasmaSearchProcessing.PROCESS_PRESORT);
        process.setYieldCount(plasmaSearchProcessing.PROCESS_PRESORT, container.size());
    }
    
    public int filteredCount() {
        return this.filteredCount;
    }
    
    private void remove(boolean rootDomExt, boolean doubleDom) {
        // this removes all refererences to urls that are extended paths of existing 'RootDom'-urls
        if (pageAcc.size() <= query.wantedResults) return;
        HashSet rootDoms = new HashSet();
        HashSet doubleDoms = new HashSet();
        Iterator i = pageAcc.entrySet().iterator();
        Map.Entry entry;
        indexRWIEntry iEntry;
        String hashpart;
        boolean isWordRootURL;
        TreeSet querywords = plasmaSearchQuery.cleanQuery(query.queryString())[0];
        while (i.hasNext()) {
            if (pageAcc.size() <= query.wantedResults) break;
            entry = (Map.Entry) i.next();
            iEntry = (indexRWIEntry) entry.getValue();
            hashpart = iEntry.urlHash().substring(6);
            isWordRootURL = plasmaURL.isWordRootURL(iEntry.urlHash(), querywords);
            if (isWordRootURL) {
                rootDoms.add(hashpart);
            } else {
            	if (((rootDomExt) && (rootDoms.contains(hashpart))) ||
                    ((doubleDom) && (doubleDoms.contains(hashpart)))) {
            		i.remove();
                }
            }
            doubleDoms.add(hashpart);
        }
    }
    
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
    
    public plasmaSearchPreOrder cloneSmart() {
        // clones only the top structure
        plasmaSearchPreOrder theClone = new plasmaSearchPreOrder();
        theClone.query = this.query;
        theClone.ranking = this.ranking;
        theClone.pageAcc = (TreeMap) this.pageAcc.clone();
        return theClone;
    }
    
    private boolean hasNext() {
        return pageAcc.size() > 0;
    }
    
    private Object[] /*{indexEntry, Long}*/ next() {
        String top = (String) pageAcc.firstKey();
        //System.out.println("preorder-key:  " + top);
        Long preranking;
        try {
            preranking = new Long(Long.MAX_VALUE - Long.parseLong(top.substring(0, 16), 16)); // java.lang.NumberFormatException: For input string: "8000000000020b17" ???
        } catch (NumberFormatException e) {
            e.printStackTrace();
            preranking = new Long(0);
        }
        return new Object[]{(indexRWIEntry) pageAcc.remove(top), preranking};
    }
    
    public indexContainer strippedContainer(int count) {
        // return an indexContainer with a limited number of results
        indexContainer container = plasmaWordIndex.emptyContainer(null, count);
        Object[] o;
        indexRWIEntry entry;
        while ((count-- > 0) && (hasNext())) {
            o = next();
            entry = (indexRWIEntry) o[0];
            container.addUnique(entry.toKelondroEntry());
        }
        return container;
    }
    
    public indexRWIEntry[] getNormalizer() {
        return new indexRWIEntry[] {entryMin, entryMax};
    }

    public static int ybr_p(String urlHash) {
        return 16 * (16 - ybr(urlHash));
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
