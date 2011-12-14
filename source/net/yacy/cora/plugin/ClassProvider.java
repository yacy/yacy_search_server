/**
 *  ClassProvider
 *  Copyright 201 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 13.12.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class ClassProvider {

    public static Class<?> load(final String classname, final File jarfile) {
        Class<?> c;
        try {
            c = Class.forName(classname);
        } catch (final ClassNotFoundException e) {
            c = null;
        }
        if (c == null) {
            // load jar
            String path = jarfile.getAbsolutePath();
            if (File.separatorChar != '/') path = path.replace(File.separatorChar, '/');
            if (!path.startsWith("/")) path = "/" + path;
            URL[] urls;
            try {
                urls = new URL[]{new URL("file", "", path)};
                final ClassLoader cl = new URLClassLoader(urls);
                c = cl.loadClass(classname);
            } catch (final MalformedURLException e) {
            } catch (final ClassNotFoundException e) {
            }
        }
        return c;
    }

    public static Method getStaticMethod(final Class<?> c, final String methodName, final Class<?>[] args) {
        if (c == null) return null;
        try {
            return c.getMethod(methodName, args);
        } catch (final SecurityException e) {
            return null;
        } catch (final NoSuchMethodException e) {
            return null;
        }
    }
}
