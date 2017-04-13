/**
 *  Shutdown
 *  Copyright 2012 by Michael Peter Christen
 *  First released 24.07.2012 at http://yacy.net
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

package net.yacy.search;

import net.yacy.cora.util.ConcurrentLog;

/**
 * Thread used to trigger the server shutdown after a given delay.
 */
public class Shutdown extends Thread {
    private final Switchboard sb;
    private final long delay;
    private final String reason;

    /**
     * @param sb Switchboard instance
     * @param delay delay in milliseconds
     * @param reason shutdown reason for log information
     */
    public Shutdown(final Switchboard sb, final long delay, final String reason) {
    	super(Shutdown.class.getSimpleName());
        this.sb = sb;
        this.delay = delay;
        this.reason = reason;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(this.delay);
        } catch (final InterruptedException e ) {
            this.sb.getLog().info("interrupted delayed shutdown");
        } catch (final Exception e ) {
            ConcurrentLog.logException(e);
        }
        this.sb.terminate(this.reason);
    }
}
