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
import java.util.ArrayList;
import java.util.List;

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
	 * Translate all files with specified extensions from srcDir directory into
	 * dstDir directory using locale files in specified directory. If no
	 * argument is set, default values are used.
	 * 
	 * @param args
	 *            runtime arguments<br/>
	 *            <ul>
	 *            <li>args[0] : source dir path</li>
	 *            <li>args[1] : destination dir path</li>
	 *            <li>args[2] : locale files dir path</li>
	 *            <li>args[3] : extensions (separated by commas)</li>
	 *            </ul>
	 */
	public static void main(String args[]) {
		File sourceDir = getSourceDir(args, 0);

		File destDir = getDestDir(args);

		File localesDir = getLocalesDir(args, 2);

		String extensions = getExtensions(args, 3);

		try {
			List<String> exts = new ArrayList<String>();
			exts.add(".lng");
			ExtensionsFileFilter localesFilter = new ExtensionsFileFilter(exts);
			File[] translationFiles = localesDir.listFiles(localesFilter);
			
			if (translationFiles == null || translationFiles.length == 0) {
				ConcurrentLog.info("TranslateAll",
						"No translation file found in " + localesDir);
			} else {

				ConcurrentLog.info("TranslateAll", "Translating "
						+ extensions + " files from " + sourceDir + " to "
						+ destDir + " using all locale files in " + localesDir);
				
				for (File translationFile : translationFiles) {
					
					/* Make a sub directory with locale code in destination directory*/
					String localeCode = translationFile.getName().substring(0, translationFile.getName().length() - exts.get(0).length());
					
					File localeDestDir = new File(destDir, localeCode);
					localeDestDir.mkdirs();

					new Translator().translateFilesRecursive(sourceDir, localeDestDir,
							translationFile, extensions, "locale");
				}
			}
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

	/**
	 * @param args
	 *            main parameters
	 * @parm argIndex index of translation files dir parameter
	 * @return translation files dir from parameters or default
	 *         (workingDir/locales)
	 * @throws IllegalArgumentException
	 *             when no parameters is set and default is not found
	 */
	protected static File getLocalesDir(String[] args, int argIndex) {
		File localesDir;
		if (args.length > argIndex && argIndex >= 0) {
			localesDir = new File(args[argIndex]);
		} else {
			String workingDir = System.getProperty("user.dir");
			if (workingDir == null) {
				throw new IllegalArgumentException(
						"No translation files directory specified, and default not found");
			}
			localesDir = new File(workingDir, "locales");
			if (!localesDir.exists() && !localesDir.isDirectory()) {
				throw new IllegalArgumentException(
						"No translation files directory specified, and default not found : "
								+ localesDir.getPath());
			}
		}
		return localesDir;
	}

}
