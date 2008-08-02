// kelondroMHashMap.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 08.12.2005
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

import java.util.Iterator;

public class kelondroMHashMap {

    int keylen;
    int valuelen;
    int reclen, count;
    byte[] mem;
    private final byte[] emptykey;
    
    public kelondroMHashMap(final int valuelen) {
        // initializes a hash map with integer access key
        this(4, valuelen);
    }

    public kelondroMHashMap(final int keylen, final int valuelen) {
        this.keylen = keylen;
        this.valuelen = valuelen;
        this.reclen = keylen + valuelen;
        this.mem = new byte[1 * reclen];
        this.count = 0;
        this.emptykey = new byte[keylen];
        for (int i = 0; i < keylen; i++) emptykey[i] = 0;
        for (int i = 0; i < (mem.length / reclen); i++) System.arraycopy(emptykey, 0, mem, i * reclen, keylen);
    }
    
    private boolean equals(final int posKeyInMem, final byte[] otherkey) {
        assert (otherkey.length == keylen);
        int pos = posKeyInMem * reclen;
        int i = 0;
        while (i < keylen) if (mem[pos++] != otherkey[i++]) return false;
        return true;
    }
    
    private int rehashtry() {
        return 1 + mem.length / 2;
    }
    
    private int capacity() {
        return mem.length / reclen;
    }
    
    private static int hashkey(final byte[] key, final int capacity) {
        int h = 0;
        for (int i = 0; i < key.length; i++) h = h * 15 + (0xff & key[i]);
        //System.out.println("hash code of key " + new String(key) + " is " + h);
        return h % capacity;
    }
    
    private static int rehash(int previousKey, final int capacity) {
        if (previousKey == 0) previousKey = capacity;
        return previousKey - 1;
    }
    
    public int size() {
        return count;
    }
    
    private int findExisting(final byte[] key) {
        // returns an index position if found; -1 otherwise
        int hash = hashkey(key, capacity());
        //System.out.println("first guess for key " + new String(key) + ": " + hash + "( capacity is " + capacity() + " )");
        int testcount = 0;
        while (testcount++ < rehashtry()) {
            if (mem[hash * reclen] == 0) return -1;
            if (equals(hash, key)) return hash;
            hash = rehash(hash, capacity());
        }
        return -1;
    }
    
    private int findSpace(final byte[] key) {
        // returns an new index position which is empty
        // if there is no space left -1 is returned
        int hash = hashkey(key, capacity());
        int testcount = 0;
        while (testcount++ < rehashtry()) {
            if (mem[hash * reclen] == 0) return hash;
            hash = rehash(hash, capacity());
        }
        return -1;
    }
    
    private static int findSpace(final byte[] m, final byte[] key, final int rl, final int trycount, final int capacity) {
        // returns an new index position which is empty
        // if there is no space left -1 is returned
        int hash = hashkey(key, capacity);
        int testcount = 0;
        while (testcount++ < trycount) {
            if (m[hash * rl] == 0) return hash;
            hash = rehash(hash, capacity);
        }
        return -1;
    }
    
    private static byte[] toByteKey(int v) {
        assert (v >= 0);
        v = v | 0x80000000; // set bit in first byte
        return new byte[]{(byte) ((v >>> 24) & 0xFF), (byte) ((v >>> 16) & 0xFF),
                          (byte) ((v >>> 8) & 0xFF), (byte) ((v >>> 0) & 0xFF)};
    }
    
    public void put(final int key, final byte[] value) {
        put(toByteKey(key), value);
    }
    
    public void put(final byte[] key, final byte[] value) {
        // inserts a new value or overwrites existing
        // if the hash is full, a RuntimeException is thrown
        // this method does not return the old value to avoid generation of objects
        assert (key.length == keylen);
        assert (value.length == valuelen);
        
        int hash = findExisting(key);
        if (hash < 0) {
            // insert new entry
            hash = findSpace(key);
            if (hash < 0) {
                // increase space of hashtable
                // create temporary bigger hashtable and insert all
                synchronized (mem) {
                    System.out.println("increasing space to " + mem.length * 2);
                    final int newspace = mem.length * 2;
                    final int newcapacity = capacity() * 2;
                    byte[] newmem = new byte[newspace];
                    final Iterator<entry> i = entries();
                    kelondroMHashMap.entry e;
                    int mempos;
                    while (i.hasNext()) {
                        e = i.next();
                        hash = findSpace(newmem, e.key, reclen, newspace, newcapacity);
                        mempos = hash * reclen;
                        System.arraycopy(e.key, 0, newmem, mempos, keylen);
                        System.arraycopy(e.value, 0, newmem, mempos + keylen, valuelen);
                    }
                    // finally insert new value
                    hash = findSpace(newmem, key, reclen, newspace, newcapacity);
                    mempos = hash * reclen;
                    System.arraycopy(key, 0, newmem, mempos, keylen);
                    System.arraycopy(value, 0, newmem, mempos + keylen, valuelen);
                    // move newmem to mem
                    mem = newmem;
                    newmem = null;
                }
            } else {
                // there is enough space
                //System.out.println("put " + new String(key) + " into cell " + hash);
                final int mempos = hash * reclen;
                System.arraycopy(key, 0, mem, mempos, keylen);
                System.arraycopy(value, 0, mem, mempos + keylen, valuelen);
            }
            count++;
        } else {
            // overwrite old entry
            final int mempos = hash * reclen;
            System.arraycopy(key, 0, mem, mempos, keylen);
            System.arraycopy(value, 0, mem, mempos + keylen, valuelen);
        }
    }
    
