// serverProfiling.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.server;

import java.util.ArrayList;
import java.util.Iterator;

public class serverProfiling implements Cloneable {

    private ArrayList yield;
    private long timer;

    public serverProfiling() {
        yield = new ArrayList();
        timer = 0;
    }

    public static class Entry {
        public String process;
        public int count;
        public long time;

        public Entry(String process, int count, long time) {
            this.process = process;
            this.count = count;
            this.time = time;
        }
    }

    public void startTimer() {
        this.timer = System.currentTimeMillis();
    }

    public void yield(String s, int count) {
        long t = System.currentTimeMillis() - this.timer;
        Entry e = new Entry(s, count, t);
        yield.add(e);
    }

    public Iterator events() {
        // iteratese Entry-type Objects
        return yield.iterator();
    }

    public int size() {
        // returns number of events / Entry-Objects in yield array
        return yield.size();
    }

}
