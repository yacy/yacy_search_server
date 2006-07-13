//vcfParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 20.11.2005
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.vcf;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import de.anomic.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.codec.net.QuotedPrintableCodec;

import de.anomic.http.httpc;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

/**
 * Vcard specification: http://www.imc.org/pdi/vcard-21.txt
 * @author theli
 *
 */
public class vcfParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     * 
     * TODO: support of x-mozilla-cpt and x-mozilla-html tags
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("text/x-vcard","vcf");
        SUPPORTED_MIME_TYPES.put("application/vcard","vcf");        
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"commons-codec-1.3.jar"};        
    
    public vcfParser() {        
        super(LIBX_DEPENDENCIES);
        parserName = "vCard Parser"; 
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) throws ParserException {
        
        try {
            StringBuffer parsedTitle = new StringBuffer();
            StringBuffer parsedDataText = new StringBuffer();
            HashMap parsedData = new HashMap();
            HashMap anchors = new HashMap();
            LinkedList parsedNames = new LinkedList();
            
            boolean useLastLine = false;
            int lineNr = 0;
            String line = null;            
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(source));
            while (true) {
                if (!useLastLine) {
                    line = inputReader.readLine();
                } else {
                    useLastLine = false;
                }
                
                if (line == null) break;                
                else if (line.length() == 0) continue;
                
                lineNr++;                
                int pos = line.indexOf(":");
                if (pos != -1) {
                    String key = line.substring(0,pos).trim().toUpperCase();
                    String value = line.substring(pos+1).trim();
                    
                    String encoding = null;
                    String[] keyParts = key.split(";");
                    if (keyParts.length > 1) {
                        for (int i=0; i < keyParts.length; i++) {
                            if (keyParts[i].toUpperCase().startsWith("ENCODING")) {
                                encoding = keyParts[i].substring("ENCODING".length()+1);
                            } else if (keyParts[i].toUpperCase().startsWith("QUOTED-PRINTABLE")) {
                                encoding = "QUOTED-PRINTABLE";
                            } else if (keyParts[i].toUpperCase().startsWith("BASE64")) {
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
                                    value = (new QuotedPrintableCodec()).decode(value);
                                } else if (encoding.equalsIgnoreCase("base64")) {
                                    do {
                                        line = inputReader.readLine();
                                        if (line == null) break;
                                        if (line.indexOf(":")!= -1) {
                                            // we have detected an illegal block end of the base64 data
                                            useLastLine = true;
                                        }
                                        if (!useLastLine) value += line.trim();
                                        else break;
                                    } while (line.length()!=0);
                                    value = kelondroBase64Order.standardCoder.decodeString(value);
                                }  
                            } catch (Exception ey) {
                                // Encoding error: This could occure e.g. if the base64 doesn't 
                                // end with an empty newline
                                // 
                                // We can simply ignore it.
                            }
                        }
                    }                    
                    
                    if (key.equalsIgnoreCase("END")) {
                        String name = null, title = null;
                        
                        // using the name of the current persion as section headline
                        if (parsedData.containsKey("FN")) {
                            parsedNames.add(name = (String)parsedData.get("FN"));
                        } else if (parsedData.containsKey("N")) {
                            parsedNames.add(name = (String)parsedData.get("N"));
                        } else {
                            parsedNames.add(name = "unknown name");
                        }
                        
                        // getting the vcard title
                        if (parsedData.containsKey("TITLE")) {
                            parsedNames.add(title = (String) parsedData.get("TITLE"));
                        }
                        
                        if (parsedTitle.length() > 0) parsedTitle.append(", ");
                        parsedTitle.append((title==null)?name:name + " - " + title);
                        
                        
                        // looping through the properties and add there values to
                        // the text representation of the vCard
                        Iterator iter = parsedData.values().iterator();  
                        while (iter.hasNext()) {
                            value = (String) iter.next();
                            parsedDataText.append(value).append("\r\n");
                        }
                        parsedDataText.append("\r\n");
                        parsedData.clear();
                    } else if (key.toUpperCase().startsWith("URL")) {
                        try {
                            URL newURL = new URL(value);
                            anchors.put(newURL.toString(),newURL.toString());   
                            //parsedData.put(key,value);
                        } catch (MalformedURLException ex) {}                                                
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
                    this.theLogger.logFinest("Invalid data in vcf file" +
                                             "\n\tURL: " + location +
                                             "\n\tLine: " + line + 
                                             "\n\tLine-Nr: " + lineNr);
                }
            }

            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    null,
                    null,
                    parsedTitle.toString(),
                    (String[]) parsedNames.toArray(new String[parsedNames.size()]),
                    "vCard",
                    parsedDataText.toString().getBytes(),
                    anchors,
                    null);    
            return theDoc;
        } catch (Exception e) {            
            throw new ParserException("Unable to parse the vcard content. " + e.getMessage());
        } finally {
        }
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }
    
    public static void main(String[] args) {
        try {
            URL contentUrl = new URL(args[0]);
            
            vcfParser testParser = new vcfParser();
            byte[] content = httpc.singleGET(contentUrl, contentUrl.getHost(), 10000, null, null, null);
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            testParser.parse(contentUrl, "text/x-vcard", input);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
