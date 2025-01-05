//ConsoleOutHandler.java
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.logging;

import java.io.BufferedOutputStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public final class ConsoleOutHandler extends StreamHandler {

    private static int c = 0;

    public ConsoleOutHandler() {
        setLevel(Level.FINEST);
        setFormatter(new SimpleFormatter());
        setOutputStream(new BufferedOutputStream(System.out));
    }

    @Override
    public final synchronized void publish(final LogRecord record) {
        super.publish(record);
        if (c++ % 10 == 0) flush(); // not so many flushes, makes too much IO
    }

    @Override
    public final synchronized void close() {
        flush();
    }
}
