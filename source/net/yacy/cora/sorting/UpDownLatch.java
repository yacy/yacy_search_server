/**
 *  UpDownLatch
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 16.07.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.sorting;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class UpDownLatch extends AbstractQueuedSynchronizer {

    private static final long serialVersionUID = 1L;

    public UpDownLatch(final int count) {
        setState(count);
    }

    public int getCount() {
        return getState();
    }

    @Override
    public int tryAcquireShared(final int acquires) {
        return getState() == 0? 1 : -1;
    }

    @Override
    public boolean tryReleaseShared(final int releases) {
        // Decrement count; signal when transition to zero
        for (;;) {
            final int c = getState();
            if (c == 0) return false;
            final int nextc = c-1;
            if (compareAndSetState(c, nextc)) return nextc == 0;
        }
    }

    public void countUp() {
        for (;;) {
            final int c = getState();
            if (compareAndSetState(c, c + 1)) return;
        }
    }

    public void countDown() {
        releaseShared(1);
    }

    public void await() throws InterruptedException {
        acquireSharedInterruptibly(1);
    }
}
