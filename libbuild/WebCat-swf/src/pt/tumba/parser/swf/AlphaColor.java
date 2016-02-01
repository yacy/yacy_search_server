package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  A Color with an Alpha component
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class AlphaColor extends Color {
    /**
     *  Description of the Field
     */
    protected int alpha;


    /**
     *  Gets the alpha attribute of the AlphaColor object
     *
     *@return    The alpha value
     */
    public int getAlpha() {
        return alpha;
    }


    /**
     *  Sets the alpha attribute of the AlphaColor object
     *
     *@param  alpha  The new alpha value
     */
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }


    /**
     *  Constructor for the AlphaColor object
     *
     *@param  red    Description of the Parameter
     *@param  green  Description of the Parameter
     *@param  blue   Description of the Parameter
     *@param  alpha  Description of the Parameter
     */
    public AlphaColor(int red, int green, int blue, int alpha) {
        super(red, green, blue);
        this.alpha = alpha;
    }


    /**
     *  Constructor for the AlphaColor object
     *
     *@param  color  Description of the Parameter
     *@param  alpha  Description of the Parameter
     */
    public AlphaColor(Color color, int alpha) {
        this(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }


    /**
     *  Constructor for the AlphaColor object
     *
     *@param  in               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public AlphaColor(InStream in) throws IOException {
        super(in);
        alpha = in.readUI8();
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write(OutStream out) throws IOException {
        writeRGB(out);
        out.writeUI8(alpha);
    }


    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void writeWithAlpha(OutStream out) throws IOException {
        write(out);
    }


    /**
     *  Description of the Method
     *
     *@param  color  Description of the Parameter
     *@return        Description of the Return Value
     */
    public boolean equals(Object color) {
        return super.equals(color) && (alpha == ((AlphaColor)color).getAlpha());
    }

    public int hashCode() { 
    	 return super.hashCode();
    }
    

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return "RGBA(" + red + "," + green + "," + blue + "," + alpha + ")";
    }
}
