
// yacysearchpagination.java
// ---------------------------
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Render yacysearch results page fragment containing pagination links.
 */
public class yacysearchpagination {

	/** The maximum number of pagination links to render */
	private static final int MAX_PAGINATION_LINKS = 10;

	/**
	 * @param header servlet request headers
	 * @param post   request parameters
	 * @param env    server environment
	 * @return the servlet answer object
	 */
	public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
		if (post == null) {
			throw new TemplateMissingParameterException("The eventID parameter is required");
		}

		final serverObjects prop = new serverObjects();
		final Switchboard sb = (Switchboard) env;
		final String eventID = post.get("eventID");
		if (eventID == null) {
			throw new TemplateMissingParameterException("The eventID parameter is required");
		}
		final boolean jsResort = post.getBoolean("jsResort");
		final boolean authFeatures = post.containsKey("auth");
		final int defaultItemsPerPage = sb.getConfigInt(SwitchboardConstants.SEARCH_ITEMS, 10);

		/* Detailed rules on items per page limits are handle in yacysearch.html */
		final int itemsPerPage = Math.max(1, post.getInt("maximumRecords", defaultItemsPerPage));

		final SearchEvent theSearch = SearchEventCache.getEvent(eventID);
		if (theSearch == null) {
			/*
			 * the event does not exist in cache
			 */
			prop.put("pagination", false);
		} else {
			prop.put("pagination", true);

			final RequestHeader.FileType fileType = header.fileType();

			if(jsResort) {
				/* Pagination links are processed on browser side : just prepare prev and next buttons */
				prop.put("pagination_hidePagination", true);
				prop.put("pagination_prevDisabled", true);
				prop.put("pagination_pages", 0);
				prop.put("pagination_nextDisabled", true);
			} else {
				final int startRecord = post.getInt("offset", 0);
				final int totalCount = theSearch.getResultCount();

				final int activePage = (int) Math.floor(startRecord / (double) itemsPerPage);
				final int firstLinkedPage = activePage - (activePage % MAX_PAGINATION_LINKS);
				final int totalPagesNb = (int) Math.floor(1 + ((totalCount - 1) / (double) itemsPerPage));
				final int displayedPagesNb = Math.min(MAX_PAGINATION_LINKS, totalPagesNb - firstLinkedPage);


				prop.put("pagination_prevDisabled", activePage == 0);
				prop.putUrlEncoded(fileType, "pagination_prevDisabled_prevHref", QueryParams
						.navurl(fileType, Math.max(activePage - 1, 0), theSearch.query, null, false, authFeatures).toString());

				prop.put("pagination_hidePagination", totalPagesNb <= 1 || displayedPagesNb < 1);

				for (int i = 0; i < displayedPagesNb; i++) {
					if (activePage == (firstLinkedPage + i)) {
						prop.put("pagination_pages_" + i + "_active", true);
					} else {
						prop.put("pagination_pages_" + i + "_active", false);
						prop.put("pagination_pages_" + i + "_active_pageIndex", (firstLinkedPage + i));
						prop.putUrlEncoded(fileType, "pagination_pages_" + i + "_active_href",
								QueryParams
										.navurl(fileType, firstLinkedPage + i, theSearch.query, null, false, authFeatures)
										.toString());
					}
					prop.put("pagination_pages_" + i + "_pageNum", firstLinkedPage + i + 1L);
				}
				prop.put("pagination_pages", displayedPagesNb);

				final boolean localQuery = theSearch.query.isLocal();
				if ((localQuery && activePage >= (totalPagesNb - 1))
						|| (!localQuery && activePage >= (displayedPagesNb - 1))) {
					/*
					 * Last page on a local query, or last fetchable page in p2p mode : the next
					 * page button is disabled
					 */
					prop.put("pagination_nextDisabled", true);
				} else {
					prop.put("pagination_nextDisabled", false);
					prop.putUrlEncoded(fileType, "pagination_nextDisabled_nextHref", QueryParams
							.navurl(fileType, activePage + 1, theSearch.query, null, false, authFeatures).toString());
				}
			}


		}

		return prop;
	}

}