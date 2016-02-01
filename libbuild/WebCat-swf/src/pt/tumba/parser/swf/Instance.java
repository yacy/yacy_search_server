package pt.tumba.parser.swf;

/**
 *  An instance of a Symbol that has been placed on the stage.
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Instance {
    /**
     *  Description of the Field
     */
    protected Symbol symbol;
    /**
     *  Description of the Field
     */
    protected int depth;


    /**
     *  Constructor for the Instance object
     *
     *@param  symbol  Description of the Parameter
     *@param  depth   Description of the Parameter
     */
    protected Instance(Symbol symbol, int depth) {
        this.symbol = symbol;
        this.depth = depth;
    }


    /**
     *  Gets the depth attribute of the Instance object
     *
     *@return    The depth value
     */
    public int getDepth() {
        return depth;
    }


    /**
     *  Gets the symbol attribute of the Instance object
     *
     *@return    The symbol value
     */
    public Symbol getSymbol() {
        return symbol;
    }
}
