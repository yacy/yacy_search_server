//xlsParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005

//this file is contributed by Tim Riemann
//last major change: 12.09.2006

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

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

//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.plasma.parser.xls;

import java.io.InputStream;
import java.util.Hashtable;

import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import de.anomic.net.URL;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

public class xlsParser extends AbstractParser implements Parser, HSSFListener {

    //StringBuffer for parsed text
    private StringBuffer sbFoundStrings = null;
    
    //sstrecord needed for event parsing
    private SSTRecord sstrec;
    
    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("application/msexcel","xls");
        SUPPORTED_MIME_TYPES.put("application/excel","xls");
        SUPPORTED_MIME_TYPES.put("application/vnd.ms-excel","xls");
        SUPPORTED_MIME_TYPES.put("application/x-excel","xls");
        SUPPORTED_MIME_TYPES.put("application/x-msexcel","xls");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "poi-3.0-alpha2-20060616.jar",
        "poi-scratchpad-3.0-alpha2-20060616.jar",
    }; 

    public xlsParser(){
        super(LIBX_DEPENDENCIES);
        this.parserName = "Microsoft Excel Parser";
        this.parserVersionNr = "0.1"; 
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */ 
    public plasmaParserDocument parse(URL location, String mimeType,
            String charset, InputStream source) throws ParserException,
            InterruptedException {
        try {
            //generate new StringBuffer for parsing
            sbFoundStrings = new StringBuffer();
            
            //create a new org.apache.poi.poifs.filesystem.Filesystem
            POIFSFileSystem poifs = new POIFSFileSystem(source);
            //get the Workbook (excel part) stream in a InputStream
            InputStream din = poifs.createDocumentInputStream("Workbook");
            //construct out HSSFRequest object
            HSSFRequest req = new HSSFRequest();
            //lazy listen for ALL records with the listener shown above
            req.addListenerForAllRecords(this);
            //create our event factory
            HSSFEventFactory factory = new HSSFEventFactory();
            //process our events based on the document input stream
            factory.processEvents(req, din);
            //close our document input stream (don't want to leak these!)
            din.close();
            
            //now the parsed strings are in the StringBuffer, now convert them to a String
            String contents = sbFoundStrings.toString();
            
            /*
             * create the plasmaParserDocument for the database
             * and set shortText and bodyText properly
             */
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    ((contents.length() > 80) ? contents.substring(0, 80) : contents.trim()).
                    replaceAll("\r\n"," ").
                    replaceAll("\n"," ").
                    replaceAll("\r"," ").
                    replaceAll("\t"," "),
                    "", // TODO: AUTHOR
                    null,
                    null,
                    contents.getBytes("UTF-8"),
                    null,
                    null);
            return theDoc;
        } catch (Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            /*
             * an unexpected error occurred, log it and throw a ParserException
             */            
            String errorMsg = "Unable to parse the xls document '" + location + "':" + e.getMessage();
            this.theLogger.logSevere(errorMsg);            
            throw new ParserException(errorMsg, location);
        } finally {
            sbFoundStrings = null;
        }
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    public void reset(){
        //nothing to do
        super.reset();
    }

    public void processRecord(Record record) {
        switch (record.getSid()){
            case NumberRecord.sid: {
                NumberRecord numrec = (NumberRecord) record;
                sbFoundStrings.append(numrec.getValue());
                break;
            }
            //unique string records
            case SSTRecord.sid: {
                sstrec = (SSTRecord)record;
                for (int k = 0; k < sstrec.getNumUniqueStrings(); k++){
                    sbFoundStrings.append( sstrec.getString(k) );
                    
                    //add line seperator
                    sbFoundStrings.append( "\n" );
                }
                break;
            }
            
            case LabelSSTRecord.sid: {
                LabelSSTRecord lsrec = (LabelSSTRecord)record;
                sbFoundStrings.append( sstrec.getString(lsrec.getSSTIndex()) );
                break;
            }
        }
        
        //add line seperator
        sbFoundStrings.append( "\n" );
    }
}
