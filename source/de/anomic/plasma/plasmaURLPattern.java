// plasmaURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 11.07.2005
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

package de.anomic.plasma;

import java.io.File;
import java.util.HashMap;
import de.anomic.kelondro.kelondroMSetTools;

public class plasmaURLPattern {

    private File rootPath = null;
    private HashMap hostpaths = null; // key=host, value=path; mapped url is http://host/path; path does not start with '/' here

    public plasmaURLPattern(File rootPath) {
        super();
        this.rootPath = rootPath;
        this.hostpaths = new HashMap();
    }

    public void clear() {
        this.hostpaths = new HashMap();
    }

    public int size() {
        return hostpaths.size();
    }

    public void loadLists(String mapname, String filenames, String sep) {
        // File listsPath = new File(getRootPath(), getConfig("listsPath", "DATA/LISTS"));
        final String[] filenamesarray = filenames.split(",");

        if( filenamesarray.length > 0) {
            for (int i = 0; i < filenamesarray.length; i++) {
                hostpaths.putAll(kelondroMSetTools.loadMap(mapname, (new File(rootPath, filenamesarray[i])).toString(), sep));
            }
        }
    }

    public void remove(String host) {
        hostpaths.remove(host);
    }

    public void add(String host, String path) {
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        hostpaths.put(host, path);
    }

    public boolean isListed(String hostlow, String path) {
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        String pp = ""; // path-pattern

        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while ((index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((pp = (String) hostpaths.get(hostlow.substring(0, index + 1) + "*")) != null) {
                return ((pp.equals("*")) || (path.matches(pp)));
            }
        }
        index = hostlow.length();
        while ((index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((pp = (String) hostpaths.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                return ((pp.equals("*")) || (path.matches(pp)));
            }
        }

        // try to match without wildcard in domain
        return (((pp = (String) hostpaths.get(hostlow)) != null) &&
                ((pp.equals("*")) || (path.matches(pp))));
    }

}
