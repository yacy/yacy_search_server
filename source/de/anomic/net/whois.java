// whois.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class whois {

    public static Properties Whois(final String dom) {
        try {
            final Process p = Runtime.getRuntime().exec("whois " + dom);
            final BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line, key, value, oldValue;
            int pos;
            final Properties result = new Properties();
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
            br.close();
            return result;
        } catch (final IOException e) {
            //e.printStackTrace();
            return null;
        }
    }

    public static String evaluateWhois(final Properties p) {
        String info1, info2;
        info1 = p.getProperty("netname");
        info2 = p.getProperty("descr");
        if ((info1 != null) && (info2 != null)) return info1 + " / " + info2;
        info1 = p.getProperty("type");
        info2 = p.getProperty("name");
        if ((info1 != null) && (info2 != null) && (info1.toLowerCase().startsWith("person"))) return "Person: " + info2;
        return "unknown";
    }
}
