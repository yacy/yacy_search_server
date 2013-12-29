// serverCore.java
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2002-2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// ThreadPool
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

package net.yacy.server;


import net.yacy.cora.document.encoding.UTF8;

public final class serverCore {

    // special ASCII codes used for protocol handling
    private static final byte LF = 10;
    private static final byte CR = 13;
    public static final byte[] CRLF = {CR, LF};
    public static final String CRLF_STRING = UTF8.String(CRLF);
    public static final String LF_STRING = UTF8.String(new byte[]{LF});
    public static final long startupTime = System.currentTimeMillis();

    public static boolean useStaticIP = false;

}
