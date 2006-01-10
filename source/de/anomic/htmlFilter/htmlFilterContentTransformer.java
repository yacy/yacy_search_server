// htmlFilterContentTransformer.java
// ---------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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

package de.anomic.htmlFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import de.anomic.server.serverByteBuffer;

public class htmlFilterContentTransformer extends htmlFilterAbstractTransformer implements htmlFilterTransformer {

    // statics: for initialisation of the HTMLFilterAbstractTransformer
    private static TreeSet linkTags0;
    private static TreeSet linkTags1;

    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    static {
        linkTags0 = new TreeSet(insensitiveCollator);
        linkTags0.add("img");

        linkTags1 = new TreeSet(insensitiveCollator);
        linkTags1.add("a");
    }

    private static ArrayList bluelist = null;

    public htmlFilterContentTransformer() {
        super(linkTags0, linkTags1);
    }

    public void init(String initarg) {
//      System.out.println("Transformer init: " + initarg);
        if (bluelist == null) {
            // here, the initarg is used to load a list of bluelisted words
            bluelist = new ArrayList();
            File f = new File(initarg);
            if (f.canRead()) {
                try {
                    BufferedReader r = new BufferedReader(new FileReader(f));
                    String s;
                    while ((s = r.readLine()) != null) {
                        if (!s.startsWith("#") && s.length() > 0) bluelist.add(s.toLowerCase());
                    }
                    r.close();
                } catch (Exception e) {
                }
                // if (bluelist.size() == 0) System.out.println("BLUELIST is empty");
            }
        }
    }

    public boolean isIdentityTransformer() {
        return bluelist.size() == 0;
    }

    private static byte[] genBlueLetters(int length) {
        serverByteBuffer bb = new serverByteBuffer(" <FONT COLOR=#0000FF>".getBytes());
        length = length / 2;
        if (length > 10) length = 7;
        while (length-- > 0) {
            bb.append((byte) 'X');
        }
        bb.append("</FONT> ".getBytes());
        return bb.getBytes();
    }

    private boolean hit(byte[] text) {
        if (text == null || bluelist == null) return false;
        String lc;
        try {
            lc = new String(text, "UTF-8").toLowerCase();
        } catch (UnsupportedEncodingException e) {
            lc = new String(text).toLowerCase();
        }
        for (int i = 0; i < bluelist.size(); i++) {
            if (lc.indexOf((String) bluelist.get(i)) >= 0) return true;
        }
        return false;
    }

    public byte[] transformText(byte[] text) {
        if (hit(text)) {
//          System.out.println("FILTERHIT: " + text);
            return genBlueLetters(text.length);
        }
        return text;
    }

    public byte[] transformTag0(String tagname, Properties tagopts, byte quotechar) {
        if (hit(tagopts.getProperty("src","").getBytes())) return genBlueLetters(5);
        if (hit(tagopts.getProperty("alt","").getBytes())) return genBlueLetters(5);
        return htmlFilterOutputStream.genTag0(tagname, tagopts, quotechar);
    }

    public byte[] transformTag1(String tagname, Properties tagopts, byte[] text, byte quotechar) {
        if (hit(tagopts.getProperty("href","").getBytes())) return genBlueLetters(text.length);
        if (hit(text)) return genBlueLetters(text.length);
        return htmlFilterOutputStream.genTag1(tagname, tagopts, text, quotechar);
    }

    public void close() {
        // free resources
        super.close();
    }

}