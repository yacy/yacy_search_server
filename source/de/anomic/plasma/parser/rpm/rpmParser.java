//rpmParser.java 
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

package de.anomic.plasma.parser.rpm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;

import com.jguild.jrpm.io.RPMFile;
import com.jguild.jrpm.io.datatype.DataTypeIf;

import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;

/**
 * @author theli
 *
 */
public class rpmParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { ttp://packman.inode.at/suse/10.1/setup/descr/packages 
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
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset,
            InputStream source) throws ParserException {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("rpmParser",".tmp");
            serverFileUtils.copy(source,dstFile);
            return parse(location,mimeType,charset,dstFile);
        } catch (Exception e) {            
            return null;
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }        
    }    
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, File sourceFile) throws ParserException, InterruptedException {
        RPMFile rpmFile = null;        
        try {
            String summary = null, description = null, name = sourceFile.getName();
            HashMap anchors = new HashMap();
            StringBuffer content = new StringBuffer();            
            
            // opening the rpm file
            rpmFile = new RPMFile(sourceFile);
            
            // parsing the file
            rpmFile.parse();   
            
            // getting all header names
            String[] headerNames = rpmFile.getTagNames();
            for (int i=0; i<headerNames.length; i++) {
                // check for interruption
                checkInterruption();
                
                // getting the next tag
                DataTypeIf tag = rpmFile.getTag(headerNames[i]);
                if (tag == null) continue;
                
                content.append(headerNames[i])
                .append(": ")
                .append(tag.toString())
                .append("\n");
                
                if (headerNames[i].equals("N")) name = tag.toString();
                else if (headerNames[i].equals("SUMMARY")) summary = tag.toString();
                else if (headerNames[i].equals("DESCRIPTION")) description = tag.toString();
                else if (headerNames[i].equals("URL")) anchors.put(tag.toString(), tag.toString());
                
            }

            // closing the rpm file
            rpmFile.close();
            rpmFile = null;
            
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    name,
                    summary,
                    null,
                    description,
                    content.toString().getBytes("UTF-8"),
                    anchors,
                    null); 
            
            return theDoc;
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing rpm file. " + e.getMessage(),location); 
        } finally {
            if (rpmFile != null) try { rpmFile.close(); } catch (Exception e) {/* ignore this */}
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
    public static void main(String[] args) {
        try {
            URL contentUrl = new URL(args[0]);
            
            rpmParser testParser = new rpmParser();
            byte[] content = httpc.singleGET(contentUrl, contentUrl.getHost(), 10000, null, null, null);
            ByteArrayInputStream input = new ByteArrayInputStream(content);
            testParser.parse(contentUrl, "application/x-rpm", null, input);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
