package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  A SWF Rectangle structure
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Rect {
    /**
     *  Description of the Field
     */
    protected int bitSize = -1;
    /**
     *  Description of the Field
     */
    protected int minX;
    /**
     *  Description of the Field
     */
    protected int minY;
    /**
     *  Description of the Field
     */
    protected int maxX;
    /**
     *  Description of the Field
     */
    protected int maxY;


    /**
     *  Gets the minX attribute of the Rect object
     *
     *@return    The minX value
     */
    public int getMinX() {
        return minX;
    }


    /**
     *  Gets the minY attribute of the Rect object
     *
     *@return    The minY value
     */
    public int getMinY() {
        return minY;
    }


    /**
     *  Gets the maxX attribute of the Rect object
     *
     *@return    The maxX value
     */
    public int getMaxX() {
        return maxX;
    }


    /**
     *  Gets the maxY attribute of the Rect object
     *
     *@return    The maxY value
     */
    public int getMaxY() {
        return maxY;
    }


    /**
     *  Sets the minX attribute of the Rect object
     *
     *@param  minX  The new minX value
     */
    public void setMinX(int minX) {
        this.minX = minX;
        bitSize = -1;
    }


    /**
     *  Sets the minY attribute of the Rect object
     *
     *@param  minY  The new minY value
     */
    public void setMinY(int minY) {
        this.minY = minY;
        bitSize = -1;
    }


    /**
     *  Sets the maxX attribute of the Rect object
     *
     *@param  maxX  The new maxX value
     */
    public void setMaxX(int maxX) {
        this.maxX = maxX;
        bitSize = -1;
    }


    /**
     *  Sets the maxY attribute of the Rect object
     *
     *@param  maxY  The new maxY value
     */
    public void setMaxY(int maxY) {
        this.maxY = maxY;
        bitSize = -1;
    }


    /**
     *  Constructor for the Rect object
     *
     *@param  minX  Description of the Parameter
     *@param  minY  Description of the Parameter
     *@param  maxX  Description of the Parameter
     *@param  maxY  Description of the Parameter
     */
    public Rect(int minX, int minY, int maxX, int maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }


    /**
     *  Constructor for the Rect object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public Rect(InStream in) throws IOException {
        in.synchBits();
        bitSize = (int) in.readUBits(5);
        minX = (int) in.readSBits(bitSize);
        maxX = (int) in.readSBits(bitSize);
        minY = (int) in.readSBits(bitSize);
        maxY = (int) in.readSBits(bitSize);
    }


    /**
     *  Constructor for the Rect object
     */
    public Rect() {
        this(0, 0, 11000, 8000);
        //default size
    }


    /**
     *  Calculate the minimum bit size based on the current values
     *
     *@return    The bitSize value
     */
    protected int getBitSize() {
        if (bitSize == -1) {
            //bitsize not defined

            int bsMinX = OutStream.determineSignedBitSize(minX);
            int bsMaxX = OutStream.determineSignedBitSize(maxX);
            int bsMinY = OutStream.determineSignedBitSize(minY);
            int bsMaxY = OutStream.determineSignedBitSize(maxY);

            bitSize = bsMinY;
            if (bitSize < bsMaxX) {
                bitSize = bsMaxX;
            }
            if (bitSize < bsMinX) {
                bitSize = bsMinX;
            }
            if (bitSize < bsMaxY) {
                bitSize = bsMaxY;
            }
        }

        return bitSize;
    }


    /**
     *  Gets the length attribute of the Rect object
     *
     *@return    The length value
     */
    public long getLength() {
        int bits = 5 + (getBitSize() * 4);
        int bytes = bits / 8;

        if (bytes * 8 < bits) {
            bytes++;
        }

        return bytes;
    }


    /**
     *  Write the rect contents to the output stream
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        out.flushBits();

        out.writeUBits(5, getBitSize());
        out.writeSBits(bitSize, minX);
        out.writeSBits(bitSize, maxX);
        out.writeSBits(bitSize, minY);
        out.writeSBits(bitSize, maxY);

        out.flushBits();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return "Rect bitsize=" + bitSize +
                " (" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")";
    }
}
