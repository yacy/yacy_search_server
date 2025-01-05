// ListNonTranslatedFiles.java
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
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.ListManager;
import net.yacy.data.Translator;
import net.yacy.kelondro.util.FileUtils;

/**
 * Util to help identifying non translated files.
 * 
 * @author luc
 * 
 */
public class ListNonTranslatedFiles extends TranslatorUtil {
	
	/**
	 * Print on standard output result of search
	 * @param nonTranslatedFiles list of non translated files
	 */
	private static void printResults(List<File> nonTranslatedFiles) {
		System.out.println(nonTranslatedFiles.size() + " files are not translated.");
		for(File file : nonTranslatedFiles) {
			System.out.println(file);
		}
	}

	/**
	 * List all files from srcDir directory which are not translated using
	 * specified locale file with specified extensions. If no argument is set,
	 * default values are used.
	 * 
	 * @param args
	 *            runtime arguments<br/>
	 *            <ul>
	 *            <li>args[0] : source dir path</li>
	 *            <li>args[1] : translation file path</li>
	 *            <li>args[2] : extensions (separated by commas)</li>
	 *            </ul>
	 */
	public static void main(String args[]) {
		File sourceDir = getSourceDir(args, 0);
		Path sourcePath = sourceDir.toPath();

		File translationFile = getTranslationFile(args, 1);

		List<String> extensions = ListManager
				.string2vector(getExtensions(args, 2));
		
		FilenameFilter fileFilter = new ExtensionsFileFilter(extensions);

		String excludedDir = "locale";

		ConcurrentLog.info("ListNonTranslatedFiles", "Listing non translated "
				+ extensions + " files from " + sourceDir + " using "
				+ translationFile);

		try {
			Set<String> translatedRelativePaths = new Translator().loadTranslationsLists(translationFile).keySet();

			List<File> srcFiles = FileUtils.getFilesRecursive(sourceDir, excludedDir, fileFilter);
			
			List<File> nonTranslatedFiles = new ArrayList<>();
			for(File srcFile : srcFiles) {
                            Path relativeSrcFile = sourcePath.relativize(srcFile.toPath());
                            if (!translatedRelativePaths.contains(relativeSrcFile.toString().replace('\\', '/'))) { // replace windows path separator for compare
                                nonTranslatedFiles.add(srcFile);
                            }
			}
			
			printResults(nonTranslatedFiles);


		} finally {
			ConcurrentLog.shutdown();
		}

	}

}
