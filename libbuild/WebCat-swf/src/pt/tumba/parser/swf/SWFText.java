package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing static text
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFText {
    /**
     *  Description of the Method
     *
     *@param  fontId           Description of the Parameter
     *@param  textHeight       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void font(int fontId, int textHeight) throws IOException;


    /**
     *  Color is AlphaColor for DefineText2
     *
     *@param  color            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void color(Color color) throws IOException;


    /**
     *  Sets the x attribute of the SWFText object
     *
     *@param  x                The new x value
     *@exception  IOException  Description of the Exception
     */
    public void setX(int x) throws IOException;


    /**
     *  Sets the y attribute of the SWFText object
     *
     *@param  y                The new y value
     *@exception  IOException  Description of the Exception
     */
    public void setY(int y) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  glyphIndices     Description of the Parameter
     *@param  glyphAdvances    Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void text(int[] glyphIndices, int[] glyphAdvances) throws IOException;


    /**
     *  Called at end of all text
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException;
}
