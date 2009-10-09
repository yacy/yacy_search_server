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

package de.anomic.document.parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import net.yacy.kelondro.order.Base64Order;

import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.http.client.Client;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.yacy.yacyURL;

/**
 * Vcard specification: http://www.imc.org/pdi/vcard-21.txt
 * @author theli
 *
 */
public class vcfParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     * 
     * TODO: support of x-mozilla-cpt and x-mozilla-html tags
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("vcf");
        SUPPORTED_MIME_TYPES.add("text/x-vcard");
        SUPPORTED_MIME_TYPES.add("application/vcard");
        SUPPORTED_MIME_TYPES.add("application/x-versit");
        SUPPORTED_MIME_TYPES.add("text/x-versit");
        SUPPORTED_MIME_TYPES.add("text/x-vcalendar");
    }
    
    public vcfParser() {        
        super("vCard Parser"); 
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    public Document parse(final yacyURL url, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
        try {
            final StringBuilder parsedTitle = new StringBuilder();
            final StringBuilder parsedDataText = new StringBuilder();
            final HashMap<String, String> parsedData = new HashMap<String, String>();
            final HashMap<yacyURL, String> anchors = new HashMap<yacyURL, String>();
            final LinkedList<String> parsedNames = new LinkedList<String>();
            
            boolean useLastLine = false;
            int lineNr = 0;
            String line = null;            
            final BufferedReader inputReader = (charset!=null)
                                       ? new BufferedReader(new InputStreamReader(source,charset))
                                       : new BufferedReader(new InputStreamReader(source));
            while (true) {
                // check for interruption
                checkInterruption();
                
                // getting the next line
                if (!useLastLine) {
                    line = inputReader.readLine();
                } else {
                    useLastLine = false;
                }
                
                if (line == null) break;                
                else if (line.length() == 0) continue;
                
                lineNr++;                
                final int pos = line.indexOf(":");
                if (pos != -1) {
                    final String key = line.substring(0,pos).trim().toUpperCase();
                    String value = line.substring(pos+1).trim();
                    
                    String encoding = null;
                    final String[] keyParts = key.split(";");
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
                                    value = decodeQuotedPrintable(value);
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
                            final yacyURL newURL = new yacyURL(value, null);
                            anchors.put(newURL, newURL.toString());   
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
                    if (theLogger.isFinest()) this.theLogger.logFinest("Invalid data in vcf file" +
                                             "\n\tURL: " + url +
                                             "\n\tLine: " + line + 
                                             "\n\tLine-Nr: " + lineNr);
                }
            }

            final String[] sections = parsedNames.toArray(new String[parsedNames.size()]);
            final byte[] text = parsedDataText.toString().getBytes();
            final Document theDoc = new Document(
                    url,                   // url of the source document
                    mimeType,                   // the documents mime type
                    null,
                    null,                       // a list of extracted keywords
                    null,                       // the language
                    parsedTitle.toString(),     // a long document title
                    "",                         // TODO: AUTHOR
                    sections,                   // an array of section headlines
                    "vCard",                    // an abstract
                    text,                       // the parsed document text
                    anchors,                    // a map of extracted anchors
                    null);                      // a treeset of image URLs
            return theDoc;
        } catch (final Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing vcf resource. " + e.getMessage(),url);
        } 
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
    public static final String decodeQuotedPrintable(final String s) {
		if (s == null) return null;
		final byte[] b = s.getBytes();
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
    
    public static void main(final String[] args) {
        try {
            final yacyURL contentUrl = new yacyURL(args[0], null);
            
            final vcfParser testParser = new vcfParser();
            final RequestHeader reqHeader = new RequestHeader();
            reqHeader.put(HeaderFramework.USER_AGENT, HTTPLoader.crawlerUserAgent);
            final byte[] content = Client.wget(contentUrl.toString(), reqHeader, 10000);
            final ByteArrayInputStream input = new ByteArrayInputStream(content);
            testParser.parse(contentUrl, "text/x-vcard", "UTF-8",input);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
