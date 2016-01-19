package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class FillStyle implements Style {
    /**
     *  Description of the Field
     */
    protected int fillType;
    /**
     *  Description of the Field
     */
    protected Color color;
    /**
     *  Description of the Field
     */
    protected Matrix matrix;
    /**
     *  Description of the Field
     */
    protected int[] ratios;
    //for gradient fill
    /**
     *  Description of the Field
     */
    protected Color[] colors;
    //for gradient fill
    /**
     *  Description of the Field
     */
    protected int bitmapId;


    /**
     *  Gets the type attribute of the FillStyle object
     *
     *@return    The type value
     */
    public int getType() {
        return fillType;
    }


    /**
     *  Gets the solidColor attribute of the FillStyle object
     *
     *@return    The solidColor value
     */
    public Color getSolidColor() {
        return color;
    }


    /**
     *  Gets the matrix attribute of the FillStyle object
     *
     *@return    The matrix value
     */
    public Matrix getMatrix() {
        return matrix;
    }


    /**
     *  Gets the imageId attribute of the FillStyle object
     *
     *@return    The imageId value
     */
    public int getImageId() {
        return bitmapId;
    }


    /**
     *  Gets the gradientRatios attribute of the FillStyle object
     *
     *@return    The gradientRatios value
     */
    public int[] getGradientRatios() {
        return ratios;
    }


    /**
     *  Gets the gradientColors attribute of the FillStyle object
     *
     *@return    The gradientColors value
     */
    public Color[] getGradientColors() {
        return colors;
    }


    /**
     *  Solid color fill (alpha depends on the TagDefineShapeX tag used)
     *
     *@param  solidColor  Description of the Parameter
     */
    public FillStyle(Color solidColor) {
        fillType = SWFConstants.FILL_SOLID;
        color = solidColor;
    }


    /**
     *  Linear/Radial Gradient Fill
     *
     *@param  matrix  Description of the Parameter
     *@param  ratios  Description of the Parameter
     *@param  colors  Description of the Parameter
     *@param  radial  Description of the Parameter
     */
    public FillStyle(Matrix matrix, int[] ratios,
            Color[] colors, boolean radial) {
        this.matrix = matrix;
        this.ratios = ratios;
        this.colors = colors;

        fillType = radial ? SWFConstants.FILL_RADIAL_GRADIENT :
                SWFConstants.FILL_LINEAR_GRADIENT;
    }


    /**
     *  Bitmap fill
     *
     *@param  bitmapId  Description of the Parameter
     *@param  matrix    Description of the Parameter
     *@param  clipped   Description of the Parameter
     */
    public FillStyle(int bitmapId, Matrix matrix, boolean clipped) {
        this.matrix = matrix;
        this.bitmapId = bitmapId;

        fillType = clipped ? SWFConstants.FILL_CLIPPED_BITMAP :
                SWFConstants.FILL_TILED_BITMAP;
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out, boolean hasAlpha) throws IOException {
        out.writeUI8(fillType);

        if (fillType == SWFConstants.FILL_SOLID) {
            if (hasAlpha) {
                color.writeWithAlpha(out);
            } else {
                color.writeRGB(out);
            }
        } else if (fillType == SWFConstants.FILL_LINEAR_GRADIENT
                || fillType == SWFConstants.FILL_RADIAL_GRADIENT) {
            matrix.write(out);

            int numRatios = ratios.length;

            out.writeUI8(numRatios);

            for (int i = 0; i < numRatios; i++) {
                if (colors[i] == null) {
                    continue;
                }

                out.writeUI8(ratios[i]);

                if (hasAlpha) {
                    colors[i].writeWithAlpha(out);
                } else {
                    colors[i].writeRGB(out);
                }
            }
        } else if (fillType == SWFConstants.FILL_TILED_BITMAP
                || fillType == SWFConstants.FILL_CLIPPED_BITMAP) {
            out.writeUI16(bitmapId);
            matrix.write(out);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@param  startStyle       Description of the Parameter
     *@param  endStyle         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public static void writeMorphFillStyle(OutStream out,
            FillStyle startStyle,
            FillStyle endStyle)
             throws IOException {
        int fillType = startStyle.fillType;

        out.writeUI8(fillType);

        if (fillType == SWFConstants.FILL_SOLID) {
            startStyle.color.writeWithAlpha(out);
            endStyle.color.writeWithAlpha(out);
        } else if (fillType == SWFConstants.FILL_LINEAR_GRADIENT
                || fillType == SWFConstants.FILL_RADIAL_GRADIENT) {
            startStyle.matrix.write(out);
            endStyle.matrix.write(out);

            int numRatios = startStyle.ratios.length;
            out.writeUI8(startStyle.ratios.length);

            for (int i = 0; i < numRatios; i++) {
                if (startStyle.colors[i] == null ||
                        endStyle.colors[i] == null) {
                    continue;
                }

                out.writeUI8(startStyle.ratios[i]);
                startStyle.colors[i].writeWithAlpha(out);

                out.writeUI8(endStyle.ratios[i]);
                endStyle.colors[i].writeWithAlpha(out);
            }
        } else if (fillType == SWFConstants.FILL_TILED_BITMAP
                || fillType == SWFConstants.FILL_CLIPPED_BITMAP) {
            int bitmapId = startStyle.bitmapId;

            out.writeUI16(bitmapId);

            startStyle.matrix.write(out);
            endStyle.matrix.write(out);
        }
    }
}
