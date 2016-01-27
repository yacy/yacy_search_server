package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing basic shape information without styles.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFVectors {
    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    public void done() throws IOException;


    /**
     *  Description of the Method
     *
     *@param  dx               Description of the Parameter
     *@param  dy               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void line(int dx, int dy) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  cx               Description of the Parameter
     *@param  cy               Description of the Parameter
     *@param  dx               Description of the Parameter
     *@param  dy               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void curve(int cx, int cy, int dx, int dy) throws IOException;


    /**
     *  Description of the Method
     *
     *@param  x                Description of the Parameter
     *@param  y                Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void move(int x, int y) throws IOException;
}
