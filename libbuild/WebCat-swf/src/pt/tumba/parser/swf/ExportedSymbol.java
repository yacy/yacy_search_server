package pt.tumba.parser.swf;


/**
 *  Information about a symbol exported from the movie
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class ExportedSymbol {
    /**
     *  Description of the Field
     */
    protected Symbol symbol;
    /**
     *  Description of the Field
     */
    protected String exportName;


    /**
     *  Gets the symbol attribute of the ExportedSymbol object
     *
     *@return    The symbol value
     */
    public Symbol getSymbol() {
        return symbol;
    }


    /**
     *  Gets the exportName attribute of the ExportedSymbol object
     *
     *@return    The exportName value
     */
    public String getExportName() {
        return exportName;
    }


    /**
     *  Constructor for the ExportedSymbol object
     *
     *@param  s     Description of the Parameter
     *@param  name  Description of the Parameter
     */
    protected ExportedSymbol(Symbol s, String name) {
        symbol = s;
        exportName = name;
    }
}
