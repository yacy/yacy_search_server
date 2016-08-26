package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class AlphaTransform extends ColorTransform {
    /**
     *  Gets the multAlpha attribute of the AlphaTransform object
     *
     *@return    The multAlpha value
     */
    public double getMultAlpha() {
        return multAlpha;
    }


    /**
     *  Gets the addAlpha attribute of the AlphaTransform object
     *
     *@return    The addAlpha value
     */
    public int getAddAlpha() {
        return addAlpha;
    }


    /**
     *  Sets the multAlpha attribute of the AlphaTransform object
     *
     *@param  multAlpha  The new multAlpha value
     */
    public void setMultAlpha(double multAlpha) {
        this.multAlpha = multAlpha;
    }


    /**
     *  Sets the addAlpha attribute of the AlphaTransform object
     *
     *@param  addAlpha  The new addAlpha value
     */
    public void setAddAlpha(int addAlpha) {
        this.addAlpha = addAlpha;
    }


    /**
     *  An identity transform
     */
    public AlphaTransform() { }


    /**
     *  Copy another transform
     *
     *@param  copy  Description of the Parameter
     */
    public AlphaTransform(ColorTransform copy) {
        if (copy == null) {
            return;
        }
        this.addRed = copy.addRed;
        this.addGreen = copy.addGreen;
        this.addBlue = copy.addBlue;
        this.addAlpha = copy.addAlpha;

        this.multRed = copy.multRed;
        this.multGreen = copy.multGreen;
        this.multBlue = copy.multBlue;
        this.multAlpha = copy.multAlpha;
    }


    /**
     *  Constructor for the AlphaTransform object
     *
     *@param  multRed    Description of the Parameter
     *@param  multGreen  Description of the Parameter
     *@param  multBlue   Description of the Parameter
     *@param  multAlpha  Description of the Parameter
     *@param  addRed     Description of the Parameter
     *@param  addGreen   Description of the Parameter
     *@param  addBlue    Description of the Parameter
     *@param  addAlpha   Description of the Parameter
     */
    public AlphaTransform(double multRed, double multGreen, double multBlue,
            double multAlpha,
            int addRed, int addGreen, int addBlue,
            int addAlpha) {
        super(multRed, multGreen, multBlue, addRed, addGreen, addBlue);
        this.multAlpha = multAlpha;
        this.addAlpha = addAlpha;
    }


    /**
     *  Constructor for the AlphaTransform object
     *
     *@param  addRed    Description of the Parameter
     *@param  addGreen  Description of the Parameter
     *@param  addBlue   Description of the Parameter
     *@param  addAlpha  Description of the Parameter
     */
    public AlphaTransform(int addRed, int addGreen, int addBlue, int addAlpha) {
        super(addRed, addGreen, addBlue);
        this.addAlpha = addAlpha;
    }


    /**
     *  Constructor for the AlphaTransform object
     *
     *@param  multRed    Description of the Parameter
     *@param  multGreen  Description of the Parameter
     *@param  multBlue   Description of the Parameter
     *@param  multAplha  Description of the Parameter
     */
    public AlphaTransform(double multRed, double multGreen, double multBlue,
            double multAlpha) {
        super(multRed, multGreen, multBlue);
        this.multAlpha = multAlpha;
    }


    /**
     *  Constructor for the AlphaTransform object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public AlphaTransform(InStream in) throws IOException {
        in.synchBits();

        //--Add and mult are reversed
        boolean hasAddTerms = (in.readUBits(1) == 1);
        boolean hasMultTerms = (in.readUBits(1) == 1);

        int numBits = (int) in.readUBits(4);

        if (hasMultTerms) {
            multRed = ((double) in.readSBits(numBits)) / 256.0;
            multGreen = ((double) in.readSBits(numBits)) / 256.0;
            multBlue = ((double) in.readSBits(numBits)) / 256.0;
            multAlpha = ((double) in.readSBits(numBits)) / 256.0;
        }

        if (hasAddTerms) {
            addRed = in.readSBits(numBits);
            addGreen = in.readSBits(numBits);
            addBlue = in.readSBits(numBits);
            addAlpha = in.readSBits(numBits);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        writeWithAlpha(out);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeWithoutAlpha(OutStream out) throws IOException {
        super.write(out);
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return " cxform(+rgba,*rgba)=(" + addRed + "," + addGreen + "," + addBlue
                + "," + addAlpha + "," + multRed + "," + multGreen + "," +
                multBlue + "," + multAlpha + ")";
    }
}
