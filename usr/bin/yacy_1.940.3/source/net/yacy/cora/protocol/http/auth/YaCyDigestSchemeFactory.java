// YaCyDigestSchemeFactory.java
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.cora.protocol.http.auth;

import java.nio.charset.Charset;

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.protocol.HttpContext;

/**
 * {@link AuthSchemeProvider} implementation that creates and initializes
 * {@link YaCyDigestScheme} instances that support use of YaCy encoded password
 * instead of clear-text password.
 */
public class YaCyDigestSchemeFactory implements AuthSchemeProvider {

    private final Charset charset;

    /**
     * @param charset characters set
     */
    public YaCyDigestSchemeFactory(final Charset charset) {
        super();
        this.charset = charset;
    }

    public YaCyDigestSchemeFactory() {
        this(null);
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new YaCyDigestScheme(this.charset);
    }

}
