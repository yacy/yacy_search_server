package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  A Symbol that is to be imported (within the Player) from another Flash
 *  movie. The import/export feature only works with Flash version 5 and up.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ImportedSymbol extends Symbol {
    /**
     *  Description of the Field
     */
    protected String name;
    /**
     *  Description of the Field
     */
    protected String libName;


    /**
     *  Constructor for the ImportedSymbol object
     *
     *@param  id       Description of the Parameter
     *@param  name     Description of the Parameter
     *@param  libName  Description of the Parameter
     */
    protected ImportedSymbol(int id, String name, String libName) {
        super(id);
        this.name = name;
        this.libName = libName;
    }


    /**
     *  The import name of the symbol
     *
     *@return    The name value
     */
    public String getName() {
        return name;
    }


    /**
     *  The library name (another Flash movie)
     *
     *@return    The libraryName value
     */
    public String getLibraryName() {
        return libName;
    }


    /**
     *  Description of the Method
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionwriter  Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  IOException   Description of the Exception
     */
    protected int defineSymbol(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionwriter)
             throws IOException {
        return getNextId(movie);
    }
}
