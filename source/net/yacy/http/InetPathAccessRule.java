package net.yacy.http;

/** Container-neutral representation of a server-client address/path rule. */
public final class InetPathAccessRule {

    private static final String DEFAULT_PATH = "/*";

    private final String addressPattern;
    private final String pathPattern;

    private InetPathAccessRule(final String addressPattern, final String pathPattern) {
        this.addressPattern = addressPattern;
        this.pathPattern = pathPattern;
    }

    public static InetPathAccessRule parse(final String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Access rule must not be empty");
        }
        final int separator = pattern.indexOf('|');
        final String address = separator > 0 ? pattern.substring(0, separator) : pattern;
        final String path = separator > 0 && pattern.length() > separator + 1
                ? pattern.substring(separator + 1)
                : DEFAULT_PATH;
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Access rule has no address: " + pattern);
        }
        return new InetPathAccessRule(address, path);
    }

    public String addressPattern() {
        return this.addressPattern;
    }

    public String pathPattern() {
        return this.pathPattern;
    }

    public String asJettyPattern() {
        return this.addressPattern + '|' + this.pathPattern;
    }
}
