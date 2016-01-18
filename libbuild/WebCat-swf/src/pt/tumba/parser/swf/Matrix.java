package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Matrix {
    /**
     *  Description of the Field
     */
    protected double scaleX = 1.0;
    /**
     *  Description of the Field
     */
    protected double scaleY = 1.0;

    /**
     *  Description of the Field
     */
    protected double skew0 = 0.0;
    /**
     *  Description of the Field
     */
    protected double skew1 = 0.0;

    /**
     *  Description of the Field
     */
    protected double translateX = 0.0;
    /**
     *  Description of the Field
     */
    protected double translateY = 0.0;


    /**
     *  Gets the scaleX attribute of the Matrix object
     *
     *@return    The scaleX value
     */
    public double getScaleX() {
        return scaleX;
    }


    /**
     *  Gets the scaleY attribute of the Matrix object
     *
     *@return    The scaleY value
     */
    public double getScaleY() {
        return scaleY;
    }


    /**
     *  Gets the skew0 attribute of the Matrix object
     *
     *@return    The skew0 value
     */
    public double getSkew0() {
        return skew0;
    }


    /**
     *  Gets the skew1 attribute of the Matrix object
     *
     *@return    The skew1 value
     */
    public double getSkew1() {
        return skew1;
    }


    /**
     *  Gets the translateX attribute of the Matrix object
     *
     *@return    The translateX value
     */
    public double getTranslateX() {
        return translateX;
    }


    /**
     *  Gets the translateY attribute of the Matrix object
     *
     *@return    The translateY value
     */
    public double getTranslateY() {
        return translateY;
    }


    /**
     *  Sets the scaleX attribute of the Matrix object
     *
     *@param  scaleX  The new scaleX value
     */
    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }


    /**
     *  Sets the scaleY attribute of the Matrix object
     *
     *@param  scaleY  The new scaleY value
     */
    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
    }


    /**
     *  Sets the skew0 attribute of the Matrix object
     *
     *@param  skew0  The new skew0 value
     */
    public void setSkew0(double skew0) {
        this.skew0 = skew0;
    }


    /**
     *  Sets the skew1 attribute of the Matrix object
     *
     *@param  skew1  The new skew1 value
     */
    public void setSkew1(double skew1) {
        this.skew1 = skew1;
    }


    /**
     *  Sets the translateX attribute of the Matrix object
     *
     *@param  translateX  The new translateX value
     */
    public void setTranslateX(double translateX) {
        this.translateX = translateX;
    }


    /**
     *  Sets the translateY attribute of the Matrix object
     *
     *@param  translateY  The new translateY value
     */
    public void setTranslateY(double translateY) {
        this.translateY = translateY;
    }


    /**
     *  An identity matrix
     */
    public Matrix() {
        this(1.0, 1.0, 0.0, 0.0, 0, 0);
    }


    /**
     *  Constructor for the Matrix object
     *
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Matrix(double translateX, double translateY) {
        this(1.0, 1.0, 0.0, 0.0, translateX, translateY);
    }


    /**
     *  Copy another matrix
     *
     *@param  copy  Description of the Parameter
     */
    public Matrix(Matrix copy) {
        if (copy == null) {
            return;
        }
        scaleX = copy.scaleX;
        scaleY = copy.scaleY;
        skew0 = copy.skew0;
        skew1 = copy.skew1;
        translateX = copy.translateX;
        translateY = copy.translateY;
    }


    /**
     *  Constructor for the Matrix object
     *
     *@param  scaleX      Description of the Parameter
     *@param  scaleY      Description of the Parameter
     *@param  skew0       Description of the Parameter
     *@param  skew1       Description of the Parameter
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Matrix(double scaleX, double scaleY,
            double skew0, double skew1,
            double translateX, double translateY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.skew0 = skew0;
            this.skew1 = skew1;
			this.translateX = translateX;
			this.translateY = translateY;
    }


    /**
     *  Constructor for the Matrix object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public Matrix(InStream in) throws IOException {
        in.synchBits();

        if (in.readUBits(1) == 1) {
            //has scale values

            int scaleBits = (int) in.readUBits(5);
            scaleX = ((double) in.readSBits(scaleBits)) / 65536.0;
            scaleY = ((double) in.readSBits(scaleBits)) / 65536.0;
        }

        if (in.readUBits(1) == 1) {
            //has rotate/skew values

            int skewBits = (int) in.readUBits(5);
            skew0 = ((double) in.readSBits(skewBits)) / 65536.0;
            skew1 = ((double) in.readSBits(skewBits)) / 65536.0;
        }

        int translateBits = (int) in.readUBits(5);
        translateX = in.readSBits(translateBits);
        translateY = in.readSBits(translateBits);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        out.flushBits();

        if (scaleX != 1.0 || scaleY != 1.0) {
            //if non-default values

            int intScaleX = (int) (scaleX * 65536.0);
            int intScaleY = (int) (scaleY * 65536.0);

            int scaleBits = OutStream.determineSignedBitSize(intScaleX);
            int scaleBits2 = OutStream.determineSignedBitSize(intScaleY);
            if (scaleBits < scaleBits2) {
                scaleBits = scaleBits2;
            }

            out.writeUBits(1, 1);
            out.writeUBits(5, scaleBits);
            out.writeSBits(scaleBits, intScaleX);
            out.writeSBits(scaleBits, intScaleY);
        } else {
            out.writeUBits(1, 0);
        }

        if (skew0 != 0.0 || skew1 != 0.0) {
            //if non-default values

            int intSkew0 = (int) (skew0 * 65536.0);
            int intSkew1 = (int) (skew1 * 65536.0);

            int skewBits = OutStream.determineSignedBitSize(intSkew0);
            int skewBits2 = OutStream.determineSignedBitSize(intSkew1);
            if (skewBits < skewBits2) {
                skewBits = skewBits2;
            }

            out.writeUBits(1, 1);
            out.writeUBits(5, skewBits);
            out.writeSBits(skewBits, intSkew0);
            out.writeSBits(skewBits, intSkew1);
        } else {
            out.writeUBits(1, 0);
        }

        if (translateX == 0 && translateY == 0) {
            out.writeUBits(5, 0);
        } else {
            int translateBits = OutStream.determineSignedBitSize((int) translateX);
            int translateBits2 = OutStream.determineSignedBitSize((int) translateY);
            if (translateBits < translateBits2) {
                translateBits = translateBits2;
            }

            out.writeUBits(5, translateBits);
            out.writeSBits(translateBits, (int) translateX);
            out.writeSBits(translateBits, (int) translateY);
        }

        out.flushBits();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return " Matrix(sx,sy,s0,s1,tx,ty)=(" +
                scaleX + "," + scaleY + "," + skew0 + "," + skew1 + "," +
                translateX + "," + translateY + ")";
    }
}
