// enumerateFiles.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import net.yacy.kelondro.util.FileUtils;


public class enumerateFiles implements Enumeration<File> {
    
    // implements iterative search through recursively defined subdirectories
    // and return all paths to the files
    
    private final List<TreeSet<File>> hierarchy; // contains TreeSet elements, each TreeSet contains File Entries
    private final boolean incOrder;    // if true, the smallest value is returned first
    private File buffer;       // the prefetch-buffer
    private final boolean return_files;
    private final boolean return_folders;
    private final boolean delete_emptyFolders;
    
    public enumerateFiles(final File root, final boolean files, final boolean folders, final boolean increasing, final boolean deleteEmptyFolders) {
        // we define our data structures first
        this.return_files = files;
        this.return_folders = folders;
        this.delete_emptyFolders = deleteEmptyFolders;
        this.hierarchy = new ArrayList<TreeSet<File>>();
        this.incOrder = increasing;
        // the we initially fill the hierarchy with the content of the root folder
        final TreeSet<File> t = new TreeSet<File>();
        final String[] l = root.list();
        // System.out.println("D " + l.toString());
        if (l != null) for (int i = 0; i < l.length; i++) t.add(new File(root, l[i]));
        this.hierarchy.add(t);
        // start with search by filling the buffer
        this.buffer = nextElement0();
    }
    
    private File nextElement0() {
        // the object is a File pointing to the corresponding file
        File f;
        TreeSet<File> t;
        do {
            // System.out.println("D " + hierarchy.toString());
            t = null;
            while ((t == null) && (!hierarchy.isEmpty())) {
                t = hierarchy.get(hierarchy.size() - 1);
                if (t.isEmpty()) {
                    hierarchy.remove(hierarchy.size() - 1); // we step up one hierarchy
                    t = null;
                }
            }
            if (hierarchy.isEmpty() || (t == null || t.isEmpty())) return null; // this is the end
            // fetch value
            if (incOrder) f = t.first(); else f = t.last();
            t.remove(f);
            // if the value represents another folder, we step into the next hierarchy
            if (f.isDirectory()) {
                t = new TreeSet<File>();
                final String[] l = f.list();
                if (l == null) {
                    // f has disappeared
                    f = null;
                } else {
                    if (l.length == 0) {
                        if (delete_emptyFolders) {
                            FileUtils.deletedelete(f);
                            f = null;
                        } else {
                            if (!(return_folders)) f = null;
                        }
                    } else {
                        for (int i = 0; i < l.length; i++) t.add(new File(f, l[i]));
                        hierarchy.add(t);
                        if (!(return_folders)) f = null;
                    }
                }
            } else {
                if (!(return_files)) f = null;
            }
        } while (f == null);
        // thats it
        return f;
    }
    
    @Override
    public boolean hasMoreElements() {
        return buffer != null;
    }
    
    @Override
    public File nextElement() {
        final File r = buffer;
        buffer = nextElement0();
        return r;
    }
    
}
