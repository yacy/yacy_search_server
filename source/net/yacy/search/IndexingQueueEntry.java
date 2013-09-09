/**
 *  IndexingQueueEntry
 *  Copyright 2012 by Michael Peter Christen
 *  First released 24.07.2012 at http://yacy.net
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


package net.yacy.search;

import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.kelondro.workflow.WorkflowJob;

public class IndexingQueueEntry extends WorkflowJob {

    public Response queueEntry;
    public Document[] documents;
    public Condenser[] condenser;

    public IndexingQueueEntry(final Response queueEntry, final Document[] documents, final Condenser[] condenser) {
        super();
        this.queueEntry = queueEntry;
        this.documents = documents;
        this.condenser = condenser;
    }
}
