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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;

import de.anomic.http.httpTemplate;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCharBuffer;

public class htmlFilterContentTransformer extends htmlFilterAbstractTransformer implements htmlFilterTransformer {

    // statics: for initialisation of the HTMLFilterAbstractTransformer
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    private static final TreeSet<String> linkTags0 = new TreeSet<String>(insensitiveCollator);;
    private static final TreeSet<String> linkTags1 = new TreeSet<String>(insensitiveCollator);;

    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    static {
        linkTags0.add("img");
        linkTags0.add("input");

        linkTags1.add("a");
    }

    private ArrayList<String> bluelist = null;

    public htmlFilterContentTransformer() {
        super(linkTags0, linkTags1);
    }

    public void init(String initarg) {
        if (bluelist == null) {
            // here, the init arg is used to load a list of blue-listed words
            bluelist = new ArrayList<String>();
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
        return (bluelist.size() == 0);
    }

    private static char[] genBlueLetters(int length) {
            serverCharBuffer bb = new serverCharBuffer(" <FONT COLOR=#0000FF>".toCharArray());
            length = length / 2;
            if (length > 10) length = 7;
            while (length-- > 0) {
                bb.append((int)'X');
            }
            bb.append("</FONT> ");
            return bb.getChars();
    }

    private boolean bluelistHit(char[] text) {
        if (text == null || bluelist == null) return false;
        String lc = new String(text).toLowerCase();
        for (int i = 0; i < bluelist.size(); i++) {
            if (lc.indexOf((String) bluelist.get(i)) >= 0) return true;
        }
        return false;
    }

    public ArrayList<String> getStrings(byte[] text){
        ArrayList<String> result = new ArrayList<String>();
        
        serverByteBuffer sbb = new serverByteBuffer(text);
        serverByteBuffer[] sbbs = httpTemplate.splitQuotations(sbb);
        //sbb = new serverByteBuffer();
        for (int i = 0; i < sbbs.length; i++) {
            // TODO: avoid empty if statements
            if (sbbs[i].isWhitespace(true)) {
                //sbb.append(sbbs[i]);
            } else if ((sbbs[i].byteAt(0) == httpTemplate.hash) ||
                       (sbbs[i].startsWith(httpTemplate.dpdpa))) {
                // this is a template or a part of a template
                //sbb.append(sbbs[i]);
            } else {
                // this is a text fragment, generate gettext quotation
                int ws = sbbs[i].whitespaceStart(true);
                int we = sbbs[i].whitespaceEnd(true);
                result.add(new String(sbbs[i].getBytes(ws, we - ws)));
            }
        }
        return result;
    }
    public char[] transformText(char[] text) {
        if (bluelist != null) {
            if (bluelistHit(text)) {
                // System.out.println("FILTERHIT: " + text);
                return genBlueLetters(text.length);
            }
            return text;
        }
        return text;
    }

    public char[] transformTag0(String tagname, Properties tagopts, char quotechar) {
        if (tagname.equals("img")) {
            // check bluelist
            if (bluelistHit(tagopts.getProperty("src", "").toCharArray())) return genBlueLetters(5);
            if (bluelistHit(tagopts.getProperty("alt", "").toCharArray())) return genBlueLetters(5);

            // replace image alternative name
            tagopts.setProperty("alt", new String(transformText(tagopts.getProperty("alt", "").toCharArray())));
        }
        if (tagname.equals("input") && (tagopts.getProperty("type") != null && tagopts.getProperty("type").equals("submit"))) {
            // rewrite button name
            tagopts.setProperty("value", new String(transformText(tagopts.getProperty("value", "").toCharArray())));
        }
        return htmlFilterWriter.genTag0(tagname, tagopts, quotechar);
    }

    public char[] transformTag1(String tagname, Properties tagopts, char[] text, char quotechar) {
        if (bluelistHit(tagopts.getProperty("href","").toCharArray())) return genBlueLetters(text.length);
        if (bluelistHit(text)) return genBlueLetters(text.length);
        return htmlFilterWriter.genTag1(tagname, tagopts, text, quotechar);
    }

    public void close() {
        // free resources
        super.close();
    }

}