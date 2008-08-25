// SearchEventPicture.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 24.10.2005
//
// $LastChangedDate: 2005-10-23 19:50:27 +0200 (Sun, 23 Oct 2005) $
// $LastChangedRevision: 976 $
// $LastChangedBy: orbiter $
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


import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaGrafics;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;

// draw a picture of the yacy network

public class SearchEventPicture {
    
    public static ymageMatrix respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final String eventID = (String) header.get("event", plasmaSearchEvent.lastEventID);
        if (eventID == null) return null;
        final ymageMatrix yp = plasmaGrafics.getSearchEventPicture(sb.webIndex.seedDB, eventID);
        if (yp == null) return new ymageMatrix(1, 1, ymageMatrix.MODE_SUB, "000000"); // empty image
        
        return yp;
    }
    
}
