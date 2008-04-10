// kelondroLock.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 08.12.2005
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

public class kelondroLock {

    private boolean lock;
    private long releaseTime;

    public kelondroLock() {
        lock = false;
        releaseTime = 0;
    }
    
    public synchronized void apply() {
        // this applies a lock to this objekt
        // if the lock is already applied
        // this object waits until it is released and applies it then again
        // before it returns
        apply(-1);
    }

    public synchronized void apply(long millis) {
        // sets a lock that is valid for a specific time
        // if the time is over, the lock is released automatically
        if (locked()) {
            do {
                try { wait(100); }
                catch (InterruptedException e) { }
                catch (Exception e) { e.printStackTrace(); }
            } while (locked());
        }
        lock = true;
        releaseTime = (millis < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + millis;
    }

    public synchronized boolean stay(long waitMillis, long lockMillis) {
        // this tries to apply a lock, but does not block infinitely until
        // the lock is released. If after the given time the lock is not released
        // the method returns with false and no more lock is applied
        // if the lock was applied successfully, it returns with true
        if (locked()) {
            try { wait(waitMillis); }
            catch (InterruptedException e) { }
            catch (Exception e) { e.printStackTrace(); }
            if (locked()) return false;
        }
        lock = true;
        releaseTime = (lockMillis < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + lockMillis;
        return true;
    }

    public synchronized void release() {
        // this must be called to releas the lock to the object
        // if it is not released, possibly all other tasks are blocked
        if (locked()) {
            lock = false;
            releaseTime = 0;
            notifyAll();
        }
    }

    public synchronized boolean locked() {
        return lock && (System.currentTimeMillis() < releaseTime);
    }
}
