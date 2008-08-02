package de.anomic.tools;
// md5DirFileFilter.java 
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// This file is contributed by Martin Thelian
//
// $LastChangedDate: 2006-08-06 10:09:39 +0200 (So, 06 Aug 2006) $
// $LastChangedRevision: 2349 $
// $LastChangedBy: theli $
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

import java.io.File;
import java.io.FilenameFilter;


public class md5DirFileFilter implements FilenameFilter {

    public boolean accept(final File dir, final String name) {
        return !(name.startsWith("dir.") || name.endsWith(".md5"));
    }

}
