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

package de.anomic.server;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import de.anomic.server.logging.serverLog;

public final class serverClassLoader extends ClassLoader {
    /**
     * directory of class files
     */
    private static final String HTROOT = "htroot/";
    private final static String baseDir = System.getProperty("user.dir");
    private final HashMap<File, Class<?>> classes;

    public serverClassLoader() {
        //super(ClassLoader.getSystemClassLoader());
    	super(Thread.currentThread().getContextClassLoader());
        this.classes = new HashMap<File, Class<?>>();
    }

    public serverClassLoader(ClassLoader parent) {
        super(parent);
        classes = new HashMap<File, Class<?>>();
    }

    public Package[] packages() {
        return super.getPackages();
    }

    public Class<?> loadClass(File classfile) throws ClassNotFoundException {
        // we consider that the classkey can either be only the name of a class, or a partial or
        // complete path to a class file
        
        // normalize classkey: strip off '.class'
        //if (classkey.endsWith(".class")) classkey = classkey.substring(0, classkey.length() - 6);
        
        String classFileName = null;
        try {
            classFileName = classfile.getCanonicalPath();
        } catch (IOException e) {
            throw new ClassNotFoundException("Unable to resolve the classfile path");
        }
        // try to load the class
        synchronized(classFileName.intern()) {
            // first try: take the class out of the cache, denoted by the classname    
            Class<?> c = this.classes.get(classfile);
            if (c != null) return c;
            
            // consider classkey as a file and extract the file name
            //File classfile = new File(classkey);
            // this file cannot exist for real, since we stripped off the .class
            // we constructed the classfile for the only purpose to strip off the name:
            
            /* get the class name out of the classfile */
            // make classFileName relative
            classFileName = classFileName.substring(classFileName.indexOf(baseDir) + baseDir.length());
            // special source dirs
            if(classFileName.contains(HTROOT)) {
                classFileName = classFileName.substring(classFileName.indexOf(HTROOT) + HTROOT.length());
            }
            
            String packge = "";
            final int endPackage = classFileName.lastIndexOf(File.separatorChar);
            if(endPackage != -1) {
                packge = classFileName.substring(0, endPackage);
                // the files under htroot/yacy are all in 'default package'!
                if(packge.startsWith("yacy")) {
                    packge = "";
                }
                if(packge.length() > 0) {
                    packge.replace(File.separatorChar, '.');
                    packge += ".";
                }
            }
            int p = classFileName.indexOf(".", endPackage);
            String classname = packge + classFileName.substring(endPackage + 1, p);
            
            // now that we have the name, we can create the real class file
            //classfile = new File(classkey + ".class");
            

// This code doesn't work properly if there are multiple classes with the same name
// This is because we havn't definded package names in our servlets
//                
//          try {
//          c = findLoadedClass(classname);
//          if (c == null) {
//          // second try: ask the system
//          c = findSystemClass(classname);
//          }
//          if (c == null) {
//          // third try
//          throw new ClassNotFoundException("internal trigger");
//          }
//          } catch (ClassNotFoundException e) {
            //System.out.println("INTERNAL ERROR1 in cachedClassLoader: " + e.getMessage());
            
            // third try: load the file from the file system
            byte[] b;
            try {
                b = serverFileUtils.read(classfile);
                // now make a class out of the stream
                //  System.out.println("loading class " + classname + " from file " + classfile.toString());
                c = this.defineClass(null, b, 0, b.length);
                resolveClass(c);
                this.classes.put(classfile, c);
            } catch (LinkageError ee) {
                c = findLoadedClass(classname);
                if (c!=null) return c;                
                serverLog.logSevere("ClassLoader", "class "+ classname +" not defined: "+ ee);
            } catch (IOException ee) {
                //System.out.println("INTERNAL ERROR2 in cachedClassLoader: " + ee.getMessage());
                throw new ClassNotFoundException(classfile.toString());
            }
//          }
            return c;
        }
    }

}