// serverClassLoader.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 11.07.2004
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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.util.FileUtils;


public final class serverClassLoader extends ClassLoader {
    /**
     * directory of class files
     */
    private final Map<File, Class<?>> classes;

    public serverClassLoader() {
        //super(ClassLoader.getSystemClassLoader());
    	super(Thread.currentThread().getContextClassLoader());
        this.classes = new ConcurrentHashMap<File, Class<?>>(100);
    }

    public serverClassLoader(final ClassLoader parent) {
        super(parent);
        this.classes = new ConcurrentHashMap<File, Class<?>>(100);
    }

    public Package[] packages() {
        return super.getPackages();
    }

    public Class<?> loadClass(final File classfile) throws ClassNotFoundException {
        // take the class out of the cache, denoted by the class file
        Class<?> c = this.classes.get(classfile);
        if (c != null) return c;

        final int p = classfile.getName().indexOf('.',0);
        if (p < 0) throw new ClassNotFoundException("wrong class name: " + classfile.getName());
        final String classname = classfile.getName().substring(0, p);

        // load the file from the file system
        byte[] b;
        try {
            //System.out.println("*** DEBUG CLASSLOADER: " + classfile + "; file " + (classfile.exists() ? "exists": "does not exist"));
            b = FileUtils.read(classfile);
            // make a class out of the stream
            c = this.defineClass(null, b, 0, b.length);
            resolveClass(c);
            this.classes.put(classfile, c);
        } catch (final LinkageError ee) {
        	c = findLoadedClass(classname);
        	if (c != null) return c;
            throw new ClassNotFoundException("linkageError, " + ee.getMessage() + ":" + classfile.toString());
        } catch (final IOException ee) {
            throw new ClassNotFoundException(ee.getMessage() + ":" + classfile.toString());
        }
        return c;
    }

}