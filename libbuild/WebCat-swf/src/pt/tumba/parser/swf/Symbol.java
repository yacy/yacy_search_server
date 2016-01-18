package pt.tumba.parser.swf;

import java.io.IOException;

/**
 *  Base class for all defined symbols
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public abstract class Symbol {
    /**
     *  Description of the Field
     */
    protected int id = 0;


    /**
     *  Constructor for the Symbol object
     */
    protected Symbol() { }


    /**
     *  Constructor for the Symbol object
     *
     *@param  id  Description of the Parameter
     */
    protected Symbol(int id) {
        this.id = id;
    }


    /**
     *  Get the internal SWF id for the symbol. This will always be zero for a
     *  Movie that was not loaded from an existing SWF until the Movie is
     *  written out.
     *
     *@return    The id value
     */
    public int getId() {
        return id;
    }


    /**
     *  Make sure that the Symbol is fully defined in the given Movie and return
     *  the character id
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionWriter  Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  IOException   Description of the Exception
     */
    protected int define(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionWriter)
             throws IOException {
        Integer integerId = (Integer) movie.definedSymbols.get(this);

        if (integerId == null) {
            integerId = new Integer(defineSymbol(movie,
                    timelineWriter,
                    definitionWriter));
            movie.definedSymbols.put(this, integerId);
        }

        id = integerId.intValue();
        return id;
    }


    /**
     *  Gets the nextId attribute of the Symbol object
     *
     *@param  movie  Description of the Parameter
     *@return        The nextId value
     */
    protected int getNextId(Movie movie) {
        return movie.maxId++;
    }


    /**
     *  Override to provide symbol definition
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionwriter  Description of the Parameter
     *@return                   the new symbol id
     *@exception  IOException   Description of the Exception
     */
    protected abstract int defineSymbol(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionwriter)
             throws IOException;
}
