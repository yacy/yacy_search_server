// kelondroMScoreIndex.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.09.2004
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

public class kelondroMScoreIndex {
    
    private kelondroMScoreCluster scoreCluster;
    private HashMap objects; // encnt/object - relation
    private TreeMap handles; // encnt/encnt - relation, ordered by objects
    private int encnt;
    
    protected class objcomp implements Comparator {
        private HashMap os;
        public objcomp(HashMap objs) {
            os = objs;
        }
        public int compare(Object o1, Object o2) {
            if (o1 instanceof Integer) o1 = os.get(o1);
            if (o2 instanceof Integer) o2 = os.get(o2);
            return ((Comparable) o1).compareTo(o2);
        }
        public boolean equals(Object obj) {
            return false;
        }
    }
    
    public kelondroMScoreIndex()  {
        encnt = 0;
        objects = new HashMap(); // storage space for values
        handles = new TreeMap(new objcomp(objects)); // int-handle/value relation
        scoreCluster = new kelondroMScoreCluster();  // scores for int-handles
    }
    
    public long totalCount() {
        return scoreCluster.totalCount();
    }
    
    public int size() {
        return handles.size();
    }
    
    public void incScore(Object[] objs) {
        addScore(objs, 1);
    }
    
    public void addScore(Object[] objs, int count) {
        if (objs != null)
            for (int i = 0; i < objs.length; i++)
                addScore(objs[i], count);
    }
    
    public void setScore(Object[] objs, int count) {
        if (objs != null)
            for (int i = 0; i < objs.length; i++)
                setScore(objs[i], count);
    }
     
    public void incScore(Object obj) {
        addScore(obj, 1);
    }
    
    public void addScore(Object obj, int count) {
        // get handle
        Integer handle = (Integer) handles.get(obj);
        if (handle == null) {
            // new object
            handle = new Integer(encnt++);
            objects.put(handle, obj);
            handles.put(handle, handle);
        }
        // add score
        scoreCluster.addScore(handle, count);
    }
    
    public void setScore(Object obj, int count) {
        // get handle
        Integer handle = (Integer) handles.get(obj);
        if (handle == null) {
            // new object
            handle = new Integer(encnt++);
            objects.put(handle, obj);
            handles.put(handle, handle);
        }
        // set score
        scoreCluster.setScore(handle, count);
    }
    
    public void deleteScore(Object obj) {
        // get handle
        Integer handle = (Integer) handles.get(obj);
        if (handle != null) {
            objects.remove(handle);
            handles.remove(handle);
            scoreCluster.deleteScore(handle);
        }
    }

    public int getScore(Object obj) {
        // get handle
        Integer handle = (Integer) handles.get(obj);
        if (handle == null) return -1;
        return scoreCluster.getScore(handle);
    }
    
    public Object[] getScores(int count, boolean up, boolean weight, char weightsep) {
        return new Object[1];
    }
    
    public Object[] getScores(int maxCount, boolean up) {
        return getScores(maxCount, up, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    public Object[] getScores(int maxCount, boolean up, int minScore, int maxScore) {
        if (maxCount > handles.size()) maxCount = handles.size();
        Object[] s = new Object[maxCount];
        Iterator it = scores(up, minScore, maxScore);
        int i = 0;
        while ((i < maxCount) && (it.hasNext())) s[i++] = (Object) it.next();
        if (i < maxCount) {
            // re-copy the result array
            Object[] sc = new Object[i];
            System.arraycopy(s, 0, sc, 0, i);
            s = sc;
            sc = null;
        }
        return s;
    }
    
    public Iterator scores(boolean up) {
        return scores(up, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    public Iterator scores(boolean up, int minScore, int maxScore) {
        return new scoreIterator(up, minScore, maxScore);
    }
    
    private class scoreIterator implements Iterator {
        
        Iterator scoreClusterIterator;
        
        public scoreIterator(boolean up, int minScore, int maxScore) {
            this.scoreClusterIterator = scoreCluster.scores(up, minScore, maxScore);
        }
        
        public boolean hasNext() {
            return scoreClusterIterator.hasNext();
        }
        
        public Object next() {
            return objects.get(scoreClusterIterator.next());
        }
        
        public void remove() {
            scoreClusterIterator.remove();
        }
    }
    
    public static void main(String[] args) {
        System.out.println("Test for Score: start");
        long time = System.currentTimeMillis();
        kelondroMScoreIndex s = new kelondroMScoreIndex();
        for (int i = 0; i < 10000; i++) s.addScore("score#" + i + "xxx" + i + "xxx" + i + "xxx" + i + "xxx", i/10);
        System.out.println("result:");
        Object[] result;
        result = s.getScores(s.size(), true);
        for (int i = 0; i < s.size(); i++) System.out.println("up: " + result[i]);
        result = s.getScores(s.size(), false);
        for (int i = 0; i < s.size(); i++) System.out.println("down: " + result[i]);
        System.out.println("Test for Score: finish. time = " + (System.currentTimeMillis() - time));
        System.out.println("total=" + s.totalCount() + ", element=" + s.size());
    }
    
}
