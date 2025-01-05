// GenerateMasterXliff.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.utils.translation;

import java.io.File;
import java.io.IOException;

import net.yacy.cora.util.ConcurrentLog;

/**
 * Utility that can be used to generate a xliff file from a list of YaCy custom
 * *.lng translation files.
 * 
 * @author luccioman
 * 
 */
public class GenerateMasterXliff {

	/**
	 * Read all translation files in YaCy custom lng format from a given folder
	 * (locales/ as default), and convert them to a single master xliff file
	 * containing entries to translate. When the ouput master file already exists,
	 * it is updated with new entries from the lng files.
	 * 
	 * @param args
	 *            runtime optional arguments<br/>
	 *            <ul>
	 *            <li>args[0] : translation files (*.lng) folder path</li>
	 *            <li>args[1] : output xliff master file path</li>
	 *            </ul>
	 * @throws IOException
	 *             when a read/write error occurred
	 */
	public static void main(final String args[]) throws IOException {
		try {
			final File localesFolder;
			if (args.length > 0 && args[0] != null) {
				localesFolder = new File(args[0]);
			} else {
				localesFolder = new File("locales");
			}
			if(!localesFolder.isDirectory()) {
				System.err.println(localesFolder.getPath() + " is not a directory");
				return;
			}
			
			
			System.out.println("Using lng files from folder " + localesFolder.getAbsolutePath());

			final File masterXlf;
			if (args.length > 1 && args[1] != null) {
				masterXlf = new File(args[1]);
			} else {
				masterXlf = new File("master.lng.xlf");
			}
			if (masterXlf.exists()) {
				System.out.println("Updating master xliff file at " + masterXlf.getAbsolutePath());
			} else {
				System.out.println("Generating a new master xliff file at " + masterXlf.getAbsolutePath());
			}
			new TranslationManager().createMasterTranslationLists(localesFolder, masterXlf);
		} finally {
			ConcurrentLog.shutdown();
		}
	}

}
