// BEncoder.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.01.2010 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.kelondro.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.kelondro.util.BDecoder.BObject;

public class BEncoder {
    
    // maps
    public static Map<String, BObject> transcode(Map<String, byte[]> map) {
        Map<String, BObject> m = new HashMap<String, BObject>();
        for (Map.Entry<String, byte[]> entry: map.entrySet()) m.put(entry.getKey(), new BDecoder.BStringObject(entry.getValue()));
        return m;
    }
    
    public static byte[] encode(Map<String, BObject> map) {
        BDecoder.BDictionaryObject dict = new BDecoder.BDictionaryObject(map);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            dict.toStream(baos);
            baos.close();
            return baos.toByteArray();
        } catch (final IOException e) {}
        return null;
    }
    public static byte[] encodeMap(String key, byte[] value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BDecoder.BDictionaryObject.toStream(baos, key, value);
            baos.close();
            return baos.toByteArray();
        } catch (final IOException e) {}
        return null;
    }

    public static void main(final String[] args) {
        Map<String, byte[]> m = new HashMap<String, byte[]>();
        m.put("k", "000".getBytes());
        m.put("r", "111".getBytes());
        m.put("s", "222".getBytes());
        Map<String, BObject> t = transcode(m);
        byte[] b = encode(t);
        System.out.println(UTF8.String(b));
        BDecoder d = new BDecoder(b);
        BObject o = d.parse();
        System.out.println(o.toString());
    }
    
}
