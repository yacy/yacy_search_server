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
