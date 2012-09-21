package net.yacy.crawler.retrieval;

public class ImporterException extends Exception {

    private static final long serialVersionUID = 6070972210596234670L;

    public ImporterException(final String message) {
		super(message);
	}
	
    public ImporterException(final String message, final Throwable cause) {
        super(message, cause);
    }	
}
