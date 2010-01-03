// Attempts.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.12.2009 on http://yacy.net;
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
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

package net.yacy.ai.greedy;

import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;

/**
 * the attempts object is a stack of Challenges that are ordered by the
 * priority of the findings within the single challenges
 * @param <SpecificRole>
 */
public class Attempts<SpecificRole extends Role> {

    TreeMap<Long, BlockingQueue<Finding<SpecificRole>>> stack;
    
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
            } catch (InterruptedException e) {
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
