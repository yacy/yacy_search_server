// NavigatorSort.java
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

package net.yacy.search.navigator;

/**
 * Enumeration of navigator sort properties.
 */
public enum NavigatorSort {

	/**
	 * Descending sort on count values.
	 */
	COUNT_DESC(NavigatorSortType.COUNT, NavigatorSortDirection.DESC),
	
	/**
	 * Ascending sort on count values.
	 */
	COUNT_ASC(NavigatorSortType.COUNT, NavigatorSortDirection.ASC),
	
	/**
	 * Descending sort on displayed labels.
	 */
	LABEL_DESC(NavigatorSortType.LABEL, NavigatorSortDirection.DESC),

	/**
	 * Ascending sort on displayed labels.
	 */
	LABEL_ASC(NavigatorSortType.LABEL, NavigatorSortDirection.ASC);

	/**
	 * The type of sort to apply when iterating over a navigator keys with the
	 * {@link Navigator#navigatorKeys()} function
	 */
	private final NavigatorSortType sortType;

	/**
	 * The direction of the sort to apply when iterating over a navigator keys with
	 * the {@link Navigator#navigatorKeys()} function
	 */
	private final NavigatorSortDirection sortDir;

	/**
	 * @param sortType The type of sort to apply when iterating over a navigator
	 *                 keys with the {@link Navigator#navigatorKeys(boolean)}
	 *                 function
	 * @param sortDir  The direction of the sort to apply when iterating over a
	 *                 navigator keys with the
	 *                 {@link Navigator#navigatorKeys(boolean)} function
	 */
	private NavigatorSort(final NavigatorSortType sortType, final NavigatorSortDirection sortDir) {
		if (sortType == null) {
			this.sortType = NavigatorSortType.COUNT;
		} else {
			this.sortType = sortType;
		}
		if (sortDir == null) {
			this.sortDir = NavigatorSortDirection.DESC;
		} else {
			this.sortDir = sortDir;
		}
	}

	/**
	 * @return The type of sort to apply when iterating over a navigator keys with
	 *         the {@link Navigator#navigatorKeys(boolean)} function
	 */
	public NavigatorSortType getSortType() {
		return this.sortType;
	}

	/**
	 * @return The direction of the sort to apply when iterating over a navigator
	 *         keys with the {@link Navigator#navigatorKeys(boolean)} function
	 */
	public NavigatorSortDirection getSortDir() {
		return this.sortDir;
	}
}
