package de.anomic.crawler;

public class ImporterException extends Exception {

    private static final long serialVersionUID = 6070972210596234670L;

    public ImporterException(String message) {
		super(message);
	}
	
    public ImporterException(String message, Throwable cause) {
        super(message, cause);
    }	
}
