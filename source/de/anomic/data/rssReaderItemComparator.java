//rssReaderItemComparator.java
//------------
// part of YACY
//
// (C) 2007 Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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
package de.anomic.data;

import java.util.Comparator;

public class rssReaderItemComparator implements Comparator{
	public int compare(Object o1, Object o2){
		int num1=((rssReaderItem)o1).getNum();
		int num2=((rssReaderItem)o2).getNum();
		return num2-num1;
	}
	public boolean equals(Object o1, Object o2){
		return compare(o1, o2)==0;
	}
}