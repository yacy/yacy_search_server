// kelondroMHashMap.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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

package de.anomic.kelondro;

import java.util.Iterator;

public class kelondroMHashMap {

    private int keylen, valuelen, reclen, count;
    private byte[] mem;
    private byte[] emptykey;
    
    public kelondroMHashMap(int valuelen) {
        // initializes a hash map with integer access key
        this(4, valuelen);
    }

    public kelondroMHashMap(int keylen, int valuelen) {
        this.keylen = keylen;
        this.valuelen = valuelen;
        this.reclen = keylen + valuelen;
        this.mem = new byte[1 * reclen];
        this.count = 0;
        this.emptykey = new byte[keylen];
        for (int i = 0; i < keylen; i++) emptykey[i] = 0;
        for (int i = 0; i < (mem.length / reclen); i++) System.arraycopy(emptykey, 0, mem, i * reclen, keylen);
    }
    
    private boolean equals(int posKeyInMem, byte[] otherkey) {
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
    
    private static int hashkey(byte[] key, int capacity) {
        int h = 0;
        for (int i = 0; i < key.length; i++) h = h * 15 + (0xff & key[i]);
        //System.out.println("hash code of key " + new String(key) + " is " + h);
        return h % capacity;
    }
    
    private static int rehash(int previousKey, int capacity) {
        if (previousKey == 0) previousKey = capacity;
        return previousKey - 1;
    }
    
    public int size() {
        return count;
    }
    
    private int findExisting(byte[] key) {
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
    
    private int findSpace(byte[] key) {
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
    
    private static int findSpace(byte[] m, byte[] key, int rl, int trycount, int capacity) {
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
    
    public void put(int key, byte[] value) {
        put(toByteKey(key), value);
    }
    
    public void put(byte[] key, byte[] value) {
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
                    int newspace = mem.length * 2;
                    int newcapacity = capacity() * 2;
                    byte[] newmem = new byte[newspace];
                    Iterator i = entries();
                    kelondroMHashMap.entry e;
                    int mempos;
                    while (i.hasNext()) {
                        e = (kelondroMHashMap.entry) i.next();
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
                int mempos = hash * reclen;
                System.arraycopy(key, 0, mem, mempos, keylen);
                System.arraycopy(value, 0, mem, mempos + keylen, valuelen);
            }
            count++;
        } else {
            // overwrite old entry
            int mempos = hash * reclen;
            System.arraycopy(key, 0, mem, mempos, keylen);
            System.arraycopy(value, 0, mem, mempos + keylen, valuelen);
        }
    }
    
    public byte[] get(int key) {
        return get(toByteKey(key));
    }
    
    public byte[] get(byte[] key) {
        assert (key.length == keylen);
        
        int hash = findExisting(key);
        //System.out.println("get " + new String(key) + " from cell " + hash);
        if (hash < 0) {
            return null;
        } else {
            // read old entry
            byte[] value = new byte[valuelen];
            System.arraycopy(mem, hash * reclen + keylen, value, 0, valuelen);
            return value;
        }
    }
    
    public void remove(int key) {
        remove(toByteKey(key));
    }
    
    public void remove(byte[] key) {
        assert (key.length == keylen);
        
        System.out.println("REMOVE!");
        int hash = findExisting(key);
        if (hash >= 0) {
            // overwrite old key
            System.arraycopy(emptykey, 0, mem, hash * reclen, keylen);
            count--;
        }
    }
                    
    Iterator entries() {
        return new entryIterator();
    }
    
    public class entryIterator implements Iterator {

        int hashkey;
        
        public entryIterator() {
            hashkey = anyhashpos(0);
        }
        
        public boolean hasNext() {
            return hashkey >= 0;
        }

        public Object next() {
            int i = hashkey;
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
    
    private int anyhashpos(int start) {
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
                byte[] key = new byte[keylen];
                System.arraycopy(mem, mempos, key, 0, keylen);
                return key;
            }
            hash++;
        }
        return null;
    }
    
    public class entry {
        public byte[] key, value;
        
        public entry(int index) {
            this.key = new byte[keylen];
            this.value = new byte[valuelen];
            int mempos = index * (reclen);
            System.arraycopy(mem, mempos, key, 0, keylen);
            System.arraycopy(mem, mempos + keylen, value, 0, valuelen);
        }
        
        public entry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
    }
    
    
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        kelondroMHashMap map = new kelondroMHashMap(4);
        for (int i = 0; i < 100; i++) map.put(3333 + i, ("" + (1000 + i)).getBytes());
        Iterator i = map.entries();
        kelondroMHashMap.entry e;
        System.out.println("Enumeration of elements: count=" + map.size());
        int c = 0;
        while (i.hasNext()) {
            e = (kelondroMHashMap.entry) i.next();
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
