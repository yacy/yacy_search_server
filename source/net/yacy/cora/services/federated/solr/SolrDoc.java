/**
 *  SolrDoc
 *  Copyright 2011 by Michael Peter Christen
 *  First released 09.05.2012 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.services.federated.solr;

import java.util.Date;
import java.util.List;

import org.apache.solr.common.SolrInputDocument;

/**
 * helper class to produce SolrInputDocuments
 */
public class SolrDoc extends SolrInputDocument {

    private static final long serialVersionUID=1L;

    public SolrDoc() {
        super();
    }

    public final void addSolr(final SolrField key, final String value) {
       this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final Date value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final int value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final long value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final String[] value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final List<String> value) {
        this.setField(key.getSolrFieldName(), value.toArray(new String[value.size()]));
    }

    public final void addSolr(final SolrField key, final float value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final double value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final boolean value) {
        this.setField(key.getSolrFieldName(), value);
    }

    public final void addSolr(final SolrField key, final String value, final float boost) {
        this.setField(key.getSolrFieldName(), value, boost);
    }

}
