//pdfParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 24.04.2005
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

package de.anomic.plasma.parser.pdf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Hashtable;

import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;

import de.anomic.net.URL;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverCharBuffer;

public class pdfParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { SUPPORTED_MIME_TYPES.put("application/pdf","pdf"); }     
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "PDFBox-0.7.2.jar"
    };        
    
    public pdfParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "Acrobat Portable Document Parser"; 
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        
        PDDocument theDocument = null;
        Writer writer = null;
        File writerFile = null;
        try {       
            // reducing thread priority
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);                        
            
            // deactivating the logging for jMimeMagic
//            Logger theLogger = Logger.getLogger("org.pdfbox");
//            theLogger.setLevel(Level.INFO);            
            
            String docTitle = null, docSubject = null, /*docAuthor = null,*/ docKeywordStr = null;
            
            // check for interruption
            checkInterruption();
            
            // creating a pdf parser
            PDFParser parser = new PDFParser(source);
            parser.parse();
                        
            // check for interruption
            checkInterruption();
            
            // creating a text stripper
            PDFTextStripper stripper = new PDFTextStripper();
            theDocument = parser.getPDDocument();
            
            if (theDocument.isEncrypted()) {
                throw new ParserException("Document is encrypted",location,plasmaCrawlEURL.DENIED_DOCUMENT_ENCRYPTED);
            }
            
            // extracting some metadata
            PDDocumentInformation theDocInfo = theDocument.getDocumentInformation();            
            if (theDocInfo != null) {
                docTitle = theDocInfo.getTitle();
                docSubject = theDocInfo.getSubject();
                //docAuthor = theDocInfo.getAuthor();
                docKeywordStr = theDocInfo.getKeywords();
            }            
            
            // creating a writer for output
            if ((this.contentLength == -1) || (this.contentLength > Parser.MAX_KEEP_IN_MEMORY_SIZE)) {
                writerFile = File.createTempFile("pdfParser",".tmp");
                writer = new OutputStreamWriter(new FileOutputStream(writerFile),"UTF-8");
            } else {
                writer = new serverCharBuffer(); 
            }

            stripper.writeText(theDocument, writer );
            theDocument.close(); theDocument = null;            
            writer.close();

            
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");
            
            plasmaParserDocument theDoc = null;
            
            if (writer instanceof serverCharBuffer) {
                byte[] contentBytes = ((serverCharBuffer)writer).toString().getBytes("UTF-8");
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
                        docKeywords,
                        docSubject,
                        docTitle,
                        "", // TODO: AUTHOR
                        null,
                        null,
                        contentBytes,
                        null,
                        null);
            } else {
                theDoc = new plasmaParserDocument(
                        location,
                        mimeType,
                        "UTF-8",
                        docKeywords,
                        docSubject,
                        docTitle,
                        "", // TODO: AUTHOR
                        null,
                        null,
                        writerFile,
                        null,
                        null);                
            }
            
            return theDoc;
        }
        catch (Exception e) {       
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // close the writer
            if (writer != null) try { writer.close(); } catch (Exception ex) {/* ignore this */}
            
            // delete the file
            if (writerFile != null) try { writerFile.delete(); } catch (Exception ex)  {/* ignore this */}
            
            throw new ParserException("Unexpected error while parsing pdf file. " + e.getMessage(),location); 
        } finally {
            if (theDocument != null) try { theDocument.close(); } catch (Exception e) {/* ignore this */}
            if (writer != null)      try { writer.close(); }      catch (Exception e) {/* ignore this */}
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }

}
