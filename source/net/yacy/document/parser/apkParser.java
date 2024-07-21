/**
 *  apkParser
 *  Copyright 2014 by Michael Peter Christen
 *  First released 09.06.2014 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

public class apkParser extends AbstractParser implements Parser  {

    public apkParser() {
        super("Android Application Parser");
        this.SUPPORTED_EXTENSIONS.add("apk");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.android.package-archive");
    }
    
    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        /*
         * things to discover:
         * - name
         * - version
         * - signature hash
         * - class root (to identify same apps for different versions)
         * - author (name of signer)
         * - strings from resources
         */
        Document[] docs = null;
        File tempFile = null;
        FileOutputStream out = null;
        try {
            tempFile = File.createTempFile("apk" + System.currentTimeMillis(), "jar");
            out = new FileOutputStream(tempFile);
            int read = 0;
            final byte[] data = new byte[1024];
            while((read = source.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            out.close();
            out = null;
            JarFile jf = new JarFile(tempFile);
            docs = parse(location, mimeType, charset, jf);
        } catch (IOException e) {
            ConcurrentLog.logException(e);
        } finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				ConcurrentLog.logException(e);
			} finally {
				if (tempFile != null) {
					if (!tempFile.delete()) {
						log.warn("Could not delete temporary file " + tempFile);
					}
				}
			}
        }
        return docs;
    }
    
    public Document[] parse(final DigestURL location, final String mimeType, final String charset, final JarFile jf) {
        StringBuilder sb = new StringBuilder();
        String title = location.getFileName();
        AndroidManifestParser manifest = null;
        try {
            InputStream is = jf.getInputStream(jf.getEntry("AndroidManifest.xml"));
            byte[] xml = new byte[is.available()];
            is.read(xml);
            manifest = new AndroidManifestParser(xml, true);
            title = location.getFileName() + " " + manifest.packageName + " " + manifest.versionName;
            sb.append(title).append(". ");
            for (String p: manifest.permissions) sb.append(p).append(". ");
        } catch (IOException e) {
            ConcurrentLog.logException(e);
        }

        Enumeration<JarEntry> je = jf.entries();
        while (je.hasMoreElements()) {
            String path = je.nextElement().toString();
            sb.append(path).append(". ");
        }

        final Collection<AnchorURL> links = new ArrayList<>();
        try {
            InputStream is = jf.getInputStream(jf.getEntry("resources.arsc"));
            List<String> resources = resourcesArscParser(is);
            for (String s: resources) {
                sb.append(s).append(". ");
                int p = s.indexOf("http://");
                if (p < 0) p = s.indexOf("https://");
                if (p < 0) p = s.indexOf("ftp://");
                if (p >= 0) {
                    int q = s.indexOf(' ', p + 1);
                    String link = q < 0 ? s.substring(p) : s.substring(p, q);
                    try {
                        links.add(new AnchorURL(link));
                    } catch (MalformedURLException e) {}
                }
            }
        } catch (IOException e) {
            ConcurrentLog.logException(e);
        }        

        return new Document[]{new Document(
                location,
                mimeType,
                charset,
                this,
                null,
                null,
                singleList(title),
                null,
                manifest == null ? "" : manifest.packageName,
                null,
                null,
                0.0d, 0.0d,
                sb.toString(),
                links,
                null,
                null,
                false,
                new Date())};
    }
    
    public static class AndroidManifestParser {
        // this is a simplified Android binary XML parser which reads
        // parts of the xml into metadata fields
    
        private boolean debug = false;
        public String versionCode = null;
        public String versionName = null;
        public String packageName = null;
        public String minSdkVersion = null;
        public String targetSdkVersion = null;
        public Set<String> permissions = new HashSet<>();
        public Set<String> actions = new HashSet<>();
        public Set<String> categories = new HashSet<>();
        
        public AndroidManifestParser(final byte[] xml, final boolean debug) {
            this.debug = debug;
            decompressXML(xml);
        }
            
        /**
         * code taken from
         * http://stackoverflow.com/questions/2097813/how-to-parse-the-androidmanifest-xml-file-inside-an-apk-package
         * original author: http://stackoverflow.com/users/539612/ribo
         * The author has taken the code snippet from his own application published
         * as "PackageExlorer", see https://play.google.com/store/apps/details?id=org.andr.pkgexp
         * 
         * The code was adopted to produce a org.w3c.dom.Document data structure by [MC]
         *
         * documentation about binary xml can be found at:
         * http://justanapplication.wordpress.com/category/android/android-binary-xml/
         * 
         * consider to replace this with one of
         * https://github.com/xiaxiaocao/apk-parser
         * http://code.google.com/p/axml/
         * https://github.com/joakime/android-apk-parser
         */
        
        private static final int endDocTag = 0x00100101;
        private static final int startTag = 0x00100102;
        private static final int endTag = 0x00100103;
    
        /**
         * Parse the 'compressed' binary form of Android XML docs such as for AndroidManifest.xml in .apk files
         * @param xml
         */
        private void decompressXML(byte[] xml) {
            // Compressed XML file/bytes starts with 24x bytes of data,
            // 9 32 bit words in little endian order (LSB first):
            // 0th word is 03 00 08 00
            // 3rd word SEEMS TO BE: Offset at then of StringTable
            // 4th word is: Number of strings in string table
            // WARNING: Sometime I indiscriminently display or refer to word in
            // little endian storage format, or in integer format (ie MSB first).
            int numbStrings = LEW(xml, 4 * 4);
    
            // StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
            // of the length/string data in the StringTable.
            int sitOff = 0x24; // Offset of start of StringIndexTable
    
            // StringTable, each string is represented with a 16 bit little endian
            // character count, followed by that number of 16 bit (LE) (Unicode)
            // chars.
            int stOff = sitOff + numbStrings * 4; // StringTable follows StrIndexTable
    
            // XMLTags, The XML tag tree starts after some unknown content after the
            // StringTable. There is some unknown data after the StringTable, scan
            // forward from this point to the flag for the start of an XML start
            // tag.
            int xmlTagOff = LEW(xml, 3 * 4); // Start from the offset in the 3rd word.
            // Scan forward until we find the bytes: 0x02011000(x00100102 in normal int)
            for (int ii = xmlTagOff; ii < xml.length - 4; ii += 4) {
                if (LEW(xml, ii) == startTag) {
                    xmlTagOff = ii;
                    break;
                }
            } // end of hack, scanning for start of first start tag
    
            // XML tags and attributes:
            // Every XML start and end tag consists of 6 32 bit words:
            // 0th word: 02011000 for startTag and 03011000 for endTag
            // 1st word: a flag?, like 38000000
            // 2nd word: Line of where this tag appeared in the original source file
            // 3rd word: FFFFFFFF ??
            // 4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
            // 5th word: StringIndex of Element Name
            // (Note: 01011000 in 0th word means end of XML document, endDocTag)
    
            // Start tags (not end tags) contain 3 more words:
            // 6th word: 14001400 meaning??
            // 7th word: Number of Attributes that follow this tag(follow word 8th)
            // 8th word: 00000000 meaning??
    
            // Attributes consist of 5 words:
            // 0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
            // 1st word: StringIndex of Attribute Name
            // 2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId
            // used
            // 3rd word: Flags?
            // 4th word: str ind of attr value again, or ResourceId of value
    
            // TMP, dump string table to tr for debugging
            // tr.addSelect("strings", null);
            // for (int ii=0; ii<numbStrings; ii++) {
            // // Length of string starts at StringTable plus offset in StrIndTable
            // String str = compXmlString(xml, sitOff, stOff, ii);
            // tr.add(String.valueOf(ii), str);
            // }
            // tr.parent();
    
            // Step through the XML tree element tags and attributes
            int off = xmlTagOff;
            int indent = 0;
            //int startTagLineNo = -2;
            while (off < xml.length) {
                int tag0 = LEW(xml, off);
                // int tag1 = LEW(xml, off+1*4);
                //int lineNo = LEW(xml, off + 2 * 4);
                // int tag3 = LEW(xml, off+3*4);
                //int nameNsSi = LEW(xml, off + 4 * 4);
                int nameSi = LEW(xml, off + 5 * 4);
    
                if (tag0 == startTag) { // XML START TAG
                    //int tag6 = LEW(xml, off + 6 * 4); // Expected to be 14001400
                    int numbAttrs = LEW(xml, off + 7 * 4); // Number of Attributes
                                                           // to follow
                    // int tag8 = LEW(xml, off+8*4); // Expected to be 00000000
                    off += 9 * 4; // Skip over 6+3 words of startTag data
                    String name = compXmlString(xml, sitOff, stOff, nameSi);
                    // tr.addSelect(name, null);
                    //startTagLineNo = lineNo;
    
                    // Look for the Attributes
                    Map<String, String> attributes = new LinkedHashMap<>();
                    for (int ii = 0; ii < numbAttrs; ii++) {
                        //int attrNameNsSi = LEW(xml, off); // AttrName Namespace Str
                                                          // Ind, or FFFFFFFF
                        int attrNameSi = LEW(xml, off + 1 * 4); // AttrName String
                                                                // Index
                        int attrValueSi = LEW(xml, off + 2 * 4); // AttrValue Str
                                                                 // Ind, or FFFFFFFF
                        //int attrFlags = LEW(xml, off + 3 * 4);
                        int attrResId = LEW(xml, off + 4 * 4); // AttrValue
                                                               // ResourceId or dup
                                                               // AttrValue StrInd
                        off += 5 * 4; // Skip over the 5 words of an attribute
    
                        String attrName = compXmlString(xml, sitOff, stOff, attrNameSi);
                        String attrValue = attrValueSi != -1 ? compXmlString(xml, sitOff, stOff, attrValueSi) : "resourceID 0x" + Integer.toHexString(attrResId);
                        attributes.put(attrName, attrValue);
                        // tr.add(attrName, attrValue);
                    }
                    evaluateTag(indent, name, attributes);
                    indent++;
                } else if (tag0 == endTag) { // XML END TAG
                    indent--;
                    off += 6 * 4; // Skip over 6 words of endTag data
                    String name = compXmlString(xml, sitOff, stOff, nameSi);
                    evaluateTag(indent, name, null);
                    // tr.parent(); // Step back up the NobTree
                } else if (tag0 == endDocTag) { // END OF XML DOC TAG
                    break;    
                } else {
                    // prt("  Unrecognized tag code '"+Integer.toHexString(tag0) +"' at offset "+off);
                    break;
                }
            }
        }
    
        public String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
            if (strInd < 0) return null;
            int strOff = stOff + LEW(xml, sitOff + strInd * 4);
            return compXmlStringAt(xml, strOff);
        }
    
        public void evaluateTag(int indent, String tagName, Map<String, String> attributes) {
            if (this.debug) {
                StringBuilder sb = new StringBuilder(100);
                for (int i = 0; i < indent; i++) sb.append("  ");
                if (attributes == null) {
                    sb.append("</").append(tagName).append('>');
                } else {
                    sb.append('<').append(tagName);
                    for (Map.Entry<String, String> entry: attributes.entrySet()) {
                        sb.append(' ').append(entry.getKey()).append("=\"").append(entry.getValue()).append('\"');
                    }
                    sb.append('>');
                }
                //System.out.println(sb.toString());
            }
            
            // evaluate the content
            if (attributes != null) {
                if ("manifest".equals(tagName)) {
                    this.versionCode = attributes.get("versionCode");
                    this.versionName = attributes.get("versionName");
                    this.packageName = attributes.get("package");
                }
                if ("uses-sdk".equals(tagName)) {
                    this.minSdkVersion = attributes.get("minSdkVersion");
                    this.targetSdkVersion = attributes.get("targetSdkVersion");
                }
                if ("uses-permission".equals(tagName)) {
                    final String permission = attributes.get("name");
                    if (permission != null) this.permissions.add(permission);
                }
                if ("action".equals(tagName)) {
                    final String action = attributes.get("name");
                    if (action != null) this.actions.add(action);
                }
                if ("category".equals(tagName)) {
                    final String category = attributes.get("name");
                    if (category != null) this.categories.add(category);
                }
            }
        }
    
        /**
         * Return the string stored in StringTable format at offset strOff.
         * This offset points to the 16 bit string length, which
         * is followed by that number of 16 bit (Unicode) chars.
         * @param arr source byte array
         * @param strOff offset position
         * @return the computed string
         */
        public String compXmlStringAt(byte[] arr, int strOff) {
            int strLen = arr[strOff + 1] << 8 & 0xff00 | arr[strOff] & 0xff;
            char[] chars = new char[strLen];
            for (int ii = 0; ii < strLen; ii++) {
                int p0 = strOff + 2 + ii * 2;
                if (p0 >= arr.length - 1) break; // this should never happen if the compressed xml is well-formed, but some are not(!)
                chars[ii] = (char) (((arr[p0 + 1] & 0x00FF) << 8) + (arr[p0] & 0x00FF));
            }
            return new String(chars);
        }
    
        /**
         * @param arr source byte array
         * @param off byte array offset position
         * @return value of a Little Endian 32 bit word from the byte array at offset off.
         */
        public int LEW(byte[] arr, int off) {
            return arr[off + 3] << 24 & 0xff000000 | arr[off + 2] << 16 & 0xff0000 | arr[off + 1] << 8 & 0xff00 | arr[off] & 0xFF;
        } // end of LEW
    
    }
    
    /**
     * this arsc parser is far away from being correct, it's just a hack
     * @param arscStream a stream from the arsc content
     * @return a list of resource strings
     * @throws IOException
     */
    public static List<String> resourcesArscParser(InputStream arscStream) throws IOException {
        final byte[] asa = new byte[arscStream.available()];
        arscStream.read(asa);
        int pos = 0;
        final Charset charset = StandardCharsets.UTF_8;
        final List<String> s = new ArrayList<>();
        parseloop: while (pos < asa.length) {
            while (pos < asa.length && asa[pos] != 0) pos++;
            if (pos + 2 >= asa.length) break parseloop;
            // the next two bytes are counters:
            // the first counts the number of characters
            // the second counts the number of bytes (which may be greater)
            int charcount = asa[++pos];
            if (charcount == 0) continue parseloop;
            int bytecount = asa[++pos];
            if (bytecount == 0) continue parseloop;
            pos++;
            if (bytecount < charcount) continue parseloop;
            if (pos + bytecount + 1 > asa.length) break parseloop;
            if (asa[pos + bytecount] != 0) {pos++; continue parseloop;} // must be terminated by 0
            for (int i = pos; i < pos + bytecount; i++) if (asa[i] == 0) {pos++; continue parseloop;} // must not contain a 0
            String t = new String(asa, pos, bytecount, charset);
            if (t.length() == charcount) s.add(t);
            pos += bytecount;
        }
        return s;
    }
    
    public static void main(String[] args) {
        System.out.println("apk parser test with file " + args[0]);
        System.out.println();
        System.out.println("File list:");
        try {
            JarFile jf = new JarFile(args[0]);
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                String path = e.nextElement().toString();
                System.out.println(path);
            }
            System.out.println();
            System.out.println("AndroidManifest.xml:");
            InputStream is = jf.getInputStream(jf.getEntry("AndroidManifest.xml"));
            byte[] xml = new byte[is.available()];
            is.read(xml);
            @SuppressWarnings("unused")
            AndroidManifestParser manifest = new AndroidManifestParser(xml, true);

            System.out.println();
            System.out.println("resources.arsc:");
            is = jf.getInputStream(jf.getEntry("resources.arsc"));
            List<String> resources = resourcesArscParser(is);
            for (String s: resources) {
                System.out.println(s);
            }
            jf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(1);
    }
    
}
