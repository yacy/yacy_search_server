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

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

public class kelondroMSetTools {

    
    public static Comparator fastStringComparator = fastStringComparator(true);

    // ------------------------------------------------------------------------------------------------
    // helper methods

    private static int compare(Object a, Object b, Comparator c) {
	if (c != null) return c.compare(a,b);
	if ((a instanceof String) && (b instanceof String)) return ((String) a).compareTo((String) b);
	throw new ClassCastException();
    }

    private static int log2(int x) {
	int l = 0;
	while (x > 0) {x = x >> 1; l++;}
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

    public static TreeMap joinConstructive(TreeMap map, TreeSet set) {
	// comparators must be equal
        if ((map == null) || (set == null)) return null;
	if (map.comparator() != set.comparator()) return null;
        if ((map.size() == 0) || (set.size() == 0)) return new TreeMap(map.comparator());

	// decide which method to use
	int high = ((map.size() > set.size()) ? map.size() : set.size());
	int low  = ((map.size() > set.size()) ? set.size() : map.size());
	int stepsEnum = 10 * (high + low - 1);
	int stepsTest = 12 * log2(high) * low;

	// start most efficient method
	if (stepsEnum > stepsTest) {
	    if (map.size() < set.size())
                return joinConstructiveByTestSetInMap(map, set);
            else
                return joinConstructiveByTestMapInSet(map, set);
	} else {
	    return joinConstructiveByEnumeration(map, set);
        }
    }

    private static TreeMap joinConstructiveByTestSetInMap(TreeMap map, TreeSet set) {
	Iterator si = set.iterator();
	TreeMap result = new TreeMap(map.comparator());
	Object o;
	while (si.hasNext()) {
	    o = si.next();
	    if (map.containsKey(o)) result.put(o, map.get(o));
	}
	return result;
    }

    private static TreeMap joinConstructiveByTestMapInSet(TreeMap map, TreeSet set) {
	Iterator mi = map.keySet().iterator();
	TreeMap result = new TreeMap(map.comparator());
	Object o;
	while (mi.hasNext()) {
	    o = mi.next();
	    if (set.contains(o)) result.put(o, map.get(o));
	}
	return result;
    }

    private static TreeMap joinConstructiveByEnumeration(TreeMap map, TreeSet set) {
	// implement pairvise enumeration
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
		    if (mi.hasNext()) mobj = mi.next(); else break;
		} else if (c > 0) {
		    if (si.hasNext()) sobj = si.next(); else break;
		} else {
		    result.put(mobj, map.get(mobj));
		    if (mi.hasNext()) mobj = mi.next(); else break;
		    if (si.hasNext()) sobj = si.next(); else break;
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
	int stepsTest = 12 * log2(high) * low;

	// start most efficient method
	if (stepsEnum > stepsTest) {
	    if (set1.size() < set2.size())
                return joinConstructiveByTest(set1, set2);
            else
                return joinConstructiveByTest(set2, set1);
	} else {
	    return joinConstructiveByEnumeration(set1, set2);
        }
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
	if (map.comparator() != set.comparator()) return null;
        if ((map.size() == 0) || (set.size() == 0)) return map;
        
        return excludeConstructiveByTestMapInSet(map, set);
        //return excludeConstructiveByEnumeration(map, set);
    }
    
    private static TreeMap excludeConstructiveByTestMapInSet(TreeMap map, TreeSet set) {
	Iterator mi = map.keySet().iterator();
	TreeMap result = new TreeMap(map.comparator());
	Object o;
	while (mi.hasNext()) {
	    o = mi.next();
	    if (!(set.contains(o))) result.put(o, map.get(o));
	}
	return result;
    }

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
		    if (mi.hasNext()) mobj = mi.next(); else break;
		} else if (c > 0) {
		    if (si.hasNext()) sobj = si.next(); else break;
		} else {
		    if (mi.hasNext()) mobj = mi.next(); else break;
		    if (si.hasNext()) sobj = si.next(); else {
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

    public static Comparator fastStringComparator(boolean ascending) {
        return new stringComparator(ascending);
    }
    
    private static class stringComparator implements Comparator {
        // fast ordering
	boolean asc = true;
	public stringComparator(boolean ascending) {
	    asc = ascending;
	}
	public int compare(Object o1, Object o2) {
	    // returns o1<o2:-1 , o1=p2:0 , o1>o2:1
	    int l1 = ((String) o1).length();
	    int l2 = ((String) o2).length();
	    if (l1 == l2) {
		for (int i = 0; i < l1; i++) {
		    if (((byte) ((String) o1).charAt(i)) < ((byte) ((String) o2).charAt(i))) return (asc) ? -1 :  1;
		    if (((byte) ((String) o1).charAt(i)) > ((byte) ((String) o2).charAt(i))) return (asc) ?  1 : -1;
		}
		return 0;
		//return ((String) o1).compareTo((String) o2);
	    } else {
		return l1 < l2 ? ((asc) ? -1 : 1) : ((asc) ? 1 : -1);
	    }
	}
	public boolean equals(Object obj) {
	    return false;
	}
    }
    
    // ------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
	TreeMap m = new TreeMap();
	TreeSet s = new TreeSet();
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
	s.add("a");
	s.add("b");
	s.add("c");
	s.add("k");
	s.add("l");
	s.add("m");
	s.add("n");
	s.add("o");
	s.add("p");
	s.add("q");
	s.add("r");
	s.add("s");
	s.add("t");
	s.add("x");
	System.out.println("Compare " + m.toString() + " with " + s.toString());
	System.out.println("Join=" + joinConstructiveByEnumeration(m, s));
	System.out.println("Join=" + joinConstructiveByTestMapInSet(m, s));
	System.out.println("Join=" + joinConstructiveByTestSetInMap(m, s));
	System.out.println("Join=" + joinConstructive(m, s));
	System.out.println("Exclude=" + excludeConstructiveByEnumeration(m, s));

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
