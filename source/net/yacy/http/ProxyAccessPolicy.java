/**
 *  ProxyAccessPolicy
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
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

package net.yacy.http;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Container-neutral checks for the configured transparent-proxy client list. */
public final class ProxyAccessPolicy {

    private static volatile ConfiguredPatterns lastConfiguredPatterns;
    private static final ThreadLocal<MatcherCache> MATCHERS = ThreadLocal.withInitial(MatcherCache::new);

    private ProxyAccessPolicy() {
    }

    public static boolean isClientAllowed(final String configuredPatterns, final String clientHost) {
        if ("*".equals(configuredPatterns)) {
            return true;
        }
        if (configuredPatterns == null || configuredPatterns.isEmpty() || clientHost == null) {
            return false;
        }
        final ConfiguredPatterns patterns = configuredPatterns(configuredPatterns);
        final MatcherCache matchers = MATCHERS.get();
        for (int i = 0; i < patterns.values.length; i++) {
            if (!patterns.values[i].isEmpty()
                    && matchers.matches(patterns, i, patterns.pattern(i), clientHost)) {
                return true;
            }
        }
        return false;
    }

    private static ConfiguredPatterns configuredPatterns(final String value) {
        ConfiguredPatterns patterns = lastConfiguredPatterns;
        if (patterns != null && patterns.source.equals(value)) return patterns;
        synchronized (ProxyAccessPolicy.class) {
            patterns = lastConfiguredPatterns;
            if (patterns == null || !patterns.source.equals(value)) {
                patterns = new ConfiguredPatterns(value);
                lastConfiguredPatterns = patterns;
            }
            return patterns;
        }
    }

    /** Cache only the current configuration: settings may be changed at runtime. */
    private static final class ConfiguredPatterns {
        private final String source;
        private final String[] values;
        private final AtomicReferenceArray<Pattern> compiled;

        private ConfiguredPatterns(final String source) {
            this.source = source;
            this.values = source.split(",");
            this.compiled = new AtomicReferenceArray<>(this.values.length);
        }

        private Pattern pattern(final int index) {
            Pattern pattern = this.compiled.get(index);
            if (pattern != null) return pattern;
            synchronized (this) {
                pattern = this.compiled.get(index);
                if (pattern == null) {
                    pattern = Pattern.compile(this.values[index]);
                    this.compiled.set(index, pattern);
                }
                return pattern;
            }
        }
    }

    /** Matchers are mutable, so reuse one per request thread and configured entry. */
    private static final class MatcherCache {
        private ConfiguredPatterns configuration;
        private Matcher[] matchers = new Matcher[0];

        private boolean matches(final ConfiguredPatterns patterns, final int index,
                final Pattern pattern, final String clientHost) {
            if (this.configuration != patterns) {
                this.configuration = patterns;
                this.matchers = new Matcher[patterns.values.length];
            }
            Matcher matcher = this.matchers[index];
            if (matcher == null) {
                matcher = pattern.matcher(clientHost);
                this.matchers[index] = matcher;
            } else {
                matcher.reset(clientHost);
            }
            return matcher.matches();
        }
    }
}
