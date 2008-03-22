// DefinitionListToken.java
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brausze
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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

package de.anomic.data.wiki.tokens;

public class DefinitionListToken extends ListToken {
	
	//private static final String[] blockElements = { "dl", "dt", "dd" };
	
	public DefinitionListToken() {
		super(';', null, null);
	}
	
	protected StringBuffer parse(String[] t, int depth, StringBuffer sb) {
		sb.append("<dl>\n");
		while (super.aktline < t.length && getGrade(t[super.aktline]) >= depth) {
			for (int j=0; j<depth + 1; j++) sb.append("\t");
			sb.append("<dt>");
			
			if (getGrade(t[super.aktline]) > depth) {
				parse(t, depth + 1, sb);
			} else {
				sb.append(t[super.aktline].substring(depth + 1).replaceFirst(":", "</dt><dd>"));
			}
			
			sb.append("</");
			if (t[super.aktline].indexOf(':') == -1 || getGrade(t[super.aktline]) > depth)
				sb.append("dt");
			else
				sb.append("dd");
			sb.append(">\n");
			super.aktline++;
		}
		for (int j=0; j<depth; j++) sb.append("\t");
		sb.append("</dl>");
		super.aktline--;
		return sb;
	}
	
	public String[] getBlockElementNames() {
		return blockElements;
	}
}
