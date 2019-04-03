// SearchAccessRateConstants.java
// Copyright 2019 by luccioman; https://github.com/luccioman
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

package net.yacy.search;

/**
 * Configuration keys and default values related to the search interface access
 * rate limitation settings.
 * 
 * @see SearchAccessRate_p.html
 */
public enum SearchAccessRateConstants {

	/**
	 * Configuration for the maximum number of accesses within three seconds to the
	 * search interface for unauthenticated users and authenticated users with no
	 * extended search right
	 */
	PUBLIC_MAX_ACCESS_3S("search.public.max.access.3s", 60),

	/**
	 * Configuration for the maximum number of accesses within one minute to the
	 * search interface for unauthenticated users and authenticated users with no
	 * extended search right
	 */
	PUBLIC_MAX_ACCESS_1MN("search.public.max.access.1mn", 600),

	/**
	 * Configuration for the maximum number of accesses within ten minutes to the
	 * search interface for unauthenticated users and authenticated users with no
	 * extended search right
	 */
	PUBLIC_MAX_ACCESS_10MN("search.public.max.access.10mn", 3000),

	/**
	 * Configuration for the maximum number of accesses within three seconds to the
	 * search interface in P2P mode for unauthenticated users and authenticated
	 * users with no extended search right
	 */
	PUBLIC_MAX_P2P_ACCESS_3S("search.public.max.p2p.access.3s", 1),

	/**
	 * Configuration for the maximum number of accesses within one minute to the
	 * search interface in P2P mode for unauthenticated users and authenticated
	 * users with no extended search right
	 */
	PUBLIC_MAX_P2P_ACCESS_1MN("search.public.max.p2p.access.1mn", 6),

	/**
	 * Configuration for the maximum number of accesses within ten minutes to the
	 * search interface in P2P mode for unauthenticated users and authenticated
	 * users with no extended search right
	 */
	PUBLIC_MAX_P2P_ACCESS_10MN("search.public.max.p2p.access.10mn", 60),

	/**
	 * Configuration for the maximum number of accesses within three seconds to the
	 * search interface in P2P mode with browser-side JavaScript results resorting
	 * enabled for unauthenticated users and authenticated users with no extended
	 * search right
	 */
	PUBLIC_MAX_P2P_JSRESORT_ACCESS_3S("search.public.max.p2p.jsresort.access.3s", 1),

	/**
	 * Configuration for the maximum number of accesses within one minute to the
	 * search interface in P2P mode with browser-side JavaScript results resorting
	 * enabled for unauthenticated users and authenticated users with no extended
	 * search right
	 */
	PUBLIC_MAX_P2P_JSRESORT_ACCESS_1MN("search.public.max.p2p.jsresort.access.1mn", 1),

	/**
	 * Configuration for the maximum number of accesses within ten minutes to the
	 * search interface in P2P mode with browser-side JavaScript results resorting
	 * enabled for unauthenticated users and authenticated users with no extended
	 * search right
	 */
	PUBLIC_MAX_P2P_JSRESORT_ACCESS_10MN("search.public.max.p2p.jsresort.access.10mn", 10),

	/**
	 * Configuration for the maximum number of accesses within three seconds to the
	 * search interface to support fetching remote results snippets for
	 * unauthenticated users and authenticated users with no extended search right
	 */
	PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_3S("search.public.max.remoteSnippet.access.3s", 1),

	/**
	 * Configuration for the maximum number of accesses within one minute to the
	 * search interface to support fetching remote results snippets for
	 * unauthenticated users and authenticated users with no extended search right
	 */
	PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_1MN("search.public.max.remoteSnippet.access.1mn", 4),

	/**
	 * Configuration for the maximum number of accesses within ten minutes to the
	 * search interface to support fetching remote results snippets mode for
	 * unauthenticated users and authenticated users with no extended search right
	 */
	PUBLIC_MAX_REMOTE_SNIPPET_ACCESS_10MN("search.public.max.remoteSnippet.access.10mn", 20);

	/** The configuration setting key */
	private final String key;

	/** The default configuration value */
	private final int defaultValue;

	private SearchAccessRateConstants(final String key, final int defaultValue) {
		this.key = key;
		this.defaultValue = defaultValue;
	}

	/**
	 * @return the configuration setting key
	 */
	public String getKey() {
		return this.key;
	}

	/**
	 * @return the default configuration value
	 */
	public int getDefaultValue() {
		return this.defaultValue;
	}

}
