/**
 *  apkParser
 *  Copyright 2014 by Michael Peter Christen
 *  First released 09.06.2014 at http://yacy.net
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
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

public class apkParser extends AbstractParser implements Parser  {

    public apkParser() {
        super("Android Application Parser");
        this.SUPPORTED_EXTENSIONS.add("apk");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.android.package-archive");
    }
    
    @Override
    public Document[] parse(final AnchorURL location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {

        /*
         * things to discover:
         * - name
         * - version
         * - signature hash
         * - class root (to identify same apps for different versions)
         * - author (name of signer)
         * - strings from resources
         */
        return null;
    }
    
    
    public static class BinaryXMLParser {
    
        private boolean debug = false;
        private org.w3c.dom.Document w3cdoc;
        
        public BinaryXMLParser(boolean debug) {
            this.debug = debug;
            try {

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                this.w3cdoc = docBuilder.newDocument();

                Element manifestElement = this.w3cdoc.createElement("manifest");
                this.w3cdoc.appendChild(manifestElement);
                manifestElement.setAttribute("versionCode", "resourceID 0x4");
                manifestElement.setAttribute("versionName", "0.4");
                manifestElement.setAttribute("package", "de.anomic.tvtroll");
                
                Element usessdk = this.w3cdoc.createElement("uses-sdk");
                manifestElement.appendChild(usessdk);
                usessdk.setAttribute("minSdkVersion", "resourceID 0x8");
                usessdk.setAttribute("targetSdkVersion", "resourceID 0x8");
                
                Element usespermission = this.w3cdoc.createElement("uses-permission");
                manifestElement.appendChild(usespermission);
                usespermission.setAttribute("name", "android.permission.INTERNET");
                usespermission.setTextContent("dummy");
         
                // write the content into xml file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(this.w3cdoc);
                StreamResult result = new StreamResult(new File("test.xml"));
         
                // Output to console for testing
                // StreamResult result = new StreamResult(System.out);
         
                transformer.transform(source, result);
         
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (TransformerException e) {
                e.printStackTrace();
            }
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
        public void decompressXML(byte[] xml) {
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
                    StringBuffer sb = new StringBuffer();
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
                        sb.append(" " + attrName + "=\"" + attrValue + "\"");
                        attributes.put(attrName, attrValue);
                        // tr.add(attrName, attrValue);
                    }
                    if (this.debug) prtIndent(indent, "<" + name + sb + ">");
                    indent++;
                } else if (tag0 == endTag) { // XML END TAG
                    indent--;
                    off += 6 * 4; // Skip over 6 words of endTag data
                    String name = compXmlString(xml, sitOff, stOff, nameSi);
                    if (this.debug) prtIndent(indent, "</" + name + ">");
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
            if (strInd < 0)
                return null;
            int strOff = stOff + LEW(xml, sitOff + strInd * 4);
            return compXmlStringAt(xml, strOff);
        }
    
        public void prtIndent(int indent, String str) {
            StringBuilder sb = new StringBuilder(indent * 2 + str.length());
            for (int i = 0; i < indent; i++) sb.append("  ");
            sb.append(str);
            System.out.println(sb.toString());
        }
    
        /**
         * Return the string stored in StringTable format at offset strOff.
         * This offset points to the 16 bit string length, which
         * is followed by that number of 16 bit (Unicode) chars.
         * @param arr
         * @param strOff
         * @return
         */
        public String compXmlStringAt(byte[] arr, int strOff) {
            int strLen = arr[strOff + 1] << 8 & 0xff00 | arr[strOff] & 0xff;
            char[] chars = new char[strLen];
            for (int ii = 0; ii < strLen; ii++) {
                chars[ii] = (char) (((arr[strOff + 2 + ii * 2 + 1] & 0x00FF) << 8) + (arr[strOff + 2 + ii * 2] & 0x00FF));
            }
            return new String(chars);
        }
    
        /**
         * Return value of a Little Endian 32 bit word from the byte array at offset off.
         * @param arr
         * @param off
         * @return
         */
        public int LEW(byte[] arr, int off) {
            return arr[off + 3] << 24 & 0xff000000 | arr[off + 2] << 16 & 0xff0000 | arr[off + 1] << 8 & 0xff00 | arr[off] & 0xFF;
        } // end of LEW
    
    }
    
    public static void main(String[] args) {
        try {
            JarFile jf = new JarFile(args[0]);
            InputStream is = jf.getInputStream(jf.getEntry("AndroidManifest.xml"));
            byte[] xml = new byte[is.available()];
            is.read(xml);
            //Tree tr = TrunkFactory.newTree();
            new BinaryXMLParser(true).decompressXML(xml);
            //prt("XML\n"+tr.list());
          } catch (Exception ex) {
            //log("getIntents, ex: "+ex);  ex.printStackTrace();
          }
        System.exit(1);
    }
    
}
