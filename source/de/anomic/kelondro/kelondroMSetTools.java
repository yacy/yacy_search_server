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
import java.util.ArrayList;
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
    // The algorithm to perform the join can be also of two kind:
    // - join by pairwise enumeration
    // - join by iterative tests (where we distinguish left-right and right-left tests)

    
    public static <A, B> TreeMap<A, B> joinConstructive(Collection<TreeMap<A, B>> maps, boolean concatStrings) {
        // this joins all TreeMap(s) contained in maps
        
        // first order entities by their size
        TreeMap<Long, TreeMap<A, B>> orderMap = new TreeMap<Long, TreeMap<A, B>>();
        TreeMap<A, B> singleMap;
        Iterator<TreeMap<A, B>> i = maps.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleMap = i.next();
            
            // check result
            if ((singleMap == null) || (singleMap.size() == 0)) return new TreeMap<A, B>();
            
            // store result in order of result size
            orderMap.put(new Long(singleMap.size() * 1000 + count), singleMap);
            count++;
        }
        
        // check if there is any result
        if (orderMap.size() == 0) return new TreeMap<A, B>();
        
        // we now must pairwise build up a conjunction of these maps
        Long k = (Long) orderMap.firstKey(); // the smallest, which means, the one with the least entries
        TreeMap<A, B> mapA, mapB, joinResult = (TreeMap<A, B>) orderMap.remove(k);
        while ((orderMap.size() > 0) && (joinResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (Long) orderMap.firstKey(); // the next smallest...
            mapA = joinResult;
            mapB = (TreeMap<A, B>) orderMap.remove(k);
            joinResult = joinConstructiveByTest(mapA, mapB, concatStrings); // TODO: better with enumeration?
            // free resources
            mapA = null;
            mapB = null;
        }

        // in 'searchResult' is now the combined search result
        if (joinResult.size() == 0) return new TreeMap<A, B>();
        return joinResult;
    }
    
    public static <A, B> TreeMap<A, B> joinConstructive(TreeMap<A, B> map1, TreeMap<A, B> map2, boolean concatStrings) {
        // comparators must be equal
        if ((map1 == null) || (map2 == null)) return null;
        if (map1.comparator() != map2.comparator()) return null;
        if ((map1.size() == 0) || (map2.size() == 0)) return new TreeMap<A, B>(map1.comparator());

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
    
    private static <A, B> TreeMap<A, B> joinConstructiveByTest(TreeMap<A, B> small, TreeMap<A, B> large, boolean concatStrings) {
        Iterator<Map.Entry<A, B>> mi = small.entrySet().iterator();
        TreeMap<A, B> result = new TreeMap<A, B>(large.comparator());
        Map.Entry<A, B> mentry1;
        B mobj2;
        while (mi.hasNext()) {
            mentry1 = mi.next();
            mobj2 = large.get(mentry1.getKey());
            if (mobj2 != null) {
                if (mentry1.getValue() instanceof String) {
                    result.put(mentry1.getKey(), (B) ((concatStrings) ? (mentry1.getValue() + (String) mobj2) : mentry1.getValue()));
                } else {
                    result.put(mentry1.getKey(), mentry1.getValue());
                }
            }
        }
        return result;
    }

    private static <A, B> TreeMap<A, B> joinConstructiveByEnumeration(TreeMap<A, B> map1, TreeMap<A, B> map2, boolean concatStrings) {
        // implement pairwise enumeration
        Comparator<? super A> comp = map1.comparator();
        Iterator<Map.Entry<A, B>> mi1 = map1.entrySet().iterator();
        Iterator<Map.Entry<A, B>> mi2 = map2.entrySet().iterator();
        TreeMap<A, B> result = new TreeMap<A, B>(map1.comparator());
        int c;
        if ((mi1.hasNext()) && (mi2.hasNext())) {
            Map.Entry<A, B> mentry1 = mi1.next();
            Map.Entry<A, B> mentry2 = mi2.next();
            while (true) {
            	c = comp.compare(mentry1.getKey(), mentry2.getKey());
                if (c < 0) {
                    if (mi1.hasNext()) mentry1 = mi1.next(); else break;
                } else if (c > 0) {
                    if (mi2.hasNext()) mentry2 = mi2.next(); else break;
                } else {
                    if (mentry1.getValue() instanceof String) {
                        result.put(mentry1.getKey(), (B) ((concatStrings) ? ((String) mentry1.getValue() + (String) mentry2.getValue()) : (String) mentry1.getValue()));
                    } else {
                        result.put(mentry1.getKey(), mentry1.getValue());
                    }
                    if (mi1.hasNext()) mentry1 = mi1.next(); else break;
                    if (mi2.hasNext()) mentry2 = mi2.next(); else break;
                }
            }
        }
        return result;
    }
    
    // now the same for set-set
    public static <A> TreeSet<A> joinConstructive(TreeSet<A> set1, TreeSet<A> set2) {
    	// comparators must be equal
        if ((set1 == null) || (set2 == null)) return null;
        if (set1.comparator() != set2.comparator()) return null;
        if ((set1.size() == 0) || (set2.size() == 0)) return new TreeSet<A>(set1.comparator());

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

    private static <A> TreeSet<A> joinConstructiveByTest(TreeSet<A> small, TreeSet<A> large) {
    	Iterator<A> mi = small.iterator();
    	TreeSet<A> result = new TreeSet<A>(small.comparator());
    	A o;
    	while (mi.hasNext()) {
    		o = mi.next();
    		if (large.contains(o)) result.add(o);
    	}
    	return result;
    }

    private static <A> TreeSet<A> joinConstructiveByEnumeration(TreeSet<A> set1, TreeSet<A> set2) {
    	// implement pairvise enumeration
    	Comparator<? super A> comp = set1.comparator();
    	Iterator<A> mi = set1.iterator();
    	Iterator<A> si = set2.iterator();
    	TreeSet<A> result = new TreeSet<A>(set1.comparator());
    	int c;
    	if ((mi.hasNext()) && (si.hasNext())) {
    		A mobj = mi.next();
    		A sobj = si.next();
    		while (true) {
    			c = comp.compare(mobj, sobj);
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
    
    // now the same for set-set
    public static <A> boolean anymatch(TreeSet<A> set1, TreeSet<A> set2) {
		// comparators must be equal
		if ((set1 == null) || (set2 == null)) return false;
		if (set1.comparator() != set2.comparator()) return false;
		if ((set1.size() == 0) || (set2.size() == 0)) return false;

		// decide which method to use
		int high = ((set1.size() > set2.size()) ? set1.size() : set2.size());
		int low = ((set1.size() > set2.size()) ? set2.size() : set1.size());
		int stepsEnum = 10 * (high + low - 1);
		int stepsTest = 12 * log2a(high) * low;

		// start most efficient method
		if (stepsEnum > stepsTest) {
			if (set1.size() < set2.size()) return anymatchByTest(set1, set2);
			return anymatchByTest(set2, set1);
		}
		return anymatchByEnumeration(set1, set2);
	}

	private static <A> boolean anymatchByTest(TreeSet<A> small, TreeSet<A> large) {
		Iterator<A> mi = small.iterator();
		A o;
		while (mi.hasNext()) {
			o = mi.next();
			if (large.contains(o)) return true;
		}
		return false;
	}

    private static <A> boolean anymatchByEnumeration(TreeSet<A> set1, TreeSet<A> set2) {
		// implement pairvise enumeration
		Comparator<? super A> comp = set1.comparator();
		Iterator<A> mi = set1.iterator();
		Iterator<A> si = set2.iterator();
		int c;
		if ((mi.hasNext()) && (si.hasNext())) {
			A mobj = mi.next();
			A sobj = si.next();
			while (true) {
				c = comp.compare(mobj, sobj);
				if (c < 0) {
					if (mi.hasNext()) mobj = mi.next(); else break;
				} else if (c > 0) {
					if (si.hasNext()) sobj = si.next(); else break;
				} else {
					return true;
				}
			}
		}
		return false;
	}
    
    // ------------------------------------------------------------------------------------------------
    // exclude

    public static <A, B> TreeMap<A, B> excludeConstructive(TreeMap<A, B> map, TreeSet<A> set) {
        // comparators must be equal
        if (map == null) return null;
        if (set == null) return map;
        if ((map.size() == 0) || (set.size() == 0)) return map;
        if (map.comparator() != set.comparator()) return excludeConstructiveByTestMapInSet(map, set);
        return excludeConstructiveByTestMapInSet(map, set);
        // return excludeConstructiveByEnumeration(map, set);
    }
    
    private static <A, B> TreeMap<A, B> excludeConstructiveByTestMapInSet(TreeMap<A, B> map, TreeSet<A> set) {
        Iterator<A> mi = map.keySet().iterator();
        TreeMap<A, B> result = new TreeMap<A, B>(map.comparator());
        A o;
        while (mi.hasNext()) {
            o = mi.next();
            if (!(set.contains(o))) result.put(o, map.get(o));
        }
        return result;
    }
    
    public static <A, B> void excludeDestructive(TreeMap<A, B> map, TreeSet<A> set) {
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
    
    private static <A, B> void excludeDestructiveByTestMapInSet(TreeMap<A, B> map, TreeSet<A> set) {
        Iterator<A> mi = map.keySet().iterator();
        while (mi.hasNext()) if (set.contains(mi.next())) mi.remove();
    }
    
    private static <A, B> void excludeDestructiveByTestSetInMap(TreeMap<A, B> map, TreeSet<A> set) {
        Iterator<A> si = set.iterator();
        while (si.hasNext()) map.remove(si.next());
    }
    
    // and the same again with set-set
    public static <A> void excludeDestructive(TreeSet<A> set1, TreeSet<A> set2) {
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
    
    private static <A> void excludeDestructiveByTestSmallInLarge(TreeSet<A> small, TreeSet<A> large) {
        Iterator<A> mi = small.iterator();
        while (mi.hasNext()) if (large.contains(mi.next())) mi.remove();
    }
    
    private static <A> void excludeDestructiveByTestLargeInSmall(TreeSet<A> large, TreeSet<A> small) {
        Iterator<A> si = small.iterator();
        while (si.hasNext()) large.remove(si.next());
    }
    
    // ------------------------------------------------------------------------------------------------

    public static TreeMap<String, String> loadMap(String filename, String sep) {
        TreeMap<String, String> map = new TreeMap<String, String>();
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
    
    public static TreeMap<String, ArrayList<String>> loadMapMultiValsPerKey(String filename, String sep) {
        TreeMap<String, ArrayList<String>> map = new TreeMap<String, ArrayList<String>>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
            String line, key, value;
            int pos;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if ((line.length() > 0) && (!(line.startsWith("#"))) && ((pos = line.indexOf(sep)) > 0)) {
                    key = line.substring(0, pos).trim().toLowerCase();
                    value = line.substring(pos + sep.length()).trim();
                    if (!map.containsKey(key)) map.put(key, new ArrayList<String>());
                    map.get(key).add(value);
                }
            }
        } catch (IOException e) {            
        } finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
        }
        return map;
    }
    
    public static TreeSet<String> loadList(File file, Comparator<String> c) {
        TreeSet<String> list = new TreeSet<String>(c);
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
    
    public static String setToString(Set<String> set, char separator) {
        Iterator<String> i = set.iterator();
        StringBuffer sb = new StringBuffer(set.size() * 7);
        if (i.hasNext()) sb.append(i.next());
        while (i.hasNext()) {
            sb.append(separator).append(i.next());
        }
        return new String(sb);
    }
    
    // ------------------------------------------------------------------------------------------------

    
    public static void main(String[] args) {
	TreeMap<String, String> m = new TreeMap<String, String>();
	TreeMap<String, String> s = new TreeMap<String, String>();
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
	//System.out.println("Exclude=" + excludeConstructiveByTestMapInSet(m, s.keySet()));

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
