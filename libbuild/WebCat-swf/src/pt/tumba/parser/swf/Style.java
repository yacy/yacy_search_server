package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Common interface for Fill and Line Styles
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface Style {
    /**
     *  Description of the Method
     *
     *@param  out              Description of the Parameter
     *@param  hasAlpha         Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    void write(OutStream out, boolean hasAlpha) throws IOException;
}
