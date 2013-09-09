/**
 *  AccessGrid_p
 *  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 18.02.2010 at http://yacy.net
 *
 *  $LastChangedDate: 2010-06-16 17:11:21 +0200 (Mi, 16 Jun 2010) $
 *  $LastChangedRevision: 6922 $
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;


public class AccessGrid_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader requestHeader, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch switchboard) {
        final serverObjects prop = new serverObjects();
        return prop;
    }

}
