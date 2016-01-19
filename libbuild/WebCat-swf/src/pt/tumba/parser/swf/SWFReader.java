package pt.tumba.parser.swf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *  Reads a SWF input stream and drives the SWFConsumer interface.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class SWFReader {
    /**
     *  Description of the Field
     */
    protected SWFTags consumer;
    /**
     *  Description of the Field
     */
    protected InStream in;
    /**
     *  Description of the Field
     */
    protected InputStream inputstream;

    /**
     *  Description of the Field
     */
    public int size;


    /**
     *  Constructor for the SWFReader object
     *
     *@param  consumer     Description of the Parameter
     *@param  inputstream  Description of the Parameter
     */
    public SWFReader(SWFTags consumer, InputStream inputstream) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int aux;
        this.size = 0;
        try {
            while ((aux = inputstream.read()) != -1) {
                size++;
                out.write((char) aux);
            }
        } catch (Exception e) {aux=-1;}
        this.consumer = consumer;
        this.inputstream = new ByteArrayInputStream(out.toByteArray());
        this.in = new InStream(this.inputstream);
    }


    /**
     *  Constructor for the SWFReader object
     *
     *@param  consumer  Description of the Parameter
     *@param  instream  Description of the Parameter
     */
    public SWFReader(SWFTags consumer, InStream instream) {
       // ByteArrayOutputStream out = new ByteArrayOutputStream();
        /*
         *  int aux;
         *  this.size = 0;
         *  while ( (aux=inputstream.read())!=-1 ) {
         *  size++;
         *  out.write((char)aux);
         *  }
         */
        this.consumer = consumer;
        this.in = instream;
        this.size = (int) (this.in.bytesRead);
    }


    /**
     *  Drive the consumer by reading a SWF File - including the header and all
     *  tags
     *
     *@exception  IOException  Description of the Exception
     */
    public void readFile() throws IOException {
        readHeader();
        readTags();
    }


    /**
     *  Drive the consumer by reading SWF tags only
     *
     *@exception  IOException  Description of the Exception
     */
    public void readTags() throws IOException {
        while (readOneTag() != SWFConstants.TAG_END) {
            ;
        }
    }


    /**
     *  Drive the consumer by reading one tag
     *
     *@return                  the tag type
     *@exception  IOException  Description of the Exception
     */
    public int readOneTag() throws IOException {
        int header = in.readUI16();

        int type = header >> 6;
        //only want the top 10 bits
        int length = header & 0x3F;
        //only want the bottom 6 bits
        boolean longTag = (length == 0x3F);

        if (longTag) {
            length = (int) in.readUI32();
        }

        byte[] contents = in.read(length);

        consumer.tag(type, longTag, contents);

        return type;
    }


    /**
     *  Only read the SWF file header
     *
     *@exception  IOException  Description of the Exception
     */
    public void readHeader() throws IOException {
        //--Verify File Signature
        //if ((in.readUI8() != 0x46) || (in.readUI8() != 0x57) || (in.readUI8() != 0x53)) {
//            throw new IOException("Invalid SWF File Signature");
        //}

        int version = in.readUI8();
        long length = in.readUI32();
        Rect frameSize = new Rect(in);
        int frameRate = in.readUI16() >> 8;
        int frameCount = in.readUI16();

        consumer.header(version, length,
                frameSize.getMaxX(), frameSize.getMaxY(),
                frameRate, frameCount);
    }


    /**
     *  The main program for the SWFReader class
     *
     *@param  args             The command line arguments
     *@exception  IOException  Description of the Exception
     */
    public static void main(String[] args) throws IOException {
        SWFWriter writer = new SWFWriter(System.out);
        SWFReader reader = new SWFReader(writer, System.in);
        reader.readFile();
        System.out.flush();
    }
}
