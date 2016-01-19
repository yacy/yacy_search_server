package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  An RGB Color without alpha
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Color {
    /**
     *  Description of the Field
     */
    protected int red;
    /**
     *  Description of the Field
     */
    protected int green;
    /**
     *  Description of the Field
     */
    protected int blue;


    /**
     *  Gets the red attribute of the Color object
     *
     *@return    The red value
     */
    public int getRed() {
        return red;
    }


    /**
     *  Gets the green attribute of the Color object
     *
     *@return    The green value
     */
    public int getGreen() {
        return green;
    }


    /**
     *  Gets the blue attribute of the Color object
     *
     *@return    The blue value
     */
    public int getBlue() {
        return blue;
    }


    /**
     *  Sets the red attribute of the Color object
     *
     *@param  red  The new red value
     */
    public void setRed(int red) {
        this.red = red;
    }


    /**
     *  Sets the green attribute of the Color object
     *
     *@param  green  The new green value
     */
    public void setGreen(int green) {
        this.green = green;
    }


    /**
     *  Sets the blue attribute of the Color object
     *
     *@param  blue  The new blue value
     */
    public void setBlue(int blue) {
        this.blue = blue;
    }


    /**
     *  Constructor for the Color object
     *
     *@param  red    Description of the Parameter
     *@param  green  Description of the Parameter
     *@param  blue   Description of the Parameter
     */
    public Color(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }


    /**
     *  Constructor for the Color object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public Color(InStream in) throws IOException {
        red = in.readUI8();
        green = in.readUI8();
        blue = in.readUI8();
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        writeRGB(out);
    }


    /**
     *  Description of the Method
     *
     *@param  color  Description of the Parameter
     *@return        Description of the Return Value
     */
    public boolean equals(Object color) {
        return (red == ((Color)color).getRed())
                && (green == ((Color)color).getGreen())
                && (blue == ((Color)color).getBlue());
    }
    
    public int hashCode () {
     	return super.hashCode();
     }
    


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeRGB(OutStream out) throws IOException {
        out.writeUI8(red);
        out.writeUI8(green);
        out.writeUI8(blue);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeWithAlpha(OutStream out) throws IOException {
        writeRGB(out);
        out.writeUI8(0xff);
        //fully opaque alpha
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return "RGB(" + red + "," + green + "," + blue + ")";
    }
}
