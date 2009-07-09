//tarParser.java 
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

import de.anomic.document.AbstractParser;
import de.anomic.document.Classification;
import de.anomic.document.Idiom;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ImageEntry;
import de.anomic.kelondro.util.ByteBuffer;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.yacy.yacyURL;

public class tarParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();  
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-tar","tar");
        SUPPORTED_MIME_TYPES.put("application/tar","tar");
        SUPPORTED_MIME_TYPES.put("applicaton/x-gtar","tar");
        SUPPORTED_MIME_TYPES.put("multipart/x-tar","tar");
        SUPPORTED_MIME_TYPES.put("application/x-compress","tar");
        SUPPORTED_MIME_TYPES.put("application/x-compressed","tar");
    }     

    public tarParser() {        
        super();
        this.parserName = "Tape Archive File Parser"; 
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Document parse(final yacyURL location, final String mimeType, final String charset, InputStream source) throws ParserException, InterruptedException {
        
        long docTextLength = 0;
        OutputStream docText = null;
        File outputFile = null;
        Document subDoc = null;        
        try {           
            if ((this.contentLength == -1) || (this.contentLength > Idiom.MAX_KEEP_IN_MEMORY_SIZE)) {
                outputFile = File.createTempFile("zipParser",".prt");
                docText = new BufferedOutputStream(new FileOutputStream(outputFile));
            } else {
                docText = new ByteBuffer();
            }            
            
            /*
             * If the mimeType was not reported correcly by the webserve we
             * have to decompress it first
             */
            final String ext = Classification.getFileExt(location).toLowerCase();
            if (ext.equals("gz") || ext.equals("tgz")) {
                source = new GZIPInputStream(source);
            }
            
            // TODO: what about bzip ....

            final StringBuilder docKeywords = new StringBuilder();
            final StringBuilder docLongTitle = new StringBuilder();   
            final LinkedList<String> docSections = new LinkedList<String>();
            final StringBuilder docAbstrct = new StringBuilder();

            final Map<yacyURL, String> docAnchors = new HashMap<yacyURL, String>();
            final HashMap<String, ImageEntry> docImages = new HashMap<String, ImageEntry>(); 
                        
            // looping through the contained files
            TarEntry entry;
            final TarInputStream tin = new TarInputStream(source);                      
            while ((entry = tin.getNextEntry()) !=null) {
                // check for interruption
                checkInterruption();
                
                // skip directories
                if (entry.isDirectory()) continue;
                
                // Get the short entry name
                final String entryName = entry.getName();
                
                // getting the entry file extension
                final int idx = entryName.lastIndexOf(".");
                final String entryExt = (idx > -1) ? entryName.substring(idx+1) : "";
                
                // trying to determine the mimeType per file extension   
                final String entryMime = Classification.getMimeTypeByFileExt(entryExt);
                
                // getting the entry content
                File subDocTempFile = null;
                try {
                    // create the temp file
                    subDocTempFile = createTempFile(entryName);
                    
                    // copy the data into the file
                    FileUtils.copy(tin,subDocTempFile,entry.getSize());
                    
                    // check for interruption
                    checkInterruption();
                    
                    // parsing the content                    
                    subDoc = Parser.parseSource(yacyURL.newURL(location,"#" + entryName),entryMime,null,subDocTempFile);
                } catch (final ParserException e) {
                    this.theLogger.logInfo("Unable to parse tar file entry '" + entryName + "'. " + e.getMessage());
                } finally {
                    if (subDocTempFile != null) FileUtils.deletedelete(subDocTempFile);
                }
                if (subDoc == null) continue;
                
                // merging all documents together
                if (docKeywords.length() > 0) docKeywords.append(",");
                docKeywords.append(subDoc.dc_subject(','));
                
                if (docLongTitle.length() > 0) docLongTitle.append("\n");
                docLongTitle.append(subDoc.dc_title());
                
                docSections.addAll(Arrays.asList(subDoc.getSectionTitles()));
                
                if (docAbstrct.length() > 0) docAbstrct.append("\n");
                docAbstrct.append(subDoc.dc_description());

                if (subDoc.getTextLength() > 0) {
                    if (docTextLength > 0) docText.write('\n');
                    docTextLength += FileUtils.copy(subDoc.getText(), docText);
                }               
                
                //docAnchors.putAll(subDoc.getAnchors());
                ContentScraper.addAllImages(docImages, subDoc.getImages());
                
                // release subdocument
                subDoc.close();
                subDoc = null;                
            }
            
            Document result = null;
            
            if (docText instanceof ByteBuffer) {
                result = new Document(
                    location,
                    mimeType,
                    null,
                    null,
                    docKeywords.toString().split(" |,"),
                    docLongTitle.toString(),
                    "", // TODO: AUTHOR
                    docSections.toArray(new String[docSections.size()]),
                    docAbstrct.toString(),
                    ((ByteBuffer)docText).getBytes(),
                    docAnchors,
                    docImages);
            } else {
                result = new Document(
                        location,
                        mimeType,
                        null,
                        null,
                        docKeywords.toString().split(" |,"),
                        docLongTitle.toString(),
                        "", // TODO: AUTHOR
                        docSections.toArray(new String[docSections.size()]),
                        docAbstrct.toString(),
                        outputFile,
                        docAnchors,
                        docImages);                
            }
            
            return result;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            if (subDoc != null) subDoc.close();
            
            // close the writer
            if (docText != null) try { docText.close(); } catch (final Exception ex) {/* ignore this */}
            
            // delete the file
            if (outputFile != null) FileUtils.deletedelete(outputFile);
            
            throw new ParserException("Unexpected error while parsing tar resource. " + e.getMessage(),location); 
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}
