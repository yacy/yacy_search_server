// SourceFileFilter.java
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
import java.util.ArrayList;
import java.util.List;

/**
 * Filter file names with an extensions list.
 * 
 * @author luc
 * 
 */
public class ExtensionsFileFilter implements FilenameFilter {

	/** Extensions required to pass filter */
	private List<String> extensions;

	/**
	 * Contructor with extensions
	 * 
	 * @param extensions
	 *            extensions required. When this list is null or empty, filter
	 *            let pass all files.
	 */
	public ExtensionsFileFilter(List<String> extensions) {
		if (extensions == null) {
			this.extensions = new ArrayList<>();
		} else {
			this.extensions = new ArrayList<>(extensions);
		}
	}

	/**
	 * @param file
	 *            file to check
	 * @return true when file name ends with one of the extensions list or
	 *         extensions list is empty
	 */
	@Override
	public boolean accept(File dir, String name) {
		boolean accepted = false;
		if (name != null) {
			if (extensions.size() == 0) {
				accepted = true;
			} else {
				for (String ext : extensions) {
					if (name.endsWith(ext)) {
						accepted = true;
						break;
					}
				}
			}
		}
		return accepted;
	}

}
