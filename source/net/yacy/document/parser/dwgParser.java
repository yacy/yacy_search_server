/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * this parser was copied and modified to fit into YaCy from the apache tika project
 */


package net.yacy.document.parser;


import java.io.InputStream;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.util.MemoryControl;

import org.apache.poi.util.StringUtil;


public class dwgParser extends AbstractParser implements Parser {


    private static final String HEADER_2000_PROPERTIES_MARKER_STR = "DWGPROPS COOKIE";
    private static final byte[] HEADER_2000_PROPERTIES_MARKER = new byte[HEADER_2000_PROPERTIES_MARKER_STR.length()];

    static {
        StringUtil.putCompressedUnicode(
                HEADER_2000_PROPERTIES_MARKER_STR,
                HEADER_2000_PROPERTIES_MARKER, 0);
    }

    /**
     * How far to skip after the last standard property, before
     *  we find any custom properties that might be there.
     */
    //private static final int CUSTOM_PROPERTIES_SKIP = 20;

    public dwgParser() {
        super("DWG (CAD Drawing) parser (very basic)");
        this.SUPPORTED_EXTENSIONS.add("dwg");
        this.SUPPORTED_MIME_TYPES.add("application/dwg");
        this.SUPPORTED_MIME_TYPES.add("applications/vnd.dwg");
    }

