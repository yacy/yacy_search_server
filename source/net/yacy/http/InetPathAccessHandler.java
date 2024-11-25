// InetPathAccessHandler.java
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

package net.yacy.http;

import java.io.IOException;
import java.net.InetAddress;

import org.eclipse.jetty.http.pathmap.MappedResource;
import org.eclipse.jetty.http.pathmap.PathMappings;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.util.InetAddressSet;
import org.eclipse.jetty.util.component.DumpableCollection;

/**
 * InetPathAccessHandler Access Handler
 * <p>
 * Extends {@link InetAccessHandler} by adding path patterns capabilities as
 * previously available in the deprecated IPAccessHandler.
 * </p>
 *
 */
public class InetPathAccessHandler extends InetAccessHandler {

	/** List of white listed paths mapped to adresses sets */
	private final PathMappings<InetAddressSet> white = new PathMappings<>();

	/** List of black listed paths mapped to adresses sets */
	private final PathMappings<InetAddressSet> black = new PathMappings<>();

	/**
	 * @throws IllegalArgumentException when the pattern is malformed
	 */
	@Override
	public void include(final String pattern) throws IllegalArgumentException {
		addPattern(pattern, this.white);
	}

	/**
	 * @throws IllegalArgumentException when a pattern is malformed
	 */
	@Override
	public void include(final String... patterns) throws IllegalArgumentException  {
		for (final String pattern : patterns) {
			include(pattern);
		}
	}

	/**
	 * @throws IllegalArgumentException when the pattern is malformed
	 */
	@Override
	public void exclude(final String pattern) throws IllegalArgumentException {
		addPattern(pattern, this.black);
	}

	/**
	 * @throws IllegalArgumentException when a pattern is malformed
	 */
	@Override
	public void exclude(final String... patterns) throws IllegalArgumentException {
		for (final String pattern : patterns) {
			exclude(pattern);
		}
	}

	/**
	 * Helper method to parse the new pattern and add it to the specified mapping.
	 *
	 * @param pattern
	 *            a new pattern to process
	 * @param pathMappings
	 *            target mapping from paths to addresses sets. Must not be null.
	 * @throws IllegalArgumentException
	 *             when the pattern is malformed
	 */
	protected void addPattern(final String pattern, final PathMappings<InetAddressSet> pathMappings)
			throws IllegalArgumentException {
		if (pattern != null && !pattern.isEmpty()) {
			final int idx = pattern.indexOf('|');

			final String addr = idx > 0 ? pattern.substring(0, idx) : pattern;
			final String path = (idx > 0 && (pattern.length() > idx + 1)) ? pattern.substring(idx + 1) : "/*";

			if (!addr.isEmpty()) {
				final PathSpec pathSpec = PathSpec.from(path);
				InetAddressSet addresses = pathMappings.get(pathSpec);
				if (addresses == null) {
					addresses = new InetAddressSet();
					pathMappings.put(pathSpec, addresses);
				}
				addresses.add(addr);

			}
		}
	}
	
	/**
	 * Helper method to check pattern syntax.
	 *
	 * @param pattern pattern to check for syntax errors
	 * @throws IllegalArgumentException
	 *             when the pattern is malformed
	 */
	public static void checkPattern(final String pattern) throws IllegalArgumentException {
		new InetPathAccessHandler().include(pattern);
	}
	/**
	 * Check whether the given address and path are allowed by current rules.
	 * 
	 * @param address
	 *            the address to check
	 * @param path
	 *            an eventual path string starting with "/"
	 * @return true when allowed
	 */
	protected boolean isAllowed(final InetAddress address, final String path) {
		boolean allowed = true;
		final String nonNullPath = path != null ? path : "/";
		if (this.white.size() > 0) {
			/* Non empty white list patterns : MUST match at least one of it */
			allowed = false;
			for (final MappedResource<InetAddressSet> mapping : this.white.getMatches(nonNullPath)) {
				if (mapping.getResource().test(address)) {
					allowed = true;
					break;
				}
			}
		}
		if (allowed) {
			/* Finally check against black list patterns even when the first step passed */
			for (final MappedResource<InetAddressSet> mapping : this.black.getMatches(nonNullPath)) {
				if (mapping.getResource().test(address)) {
					allowed = false;
					break;
				}
			}
		}
		return allowed;
	}

	@Override
	public void dump(final Appendable out, final String indent) throws IOException {
		dumpObjects(out, indent,
	            DumpableCollection.from("white", this.white.getMappings()),
	            DumpableCollection.from("black", this.black.getMappings()));
	}

}
