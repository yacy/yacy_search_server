// listDirs.java
// (C) 2008 by Florian Richter <Florian_Richter@gmx.de>
// first published 06.07.2008 on http://yacy.net
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

package net.yacy.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ListDirs {

	private boolean isJar = false;
	private File FileObject = null;
	private JarFile JarFileObject = null;
	private final String uri;
	private String pathInJar;
	
	public ListDirs(final String uri) throws IOException, URISyntaxException {
		this.uri = uri;
		if(uri.startsWith("jar:")) {
			isJar = true;
			JarFileObject = new JarFile(uri.substring(9, uri.indexOf('!')));
			pathInJar = uri.substring(uri.indexOf('!') + 2);
		} else {
			FileObject = new File(new URI(uri));
		}
	}

	public ArrayList<String> listFiles(final String regex) {
		final ArrayList<String> files = getAllFiles();
		final ArrayList<String> classes = new ArrayList<String>();
		for(final String file: files) {
			if(file.matches(regex)) {
				classes.add(file);
			}
		}
		return classes;
	}

	private ArrayList<String> getAllFiles() {
		final ArrayList<String> files = new ArrayList<String>(50);
		if(isJar) {
			final Enumeration<JarEntry> entries = JarFileObject.entries();
			while(entries.hasMoreElements()) {
				final JarEntry entry = entries.nextElement();
				final String entryname = entry.getName();
				if(entryname.startsWith(pathInJar) && entryname.charAt(entryname.length()-1)!='/') {
					files.add(entryname);
				}
			}
		} else {
			for(final File file: getFilesRecursive(FileObject)) {
				files.add(file.toString());
			}
		}
		return files;
	}

	private ArrayList<File> getFilesRecursive(final File start) {
		final File[] fileList = start.listFiles();
		final ArrayList<File> completeList = new ArrayList<File>();
		for(final File file: fileList) {
			if(file.isDirectory()) {
				completeList.addAll(getFilesRecursive(file));
			} else {
				completeList.add(file);
			}
		}
		return completeList;
	}

	@Override
    public String toString() {
		return this.uri;
	}
}
