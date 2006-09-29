// kelondroMSetTools.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.12.2004
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class kelondroMSetTools {

    
    //public static Comparator fastStringComparator = fastStringComparator(true);

    // ------------------------------------------------------------------------------------------------
    // helper methods

    private static int compare(Object a, Object b, Comparator c) {
	if (c != null) return c.compare(a,b);
	if ((a instanceof String) && (b instanceof String)) return ((String) a).compareTo((String) b);
	throw new ClassCastException();
    }

    public static int log2a(int x) {
        // this computes 1 + log2
        // it is the number of bits in x, not the logarithmus by 2
	int l = 0;
	while (x > 0) {x = x >>> 1; l++;}
	return l;
    }

    // ------------------------------------------------------------------------------------------------
    // join
    // We distinguish two principal solutions
    // - constructive join (generate new data structure)
    // - destructive join (remove non-valid elements from given data structure)
    // The alogorithm to perform the join can be also of two kind:
    // - join by pairvise enumeration
    // - join by iterative tests (where we distinguish left-right and right-left tests)

    
    public static TreeMap joinConstructive(Collection maps, boolean concatStrings) {
        // this joins all TreeMap(s) contained in maps
        
        // first order entities by their size
        TreeMap orderMap = new TreeMap();
        TreeMap singleMap;
        Iterator i = maps.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleMap = (TreeMap) i.next();
            
            // check result
            if ((singleMap == null) || (singleMap.size() == 0)) return new TreeMap();
            
            // store result in order of result size
            orderMap.put(new Long(singleMap.size() * 1000 + count), singleMap);
            count++;
        }
        
        // check if there is any result
        if (orderMap.size() == 0) return new TreeMap();
        
        // we now must pairwise build up a conjunction of these maps
        Long k = (Long) orderMap.firstKey(); // the smallest, which means, the one with the least entries
        TreeMap mapA, mapB, joinResult = (TreeMap) orderMap.remove(k);
        while ((orderMap.size() > 0) && (joinResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (Long) orderMap.firstKey(); // the next smallest...
            mapA = joinResult;
            mapB = (TreeMap) orderMap.remove(k);
            joinResult = joinConstructiveByTest(mapA, mapB, concatStrings);
            // free resources
            mapA = null;
            mapB = null;
        }

        // in 'searchResult' is now the combined search result
        if (joinResult.size() == 0) return new TreeMap();
        return joinResult;
    }
    
    public static TreeMap joinConstructive(TreeMap map1, TreeMap map2, boolean concatStrings) {
        // comparators must be equal
        if ((map1 == null) || (map2 == null)) return null;
        if (map1.comparator() != map2.comparator()) return null;
        if ((map1.size() == 0) || (map2.size() == 0)) return new TreeMap(map1.comparator());

        // decide which method to use
        int high = ((map1.size() > map2.size()) ? map1.size() : map2.size());
        int low = ((map1.size() > map2.size()) ? map2.size() : map1.size());
        int stepsEnum = 10 * (high + low - 1);
        int stepsTest = 12 * log2a(high) * low;

        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (map1.size() > map2.size()) return joinConstructiveByTest(map2, map1, concatStrings);
            return joinConstructiveByTest(map1, map2, concatStrings);
        }
        return joinConstructiveByEnumeration(map1, map2, concatStrings);
    }
    
    private static TreeMap joinConstructiveByTest(TreeMap small, TreeMap large, boolean concatStrings) {
        Iterator mi = small.entrySet().iterator();
        TreeMap result = new TreeMap(large.comparator());
        Map.Entry mentry1;
        Object mobj2;
        while (mi.hasNext()) {
            mentry1 = (Map.Entry) mi.next();
            mobj2 = large.get(mentry1.getKey());
            if (mobj2 != null) result.put(mentry1.getKey(), (concatStrings) ? ((String) mentry1.getValue() + (String) mobj2) : mentry1.getValue());
        }
        return result;
    }

    private static TreeMap joinConstructiveByEnumeration(TreeMap map1, TreeMap map2, boolean concatStrings) {
        // implement pairvise enumeration
        Comparator comp = map1.comparator();
        Iterator mi1 = map1.entrySet().iterator();
        Iterator mi2 = map2.entrySet().iterator();
        TreeMap result = new TreeMap(map1.comparator());
        int c;
        if ((mi1.hasNext()) && (mi2.hasNext())) {
            Map.Entry mentry1 = (Map.Entry) mi1.next();
            Map.Entry mentry2 = (Map.Entry) mi2.next();
            while (true) {
                c = compare(mentry1.getKey(), mentry2.getKey(), comp);
                if (c < 0) {
                    if (mi1.hasNext()) mentry1 = (Map.Entry) mi1.next(); else break;
                } else if (c > 0) {
                    if (mi2.hasNext()) mentry2 = (Map.Entry) mi2.next(); else break;
                } else {
                    result.put(mentry1.getKey(), (concatStrings) ? ((String) mentry1.getValue() + (String) mentry2.getValue()) : mentry1.getValue());
                    if (mi1.hasNext()) mentry1 = (Map.Entry) mi1.next(); else break;
                    if (mi2.hasNext()) mentry2 = (Map.Entry) mi2.next(); else break;
                }
            }
        }
        return result;
    }
    
    // now the same for set-set
    public static TreeSet joinConstructive(TreeSet set1, TreeSet set2) {
	// comparators must be equal
        if ((set1 == null) || (set2 == null)) return null;
	if (set1.comparator() != set2.comparator()) return null;
        if ((set1.size() == 0) || (set2.size() == 0)) return new TreeSet(set1.comparator());

	// decide which method to use
	int high = ((set1.size() > set2.size()) ? set1.size() : set2.size());
	int low  = ((set1.size() > set2.size()) ? set2.size() : set1.size());
	int stepsEnum = 10 * (high + low - 1);
	int stepsTest = 12 * log2a(high) * low;

	// start most efficient method
	if (stepsEnum > stepsTest) {
	    if (set1.size() < set2.size()) return joinConstructiveByTest(set1, set2);
        return joinConstructiveByTest(set2, set1);
	}
	return joinConstructiveByEnumeration(set1, set2);
    }

    private static TreeSet joinConstructiveByTest(TreeSet small, TreeSet large) {
	Iterator mi = small.iterator();
	TreeSet result = new TreeSet(small.comparator());
	Object o;
	while (mi.hasNext()) {
	    o = mi.next();
	    if (large.contains(o)) result.add(o);
	}
	return result;
    }

    private static TreeSet joinConstructiveByEnumeration(TreeSet set1, TreeSet set2) {
	// implement pairvise enumeration
	Comparator comp = set1.comparator();
	Iterator mi = set1.iterator();
	Iterator si = set2.iterator();
	TreeSet result = new TreeSet(set1.comparator());
	int c;
	if ((mi.hasNext()) && (si.hasNext())) {
	    Object mobj = mi.next();
	    Object sobj = si.next();
	    while (true) {
		c = compare(mobj, sobj, comp);
		if (c < 0) {
		    if (mi.hasNext()) mobj = mi.next(); else break;
		} else if (c > 0) {
		    if (si.hasNext()) sobj = si.next(); else break;
		} else {
		    result.add(mobj);
		    if (mi.hasNext()) mobj = mi.next(); else break;
		    if (si.hasNext()) sobj = si.next(); else break;
		}
	    }
	}
	return result;
    }
    
    
    
    // ------------------------------------------------------------------------------------------------
    // exclude

    public static TreeMap excludeConstructive(TreeMap map, TreeSet set) {
        // comparators must be equal
        if (map == null) return null;
        if (set == null) return map;
        if ((map.size() == 0) || (set.size() == 0)) return map;
        if (map.comparator() != set.comparator()) return excludeConstructiveByTestMapInSet(map, set);
        return excludeConstructiveByTestMapInSet(map, set);
        // return excludeConstructiveByEnumeration(map, set);
    }
    
    private static TreeMap excludeConstructiveByTestMapInSet(TreeMap map, Set set) {
        Iterator mi = map.keySet().iterator();
        TreeMap result = new TreeMap(map.comparator());
        Object o;
        while (mi.hasNext()) {
            o = mi.next();
            if (!(set.contains(o))) result.put(o, map.get(o));
        }
        return result;
    }

    /*
    private static TreeMap excludeConstructiveByEnumeration(TreeMap map, TreeSet set) {
        // returns map without the elements in set
        // enumerates objects
        Comparator comp = map.comparator();
        Iterator mi = map.keySet().iterator();
        Iterator si = set.iterator();
        TreeMap result = new TreeMap(map.comparator());
        int c;
        if ((mi.hasNext()) && (si.hasNext())) {
            Object mobj = mi.next();
            Object sobj = si.next();
            while (true) {
                c = compare(mobj, sobj, comp);
                if (c < 0) {
                    result.put(mobj, map.get(mobj));
                    if (mi.hasNext()) mobj = mi.next();
                    else break;
                } else if (c > 0) {
                    if (si.hasNext()) sobj = si.next();
                    else break;
                } else {
                    if (mi.hasNext()) mobj = mi.next();
                    else break;
                    if (si.hasNext()) sobj = si.next();
                    else {
                        // final flush
                        result.put(mobj, map.get(mobj));
                        while (mi.hasNext()) {
                            mobj = mi.next();
                            result.put(mobj, map.get(mobj));
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }
    */
    public static void excludeDestructive(TreeMap map, TreeSet set) {
        // comparators must be equal
        if (map == null) return;
        if (set == null) return;
        if (map.comparator() != set.comparator()) return;
        if ((map.size() == 0) || (set.size() == 0)) return;

        if (map.size() < set.size())
            excludeDestructiveByTestMapInSet(map, set);
        else
            excludeDestructiveByTestSetInMap(map, set);
    }
    
    private static void excludeDestructiveByTestMapInSet(TreeMap map, TreeSet set) {
        Iterator mi = map.keySet().iterator();
        while (mi.hasNext()) if (set.contains(mi.next())) mi.remove();
    }
    
    private static void excludeDestructiveByTestSetInMap(TreeMap map, TreeSet set) {
        Iterator si = set.iterator();
        while (si.hasNext()) map.remove(si.next());
    }
    
    // and the same again with set-set
    public static void excludeDestructive(TreeSet set1, TreeSet set2) {
        // comparators must be equal
        if (set1 == null) return;
        if (set2 == null) return;
        if (set1.comparator() != set2.comparator()) return;
        if ((set1.size() == 0) || (set2.size() == 0)) return;
        
        if (set1.size() < set2.size())
            excludeDestructiveByTestSmallInLarge(set1, set2);
        else
            excludeDestructiveByTestLargeInSmall(set1, set2);
    }
    
    private static void excludeDestructiveByTestSmallInLarge(TreeSet small, TreeSet large) {
        Iterator mi = small.iterator();
        while (mi.hasNext()) if (large.contains(mi.next())) mi.remove();
    }
    
    private static void excludeDestructiveByTestLargeInSmall(TreeSet large, TreeSet small) {
        Iterator si = small.iterator();
        while (si.hasNext()) large.remove(si.next());
    }
    
    // ------------------------------------------------------------------------------------------------

    public static TreeMap loadMap(String filename, String sep) {
        TreeMap map = new TreeMap();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
            String line;
            int pos;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if ((line.length() > 0) && (!(line.startsWith("#"))) && ((pos = line.indexOf(sep)) > 0))
                    map.put(line.substring(0, pos).trim().toLowerCase(), line.substring(pos + sep.length()).trim());
            }
        } catch (IOException e) {            
        } finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
        }
        return map;
    }
    
    public static TreeSet loadList(File file, Comparator c) {
        TreeSet list = new TreeSet(c);
        if (!(file.exists())) return list;
        
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if ((line.length() > 0) && (!(line.startsWith("#")))) list.add(line.trim().toLowerCase());
            }
            br.close();
        } catch (IOException e) {            
        } finally {
            if (br != null) try{br.close();}catch(Exception e){}
        }
        return list;
    }
    
    // ------------------------------------------------------------------------------------------------

    
    public static void main(String[] args) {
	TreeMap m = new TreeMap();
	TreeMap s = new TreeMap();
	m.put("a", "a");
	m.put("x", "x");
	m.put("f", "f");
	m.put("h", "h");
	m.put("w", "w");
	m.put("7", "7");
	m.put("t", "t");
	m.put("k", "k");
	m.put("y", "y");
	m.put("z", "z");
	s.put("a", "a");
	s.put("b", "b");
	s.put("c", "c");
	s.put("k", "k");
	s.put("l", "l");
	s.put("m", "m");
	s.put("n", "n");
	s.put("o", "o");
	s.put("p", "p");
	s.put("q", "q");
	s.put("r", "r");
	s.put("s", "s");
	s.put("t", "t");
	s.put("x", "x");
	System.out.println("Compare " + m.toString() + " with " + s.toString());
	System.out.println("Join=" + joinConstructiveByEnumeration(m, s, true));
	System.out.println("Join=" + joinConstructiveByTest(m, s, true));
	System.out.println("Join=" + joinConstructiveByTest(m, s, true));
	System.out.println("Join=" + joinConstructive(m, s, true));
	System.out.println("Exclude=" + excludeConstructiveByTestMapInSet(m, s.keySet()));

	/*
	for (int low = 0; low < 10; low++)
	    for (int high = 0; high < 100; high=high + 10) {
		int stepsEnum = 10 * high;
		int stepsTest = 12 * log2(high) * low;
		System.out.println("low=" + low + ", high=" + high + ", stepsEnum=" + stepsEnum + ", stepsTest=" + stepsTest + "; best method is " + ((stepsEnum < stepsTest) ? "joinByEnumeration" : "joinByTest"));
	    }
	*/

    }

}
