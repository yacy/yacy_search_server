//xlsParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Tim Riemann
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

import java.io.InputStream;
import java.util.Date;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;


public class xlsParser extends AbstractParser implements Parser {

    public xlsParser(){
        super("Microsoft Excel Parser");
        this.SUPPORTED_EXTENSIONS.add("xls");
        this.SUPPORTED_EXTENSIONS.add("xla");
        this.SUPPORTED_MIME_TYPES.add("application/msexcel");
        this.SUPPORTED_MIME_TYPES.add("application/excel");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-msexcel");
        this.SUPPORTED_MIME_TYPES.add("application/x-ms-excel");
        this.SUPPORTED_MIME_TYPES.add("application/x-dos_ms_excel");
        this.SUPPORTED_MIME_TYPES.add("application/xls");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    @Override
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source) throws Parser.Failure,
            InterruptedException {
        return new XLSHSSFListener().parse(location, mimeType, charset, source);
    }

    public class XLSHSSFListener implements HSSFListener {

        //StringBuilder for parsed text
        private final StringBuilder sbFoundStrings;


        public XLSHSSFListener() {
            this.sbFoundStrings = new StringBuilder(100);
        }

        /*
         * parses the source documents and returns a Document containing
         * all extracted information about the parsed document
         */
        public Document[] parse(final DigestURL location, final String mimeType,
                @SuppressWarnings("unused") final String charset, final InputStream source) throws Parser.Failure,
                InterruptedException {
            try {

                //create a new org.apache.poi.poifs.filesystem.Filesystem
                final POIFSFileSystem poifs = new POIFSFileSystem(source);
                //get the Workbook (excel part) stream in a InputStream
                final InputStream din = poifs.createDocumentInputStream("Workbook");
                //construct out HSSFRequest object
                final HSSFRequest req = new HSSFRequest();
                //lazy listen for ALL records with the listener shown above
                req.addListenerForAllRecords(this);
                //create our event factory
                final HSSFEventFactory factory = new HSSFEventFactory();
                //process our events based on the document input stream
                factory.processEvents(req, din);
                //close our document input stream (don't want to leak these!)
                din.close();

                //now the parsed strings are in the StringBuilder, now convert them to a String
                final String contents = this.sbFoundStrings.toString().trim();

                /*
                 * create the plasmaParserDocument for the database
                 * and set shortText and bodyText properly
                 */
                return new Document[]{new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        this,
                        null,
                        null,
                        singleList(location.getFile()),
                        "", // TODO: AUTHOR
                        "", // TODO: publisher
                        null,
                        null,
                        0.0f, 0.0f,
                        contents,
                        null,
                        null,
                        null,
                        false,
                        new Date())};
            } catch (final Exception e) {
                if (e instanceof InterruptedException) throw (InterruptedException) e;

                /*
                 * an unexpected error occurred, log it and throw a Parser.Failure
                 */
                ConcurrentLog.logException(e);
                final String errorMsg = "Unable to parse the xls document '" + location + "':" + e.getMessage();
                throw new Parser.Failure(errorMsg, location);
            }
        }

        @Override
        public void processRecord(final Record record) {
            SSTRecord sstrec = null;
            switch (record.getSid()){
                case NumberRecord.sid: {
                    final NumberRecord numrec = (NumberRecord) record;
                    this.sbFoundStrings.append(numrec.getValue());
                    break;
                }
                //unique string records
                case SSTRecord.sid: {
                    sstrec = (SSTRecord) record;
                    for (int k = 0; k < sstrec.getNumUniqueStrings(); k++){
                        this.sbFoundStrings.append( sstrec.getString(k) );

                        //add line seperator
                        this.sbFoundStrings.append( "\n" );
                    }
                    break;
                }
                /*
                case LabelSSTRecord.sid: {
                    final LabelSSTRecord lsrec = (LabelSSTRecord)record;
                    sbFoundStrings.append( sstrec.getString(lsrec.getSSTIndex()) );
                    break;
                }
                */
            }

            //add line seperator
            this.sbFoundStrings.append( "\n" );
        }
    }

}
