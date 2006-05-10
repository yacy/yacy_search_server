// kelondroObjectCache.java
// ------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This is a part of the kelondro database, which is a part of YaCy
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
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
//
//
// A NOTE FROM THE AUTHOR TO THE USERS:
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
// 
// A NOTE FROM THE AUTHOR TO DEVELOPERS:
//
// Contributions and changes to the program code should be marked as such:
// Please enter your own (C) notice below; they must be compatible with the GPL.
// Please mark also all changes in the code; if you don't mark them then they
// can't be identified; thus all unmarked code belong to the copyright holder
// as mentioned above. A good documentation of code authorities will also help
// to maintain the code and the project.
// A re-distribution must contain the intact and unchanged copyright statement.


package de.anomic.kelondro;

import java.util.TreeMap;

public class kelondroObjectCache {

    private final  TreeMap cache;
    private final  kelondroMScoreCluster ages;
    private long   startTime;
    private int    maxSize;
    private long   maxAge;
    private long   minMem;
    private int    readHit, readMiss, writeUnique, writeDouble;
    private String name;
    
    public kelondroObjectCache(String name, int maxSize, long maxAge, long minMem) {
        this.name = name;
        this.cache = new TreeMap();
        this.ages  = new kelondroMScoreCluster();
        this.startTime = System.currentTimeMillis();
        this.maxSize = Math.max(maxSize, 1);
        this.maxAge = Math.max(maxAge, 10000);
        this.minMem = Math.max(minMem, 1024 * 1024);
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
    }

    public String getName() {
        return name;
    }
    
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
    
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
    
    public int maxSize() {
        return this.maxSize;
    }
    
    public void setMinMem(int minMem) {
        this.minMem = minMem;
    }
    
    public long minAge() {
        if (ages.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(ages.getMaxScore());
    }
    
    public long maxAge() {
        if (ages.size() == 0) return 0;
        return System.currentTimeMillis() - longEmit(ages.getMinScore());
    }
    
    public int size() {
        return cache.size();
    }
    
    public String[] status() {
        return new String[]{
                Integer.toString(maxSize()),
                Integer.toString(size()),
                Long.toString(this.maxAge),
                Long.toString(minAge()),
                Long.toString(maxAge()),
                Integer.toString(readHit),
                Integer.toString(readMiss),
                Integer.toString(writeUnique),
                Integer.toString(writeDouble)
                };
    }
    
    private static String[] combinedStatus(String[] a, String[] b) {
        return new String[]{
                Integer.toString(Integer.parseInt(a[0]) + Integer.parseInt(b[0])),
                Integer.toString(Integer.parseInt(a[1]) + Integer.parseInt(b[1])),
                Long.toString(Math.max(Long.parseLong(a[2]), Long.parseLong(b[2]))),
                Long.toString(Math.min(Long.parseLong(a[3]), Long.parseLong(b[3]))),
                Long.toString(Math.max(Long.parseLong(a[4]), Long.parseLong(b[4]))),
                Integer.toString(Integer.parseInt(a[5]) + Integer.parseInt(b[5])),
                Integer.toString(Integer.parseInt(a[6]) + Integer.parseInt(b[6])),
                Integer.toString(Integer.parseInt(a[7]) + Integer.parseInt(b[7])),
                Integer.toString(Integer.parseInt(a[8]) + Integer.parseInt(b[8]))
        };
    }
    
    public static String[] combinedStatus(String[][] a, int l) {
        if ((a == null) || (a.length == 0) || (l == 0)) return null;
        if ((a.length >= 1) && (l == 1)) return a[0];
        if ((a.length >= 2) && (l == 2)) return combinedStatus(a[0], a[1]);
        return combinedStatus(combinedStatus(a, l - 1), a[l - 1]);
    }
    
    private int intTime(long longTime) {
        return (int) Math.max(0, ((longTime - startTime) / 1000));
    }

    private long longEmit(int intTime) {
        return (((long) intTime) * (long) 1000) + startTime;
    }
    
    public void put(byte[] key, Object value) {
        if (key != null) put(new String(key), value);
    }
    
    public void put(String key, Object value) {
        if ((key == null) || (value == null)) return;
        Object prev = null;
        synchronized(cache) {
            prev = cache.put(key, value);
            ages.setScore(key, intTime(System.currentTimeMillis()));
        }
        if (prev == null) this.writeUnique++; else this.writeDouble++;
        flush();
    }
    
    public Object get(byte[] key) {
        if (key == null) return null;
        Object r = cache.get(new String(key));
        flush();
        if (r == null) this.readMiss++; else this.readHit++;
        return r;
    }
    
    public Object get(String key) {
        if (key == null) return null;
        Object r =  cache.get(key);
        flush();
        if (r == null) this.readMiss++; else this.readHit++;
        return r;
    }
    
    public void remove(byte[] key) {
        remove(new String(key));
    }
    
    public void remove(String key) {
        if (key == null) return;
        synchronized(cache) {
            cache.remove(key);
            ages.deleteScore(key);
        }
        flush();
    }
    
    public void flush() {
        String k;
        synchronized(cache) {
            while ((ages.size() > 0) &&
                   ((k = bestFlush()) != null) &&
                   ((size() > maxSize) ||
                    ((System.currentTimeMillis() - longEmit(ages.getScore(k))) > maxAge) ||
                    (Runtime.getRuntime().freeMemory() < minMem))
                  ) {
                cache.remove(k);
                ages.deleteScore(k);
                if (Runtime.getRuntime().freeMemory() < minMem) System.gc(); // prevent unnecessary loops
            }
        }
    }
    
    public String bestFlush() {
        if (cache.size() == 0) return null;
        try {
            synchronized (cache) {
                return (String) ages.getMinObject(); // flush oldest entries
            }
        } catch (Exception e) {}
        return null;
    }
    
}