    @Override
    public Document[] parse(final AnchorURL location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {

        // check memory for parser
        if (!MemoryControl.request(200 * 1024 * 1024, true))
            throw new Parser.Failure("Not enough Memory available for pdf parser: " + MemoryControl.available(), location);
        return null;
        // First up, which version of the format are we handling?
        /*
        byte[] header = new byte[128];
        IOUtils.readFully(source, header);
        String version = new String(header, 0, 6, "US-ASCII");
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        if (version.equals("AC1015")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            if (skipTo2000PropertyInfoSection(stream, header)) {
                get2000Props(stream,metadata,xhtml);
            }
        } else if (version.equals("AC1018")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            if (skipToPropertyInfoSection(stream, header)) {
                get2004Props(stream,metadata,xhtml);
            }
        } else if (version.equals("AC1021") || version.equals("AC1024")) {
            metadata.set(Metadata.CONTENT_TYPE, TYPE.toString());
            if (skipToPropertyInfoSection(stream, header)) {
                get2007and2010Props(stream,metadata,xhtml);
            }
        } else {
            throw new TikaException(
                    "Unsupported AutoCAD drawing version: " + version);
        }

        xhtml.endDocument();


        String docTitle = null, docSubject = null, docAuthor = null, docPublisher = null, docKeywordStr = null;
        if (info != null) {
            docTitle = info.getTitle();
            docSubject = info.getSubject();
            docAuthor = info.getAuthor();
            docPublisher = info.getProducer();
            if (docPublisher == null || docPublisher.isEmpty()) docPublisher = info.getCreator();
            docKeywordStr = info.getKeywords();
        }

        if (docTitle == null || docTitle.isEmpty()) {
            docTitle = MultiProtocolURI.unescape(location.getFileName());
        }

        String[] docKeywords = null;
        if (docKeywordStr != null) {
            docKeywords = docKeywordStr.split(" |,");
        }
        if (docTitle == null) {
            docTitle = docSubject;
        }

        byte[] contentBytes;

        return new Document[]{new Document(
                location,
                mimeType,
                "UTF-8",
                this,
                null,
                docKeywords,
                docTitle,
                docAuthor,
                docPublisher,
                null,
                null,
                0.0f, 0.0f,
                contentBytes,
                null,
                null,
                null,
                false)};
                */
    }

    /*
    private void get2004Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
       // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2004String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }

        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
           String propName = read2004String(stream);
           String propValue = read2004String(stream);
           if(propName.length() > 0 && propValue.length() > 0) {
              metadata.add(propName, propValue);
           }
        }
    }

    private String read2004String(InputStream stream) throws IOException, TikaException {
       int stringLen = EndianUtils.readUShortLE(stream);

       byte[] stringData = new byte[stringLen];
       IOUtils.readFully(stream, stringData);

       // Often but not always null terminated
       if (stringData[stringLen-1] == 0) {
           stringLen--;
       }
       String value = StringUtil.getFromCompressedUnicode(stringData, 0, stringLen);
       return value;
    }

    // Stored as UCS2, so 16 bit "unicode"
    private void get2007and2010Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
        // Standard properties
        for (int i = 0; i < HEADER_PROPERTIES_ENTRIES.length; i++) {
            String headerValue = read2007and2010String(stream);
            handleHeader(i, headerValue, metadata, xhtml);
        }

        // Custom properties
        int customCount = skipToCustomProperties(stream);
        for (int i = 0; i < customCount; i++) {
           String propName = read2007and2010String(stream);
           String propValue = read2007and2010String(stream);
           if(propName.length() > 0 && propValue.length() > 0) {
              metadata.add(propName, propValue);
           }
        }
    }

    private String read2007and2010String(InputStream stream) throws IOException, TikaException {
       int stringLen = EndianUtils.readUShortLE(stream);

       byte[] stringData = new byte[stringLen * 2];
       IOUtils.readFully(stream, stringData);
       String value = StringUtil.getFromUnicodeLE(stringData);

       // Some strings are null terminated
       if(value.charAt(value.length()-1) == 0) {
           value = value.substring(0, value.length()-1);
       }

       return value;
    }

    private void get2000Props(
            InputStream stream, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, TikaException, SAXException {
        int propCount = 0;
        while(propCount < 30) {
            int propIdx = EndianUtils.readUShortLE(stream);
            int length = EndianUtils.readUShortLE(stream);
            int valueType = stream.read();

            if(propIdx == 0x28) {
               // This one seems not to follow the pattern
               length = 0x19;
            } else if(propIdx == 90) {
               // We think this means the end of properties
               break;
            }

            byte[] value = new byte[length];
            IOUtils.readFully(stream, value);
            if(valueType == 0x1e) {
                // Normal string, good
                String val = StringUtil.getFromCompressedUnicode(value, 0, length);

                // Is it one we can look up by index?
                if(propIdx < HEADER_2000_PROPERTIES_ENTRIES.length) {
                   metadata.add(HEADER_2000_PROPERTIES_ENTRIES[propIdx], val);
                   xhtml.element("p", val);
                } else if(propIdx == 0x012c) {
                   int splitAt = val.indexOf('=');
                   if(splitAt > -1) {
                      String propName = val.substring(0, splitAt);
                      String propVal = val.substring(splitAt+1);
                      metadata.add(propName, propVal);
                   }
                }
            } else {
                // No idea...
            }

            propCount++;
        }
    }

    private void handleHeader(
            int headerNumber, String value, Metadata metadata,
            XHTMLContentHandler xhtml) throws SAXException {
        if(value == null || value.isEmpty()) {
            return;
        }

        String headerProp = HEADER_PROPERTIES_ENTRIES[headerNumber];
        if(headerProp != null) {
            metadata.set(headerProp, value);
        }

        xhtml.element("p", value);
    }

    // Grab the offset, then skip there
    private boolean skipToPropertyInfoSection(InputStream stream, byte[] header)
            throws IOException, TikaException {
        // The offset is stored in the header from 0x20 onwards
        long offsetToSection = EndianUtils.getLongLE(header, 0x20);
        long toSkip = offsetToSection - header.length;
        if(offsetToSection == 0){
            return false;
        }
        while (toSkip > 0) {
            byte[] skip = new byte[Math.min((int) toSkip, 0x4000)];
            IOUtils.readFully(stream, skip);
            toSkip -= skip.length;
        }
        return true;
    }

    //We think it can be anywhere...
    private boolean skipTo2000PropertyInfoSection(InputStream stream, byte[] header)
            throws IOException {
       int val = 0;
       while(val != -1) {
          val = stream.read();
          if(val == HEADER_2000_PROPERTIES_MARKER[0]) {
             boolean going = true;
             for(int i=1; i<HEADER_2000_PROPERTIES_MARKER.length && going; i++) {
                val = stream.read();
                if(val != HEADER_2000_PROPERTIES_MARKER[i]) going = false;
             }
             if(going) {
                // Bingo, found it
                return true;
             }
          }
       }
       return false;
    }

    private int skipToCustomProperties(InputStream stream)
            throws IOException, TikaException {
       // There should be 4 zero bytes next
       byte[] padding = new byte[4];
       IOUtils.readFully(stream, padding);
       if(padding[0] == 0 && padding[1] == 0 &&
             padding[2] == 0 && padding[3] == 0) {
          // Looks hopeful, skip on
          padding = new byte[CUSTOM_PROPERTIES_SKIP];
          IOUtils.readFully(stream, padding);

          // We should now have the count
          int count = EndianUtils.readUShortLE(stream);

          // Sanity check it
          if(count > 0 && count < 0x7f) {
             // Looks plausible
             return count;
          } else {
             // No properties / count is too high to trust
             return 0;
          }
       } else {
          // No padding. That probably means no custom props
          return 0;
       }
    }

    public static void main(final String[] args) {
        if (args.length > 0 && args[0].length() > 0) {
            // file
            final File dwgFile = new File(args[0]);
            if(dwgFile.canRead()) {

                System.out.println(dwgFile.getAbsolutePath());
                final long startTime = System.currentTimeMillis();

                // parse
                final AbstractParser parser = new dwgParser();
                Document document = null;
                try {
                    document = Document.mergeDocuments(null, "application/dwg", parser.parse(null, "application/dwg", null, new FileInputStream(dwgFile)));
                } catch (final Parser.Failure e) {
                    System.err.println("Cannot parse file " + dwgFile.getAbsolutePath());
                    Log.logException(e);
                } catch (final InterruptedException e) {
                    System.err.println("Interrupted while parsing!");
                    Log.logException(e);
                } catch (final NoClassDefFoundError e) {
                    System.err.println("class not found: " + e.getMessage());
                } catch (final FileNotFoundException e) {
                    Log.logException(e);
                }

                // statistics
                System.out.println("\ttime elapsed: " + (System.currentTimeMillis() - startTime) + " ms");

                // output
                if (document == null) {
                    System.out.println("\t!!!Parsing without result!!!");
                } else {
                    System.out.println("\tParsed text with " + document.getTextLength() + " chars of text and " + document.getAnchors().size() + " anchors");
                    try {
                        // write file
                        FileUtils.copy(document.getText(), new File("parsedPdf.txt"));
                    } catch (final IOException e) {
                        System.err.println("error saving parsed document");
                        Log.logException(e);
                    }
                }
            } else {
                System.err.println("Cannot read file "+ dwgFile.getAbsolutePath());
            }
        } else {
            System.out.println("Please give a filename as first argument.");
        }
    }
*/
}
