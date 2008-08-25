//rpmParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma.parser.rpm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;

import com.jguild.jrpm.io.RPMFile;
import com.jguild.jrpm.io.datatype.DataTypeIf;

import de.anomic.crawler.HTTPLoader;
import de.anomic.http.HttpClient;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyURL;

/**
 * @author theli
 *
 */
public class rpmParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();   
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-rpm","rpm");
        SUPPORTED_MIME_TYPES.put("application/x-redhat packet manager","rpm");    
        SUPPORTED_MIME_TYPES.put("application/x-redhat-package-manager","rpm");         
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"jrpm-head.jar"};        
    
    public rpmParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "rpm Parser"; 
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset,
            final InputStream source) throws ParserException {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("rpmParser",".tmp");
            serverFileUtils.copy(source,dstFile);
            return parse(location,mimeType,charset,dstFile);
        } catch (final Exception e) {            
            return null;
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }        
    }    
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset, final File sourceFile) throws ParserException, InterruptedException {
        RPMFile rpmFile = null;        
        try {
            String summary = null, description = null, packager = null, name = sourceFile.getName();
            final HashMap<yacyURL, String> anchors = new HashMap<yacyURL, String>();
            final StringBuffer content = new StringBuffer();            
            
            // opening the rpm file
            rpmFile = new RPMFile(sourceFile);
            
            // parsing the file
            rpmFile.parse();   
            
            // getting all header names
            final String[] headerNames = rpmFile.getTagNames();
            for (int i=0; i<headerNames.length; i++) {
                // check for interruption
                checkInterruption();
                
                // getting the next tag
                final DataTypeIf tag = rpmFile.getTag(headerNames[i]);
                if (tag == null) continue;
                
                content.append(headerNames[i])
                .append(": ")
                .append(tag.toString())
                .append("\n");
                
                if (headerNames[i].equalsIgnoreCase("N")) name = tag.toString();
                else if (headerNames[i].equalsIgnoreCase("SUMMARY")) summary = tag.toString();
                else if (headerNames[i].equalsIgnoreCase("DESCRIPTION")) description = tag.toString();
                else if (headerNames[i].equalsIgnoreCase("PACKAGER")) packager = tag.toString();
                else if (headerNames[i].equalsIgnoreCase("URL")) anchors.put(new yacyURL(tag.toString(), null), tag.toString());
            }

            // closing the rpm file
            rpmFile.close();
            rpmFile = null;
            if (summary == null) summary = name;
            
            final plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    summary,
                    packager,
                    null,
                    description,
                    content.toString().getBytes("UTF-8"),
                    anchors,
                    null); 
            
            return theDoc;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing rpm file. " + e.getMessage(),location); 
        } finally {
            if (rpmFile != null) try { rpmFile.close(); } catch (final Exception e) {/* ignore this */}
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
    public static void main(final String[] args) {
        try {
            final yacyURL contentUrl = new yacyURL(args[0], null);
            
            final rpmParser testParser = new rpmParser();
            final httpRequestHeader reqHeader = new httpRequestHeader();
            reqHeader.put(httpRequestHeader.USER_AGENT, HTTPLoader.crawlerUserAgent);
            final byte[] content = HttpClient.wget(contentUrl.toString(), reqHeader, 10000);
            final ByteArrayInputStream input = new ByteArrayInputStream(content);
            testParser.parse(contentUrl, "application/x-rpm", null, input);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
