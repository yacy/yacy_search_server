/**
 *  IconEntry
 *  Copyright 2016 by luccioman; https://github.com/luccioman
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

package net.yacy.document.parser.html;

import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.DigestURL;

/**
 * Represents an icon in a document.
 * 
 * @author luc
 *
 */
public class IconEntry {

	/** Patern to parse a HTML link sizes token attribute (ie. "16x16") */
	public static final Pattern SIZE_PATTERN = Pattern.compile("([1-9][0-9]*)[xX]([1-9][0-9]*)");

	/** Icon URL */
	private final DigestURL url;
	/**
	 * Icon links relations (one url may be used as multiple icon relations in
	 * the same document)
	 */
	private final Set<String> rel;
	/** Icon sizes */
	private final Set<Dimension> sizes;

	/**
	 * Constructs instance from parameters.
	 * 
	 * @param url
	 *            must not be null.
	 * @param rel
	 *            must not be null and contain at least one item.
	 * @param sizes
	 *            optional.
	 */
	public IconEntry(final DigestURL url, Set<String> rel, Set<Dimension> sizes) {
		if (url == null) {
			throw new IllegalArgumentException("url must not be null.");
		}
		if (rel == null || rel.isEmpty()) {
			throw new IllegalArgumentException("rel must be specified");
		}
		this.url = url;
		this.rel = rel;
		if (sizes != null) {
			this.sizes = sizes;
		} else {
			this.sizes = new HashSet<>();
		}
	}

	/**
	 * @return true when rel property contains a standard IANA registered icon
	 *         link relation
	 */
	public boolean isStandardIcon() {
		boolean standard = false;
		for (String relation : this.rel) {
			if (IconLinkRelations.isStandardIconRel(relation)) {
				standard = true;
				break;
			}
		}
		return standard;
	}

	/**
	 * @param size1 
	 * @param size2
	 * @return distance between two sizes, or Double.MAX_VALUE when one size is null
	 */
	public static double getDistance(Dimension size1, Dimension size2) {
		double result = Double.MAX_VALUE;
		if(size1 != null && size2 != null) {
			result = (Math.abs(size1.width - size2.width) + Math.abs(size1.height - size2.height)) / 2.0;
		}
		return result;
	}

	/**
	 * @param preferredSize
	 * @return the size among sizes property which is the closest to
	 *         preferredSize, or null when sizes is empty or preferredSize is null.
	 */
	public Dimension getClosestSize(Dimension preferredSize) {
		Dimension closest = null;
		if (preferredSize != null) {
			double closestDistance = Double.MAX_VALUE;
			for (Dimension size : this.sizes) {
				double currentDistance = getDistance(size, preferredSize);
				if (closest == null) {
					closest = size;
					closestDistance = currentDistance;
				} else {
					if (currentDistance < closestDistance) {
						closest = size;
						closestDistance = currentDistance;
					}
				}
			}
		}
		return closest;
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append("<link");
		res.append(" href=\"").append(this.url.toNormalform(false)).append("\"");
		res.append(" rel=\"");
		res.append(relToString());
		res.append("\"");
		if (!this.sizes.isEmpty()) {
			res.append(" sizes=\"");
			res.append(sizesToString());
			res.append("\"");
		}
		res.append(">");
		return res.toString();
	}

	/**
	 * @return icon URL
	 */
	public DigestURL getUrl() {
		return url;
	}

	/**
	 * @return icons link relations
	 */
	public Set<String> getRel() {
		return rel;
	}

	/**
	 * @return icon eventual sizes
	 */
	public Set<Dimension> getSizes() {
		return sizes;
	}

	/**
	 * @return a string representation of sizes property, in the form of a valid
	 *         HTML link tag sizes attribute (e.g. "16x16 64x64")
	 */
	public String sizesToString() {
		StringBuilder builder = new StringBuilder();
		for (Dimension size : this.sizes) {
			if (builder.length() > 0) {
				builder.append(" ");
			}
			builder.append(size.width).append("x").append(size.height);
		}
		return builder.toString();
	}

	/**
	 * @return a string representation of rel property, int the form of a valid
	 *         HTML link tag rel attribute (e.g. "icon apple-touch-icon")
	 */
	public String relToString() {
		StringBuilder builder = new StringBuilder();
		for (String relation : this.rel) {
			if (builder.length() > 0) {
				builder.append(" ");
			}
			builder.append(relation);
		}
		return builder.toString();
	}

}
