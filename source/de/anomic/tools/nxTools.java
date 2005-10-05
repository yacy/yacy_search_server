// nxTools.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 04.05.2004
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.tools;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

public class nxTools {


    public static HashMap table(Vector list) {
	Enumeration i = list.elements();
	int pos;
	String line;
	HashMap props = new HashMap(list.size());
	while (i.hasMoreElements()) {
	    line = ((String) i.nextElement()).trim();
	    //System.out.println("NXTOOLS_PROPS - LINE:" + line);
	    pos = line.indexOf("=");
	    if (pos > 0) props.put(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
	}
	return props;
    }
    
    public static HashMap table(ArrayList list) {
        Iterator i = list.iterator();
        int pos;
        String line;
        HashMap props = new HashMap(list.size());
        while (i.hasNext()) {
            line = ((String) i.next()).trim();
            //System.out.println("NXTOOLS_PROPS - LINE:" + line);
            pos = line.indexOf("=");
            if (pos > 0) props.put(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
        }
        return props;
        }

    public static Vector grep(Vector list, int afterContext, String pattern) {
	Enumeration i = list.elements();
	int ac = 0;
	String line;
	Vector result = new Vector();
	while (i.hasMoreElements()) {
	    line = (String) i.nextElement();
	    if (line.indexOf(pattern) >= 0) {
		result.add(line);
		ac = afterContext + 1;
	    } else if (ac > 0) {
		result.add(line);
	    }
	    ac--;
	}
	return result;
    }
    
    public static ArrayList grep(ArrayList list, int afterContext, String pattern) {
        Iterator i = list.iterator();
        int ac = 0;
        String line;
        ArrayList result = new ArrayList();
        while (i.hasNext()) {
            line = (String) i.next();
            if (line.indexOf(pattern) >= 0) {
            result.add(line);
            ac = afterContext + 1;
            } else if (ac > 0) {
            result.add(line);
            }
            ac--;
        }
        return result;
        }    

    public static String tail1(Vector list) {
	if ((list == null) || (list.size() == 0)) return "";
	return (String) list.lastElement();
    }
    
    public static String tail1(ArrayList list) {
        if ((list == null) || (list.size() == 0)) return "";
        return (String) list.get(list.size()-1);
        }

    public static String awk(String sentence, String separator, int count) {
	// returns the nth word of sentence, where count is the counter and the first word has the number 1
	// the words are separated by the separator
	if ((sentence == null) || (separator == null) || (count < 1)) return null;
	int pos;
	while ((count >= 1) && (sentence.length() > 0)) {
	    pos = sentence.indexOf(separator);
	    if (pos < 0) {
		if (count == 1) return sentence; else return null;
	    } else {
		if (count == 1) return sentence.substring(0, pos);
		sentence = sentence.substring(pos + separator.length());
		count--;
	    }
	}
        return null;
    }

}
