/**
 *  IconLinkRelations
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

/**
 * Enumeration of HTML link relationships (rel attribute) designating icon. 
 * @author luc
 *
 */
public enum IconLinkRelations {
	/** Standard IANA registered icon link relation (see https://www.iana.org/assignments/link-relations/link-relations.xhtml) */
	ICON("icon", "Standard favicon"),
	/** Icon for IOS app shortcut */
	APPLE_TOUCH_ICON("apple-touch-icon", "IOS app shortcut icon"),
	/** Icon for IOS app shortcut (deprecated but still used by major websites in 2015) */
	APPLE_TOUCH_ICON_PRECOMPOSED("apple-touch-icon-precomposed", "Deprecated IOS app shortcut icon"),
	/** icon for Safari pinned tab */
	MASK_ICON("mask-icon", "Safari browser pinned tab icon"),
	/** Icon for Fluid web app */
	FLUID_ICON("fluid-icon", "Fluid app icon");
	
	/** HTML rel attribute value */
	private String relValue;
	
	/** Human readable description */
	private String description;
	
	private IconLinkRelations(String relValue, String description) {
		this.relValue = relValue;
		this.description = description;
	}
	
	/**
	 * @return HTML rel attribute value
	 */
	public String getRelValue() {
		return relValue;
	}
	
	/**
	 * @return Human readable description of icon rel attribute 
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * @param relToken HTML rel attribute token
	 * @return true when relToken is an icon relationship (standard or non-standard)
	 */
	public static boolean isIconRel(String relToken) {
		boolean res = false;
		for(IconLinkRelations iconRel : IconLinkRelations.values()) {
			if(iconRel.getRelValue().equalsIgnoreCase(relToken)) {
				res = true;
				break;
			}
		}
		return res;
	}
	
	/**
	 * @param relToken HTML rel attribute token
	 * @return true when relToken is Standard IANA registered icon link relation
	 */
	public static boolean isStandardIconRel(String relToken) {
		return ICON.getRelValue().equalsIgnoreCase(relToken);
	}

}
