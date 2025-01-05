// WorkflowTask.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.kelondro.workflow;

/**
 * A workflow task to be processed by a {@link WorkflowProcessor}.
 * 
 * @author luccioman
 * @param <ENTRY>
 *            the workflow entry type to be processed
 *
 */
public interface WorkflowTask<ENTRY> {

	/**
	 * Process a single workflow entry and eventually return the entry to be
	 * processed by the next processor in the workflow
	 * 
	 * @param in
	 *            the workflow entry
	 * @return an entry for the next processor or null
	 * @throws Exception
	 *             when an error occurred
	 */
	ENTRY process(final ENTRY in) throws Exception;

}