    public byte[] get(final int key) {
        return get(toByteKey(key));
    }
    
    public byte[] get(final byte[] key) {
        assert (key.length == keylen);
        
        final int hash = findExisting(key);
        //System.out.println("get " + new String(key) + " from cell " + hash);
        if (hash < 0) {
            return null;
        }
        // read old entry
        final byte[] value = new byte[valuelen];
        System.arraycopy(mem, hash * reclen + keylen, value, 0, valuelen);
        return value;
    }
    
    public void remove(final int key) {
        remove(toByteKey(key));
    }
    
    public void remove(final byte[] key) {
        assert (key.length == keylen);
        
        System.out.println("REMOVE!");
        final int hash = findExisting(key);
        if (hash >= 0) {
            // overwrite old key
            System.arraycopy(emptykey, 0, mem, hash * reclen, keylen);
            count--;
        }
    }
                    
    Iterator<entry> entries() {
        return new entryIterator();
    }
    
    public class entryIterator implements Iterator<entry> {

        int hashkey;
        
        public entryIterator() {
            hashkey = anyhashpos(0);
        }
        
        public boolean hasNext() {
            return hashkey >= 0;
        }

        public entry next() {
            final int i = hashkey;
            hashkey = anyhashpos(hashkey + 1);
            return new entry(i);
        }

        public void remove() {
            mem[hashkey * reclen] = 0;
        }
        
    }
    
    public void removeany() {
        //System.out.println("CALLED REMOVEANY");
        int start = 0;
        while (start < capacity()) {
            if (mem[start * reclen] != 0) {
                System.arraycopy(emptykey,0, mem, start * reclen, keylen);
                count--;
                return;
            }
            start++;
        }
        return;
    }
    
    protected int anyhashpos(int start) {
        while (start < capacity()) {
            if (mem[start * reclen] != 0) return start;
            start++;
        }
        return -1;
    }
    
    public byte[] anykey() {
        int hash = 0;
        int mempos;
        while (hash < capacity()) {
            mempos = hash * reclen;
            if (mem[mempos] != 0) {
                final byte[] key = new byte[keylen];
                System.arraycopy(mem, mempos, key, 0, keylen);
                return key;
            }
            hash++;
        }
        return null;
    }
    
    public class entry {
        public byte[] key, value;
        
        public entry(final int index) {
            this.key = new byte[keylen];
            this.value = new byte[valuelen];
            final int mempos = index * (reclen);
            System.arraycopy(mem, mempos, key, 0, keylen);
            System.arraycopy(mem, mempos + keylen, value, 0, valuelen);
        }
        
        public entry(final byte[] key, final byte[] value) {
            this.key = key;
            this.value = value;
        }
    }
    
    
    public static void main(final String[] args) {
        final long start = System.currentTimeMillis();
        final kelondroMHashMap map = new kelondroMHashMap(4);
        for (int i = 0; i < 100; i++) map.put(3333 + i, ("" + (1000 + i)).getBytes());
        final Iterator<entry> i = map.entries();
        kelondroMHashMap.entry e;
        System.out.println("Enumeration of elements: count=" + map.size());
        int c = 0;
        while (i.hasNext()) {
            e = i.next();
            System.out.println("key=" + new String(e.key) + ", value=" + new String(e.value) + ", retrieved=" + new String(map.get(e.key)));
            c++;
        }
        System.out.println("c = " + c + "; re-catch:");
        for (int j = 0; j < 100; j++) {
            System.out.println("key=" + j + ", retrieved=" + new String(map.get(3333 + j)));
        }
        System.out.println("runtime = " + (System.currentTimeMillis() - start));
    }
}
