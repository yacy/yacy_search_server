package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Interface for passing SWF Header and Generic Tags.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public interface SWFTags extends SWFHeader {
    /**
     *  A SWF tag.
     *
     *@param  tagType          a type of zero (TAG_END) denotes the end of the
     *      tags
     *@param  longTag          true if the tag header is forced into the long
     *      form
     *@param  contents         may be null
     *@exception  IOException  Description of the Exception
     */
    public void tag(int tagType, boolean longTag, byte[] contents)
             throws IOException;

}
