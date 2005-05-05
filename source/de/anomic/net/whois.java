// whois.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 22.03.2005
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

package de.anomic.net;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class whois {

    public static Properties whois(String dom) {
        try {
            Process p = Runtime.getRuntime().exec("whois " + dom);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line, key, value, oldValue;
            int pos;
            Properties result = new Properties();
            while ((line = br.readLine()) != null) {
                pos = line.indexOf(":");
                if (pos > 0) {
                    key = line.substring(0, pos).trim().toLowerCase();
                    value = line.substring(pos + 1).trim();
                    //System.out.println(key + ":" + value);
                    oldValue = result.getProperty(key);
                    result.setProperty(key, (oldValue == null) ? value : (oldValue + "; " + value));
                }
            }
            return result;
        } catch (IOException e) {
            //e.printStackTrace();
            return null;
        }
    }

    public static String evaluateWhois(Properties p) {
        String info1, info2;
        info1 = p.getProperty("netname");
        info2 = p.getProperty("descr");
        if ((info1 != null) && (info2 != null)) return info1 + " / " + info2;
        info1 = p.getProperty("type");
        info2 = p.getProperty("name");
        if ((info1 != null) && (info2 != null) && (info1.toLowerCase().startsWith("person"))) return "Person: " + info2;
        return "unknown";
    }

    public static void main(String[] args) {
        Properties p = whois(args[0]);
        if (p != null) {
            System.out.println(p);
            System.out.println("---" + evaluateWhois(p));
        } else {
            System.out.println("whois cannot execute");
        }
    }
}
