// Navigator.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.03.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2010-01-29 16:59:24 +0100 (Fr, 29 Jan 2010) $
// $LastChangedRevision: 6630 $
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


package de.anomic.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Navigator {
    private ConcurrentHashMap<String, Item> map;
    
    public Navigator() {
        this.map = new ConcurrentHashMap<String, Item>();
    }
    
    /**
     * a reverse comparator for navigator items
     */
    public static final Comparator<Item> itemComp = new Comparator<Item>() {
        public int compare(Item o1, Item o2) {
            if (o1.count < o2.count) return 1;
            if (o2.count < o1.count) return -1;
            return 0;
        }
    };
    
    public void inc(String key, String name) {
        Item item = map.get(key);
        if (item == null) {
            map.put(key, new Item(name));
        } else {
            item.inc();
        }
    }
    
    public Map<String, Item> map() {
        return this.map;
    }
    
    public Item[] entries() {
        Item[] ii = this.map.values().toArray(new Item[this.map.size()]);
        Arrays.sort(ii, itemComp);
        return ii;
    }
    
    public List<Item> entries(int maxcount) {
        Item[] ii = entries();
        int c = Math.min(ii.length, maxcount);
        ArrayList<Item> a = new ArrayList<Item>(c);
        for (int i = 0; i < c; i++) a.add(ii[i]);
        return a;
    }
    
    public static class Item {
        public int count;
        public String name;
        public Item(String name) {
            this.count = 1;
            this.name = name;
        }
        public Item(String name, int count) {
            this.count = count;
            this.name = name;
        }
        public void inc() {
            this.count++;
        }
    }
}
