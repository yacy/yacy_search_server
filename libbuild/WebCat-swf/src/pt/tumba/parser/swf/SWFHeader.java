package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing a SWF file header
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFHeader {
    /**
     *  SWF File header.
     *
     *@param  length           -1 if the length is unknown and must be inferred
     *@param  frameCount       -1 if the framecount is unknown and must be
     *      inferred
     *@param  version          Description of the Parameter
     *@param  twipsWidth       Description of the Parameter
     *@param  twipsHeight      Description of the Parameter
     *@param  frameRate        Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void header(int version, long length,
            int twipsWidth, int twipsHeight,
            int frameRate, int frameCount) throws IOException;
}
