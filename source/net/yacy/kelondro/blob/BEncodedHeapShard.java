/**
 *  BEncodedHeapShard
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.blob;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.storage.AbstractMapStore;
import net.yacy.cora.storage.MapStore;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MergeIterator;

public class BEncodedHeapShard extends AbstractMapStore implements MapStore {

    public interface Method {
        /**
         * a sharding method produces a filename from a given key
         * @param key
         * @return
         */
        public String filename(byte[] key);
        
        /**
         * get the maximum key length for access keys
         * @return
         */
        public int getKeylength();

        /**
         * get the byte order on the keys
         * @return
         */
        public ByteOrder getOrdering();
        
        /**
         * check if the given file name is a part of the shard
         * @param filename
         * @return true if the file is part of the shar
         */
        public boolean isShardPart(String filename);

        public String getShardName(String filename);
    }
    
    public static class B64ShardMethod implements Method {

        private final int keylength;
        private final ByteOrder ordering;
        private final byte[] template;
        private final int charpos;
        private final String prefix;
        
        public B64ShardMethod(
                final int keylength,
                final ByteOrder ordering,
                final String prefix) {
            this.keylength = keylength;
            this.ordering = ordering;
            this.template = ASCII.getBytes(prefix + ".?");
            this.charpos = ASCII.getBytes(prefix).length + 1;
            this.prefix = prefix;
        }
        
        @Override
        public String filename(byte[] key) {
            byte[] s = new byte[this.template.length];
            System.arraycopy(this.template, 0, s, 0, s.length);
            s[this.charpos] = key[0];
            return ASCII.String(s);
        }

        @Override
        public int getKeylength() {
            return this.keylength;
        }

        @Override
        public ByteOrder getOrdering() {
            return this.ordering;
        }

        @Override
        public boolean isShardPart(String filename) {
            // TODO Auto-generated method stub
            return filename.startsWith(this.prefix) &&
                   filename.charAt(this.prefix.length()) == '.' &&
                   filename.endsWith(".heap");
        }
        
        @Override
        public String getShardName(String filename) {
            return filename.substring(0, this.template.length);
        }
    }
    
    private ConcurrentHashMap<String, MapStore> shard;
    private final File baseDir;
    private final Method shardMethod;
    
    public BEncodedHeapShard(File baseDir, Method shardMethod) {
        this.shard = new ConcurrentHashMap<String, MapStore>();
        this.baseDir = baseDir;
        this.shardMethod = shardMethod;
        init();
    }

    private void init() {
        // initialized tables map
        this.shard = new ConcurrentHashMap<String, MapStore>();
        if (!(this.baseDir.exists())) this.baseDir.mkdirs();
        String[] tablefile = this.baseDir.list();

        // open all tables of this shard
        for (final String element : tablefile) {
            if (this.shardMethod.isShardPart(element)) {
                ConcurrentLog.info("BEncodedHeapShard", "opening partial shard " + element);
                MapStore bag = openBag(element);
                this.shard.put(this.shardMethod.getShardName(element), bag);
            }
        }
    }
    
    @Override
    public synchronized void close() {
        if (this.shard == null) return;
        
        final Iterator<MapStore> i = this.shard.values().iterator();
        while (i.hasNext()) {
            i.next().close();
        }
        this.shard = null;
    }

    @Override
    public void clear() {
        close();
        final String[] l = this.baseDir.list();
        for (final String element : l) {
            if (this.shardMethod.isShardPart(element)) {
                final File f = new File(this.baseDir, element);
                if (!f.isDirectory()) FileUtils.deletedelete(f);
            }
        }
        init();
    }

    private MapStore keeperOf(final byte[] key) {
        String shardfile = this.shardMethod.filename(key);
        MapStore bag = this.shard.get(shardfile);
        if (bag != null) return bag;
        bag = openBag(shardfile);
        this.shard.put(shardfile, bag);
        return bag;
    }
    
    public MapStore openBag(String shardfile) {
        MapStore bag = new BEncodedHeapBag(
                this.baseDir,
                shardfile,
                this.shardMethod.getKeylength(),
                this.shardMethod.getOrdering(),
                10,
                ArrayStack.oneMonth * 12,
                Integer.MAX_VALUE);
        return bag;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null || !(key instanceof byte[])) return false;
        String shardfile = this.shardMethod.filename((byte[]) key);
        MapStore bag = this.shard.get(shardfile);
        if (bag == null) return false;
        return bag.containsKey(key);
    }
    
    @Override
    public Map<String, byte[]> put(byte[] key, Map<String, byte[]> map) {
        if (this.shard == null) return null;
        MapStore keeper = null;
        synchronized (this.shard) {
            keeper = keeperOf(key);
        }
        return keeper.put(key, map);
    }

    @Override
    public Map<String, byte[]> get(Object key) {
        if (key == null || !(key instanceof byte[])) return null;
        String shardfile = this.shardMethod.filename((byte[]) key);
        MapStore bag = this.shard.get(shardfile);
        if (bag == null) return null;
        return bag.get(key);
    }

    @Override
    public boolean isEmpty() {
        final Iterator<MapStore> i = this.shard.values().iterator();
        while (i.hasNext()) if (!i.next().isEmpty()) return false;
        return true;
    }

    @Override
    public int size() {
        final Iterator<MapStore> i = this.shard.values().iterator();
        int s = 0;
        while (i.hasNext()) s += i.next().size();
        return s;
    }

    @Override
    public Map<String, byte[]> remove(Object key) {
        if (key == null || !(key instanceof byte[])) return null;
        final MapStore bag;
        synchronized (this.shard) {
            bag = keeperOf((byte[]) key);
        }
        if (bag == null) return null;
        return bag.remove(key);
    }

    @Override
    public ByteOrder getOrdering() {
        return this.shardMethod.getOrdering();
    }

    @Override
    public CloneableIterator<byte[]> keyIterator() {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(this.shard.size());
        final Iterator<MapStore> i = this.shard.values().iterator();
        CloneableIterator<byte[]> k;
        while (i.hasNext()) {
            k = i.next().keyIterator();
            if (k != null && k.hasNext()) c.add(k);
        }
        return MergeIterator.cascade(c, this.shardMethod.getOrdering(), MergeIterator.simpleMerge, true);
    }
    
    private static BEncodedHeapShard testHeapShard(File f) {
        return new BEncodedHeapShard(f, new B64ShardMethod(12, Base64Order.enhancedCoder, "testshard"));
    }
    
    public static void main(String[] args) {
        File f = new File("/tmp");
        BEncodedHeapShard hb = testHeapShard(f);
        for (int i = 0; i < 10000; i++) {
            hb.put(Word.word2hash(Integer.toString(i)), BEncodedHeapBag.testMap(i));
        }
        System.out.println("test size after put = " + hb.size());
        hb.close();
        hb = testHeapShard(f);
        Iterator<Map.Entry<byte[], Map<String, byte[]>>> mi = hb.iterator();
        int c = 100;
        Map.Entry<byte[], Map<String, byte[]>> entry;
        while (mi.hasNext() && c-- > 0) {
            entry = mi.next();
            System.out.println(UTF8.String(entry.getKey()) + ": " + AbstractMapStore.map2String(entry.getValue()));
        }
        for (int i = 10000; i > 0; i--) {
            hb.remove(Word.word2hash(Integer.toString(i - 1)));
        }
        System.out.println("test size after remove = " + hb.size());
        hb.close();
        ConcurrentLog.shutdown();
    }
}
