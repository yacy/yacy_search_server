// TranslateAll.java
// -------------------------------------
// part of YACY
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

package net.yacy.utils.translation;

import java.io.File;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.Translator;

/**
 * Util to translate all files without launching full YaCy application.
 * 
 * @author luc
 * 
 */
public class TranslateAll extends TranslatorUtil {

	/**
	 * Translate all files from srcDir directory into dstDir directory using
	 * specified locale file with specified extensions. If no argument is set,
	 * default values are used.
	 * 
	 * @param args
	 *            runtime arguments<br/>
	 *            <ul>
	 *            <li>args[0] : source dir path</li>
	 *            <li>args[1] : destination dir path</li>
	 *            <li>args[2] : translation file path</li>
	 *            <li>args[3] : extensions (separated by commas)</li>
	 *            </ul>
	 */
	public static void main(String args[]) {
		File sourceDir = getSourceDir(args);

		File destDir = getDestDir(args);

		File translationFile = getTranslationFile(args);

		String extensions = getExtensions(args);

		ConcurrentLog.info("TranslateAll", "Translating " + extensions
				+ " files from " + sourceDir + " to " + destDir + " using "
				+ translationFile);

		try {
			Translator.translateFilesRecursive(sourceDir, destDir,
					translationFile, extensions, "locale");
		} finally {
			ConcurrentLog.shutdown();
		}

	}

	/**
	 * @param args
	 *            main parameters
	 * @return translation source dir from parameters or default
	 * @throws IllegalArgumentException
	 *             when no parameters is set and default is not found
	 */
	protected static File getDestDir(String[] args) {
		File destDir;
		if (args.length > 1) {
			destDir = new File(args[1]);
		} else {
			String tmpDir = System.getProperty("java.io.tmpdir");
			if (tmpDir == null) {
				throw new IllegalArgumentException(
						"No destination dir specified, and default not found");
			}
			destDir = new File(tmpDir + File.separator
					+ TranslateAll.class.getCanonicalName());
		}
		return destDir;
	}

}
