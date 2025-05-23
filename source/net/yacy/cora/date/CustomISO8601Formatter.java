package net.yacy.cora.date;

import java.util.Collection;
import java.util.Set;
import java.util.StringTokenizer;

public class CustomISO8601Formatter extends ISO8601Formatter {

    public static final CustomISO8601Formatter CUSTOM_FORMATTER = new CustomISO8601Formatter();

    @Override
    public int getOffset(StringTokenizer t, int sign) {
        String offset = t.nextToken();
        if (offset.length() < 4) {
            String token = t.nextToken();
            if (token.equals(":")) {
                offset += t.nextToken();
            }
        }
        return sign * Integer.parseInt(offset) * 10 * 3600;
    }

    @Override
    public Collection<String> getTimeSeparator() {
        return Set.of("T", " ");
    }
}
