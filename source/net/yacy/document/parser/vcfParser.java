//vcfParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

/**
 * Vcard specification: http://www.imc.org/pdi/vcard-21.txt
 * @author theli
 *
 */
public class vcfParser extends AbstractParser implements Parser {

    public vcfParser() {
        super("vCard Parser");
        this.SUPPORTED_EXTENSIONS.add("vcf");
        this.SUPPORTED_MIME_TYPES.add("text/x-vcard");
        this.SUPPORTED_MIME_TYPES.add("application/vcard");
        this.SUPPORTED_MIME_TYPES.add("application/x-versit");
        this.SUPPORTED_MIME_TYPES.add("text/x-versit");
        this.SUPPORTED_MIME_TYPES.add("text/x-vcalendar");
    }

    @Override
    public Document[] parse(final AnchorURL url, final String mimeType, final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

        try {
            final StringBuilder parsedTitle = new StringBuilder();
            final StringBuilder parsedDataText = new StringBuilder();
            final HashMap<String, String> parsedData = new HashMap<String, String>();
            final List<AnchorURL> anchors = new ArrayList<AnchorURL>();
            final LinkedList<String> parsedNames = new LinkedList<String>();

            boolean useLastLine = false;
            int lineNr = 0;
            String line = null;
            final BufferedReader inputReader = (charset!=null)
                                       ? new BufferedReader(new InputStreamReader(source,charset))
                                       : new BufferedReader(new InputStreamReader(source));
            while (true) {

                // get the next line
                if (!useLastLine) {
                    line = inputReader.readLine();
                } else {
                    useLastLine = false;
                }

                if (line == null) break;
                else if (line.isEmpty()) continue;

                lineNr++;
                final int pos = line.indexOf(':',0);
                if (pos != -1) {
                    final String key = line.substring(0,pos).trim().toUpperCase();
                    String value = line.substring(pos+1).trim();

                    String encoding = null;
                    final String[] keyParts = key.split(";");
                    if (keyParts.length > 1) {
                        for (final String keyPart : keyParts) {
                            if (keyPart.toUpperCase().startsWith("ENCODING")) {
                                encoding = keyPart.substring("ENCODING".length()+1);
                            } else if (keyPart.toUpperCase().startsWith("QUOTED-PRINTABLE")) {
                                encoding = "QUOTED-PRINTABLE";
                            } else if (keyPart.toUpperCase().startsWith("BASE64")) {
                                encoding = "BASE64";
                            }

                        }
                        if (encoding != null) {
                            try {
                                if (encoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                                    // if the value has multiple lines ...
                                    if (line.endsWith("=")) {
                                        do {
                                            value = value.substring(0,value.length()-1);
                                            line = inputReader.readLine();
                                            if (line == null) break;
                                            value += line;
                                        } while (line.endsWith("="));
                                    }
                                    value = decodeQuotedPrintable(value);
                                } else if (encoding.equalsIgnoreCase("base64")) {
                                    do {
                                        line = inputReader.readLine();
                                        if (line == null) break;
                                        if (line.indexOf(':',0)!= -1) {
                                            // we have detected an illegal block end of the base64 data
                                            useLastLine = true;
                                        }
                                        if (!useLastLine) value += line.trim();
                                        else break;
                                    } while (line.length()!=0);
                                    value = Base64Order.standardCoder.decodeString(value);
                                }
                            } catch (final Exception ey) {
                                // Encoding error: This could occure e.g. if the base64 doesn't
                                // end with an empty newline
                                //
                                // We can simply ignore it.
                            }
                        }
                    }

                    if (key.equalsIgnoreCase("END")) {
                        String name = null, title = null;

                        // using the name of the current version as section headline
                        if (parsedData.containsKey("FN")) {
                            parsedNames.add(name = parsedData.get("FN"));
                        } else if (parsedData.containsKey("N")) {
                            parsedNames.add(name = parsedData.get("N"));
                        } else {
                            parsedNames.add(name = "unknown name");
                        }

                        // getting the vcard title
                        if (parsedData.containsKey("TITLE")) {
                            parsedNames.add(title = parsedData.get("TITLE"));
                        }

                        if (parsedTitle.length() > 0) parsedTitle.append(", ");
                        parsedTitle.append((title==null)?name:name + " - " + title);


                        // looping through the properties and add there values to
                        // the text representation of the vCard
                        final Iterator<String> iter = parsedData.values().iterator();
                        while (iter.hasNext()) {
                            value = iter.next();
                            parsedDataText.append(value).append("\r\n");
                        }
                        parsedDataText.append("\r\n");
                        parsedData.clear();
                    } else if (key.toUpperCase().startsWith("URL")) {
                        try {
                            final AnchorURL newURL = new AnchorURL(value);
                            newURL.setNameProperty(newURL.toString());
                            anchors.add(newURL);
                            //parsedData.put(key,value);
                        } catch (final MalformedURLException ex) {/* ignore this */}
                    } else if (
                            !key.equalsIgnoreCase("BEGIN") &&
                            !key.equalsIgnoreCase("END") &&
                            !key.equalsIgnoreCase("VERSION") &&
                            !key.toUpperCase().startsWith("LOGO") &&
                            !key.toUpperCase().startsWith("PHOTO") &&
                            !key.toUpperCase().startsWith("SOUND") &&
                            !key.toUpperCase().startsWith("KEY") &&
                            !key.toUpperCase().startsWith("X-")
                    ) {
                        // value = value.replaceAll(";","\t");
                        if ((value.length() > 0)) parsedData.put(key, value);
                    }

                } else {
                    if (AbstractParser.log.isFinest()) AbstractParser.log.finest("Invalid data in vcf file" +
                                             "\n\tURL: " + url +
                                             "\n\tLine: " + line +
                                             "\n\tLine-Nr: " + lineNr);
                }
            }
            try {inputReader.close();} catch (final IOException e) {}

            final String[] sections = parsedNames.toArray(new String[parsedNames.size()]);
            final byte[] text = UTF8.getBytes(parsedDataText.toString());
            final List<String> descriptions = new ArrayList<String>(1); descriptions.add("vCard");
            return new Document[]{new Document(
                    url,                        // url of the source document
                    mimeType,                   // the documents mime type
                    null,                       // charset
                    this,
                    null,                       // set of languages
                    null,                       // a list of extracted keywords
                    singleList(parsedTitle.toString()), // a long document title
                    "",                         // TODO: AUTHOR
                    "",                         // the publisher
                    sections,                   // an array of section headlines
                    descriptions,               // an abstract
                    0.0f, 0.0f,
                    text,                       // the parsed document text
                    anchors,                    // a map of extracted anchors
                    null,
                    null,                       // a treeset of image URLs
                    false,
                    new Date())};
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing vcf resource. " + e.getMessage(),url);
        }
    }

    private String decodeQuotedPrintable(final String s) {
        if (s == null) return null;
        final byte[] b = UTF8.getBytes(s);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            final int c = b[i];
            if (c == '=') {
                try {
                    final int u = Character.digit((char) b[++i], 16);
                    final int l = Character.digit((char) b[++i], 16);
                    if (u == -1 || l == -1) throw new RuntimeException("bad quoted-printable encoding");
                    sb.append((char) ((u << 4) + l));
                } catch (final ArrayIndexOutOfBoundsException e) {
                    throw new RuntimeException("bad quoted-printable encoding");
                }
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

}
