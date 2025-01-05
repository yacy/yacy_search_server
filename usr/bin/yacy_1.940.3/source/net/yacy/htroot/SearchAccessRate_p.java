
// SearchAccessRate_p.java
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

package net.yacy.htroot;

import java.util.Properties;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.search.SearchAccessRateConstants;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Handle configuration of access rate limitations to the peer search interface
 * through the SearchAccessRate_p.html page.
 */
public class SearchAccessRate_p {

	/**
	 * @param header the current request headers
	 * @param post   the request parameters
	 * @param env    holds the server environment
	 * @return a serverObjects instance filled with the properties required by the
	 *         related template
	 */
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		final serverObjects prop = new serverObjects();

		/* Acquire a transaction token for the next POST form submission */
        try {
            prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, TransactionManager.getTransactionToken(header));
        } catch (IllegalArgumentException e) {
            sb.log.fine("access by unauthorized or unknown user: no transaction token delivered");
        }

		if (post != null) {
			/*
			 * Check the transaction is valid : validation apply then for every uses of the
			 * post parameters
			 */
			TransactionManager.checkPostTransaction(header, post);

			if (post.containsKey("set")) {
				/* Setting configuration values */
				for (final SearchAccessRateConstants config : SearchAccessRateConstants.values()) {
					sb.setConfig(config.getKey(), Math.max(0, post.getInt(config.getKey(), config.getDefaultValue())));
				}
			} else if (post.containsKey("setDefaults")) {
				/* Resetting to defaults */
				final Properties defaultConfig = sb.loadDefaultConfig();

				for (final SearchAccessRateConstants config : SearchAccessRateConstants.values()) {
					sb.setConfig(config.getKey(),
							defaultConfig.getProperty(config.getKey(), String.valueOf(config.getDefaultValue())));
				}

			}

		}

		for (final SearchAccessRateConstants config : SearchAccessRateConstants.values()) {
			/* Fill prop for template rendering */
			prop.put(config.getKey(), sb.getConfigInt(config.getKey(), config.getDefaultValue()));
		}

		return prop;
	}

}
