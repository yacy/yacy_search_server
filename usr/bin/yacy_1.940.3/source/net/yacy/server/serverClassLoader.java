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
import net.yacy.cora.util.ConcurrentLog;

import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

/**
 * Class loader for servlet classes
 * (findClass looking in default htroot directory)
 */
public final class serverClassLoader extends ClassLoader {

    public serverClassLoader() {
        //super(ClassLoader.getSystemClassLoader());
    	super(Thread.currentThread().getContextClassLoader());
        if (!registerAsParallelCapable()) { // avoid blocking
            ConcurrentLog.warn("ClassLoader", "registerAsParallelCapable failed");
        }
    }

    public serverClassLoader(final ClassLoader parent) {
        super(parent);
        if (!registerAsParallelCapable()) {
            ConcurrentLog.warn("ClassLoader", "registerAsParallelCapable failed");
        }
    }

    /**
     * Find servlet class in default htroot directory
     * but use the internal loadClass(File) methode to load the class same way
     * (e.g. caching) as direct call to loadClass(File)
     * This methode is mainly to avoid classpath conflicts for servlet to servlet calls
     * making inclusion of htroot in system classpath not crucial
     *
     * @param classname (delivered by parent loader without ".class" file extension
     * @return class in htroot
     * @throws ClassNotFoundException
     */
    @Override
    protected Class<?> findClass(String classname) throws ClassNotFoundException {
        // construct path to htroot for a servletname
        File cpath = new File (Switchboard.getSwitchboard().getAppPath(SwitchboardConstants.HTROOT_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT),classname+".class");
        return loadClass(cpath);
    }

    /**
     * A special loadClass using file as argument to find and load a class
     * This method is directly called by the application and not part of the
     * normal loadClass chain (= never called by JVM)
     *
     * @param classfile
     * @return loaded an resolved class
     * @throws ClassNotFoundException
     */
    public Class<?> loadClass(final File classfile) throws ClassNotFoundException {

        Class<?> c;
        final int p = classfile.getName().indexOf('.',0);
        if (p < 0) throw new ClassNotFoundException("wrong class name: " + classfile.getName());
        final String classname = classfile.getName().substring(0, p);

        synchronized (this) {
        	/* Important : we must first synchronize any concurrent access AND then check if the class was not already loaded.
        	 * Otherwise, trying to define again the same class (with the defineClass function) may lead to a JVM crash on some platforms, 
        	 * notably when running on Java 11 (on earlier JVMs, a LinkageError was only triggered and gracefully catched in that case) */
        	c = findLoadedClass(classname);
        	if (c != null) {
        		return c;
        	}
        	
            // load the file from the file system
            byte[] b;
            try {
                b = FileUtils.read(classfile);
                // make a class out of the stream
                c = this.defineClass(null, b, 0, b.length);
                resolveClass(c);
            } catch (final LinkageError ee) {
            	/* This error could occur when two threads try to define concurrently the same class. Should not occur here */
                throw new ClassNotFoundException("linkageError, " + ee.getMessage() + ":" + classfile.toString());
            } catch (final IOException ee) {
                throw new ClassNotFoundException(ee.getMessage() + ":" + classfile.toString());
            }
            return c;
		}
    }

}