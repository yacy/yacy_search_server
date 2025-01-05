// BDecoder.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2010
// Created 03.01.2010
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// this is an BDecoder implementation according to http://wiki.theory.org/BitTorrentSpecification
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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;

public class BDecoder {

    private final static byte[] _e = "e".getBytes();
    private final static byte[] _i = "i".getBytes();
    private final static byte[] _d = "d".getBytes();
    private final static byte[] _l = "l".getBytes();
    private final static byte[] _p = ":".getBytes();
    
    private final byte[] b;
    private int pos;
    
    public BDecoder(byte[] b) {
        this.b = b;
        this.pos = 0;
    }
    
    public static enum BType {
        string, integer, list, dictionary;
    }
    
    public static interface BObject {
        public BType getType();
        public byte[] getString();
        public long getInteger();
        public List<BObject> getList();
        public Map<String, BObject> getMap();
        @Override
        public String toString();
        public void toStream(OutputStream os) throws IOException;
    }
    
    private static abstract class BDfltObject implements BObject {

        @Override
        public long getInteger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BObject> getList() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, BObject> getMap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getString() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BType getType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            throw new UnsupportedOperationException();
        }
        
    }
    
    public static class BStringObject extends BDfltObject implements BObject {
        private byte[] b;
        public BStringObject(byte[] b) {
            this.b = b;
        }
        @Override
        public BType getType() {
            return BType.string;
        }
        @Override
        public byte[] getString() {
            return this.b;
        }
        @Override
        public String toString() {
            return UTF8.String(this.b);
        }
        @Override
        public void toStream(OutputStream os) throws IOException {
            os.write(ASCII.getBytes(Integer.toString(this.b.length)));
            os.write(_p);
            os.write(this.b);
        }
        private static void toStream(OutputStream os, byte[] b) throws IOException {
            os.write(ASCII.getBytes(Integer.toString(b.length)));
            os.write(_p);
            os.write(b);
        }
        private static void toStream(OutputStream os, String s) throws IOException {
            final byte[] b = UTF8.getBytes(s);
            os.write(ASCII.getBytes(Integer.toString(b.length)));
            os.write(_p);
            os.write(b);   
        }
    }
    
    public static class BListObject extends BDfltObject implements BObject {
        private List<BObject> l;
        public BListObject(List<BObject> l) {
            this.l = l;
        }
        @Override
        public BType getType() {
            return BType.list;
        }
        @Override
        public List<BObject> getList() {
            return this.l;
        }
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder(l.size() * 40 + 1);
            s.append("[");
            for (final BObject o: l) s.append(o.toString()).append(",");
            s.setLength(s.length() - 1);
            s.append("]");
            return s.toString();
        }
        @Override
        public void toStream(OutputStream os) throws IOException {
            os.write(_l);
            for (final BObject bo: this.l) bo.toStream(os);
            os.write(_e);
        }
    }
    
    public static class BDictionaryObject extends BDfltObject implements BObject {
        private Map<String, BObject> m;
        public BDictionaryObject(Map<String, BObject> m) {
            this.m = m;
        }
        @Override
        public BType getType() {
            return BType.dictionary;
        }
        @Override
        public Map<String, BObject> getMap() {
            return this.m;
        }
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder(m.size() * 40 + 1);
            s.append('{');
            for (final Map.Entry<String, BObject> e: m.entrySet()) s.append(e.getKey()).append(':').append(e.getValue().toString()).append(','); 
            s.setLength(s.length() - 1);
            s.append('}');
            return s.toString();
        }
        @Override
        public void toStream(OutputStream os) throws IOException {
            os.write(_d);
            for (final Map.Entry<String, BObject> e: this.m.entrySet()) {
                BStringObject.toStream(os, e.getKey());
                e.getValue().toStream(os);
            }
            os.write(_e);
        }
        public static void toStream(OutputStream os, String key, byte[] value) throws IOException {
            os.write(_d);
            BStringObject.toStream(os, key);
            BStringObject.toStream(os, value);
            os.write(_e);
        }
    }
    
    public static class BIntegerObject extends BDfltObject implements BObject {
        private long i;
        private BIntegerObject(long i) {
            this.i = i;
        }
        @Override
        public BType getType() {
            return BType.integer;
        }
        @Override
        public long getInteger() {
            return this.i;
        }
        @Override
        public String toString() {
            return Long.toString(this.i);
        }
        @Override
        public void toStream(OutputStream os) throws IOException {
            os.write(_i);
            os.write(ASCII.getBytes(Long.toString(this.i)));
            os.write(_e);
        }
    }
    
    private Map<String, BObject> convertToMap(final List<BObject> list) {
        final Map<String, BObject> m = new LinkedHashMap<String, BObject>();
        final int length = list.size();
        byte[] key;
        BObject value;
        for (int i = 0; i < length; i += 2) {
            key = list.get(i).getString();
            value = null;
            if (i + 1 < length) {
                value = list.get(i + 1);
            }
            m.put(UTF8.String(key), value);
        }
        return m;
    }

    private List<BObject> readList() {
        final List<BObject> list = new ArrayList<BObject>();
        char ch = (char) b[pos];
        BObject bo;
        while (ch != 'e') {
            bo = parse();
            if (bo == null) {pos++; break;}
            list.add(bo);
            ch = (char) b[pos];
        }
        pos++;
        return list;
    }
    
    public BObject parse() {
        if (pos >= b.length) return null;
        char ch = (char) b[pos];
        if ((ch >= '0') && (ch <= '9')) {
            int end = pos;
            end++;
            while (b[end] != ':') ++end;
            final int len = Integer.parseInt(ASCII.String(b, pos, end - pos));
            final byte[] s = new byte[len];
            System.arraycopy(b, end + 1, s, 0, len);
            pos = end + len + 1;
            return new BStringObject(s);
        } else if (ch == 'l') {
            pos++;
            return new BListObject(readList());
        } else if (ch == 'd') {
            pos++;
            return new BDictionaryObject(convertToMap(readList()));
        } else if (ch == 'i') {
            pos++;
            int end = pos;
            while (b[end] != 'e') ++end;
            BIntegerObject io = new BIntegerObject(Long.parseLong(UTF8.String(b, pos, end - pos)));
            pos = end + 1;
            return io;
        } else {
            return null;
        }
    }
    
    private static void print(BObject bo, int t) {
        for (int i = 0; i < t; i++) System.out.print(" ");
        if (bo.getType() == BType.integer) System.out.println(bo.getInteger());
        if (bo.getType() == BType.string) System.out.println(bo.getString());
        if (bo.getType() == BType.list) {
            System.out.println("[");
            //for (int i = 0; i < t + 1; i++) System.out.print(" ");
            for (final BObject o: bo.getList()) print(o, t + 1);
            for (int i = 0; i < t; i++) System.out.print(" ");
            System.out.println("]");
        }
        if (bo.getType() == BType.dictionary) {
            System.out.println("{");
            for (final Map.Entry<String, BObject> e: bo.getMap().entrySet()) {
                for (int i = 0; i < t + 1; i++) System.out.print(" ");
                System.out.print(e.getKey());
                System.out.println(":");
                print(e.getValue(), t + 2);
            }
            for (int i = 0; i < t; i++) System.out.print(" ");
            System.out.println("}");
        }
    }

    public static void main(String[] args) {
        try {
            byte[] b = FileUtils.read(new File(args[0]));
            BDecoder bdecoder = new BDecoder(b);
            BObject o = bdecoder.parse();
            print(o, 0);
            System.out.println("Object: " + o.toString());
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}