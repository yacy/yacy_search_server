// htmlFilterContentTransformer.java
// ---------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.htmlFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

    public void init(final String initarg) {
        if (bluelist == null) {
            // here, the init arg is used to load a list of blue-listed words
            bluelist = new ArrayList<String>();
            final File f = new File(initarg);
            if (f.canRead()) {
                try {
                    final BufferedReader r = new BufferedReader(new FileReader(f));
                    String s;
                    while ((s = r.readLine()) != null) {
                        if (!s.startsWith("#") && s.length() > 0) bluelist.add(s.toLowerCase());
                    }
                    r.close();
                } catch (final IOException e) {
                }
                // if (bluelist.size() == 0) System.out.println("BLUELIST is empty");
            }
        }
    }

    public boolean isIdentityTransformer() {
        return (bluelist.size() == 0);
    }

    private static char[] genBlueLetters(int length) {
            final serverCharBuffer bb = new serverCharBuffer(" <FONT COLOR=#0000FF>".toCharArray());
            length = length / 2;
            if (length > 10) length = 7;
            while (length-- > 0) {
                bb.append((int)'X');
            }
            bb.append("</FONT> ");
            final char[] result = bb.getChars();
            try {
				bb.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
            return result;
    }

    private boolean bluelistHit(final char[] text) {
        if (text == null || bluelist == null) return false;
        final String lc = new String(text).toLowerCase();
        for (int i = 0; i < bluelist.size(); i++) {
            if (lc.indexOf(bluelist.get(i)) >= 0) return true;
        }
        return false;
    }

    public ArrayList<String> getStrings(final byte[] text){
        final ArrayList<String> result = new ArrayList<String>();
        
        final serverByteBuffer sbb = new serverByteBuffer(text);
        final serverByteBuffer[] sbbs = httpTemplate.splitQuotations(sbb);
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
                final int ws = sbbs[i].whitespaceStart(true);
                final int we = sbbs[i].whitespaceEnd(true);
                result.add(new String(sbbs[i].getBytes(ws, we - ws)));
            }
        }
        return result;
    }
    public char[] transformText(final char[] text) {
        if (bluelist != null) {
            if (bluelistHit(text)) {
                // System.out.println("FILTERHIT: " + text);
                return genBlueLetters(text.length);
            }
            return text;
        }
        return text;
    }

    public char[] transformTag0(final String tagname, final Properties tagopts, final char quotechar) {
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

    public char[] transformTag1(final String tagname, final Properties tagopts, final char[] text, final char quotechar) {
        if (bluelistHit(tagopts.getProperty("href","").toCharArray())) return genBlueLetters(text.length);
        if (bluelistHit(text)) return genBlueLetters(text.length);
        return htmlFilterWriter.genTag1(tagname, tagopts, text, quotechar);
    }

    public void close() {
        // free resources
        super.close();
    }

}