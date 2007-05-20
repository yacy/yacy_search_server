package de.anomic.data.wiki;

public class wikiParserException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    public wikiParserException() {  }
    
    public wikiParserException(String message) {
        super(message);
    }
    
    public wikiParserException(Throwable cause) {
        super(cause);
    }
    
    public wikiParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
