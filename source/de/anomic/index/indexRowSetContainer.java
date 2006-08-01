// indexRowSetContainer.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 04.07.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.index;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;

public class indexRowSetContainer extends kelondroRowSet implements indexContainer {

    private String wordHash;
    
    public indexRowSetContainer(kelondroRow rowdef) {
        super(rowdef);
    }

    public indexContainer topLevelClone() {
        indexContainer newContainer = new indexRowSetContainer(this.rowdef);
        newContainer.setWordHash(this.wordHash);
        newContainer.setOrdering(this.sortOrder, this.sortColumn);
        newContainer.add(this, -1);
        return newContainer;
    }
    
    public void setWordHash(String newWordHash) {
        this.wordHash = newWordHash;
    }

    public long updated() {
        return super.lastWrote();
    }

    public String getWordHash() {
        return wordHash;
    }

    public int add(indexEntry entry) {
        this.add(entry.toKelondroEntry());
        return 1;
    }

    public int add(indexEntry entry, long updateTime) {
        this.add(entry);
        this.lastTimeWrote = updateTime;
        return 1;
    }

    public int add(indexEntry[] entries, long updateTime) {
        for (int i = 0; i < entries.length; i++) this.add(entries[i], updateTime);
        return entries.length;
    }

    public int add(indexContainer c, long maxTime) {
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean contains(String urlHash) {
        // TODO Auto-generated method stub
        return false;
    }

    public indexEntry get(String urlHash) {
        // TODO Auto-generated method stub
        return null;
    }

    public indexEntry[] getEntryArray() {
        // TODO Auto-generated method stub
        return null;
    }

    public indexEntry remove(String urlHash) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        // TODO Auto-generated method stub
        return false;
    }

    public int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        // TODO Auto-generated method stub
        return 0;
    }

    public Iterator entries() {
        // TODO Auto-generated method stub
        return null;
    }

    public static Method containerMergeMethod = null;
    static {
        try {
            Class c = Class.forName("de.anomic.index.indexRowSetContainer");
            containerMergeMethod = c.getMethod("containerMerge", new Class[]{Object.class, Object.class});
        } catch (SecurityException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        } catch (ClassNotFoundException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        } catch (NoSuchMethodException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        }
    }

    public static Object containerMerge(Object a, Object b) {
        indexContainer c = (indexContainer) a;
        c.add((indexContainer) b, -1);
        return c;
    }

    public Set urlHashes() {
        // TODO Auto-generated method stub
        return null;
    }
    
}
