// ScoreMapUpdatesListener.java
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

package net.yacy.cora.sorting;

import java.util.EventListener;

/**
 * A listener interface to monitor successful write operation on a score map (inc(), dec(), delete(), shrinkToMaxSize(),
 * shrinkToMinScore() and clear()).
 */
public interface ScoreMapUpdatesListener extends EventListener {

	/**
	 * Called when a successful update (inc(), dec(), delete(), shrinkToMaxSize(),
 * shrinkToMinScore() and clear()) has been performed on the watched score map.
	 */
	public void updatedScoreMap();

}
