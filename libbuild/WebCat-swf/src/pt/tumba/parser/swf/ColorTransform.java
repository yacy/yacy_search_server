package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ColorTransform {
    /**
     *  Description of the Field
     */
    protected double multRed = 1.0;
    /**
     *  Description of the Field
     */
    protected double multGreen = 1.0;
    /**
     *  Description of the Field
     */
    protected double multBlue = 1.0;

    /**
     *  Description of the Field
     */
    protected int addRed = 0;
    /**
     *  Description of the Field
     */
    protected int addGreen = 0;
    /**
     *  Description of the Field
     */
    protected int addBlue = 0;

    /**
     *  Description of the Field
     */
    protected double multAlpha = 1.0;
    //used by AlphaTransform
    /**
     *  Description of the Field
     */
    protected int addAlpha = 0;


    //used by AlphaTransform

    /**
     *  Gets the multRed attribute of the ColorTransform object
     *
     *@return    The multRed value
     */
    public double getMultRed() {
        return multRed;
    }


    /**
     *  Gets the multGreen attribute of the ColorTransform object
     *
     *@return    The multGreen value
     */
    public double getMultGreen() {
        return multGreen;
    }


    /**
     *  Gets the multBlue attribute of the ColorTransform object
     *
     *@return    The multBlue value
     */
    public double getMultBlue() {
        return multBlue;
    }


    /**
     *  Gets the addRed attribute of the ColorTransform object
     *
     *@return    The addRed value
     */
    public int getAddRed() {
        return addRed;
    }


    /**
     *  Gets the addGreen attribute of the ColorTransform object
     *
     *@return    The addGreen value
     */
    public int getAddGreen() {
        return addGreen;
    }


    /**
     *  Gets the addBlue attribute of the ColorTransform object
     *
     *@return    The addBlue value
     */
    public int getAddBlue() {
        return addBlue;
    }


    /**
     *  Sets the multRed attribute of the ColorTransform object
     *
     *@param  multRed  The new multRed value
     */
    public void setMultRed(double multRed) {
        this.multRed = multRed;
    }


    /**
     *  Sets the multGreen attribute of the ColorTransform object
     *
     *@param  multGreen  The new multGreen value
     */
    public void setMultGreen(double multGreen) {
        this.multGreen = multGreen;
    }


    /**
     *  Sets the multBlue attribute of the ColorTransform object
     *
     *@param  multBlue  The new multBlue value
     */
    public void setMultBlue(double multBlue) {
        this.multBlue = multBlue;
    }


    /**
     *  Sets the addRed attribute of the ColorTransform object
     *
     *@param  addRed  The new addRed value
     */
    public void setAddRed(int addRed) {
        this.addRed = addRed;
    }


    /**
     *  Sets the addGreen attribute of the ColorTransform object
     *
     *@param  addGreen  The new addGreen value
     */
    public void setAddGreen(int addGreen) {
        this.addGreen = addGreen;
    }


    /**
     *  Sets the addBlue attribute of the ColorTransform object
     *
     *@param  addBlue  The new addBlue value
     */
    public void setAddBlue(int addBlue) {
        this.addBlue = addBlue;
    }


    /**
     *  An identity transform
     */
    public ColorTransform() { }


    /**
     *  Constructor for the ColorTransform object
     *
     *@param  multRed    Description of the Parameter
     *@param  multGreen  Description of the Parameter
     *@param  multBlue   Description of the Parameter
     *@param  addRed     Description of the Parameter
     *@param  addGreen   Description of the Parameter
     *@param  addBlue    Description of the Parameter
     */
    public ColorTransform(double multRed, double multGreen, double multBlue,
            int addRed, int addGreen, int addBlue) {
        this.multRed = multRed;
        this.multGreen = multGreen;
        this.multBlue = multBlue;
        this.addRed = addRed;
        this.addGreen = addGreen;
        this.addBlue = addBlue;
    }


    /**
     *  Constructor for the ColorTransform object
     *
     *@param  addRed    Description of the Parameter
     *@param  addGreen  Description of the Parameter
     *@param  addBlue   Description of the Parameter
     */
    public ColorTransform(int addRed, int addGreen, int addBlue) {
        this.addRed = addRed;
        this.addGreen = addGreen;
        this.addBlue = addBlue;
    }


    /**
     *  Constructor for the ColorTransform object
     *
     *@param  multRed    Description of the Parameter
     *@param  multGreen  Description of the Parameter
     *@param  multBlue   Description of the Parameter
     */
    public ColorTransform(double multRed, double multGreen, double multBlue) {
        this.multRed = multRed;
        this.multGreen = multGreen;
        this.multBlue = multBlue;
    }


    /**
     *  Constructor for the ColorTransform object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public ColorTransform(InStream in) throws IOException {
        in.synchBits();

        //--Add and mult are reversed
        boolean hasAddTerms = (in.readUBits(1) == 1);
        boolean hasMultTerms = (in.readUBits(1) == 1);

        int numBits = (int) in.readUBits(4);

        if (hasMultTerms) {
            multRed = ((double) in.readSBits(numBits)) / 256.0;
            multGreen = ((double) in.readSBits(numBits)) / 256.0;
            multBlue = ((double) in.readSBits(numBits)) / 256.0;
        }

        if (hasAddTerms) {
            addRed = in.readSBits(numBits);
            addGreen = in.readSBits(numBits);
            addBlue = in.readSBits(numBits);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        out.flushBits();

        boolean hasAddTerms = (addRed != 0)
                || (addGreen != 0)
                || (addBlue != 0);

        boolean hasMultTerms = (multRed != 1.0)
                || (multGreen != 1.0)
                || (multBlue != 1.0);

        int intMultRed = (int) (multRed * 256.0);
        int intMultGreen = (int) (multGreen * 256.0);
        int intMultBlue = (int) (multBlue * 256.0);

        //--Figure out the bit sizes
        int numBits = 1;

        if (hasAddTerms) {
            int redBits = OutStream.determineSignedBitSize(addRed);
            int greenBits = OutStream.determineSignedBitSize(addGreen);
            int blueBits = OutStream.determineSignedBitSize(addBlue);

            if (numBits < redBits) {
                numBits = redBits;
            }
            if (numBits < greenBits) {
                numBits = greenBits;
            }
            if (numBits < blueBits) {
                numBits = blueBits;
            }
        }

        if (hasMultTerms) {
            int redBits = OutStream.determineSignedBitSize(intMultRed);
            int greenBits = OutStream.determineSignedBitSize(intMultGreen);
            int blueBits = OutStream.determineSignedBitSize(intMultBlue);

            if (numBits < redBits) {
                numBits = redBits;
            }
            if (numBits < greenBits) {
                numBits = greenBits;
            }
            if (numBits < blueBits) {
                numBits = blueBits;
            }
        }

        //--Add and mult are reversed
        out.writeUBits(1, hasAddTerms ? 1L : 0L);
        out.writeUBits(1, hasMultTerms ? 1L : 0L);
        out.writeUBits(4, numBits);

        if (hasMultTerms) {
            out.writeSBits(numBits, intMultRed);
            out.writeSBits(numBits, intMultGreen);
            out.writeSBits(numBits, intMultBlue);
        }

        if (hasAddTerms) {
            out.writeSBits(numBits, addRed);
            out.writeSBits(numBits, addGreen);
            out.writeSBits(numBits, addBlue);
        }

        out.flushBits();
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeWithoutAlpha(OutStream out) throws IOException {
        write(out);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeWithAlpha(OutStream out) throws IOException {
        out.flushBits();

        boolean hasAddTerms = (addRed != 0)
                || (addGreen != 0)
                || (addBlue != 0)
                || (addAlpha != 0);

        boolean hasMultTerms = (multRed != 1.0)
                || (multGreen != 1.0)
                || (multBlue != 1.0)
                || (multAlpha != 1.0);

        int intMultRed = (int) (multRed * 256.0);
        int intMultGreen = (int) (multGreen * 256.0);
        int intMultBlue = (int) (multBlue * 256.0);
        int intMultAlpha = (int) (multAlpha * 256.0);

        //--Figure out the bit sizes
        int numBits = 1;

        if (hasAddTerms) {
            int redBits = OutStream.determineSignedBitSize(addRed);
            int greenBits = OutStream.determineSignedBitSize(addGreen);
            int blueBits = OutStream.determineSignedBitSize(addBlue);
            int alphaBits = OutStream.determineSignedBitSize(addAlpha);

            if (numBits < redBits) {
                numBits = redBits;
            }
            if (numBits < greenBits) {
                numBits = greenBits;
            }
            if (numBits < blueBits) {
                numBits = blueBits;
            }
            if (numBits < alphaBits) {
                numBits = alphaBits;
            }
        }

        if (hasMultTerms) {
            int redBits = OutStream.determineSignedBitSize(intMultRed);
            int greenBits = OutStream.determineSignedBitSize(intMultGreen);
            int blueBits = OutStream.determineSignedBitSize(intMultBlue);
            int alphaBits = OutStream.determineSignedBitSize(intMultAlpha);

            if (numBits < redBits) {
                numBits = redBits;
            }
            if (numBits < greenBits) {
                numBits = greenBits;
            }
            if (numBits < blueBits) {
                numBits = blueBits;
            }
            if (numBits < alphaBits) {
                numBits = alphaBits;
            }
        }

        //--Add and mult are reversed
        out.writeUBits(1, hasAddTerms ? 1L : 0L);
        out.writeUBits(1, hasMultTerms ? 1L : 0L);
        out.writeUBits(4, numBits);

        if (hasMultTerms) {
            out.writeSBits(numBits, intMultRed);
            out.writeSBits(numBits, intMultGreen);
            out.writeSBits(numBits, intMultBlue);
            out.writeSBits(numBits, intMultAlpha);
        }

        if (hasAddTerms) {
            out.writeSBits(numBits, addRed);
            out.writeSBits(numBits, addGreen);
            out.writeSBits(numBits, addBlue);
            out.writeSBits(numBits, addAlpha);
        }

        out.flushBits();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return " cxform(+rgb,*rgb)=(" + addRed + "," + addGreen + "," + addBlue
                + "," + multRed + "," + multGreen + "," + multBlue + ")";
    }
}
