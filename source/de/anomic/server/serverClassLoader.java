// serverClassLoader.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
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


package de.anomic.server;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

public final class serverClassLoader extends ClassLoader {

    private final Hashtable classes;

    public serverClassLoader() {
	super(ClassLoader.getSystemClassLoader());
	classes = new Hashtable();
    }

    public serverClassLoader(ClassLoader parent) {
	super(parent);
	classes = new Hashtable();
    }

    public Package[] packages() {
	return super.getPackages();
    }

    public Class loadClass(File classfile) throws ClassNotFoundException {
	// we consider that the classkey can either be only the name of a class, or a partial or
	// complete path to a class file

	// normalize classkey: strip off '.class'
	//if (classkey.endsWith(".class")) classkey = classkey.substring(0, classkey.length() - 6);

	// try to find the class in the hashtable
	Class c = (Class) classes.get(classfile);
	if (c != null) return c;

	// consider classkey as a file and extract the file name
	//File classfile = new File(classkey);
	// this file cannot exist for real, since we stripped off the .class
	// we constructed the classfile for the only purpose to strip off the name:

	// get the class name out of the classfile
	String classname = classfile.getName();
        int p = classname.indexOf(".");
        classname = classname.substring(0, p);

	// now that we have the name, we can create the real class file
	//classfile = new File(classkey + ".class");

	// first try: take the class out of the cache, denoted by the classname
        try {
	    c = findLoadedClass(classname);
	    if (c == null) {
		// second try: ask the system
		c = findSystemClass(classname);
	    }
	    if (c == null) {
		// third try
		throw new ClassNotFoundException("internal trigger");
	    }
	} catch (ClassNotFoundException e) {
	    //System.out.println("INTERNAL ERROR1 in cachedClassLoader: " + e.getMessage());
	    // third try: load the file from the file system
	    byte[] b;
	    try {
                b = serverFileUtils.read(classfile);
		// now make a class out of the stream
		//  System.out.println("loading class " + classname + " from file " + classfile.toString());
		c = this.defineClass(classname, b, 0, b.length);
		resolveClass(c);
		classes.put(classfile, c);
	    } catch (IOException ee) {
		//System.out.println("INTERNAL ERROR2 in cachedClassLoader: " + ee.getMessage());
		throw new ClassNotFoundException(classfile.toString());
	    }
	}
	return c;
    }

}