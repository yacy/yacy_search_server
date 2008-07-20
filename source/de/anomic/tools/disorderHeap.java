// disorderHeap.java 
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 17.05.2004
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

package de.anomic.tools;

import java.io.Serializable;
import java.util.LinkedList;

public class disorderHeap implements Serializable {
    /**
     * generated with svn4743 on 2008-04-28
     */
    private static final long serialVersionUID = -1576632540870640019L;

    LinkedList<String> list;

    public disorderHeap() {
        list = new LinkedList<String>();
    }

    public disorderHeap(int numbers) {
        // create a disorder heap with numbers in it
        // the numbers are 0..numbers-1
        this();
        for (int i = 0; i < numbers; i++) add(Integer.toString(i));
    }
    
    public synchronized void add(String element) {
        // add one element into the list at an arbitrary position
        int pos = (int) ((System.currentTimeMillis() / 7) % (list.size() + 1));
        list.add(pos, element);
    }

    public synchronized String remove() {
        if (list.size() == 0) return null;
        int pos = (int) ((System.currentTimeMillis() / 13) % list.size());
        return list.remove(pos);
    }

    public synchronized int number() {
        String n = this.remove();
        if (n == null) return -1;
        try {
            return Integer.parseInt(n);
        } catch (Exception e) {
            return -1;
    	}
    }

    public synchronized int size() {
        return list.size();
    }


    public static void main(String[] args) {
        disorderHeap ul = new disorderHeap();
        for (int i = 0; i < args.length; i++) ul.add(args[i]);
        for (int i = 0; i < args.length; i++) System.out.print(ul.remove() + " ");
        System.out.println();
    }

}