/**
 *  SpaceExceededException
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 06.12.2009 on http://yacy.net
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

package net.yacy.cora.util;

import java.util.Date;

public class SpaceExceededException extends Exception {

    private static final long serialVersionUID = 9059516027929222151L;

    private final String forUsage;
    private final long neededRAM, availableRAM, time;

    public SpaceExceededException(final long neededRAM, final String forUsage) {
        super(Long.toString(neededRAM) + " bytes needed for " + forUsage + ": " + Memory.available() + " free at " + (new Date()).toString());
        this.time = System.currentTimeMillis();
        this.availableRAM = Memory.available();
        this.neededRAM = neededRAM;
        this.forUsage = forUsage;
    }

    public SpaceExceededException(final long neededRAM, final String forUsage, final Throwable t) {
        super(Long.toString(neededRAM) + " bytes needed for " + forUsage + ": " + Memory.available() + " free at " + (new Date()).toString(), t);
        this.time = System.currentTimeMillis();
        this.availableRAM = Memory.available();
        this.neededRAM = neededRAM;
        this.forUsage = forUsage;
    }

    public String getUsage() {
        return this.forUsage;
    }

    public long getNeededRAM() {
        return this.neededRAM;
    }

    public long getAvailableRAM() {
        return this.availableRAM;
    }

    public long getTime() {
        return this.time;
    }

}
