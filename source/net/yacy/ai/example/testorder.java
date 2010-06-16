/**
 *  testorder.java
 *  Copyright 2009 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 03.12.2009 at http://yacy.net
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.ai.example;

import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;

public class testorder implements Comparable<testorder> {

    public int x;
    public testorder(int x) {
        this.x = x;
    }
    public String toString() {
        return Integer.toString(this.x);
    }

    public int compareTo(testorder o) {
        if (this.x > o.x) return 1;
        if (this.x < o.x) return -1;
        return 0;
    }
    
    public static void main(String[] args) {
        PriorityBlockingQueue<testorder> q = new PriorityBlockingQueue<testorder>();
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            q.add(new testorder(r.nextInt(20)));
        }
        while (!q.isEmpty())
            try {
                System.out.println(q.take().toString());
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
    }
}
