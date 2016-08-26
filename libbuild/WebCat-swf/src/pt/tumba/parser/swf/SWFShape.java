package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing shape style information in addition to the basic
 *  vectors.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFShape extends SWFVectors {
    /**
     *  Sets the fillStyle0 attribute of the SWFShape object
     *
     *@param  styleIndex       The new fillStyle0 value
     *@exception  IOException  Description of the Exception
     */
    public void setFillStyle0(int styleIndex) throws IOException;


    /**
     *  Sets the fillStyle1 attribute of the SWFShape object
     *
     *@param  styleIndex       The new fillStyle1 value
     *@exception  IOException  Description of the Exception
     */
    public void setFillStyle1(int styleIndex) throws IOException;


    /**
     *  Sets the lineStyle attribute of the SWFShape object
     *
     *@param  styleIndex       The new lineStyle value
     *@exception  IOException  Description of the Exception
     */
    public void setLineStyle(int styleIndex) throws IOException;


    /**
     *  Solid color fill
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineFillStyle(Color color) throws IOException;


    /**
     *  Gradient fill - linear or radial.
     *
     *@param  colors           may have null elements - these (and the
     *      corresponding ratio) should be ignored
     *@param  matrix           Description of the Parameter
     *@param  ratios           Description of the Parameter
     *@param  radial           Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineFillStyle(Matrix matrix, int[] ratios,
            Color[] colors, boolean radial)
             throws IOException;


    /**
     *  Bitmap fill - tiled or clipped
     *
     *@param  bitmapId         Description of the Parameter
     *@param  matrix           Description of the Parameter
     *@param  clipped          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineFillStyle(int bitmapId, Matrix matrix, boolean clipped)
             throws IOException;


    /**
     *  Description of the Method
     *
     *@param  width            Description of the Parameter
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void defineLineStyle(int width, Color color) throws IOException;
}
