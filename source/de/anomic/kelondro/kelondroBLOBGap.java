// kelondroBLOBGap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.12.2008 on http:// yacy.net
// 
// This is a part of YaCy, a peer-to-peer based web search engine
// 
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
// 
// LICENSE
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

package de.anomic.kelondro;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * this is an extension of a set of {seek, size} pairs
 * it is used to denote the position and space of free records
 * The class provides methods to dump such a set to a file and read it again
 */

public class kelondroBLOBGap extends TreeMap<Long, Integer> {
    
    private static final long serialVersionUID = 1L;

    public kelondroBLOBGap() {
        super();
    }
    
    /**
     * initialize a kelondroBLOBGap with the content of a dump
     * @param file
     * @throws IOException 
     */
    public kelondroBLOBGap(final File file) throws IOException {
        super();
        // read the index dump and fill the index
        InputStream is = new BufferedInputStream(new FileInputStream(file), 1024 * 1024);
        byte[] k = new byte[8];
        byte[] v = new byte[4];
        int c;
        while (true) {
            c = is.read(k);
            if (c <= 0) break;
            c = is.read(v);
            if (c <= 0) break;
            this.put(new Long(kelondroNaturalOrder.decodeLong(k)), new Integer((int) kelondroNaturalOrder.decodeLong(v)));
        }
    }
    
    /**
     * dump the set to a file
     * @param file
     * @return
     * @throws IOException
     */
    public int dump(File file) throws IOException {
        Iterator<Map.Entry<Long, Integer>> i = this.entrySet().iterator();
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024);
        int c = 0;
        Map.Entry<Long, Integer> e;
        while (i.hasNext()) {
            e = i.next();
            os.write(kelondroNaturalOrder.encodeLong(e.getKey().longValue(), 8));
            os.write(kelondroNaturalOrder.encodeLong(e.getValue().longValue(), 4));
            c++;
        }
        os.flush();
        os.close();
        return c;
    }

}
