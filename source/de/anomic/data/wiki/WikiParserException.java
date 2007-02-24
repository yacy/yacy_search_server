package de.anomic.data.wiki;

public class WikiParserException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public WikiParserException() {  }
    
    public WikiParserException(String message) {
        super(message);
    }
    
    public WikiParserException(Throwable cause) {
        super(cause);
    }
    
    public WikiParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
