// enumerateFiles.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 26.12.2004
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

package de.anomic.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.TreeSet;

public class enumerateFiles implements Enumeration<File> {
    
    // implements iterative search through recursively defined subdirectories
    // and return all paths to the files
    
    private ArrayList<TreeSet<File>> hierarchy; // contains TreeSet elements, each TreeSet contains File Entries
    private boolean incOrder;    // if true, the smallest value is returned first
    private File buffer;       // the prefetch-buffer
    private boolean return_files;
    private boolean return_folders;
    private boolean delete_emptyFolders;
    
    public enumerateFiles(File root, boolean files, boolean folders, boolean increasing, boolean deleteEmptyFolders) {
        // we define our data structures first
        this.return_files = files;
        this.return_folders = folders;
        this.delete_emptyFolders = deleteEmptyFolders;
        this.hierarchy = new ArrayList<TreeSet<File>>();
        this.incOrder = increasing;
        // the we initially fill the hierarchy with the content of the root folder
        TreeSet<File> t = new TreeSet<File>();
        String[] l = root.list();
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
            while ((t == null) && (hierarchy.size() > 0)) {
                t = hierarchy.get(hierarchy.size() - 1);
                if (t.size() == 0) {
                    hierarchy.remove(hierarchy.size() - 1); // we step up one hierarchy
                    t = null;
                }
            }
            if ((hierarchy.size() == 0) || (t.size() == 0)) return null; // this is the end
            // fetch value
            if (incOrder) f = (File) t.first(); else f = (File) t.last();
            t.remove(f);
            // if the value represents another folder, we step into the next hierarchy
            if (f.isDirectory()) {
                t = new TreeSet<File>();
                String[] l = f.list();
                if (l == null) {
                    // f has disappeared
                    f = null;
                } else {
                    if (l.length == 0) {
                        if (delete_emptyFolders) {
                            f.delete();
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
    
    public boolean hasMoreElements() {
        return buffer != null;
    }
    
    public File nextElement() {
        File r = buffer;
        buffer = nextElement0();
        return r;
    }
    
}
