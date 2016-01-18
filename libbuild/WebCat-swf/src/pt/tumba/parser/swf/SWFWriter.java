package pt.tumba.parser.swf;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 *  Implements the SWFTags interface and writes a SWF file to the output stream
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class SWFWriter implements SWFTags {
    /**
     *  Description of the Field
     */
    protected OutStream out;
    /**
     *  Description of the Field
     */
    protected OutputStream outputstream;
    /**
     *  Description of the Field
     */
    protected ByteArrayOutputStream byteout;
    /**
     *  Description of the Field
     */
    protected String filename;

    //--deferred header values
    /**
     *  Description of the Field
     */
    protected int frameCount;
    /**
     *  Description of the Field
     */
    protected int version;
    /**
     *  Description of the Field
     */
    protected Rect frameSize;
    /**
     *  Description of the Field
     */
    protected int height;
    /**
     *  Description of the Field
     */
    protected int rate;


    /**
     *  Constructor for the SWFWriter object
     *
     *@param  filename                   Description of the Parameter
     *@exception  FileNotFoundException  Description of the Exception
     */
    public SWFWriter(String filename) throws FileNotFoundException {
        this(new FileOutputStream(filename));
        this.filename = filename;
    }


    /**
     *  Constructor for the SWFWriter object
     *
     *@param  outputstream  Description of the Parameter
     */
    public SWFWriter(OutputStream outputstream) {
        this.outputstream = outputstream;
        out = new OutStream(outputstream);
    }


    /**
     *  Constructor for the SWFWriter object
     *
     *@param  outstream  Description of the Parameter
     */
    public SWFWriter(OutStream outstream) {
        out = outstream;
    }


    /**
     *  Interface SWFTags
     *
     *@param  version          Description of the Parameter
     *@param  length           Description of the Parameter
     *@param  twipsWidth       Description of the Parameter
     *@param  twipsHeight      Description of the Parameter
     *@param  frameRate        Description of the Parameter
     *@param  frameCount       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void header(int version, long length2,
            int twipsWidth, int twipsHeight,
            int frameRate, int frameCount2) throws IOException {
        int frameCount = frameCount2;
        long length = length2;
        frameSize = new Rect(0, 0, twipsWidth, twipsHeight);

        //--Unknown values
        if (length < 0 || frameCount < 0) {
            //--defer the header
            this.version = version;
            this.rate = frameRate;
            this.frameCount = 0;

            if (filename != null) {
                //write the header later

                length = 0;
                frameCount = 0;
            } else {
                //write to a byte array first

                //--set up a byte array for the output
                if (byteout == null) {
                    byteout = new ByteArrayOutputStream(20000);
                    out = new OutStream(byteout);
                }

                return;
            }
        }

        writeHeader(version, length, frameRate, frameCount);
    }


    /**
     *  Interface SWFTags
     *
     *@param  tagType          Description of the Parameter
     *@param  longTag          Description of the Parameter
     *@param  contents         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void tag(int tagType, boolean longTag2,
            byte[] contents) throws IOException {
        boolean longTag = longTag2;
        //System.out.println( "OUT Tag " + tagType + " " + longTag + " " + ( (contents==null) ? 0 : contents.length) );
        //System.out.println();

        int length = (contents != null) ? contents.length : 0;
        longTag = (length > 62) || longTag;

        int hdr = (tagType << 6) + (longTag ? 0x3f : length);

        out.writeUI16(hdr);

        if (longTag) {
            out.writeUI32(length);
        }

        if (contents != null) {
            out.write(contents);
        }

        if (tagType == SWFConstants.TAG_SHOWFRAME) {
            frameCount++;
        }
        if (tagType == SWFConstants.TAG_END) {
            finish();
        }
    }


    /**
     *  Description of the Method
     *
     *@param  version          Description of the Parameter
     *@param  length           Description of the Parameter
     *@param  frameRate        Description of the Parameter
     *@param  frameCount       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeHeader(int version, long length,
            int frameRate, int frameCount) throws IOException {
        //--Write File Signature
        out.write(new byte[]{0x46, 0x57, 0x53});

        out.writeUI8(version);
        out.writeUI32(length);
        frameSize.write(out);
        out.writeUI16(frameRate << 8);
        out.writeUI16(frameCount);
    }


    /**
     *  Finish writing
     *
     *@exception  IOException  Description of the Exception
     */
    protected void finish() throws IOException {
        out.flush();

        //--Close the output file, calculate length and framecount and then
        // rewrite the header.
        if (filename != null) {
            outputstream.close();

            RandomAccessFile raf = new RandomAccessFile(filename, "rw");
            int length = (int) raf.length();

            byteout = new ByteArrayOutputStream();
            out = new OutStream(byteout);

            writeHeader(version, length, rate, frameCount);
            out.flush();

            raf.write(byteout.toByteArray());
            raf.close();

            return;
        }

        //--Writing to a byte array - need to recalculate lengths
        if (byteout != null) {
            byte[] bytes = byteout.toByteArray();

            long length = 12L + frameSize.getLength() + bytes.length;

            out = new OutStream(outputstream);

            writeHeader(version, length, rate, frameCount);

            out.write(bytes);
            out.flush();
        }
    }
}
