// Gap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.12.2008 on http:// yacy.net
// 
// This is a part of YaCy, a peer-to-peer based web search engine
// 
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.blob;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * this is an extension of a set of {seek, size} pairs
 * it is used to denote the position and space of free records
 * The class provides methods to dump such a set to a file and read it again
 */

public class Gap extends TreeMap<Long, Integer> {
    
    private static final long serialVersionUID = 1L;

    public Gap() {
        super();
    }
    
    /**
     * initialize a kelondroBLOBGap with the content of a dump
     * @param file
     * @throws IOException 
     * @throws IOException 
     */
    public Gap(final File file) throws IOException {
        super();
        // read the index dump and fill the index
        DataInputStream is;
        try {
            is = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 1024 * 1024));
        } catch (final OutOfMemoryError e) {
            is = new DataInputStream(new FileInputStream(file));
        }
        long p;
        int l;
        while (true) {
            try {
                p = is.readLong();
                l = is.readInt();
                this.put(Long.valueOf(p), Integer.valueOf(l));
            } catch (final IOException e) {
                break;
            }
        }
        is.close();
        is = null;
    }
    
    /**
     * dump the set to a file
     * @param file
     * @return
     * @throws IOException
     */
    public int dump(File file) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".prt");
        Iterator<Map.Entry<Long, Integer>> i = this.entrySet().iterator();
        DataOutputStream os;
        try {
            os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 4 * 1024 * 1024));
        } catch (final OutOfMemoryError e) {
            os = new DataOutputStream(new FileOutputStream(tmp));
        }
        int c = 0;
        Map.Entry<Long, Integer> e;
        while (i.hasNext()) {
            e = i.next();
            os.writeLong(e.getKey().longValue());
            os.writeInt(e.getValue().intValue());
            c++;
        }
        os.flush();
        os.close();
        tmp.renameTo(file);
        assert file.exists() : file.toString();
        assert !tmp.exists() : tmp.toString();
        
        return c;
    }

}
