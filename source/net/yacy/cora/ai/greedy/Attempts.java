/**
 *  Attempts.java
 *  Copyright 2009 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 03.12.2009 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.ai.greedy;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

/**
 * the attempts object is a stack of Challenges that are ordered by the
 * priority of the findings within the single challenges
 * @param <SpecificRole>
 */
public class Attempts<SpecificRole extends Role> {

    Map<Long, BlockingQueue<Finding<SpecificRole>>> stack;
    
    public Attempts() {
        this.stack = new TreeMap<Long, BlockingQueue<Finding<SpecificRole>>>();
    }

    public int size() {
        int s = 0;
        for (BlockingQueue<Finding<SpecificRole>> q: stack.values()) s += q.size();
        return s;
    }
    /*
    public void push(final Finding element) {
        BlockingQueue<Finding> q = this.stack.get(element.getPriority());
        if (q == null) synchronized (this) {
            q = this.stack.get(weight);
            if (q == null) q = new LinkedBlockingQueue<E>();
            try {
                q.put(element);
                this.stack.put(weight, q);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public stackElement top() {
    }
    
    public stackElement pop() {
    }
    
    public boolean exists(final E element) {
    }
    
    public boolean exists(final int hashcode) {
    }
    
    public stackElement get(final int hashcode) {
    }
    
    public stackElement remove(final int hashcode) {
    }
    
    public boolean bottom(final long weight) {
    }
    */
}
