package de.anomic.plasma.cache;

/**
 * This exception is thrown when a protocol (or a derivative using this protocol) is not
 * supported, as is the case in the {@link ResourceInfoFactory}.
 * @see package {@link de.anomic.plasma.cache} for all {@link IResourceInfo}s available
 */
public class UnsupportedProtocolException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    public static final String MESSAGE = "Unsupported protocol error: ";
    
    public UnsupportedProtocolException(final String protocol) {
        super(MESSAGE + protocol);
    }
    
    public UnsupportedProtocolException(final String protocol, final Throwable cause) {
        super(MESSAGE + protocol, cause);
    }
}
