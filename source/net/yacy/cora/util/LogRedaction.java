/**
 *  LogRedaction
 *  Copyright 2026 by contributors to the YaCy project
 *  First released 27.06.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.util;

import java.util.regex.Pattern;

/**
 * Redacts common credential shapes before values are written to operator logs.
 */
public final class LogRedaction {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(bearer\\s+|basic\\s+)?([^\\s,;]+)");
    private static final Pattern COOKIE_HEADER = Pattern.compile("(?i)(cookie\\s*[:=]\\s*)([^\\r\\n]+)");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile("(?i)\\b(api[_-]?key|access[_-]?token|auth[_-]?token|token|secret|password|passwd|pwd|login)\\b\\s*[:=]\\s*([^\\s,;&]+)");
    private static final Pattern SENSITIVE_QUERY = Pattern.compile("(?i)([?&](?:api[_-]?key|access[_-]?token|auth[_-]?token|token|secret|password|passwd|pwd|login)=)([^&#\\s]+)");
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)(https?://)([^/@\\s:]+):([^/@\\s]+)@");

    private LogRedaction() {
    }

    public static String redact(final String value) {
        if (value == null || value.isEmpty()) return value;
        String redacted = URL_USERINFO.matcher(value).replaceAll("$1" + REDACTED + ":" + REDACTED + "@");
        redacted = AUTHORIZATION_HEADER.matcher(redacted).replaceAll("$1$2" + REDACTED);
        redacted = COOKIE_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = SENSITIVE_QUERY.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = SENSITIVE_ASSIGNMENT.matcher(redacted).replaceAll("$1=" + REDACTED);
        return redacted;
    }

    public static String redactMessage(final Throwable error) {
        if (error == null) return "";
        return redact(error.getMessage());
    }
}
