package pt.tumba.parser.swf;


/**
 *  A Transformation matrix that has translation coordinates in pixels
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Transform extends Matrix {
    /**
     *  Copy an existing matrix
     *
     *@param  matrix  Description of the Parameter
     */
    public Transform(Matrix matrix) {
        super(matrix);
    }


    /**
     *  An identity transform
     */
    public Transform() { }


    /**
     *  A transform that only translates
     *
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Transform(double translateX, double translateY) {
        super(translateX, translateY);
    }


    /**
     *  A transform that rotates and translates
     *
     *@param  radians     Description of the Parameter
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Transform(double radians, double translateX, double translateY) {
        this(radians, 1.0, 1.0, translateX, translateY);
    }


    /**
     *  A transform that scales and translates
     *
     *@param  scaleX      Description of the Parameter
     *@param  scaleY      Description of the Parameter
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Transform(double scaleX, double scaleY,
            double translateX, double translateY) {
        super(scaleX, scaleY, 0.0, 0.0, translateX, translateY);
    }


    /**
     *  A transform that rotates, scales and translates
     *
     *@param  radians     Description of the Parameter
     *@param  scaleX      Description of the Parameter
     *@param  scaleY      Description of the Parameter
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Transform(double radians,
            double scaleX, double scaleY,
            double translateX, double translateY) {
        super(scaleX * Math.cos(radians),
                scaleY * Math.cos(radians),
                Math.sin(radians),
                -Math.sin(radians),
                translateX, translateY);
    }


    /**
     *  Specify all the matrix components
     *
     *@param  scaleX      Description of the Parameter
     *@param  scaleY      Description of the Parameter
     *@param  skew0       Description of the Parameter
     *@param  skew1       Description of the Parameter
     *@param  translateX  Description of the Parameter
     *@param  translateY  Description of the Parameter
     */
    public Transform(double scaleX, double scaleY,
            double skew0, double skew1,
            double translateX, double translateY) {
        super(scaleX, scaleY, skew0, skew1, translateX, translateY);
    }


    /**
     *  Gets the translateX attribute of the Transform object
     *
     *@return    The translateX value
     */
    public double getTranslateX() {
        return translateX / (double) SWFConstants.TWIPS;
    }


    /**
     *  Gets the translateY attribute of the Transform object
     *
     *@return    The translateY value
     */
    public double getTranslateY() {
        return translateY / (double) SWFConstants.TWIPS;
    }


    /**
     *  Sets the translateX attribute of the Transform object
     *
     *@param  translateX  The new translateX value
     */
    public void setTranslateX(double translateX) {
        this.translateX = translateX * (double) SWFConstants.TWIPS;
    }


    /**
     *  Sets the translateY attribute of the Transform object
     *
     *@param  translateY  The new translateY value
     */
    public void setTranslateY(double translateY) {
        this.translateY = translateY * (double) SWFConstants.TWIPS;
    }
}
