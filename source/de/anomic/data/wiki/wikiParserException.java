package de.anomic.data.wiki;

public class wikiParserException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    public wikiParserException() {  }
    
    public wikiParserException(final String message) {
        super(message);
    }
    
    public wikiParserException(final Throwable cause) {
        super(cause);
    }
    
    public wikiParserException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
