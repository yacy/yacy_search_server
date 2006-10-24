// kelondroObjectCache.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2006 on http://www.anomic.de
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

package de.anomic.kelondro;

import java.util.TreeMap;

import de.anomic.server.serverMemory;

public class kelondroObjectCache {

    private final  TreeMap cache;
    private final  kelondroMScoreCluster ages, hasnot;
    private long   startTime;
    private int    maxHitSize, maxMissSize;
    private long   maxAge;
    private long   minMem;
    private int    readHit, readMiss, writeUnique, writeDouble, cacheDelete, cacheFlush;
    private int    hasnotHit, hasnotMiss, hasnotUnique, hasnotDouble, hasnotDelete, hasnotFlush;
    private String name;
    
    public kelondroObjectCache(String name, int maxHitSize, int maxMissSize, long maxAge, long minMem) {
        this.name = name;
        this.cache = new TreeMap();
        this.ages  = new kelondroMScoreCluster();
        this.hasnot  = new kelondroMScoreCluster();
        this.startTime = System.currentTimeMillis();
        this.maxHitSize = Math.max(maxHitSize, 1);
        this.maxMissSize = Math.max(maxMissSize, 1);
        this.maxAge = Math.max(maxAge, 10000);
        this.minMem = Math.max(minMem, 1024 * 1024);
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
        this.cacheDelete = 0;
        this.cacheFlush = 0;
        this.hasnotHit = 0;
        this.hasnotMiss = 0;
        this.hasnotUnique = 0;
        this.hasnotDouble = 0;
        this.hasnotDelete = 0;
        this.hasnotFlush = 0;
    }

    public String getName() {
        return name;
    }
    
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
    
    public void setMaxHitSize(int maxSize) {
        this.maxHitSize = maxSize;
    }
    
    public void setMaxMissSize(int maxSize) {
        this.maxMissSize = maxSize;
    }
    
    public int maxHitSize() {
        return this.maxHitSize;
    }
    
    public int maxMissSize() {
        return this.maxMissSize;
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
    
    public int hitsize() {
        return cache.size();
    }
    
    public int misssize() {
        return hasnot.size();
    }
    
    public long[] status() {
        return new long[]{
                (long) maxHitSize(),
                (long) maxMissSize(),
                (long) hitsize(),
                (long) misssize(),
                this.maxAge,
                minAge(),
                maxAge(),
                (long) readHit,
                (long) readMiss,
                (long) writeUnique,
                (long) writeDouble,
                (long) cacheDelete,
                (long) cacheFlush,
                (long) hasnotHit,
                (long) hasnotMiss,
                (long) hasnotUnique,
                (long) hasnotDouble,
                (long) hasnotDelete,
                (long) hasnotFlush
                };
    }
    
    private static long[] combinedStatus(long[] a, long[] b) {
        return new long[]{
                a[0] + b[0],
                a[1] + b[1],
                a[2] + b[2],
                a[3] + b[3],
                Math.max(a[4], b[4]),
                Math.min(a[5], b[5]),
                Math.max(a[6], b[6]),
                a[7] + b[7],
                a[8] + b[8],
                a[9] + b[9],
                a[10] + b[10],
                a[11] + b[11],
                a[12] + b[12],
                a[13] + b[13],
                a[14] + b[14],
                a[15] + b[15],
                a[16] + b[16],
                a[17] + b[17],
                a[18] + b[18]
        };
    }
    
    public static long[] combinedStatus(long[][] a, int l) {
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
            if (hasnot.deleteScore(key) != 0) hasnotDelete++;
        }
        if (prev == null) this.writeUnique++; else this.writeDouble++;
        flushc();
    }
    
    public Object get(byte[] key) {
        return get(new String(key));
    }
    
    public Object get(String key) {
        if (key == null) return null;
        Object r = null;
        synchronized(cache) {
            r = cache.get(key);
            if (r == null) {
                this.readMiss++;
            } else {
                this.readHit++;
                ages.setScore(key, intTime(System.currentTimeMillis())); // renew cache update time
            }
        }
        flushc();
        return r;
    }
    
    public void hasnot(byte[] key) {
        hasnot(new String(key));
    }
    
    public void hasnot(String key) {
        if (key == null) return;
        int prev = 0;
        synchronized(cache) {
            if (cache.remove(key) != null) cacheDelete++;
            ages.deleteScore(key);
            prev = hasnot.getScore(key);
            hasnot.setScore(key, intTime(System.currentTimeMillis()));
        }
        if (prev == 0) this.hasnotUnique++; else this.hasnotDouble++;
        flushh();
    }
    
    public int has(byte[] key) {
        return has(new String(key));
    }
    
    public int has(String key) {
        // returns a 3-value boolean:
        //  1 = key definitely exists
        // -1 = key definitely does not exist
        //  0 = unknown, if key exists
        if (key == null) return 0;
        synchronized(cache) {
            if (hasnot.getScore(key) > 0) {
                hasnot.setScore(key, intTime(System.currentTimeMillis())); // renew cache update time
                this.hasnotHit++;
                return -1;
            }
            this.hasnotMiss++;
            if (cache.get(key) != null) return 1;
        }
        flushh();
        return 0;
    }
    
    public void remove(byte[] key) {
        remove(new String(key));
    }
    
    public void remove(String key) {
        if (key == null) return;
        synchronized(cache) {
            if (cache.remove(key) != null) cacheDelete++;
            ages.deleteScore(key);
            hasnot.setScore(key, intTime(System.currentTimeMillis()));
        }
        flushh();
    }
    
    public void flushc() {
        String k;
        synchronized(cache) {
            while ((ages.size() > 0) &&
                   ((k = (String) ages.getMinObject()) != null) &&
                   ((ages.size() > maxHitSize) ||
                    (((System.currentTimeMillis() - longEmit(ages.getScore(k))) > maxAge) &&
                     (serverMemory.available() < minMem)))
                  ) {
                cache.remove(k);
                ages.deleteScore(k);
                cacheFlush++;
            }
        }
    }
    
    public void flushh() {
        String k;
        synchronized(cache) {
            while ((hasnot.size() > 0) &&
                    ((k = (String) hasnot.getMinObject()) != null) &&
                    ((hasnot.size() > maxMissSize) ||
                      (((System.currentTimeMillis() - longEmit(hasnot.getScore(k))) > maxAge) &&
                       (serverMemory.available() < minMem)))
                   ) {
                 hasnot.deleteScore(k);
                 hasnotFlush++;
             }
        }
    }
    
    public static void main(String[] args) {
        // test to measure memory usage of miss cache
        kelondroMScoreCluster t = new kelondroMScoreCluster();
        System.gc(); long s0 = Runtime.getRuntime().freeMemory();
        int loop = 200000;
        for (int i = 0; i < loop; i++) t.setScore((Integer.toString(i) + "000000000000").substring(0, 12), i);        
        System.gc(); long s1 = Runtime.getRuntime().freeMemory();
        System.out.println((s1 - s0) / loop);
    }
    
}
