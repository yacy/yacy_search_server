// LanguageFilenameFilter.java
// -----------------------
// (C) by Marc Nause; marc.nause@audioattack.de
// first published on http://www.yacy.net
// Braunschweig, Germany, 2008
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.language;

import java.io.File;
import java.io.FilenameFilter;

class LanguageFilenameFilter implements FilenameFilter {
    
    private static final String fileExtension = "lng";

    @Override
    public boolean accept(final File dir, final String name) {
        if (name.matches(".+\\."+fileExtension)) {
            return true;
        }
        return false;
    }
}
