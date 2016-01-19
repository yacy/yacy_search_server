package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Description of the Class
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class LineStyle implements Style {
    /**
     *  Description of the Field
     */
    protected int width;
    /**
     *  Description of the Field
     */
    protected Color color;


    /**
     *  Gets the width attribute of the LineStyle object
     *
     *@return    The width value
     */
    public int getWidth() {
        return width;
    }


    /**
     *  Gets the color attribute of the LineStyle object
     *
     *@return    The color value
     */
    public Color getColor() {
        return color;
    }


    /**
     *  Constructor for the LineStyle object
     *
     *@param  width  Description of the Parameter
     *@param  color  Description of the Parameter
     */
    public LineStyle(int width, Color color) {
        this.width = width;
        this.color = color;
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out, boolean hasAlpha) throws IOException {
        out.writeUI16(width);

        if (hasAlpha) {
            color.writeWithAlpha(out);
        } else {
            color.writeRGB(out);
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
    public static void writeMorphLineStyle(OutStream out,
            LineStyle startStyle,
            LineStyle endStyle)
             throws IOException {
        out.writeUI16(startStyle.width);
        out.writeUI16(endStyle.width);

        startStyle.color.writeWithAlpha(out);
        endStyle.color.writeWithAlpha(out);
    }
}
