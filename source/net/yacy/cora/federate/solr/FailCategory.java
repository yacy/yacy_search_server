/**
 *  FailCategory
 *  Copyright 2013 by Michael Peter Christen
 *  First released 17.10.2013 at http://yacy.net
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

package net.yacy.cora.federate.solr;

public enum FailCategory {
    // TEMPORARY categories are such failure cases that should be tried again
    // FINAL categories are such failure cases that are final and should not be tried again
    TEMPORARY_NETWORK_FAILURE(true, FailType.fail), // an entity could not been loaded
    FINAL_PROCESS_CONTEXT(false, FailType.excl),    // because of a processing context we do not want that url again (i.e. remote crawling)
    FINAL_LOAD_CONTEXT(false, FailType.excl),       // the crawler configuration does not want to load the entity
    FINAL_ROBOTS_RULE(true, FailType.excl),         // a remote server denies indexing or loading
    FINAL_REDIRECT_RULE(true, FailType.excl);       // the remote server redirects this page, thus disallowing reading of content

    public final boolean store;
    public final FailType failType;

    private FailCategory(boolean store, FailType failType) {
        this.store = store;
        this.failType = failType;
    }
}
