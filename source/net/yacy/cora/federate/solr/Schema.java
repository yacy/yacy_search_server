/**
 *  SolrField
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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


public interface Schema {

    /**
     * this shall be implemented as enum, thus shall have the name() method
     * @return the name of the enum constant
     */
    public String name(); // default field name (according to SolCell default schema) <= enum.name()
    
    public String getSolrFieldName(); // return the default or custom solr field name to use for solr requests

    public SolrType getType();

    public boolean isIndexed();

    public boolean isStored();

    public boolean isMultiValued();

    public boolean isOmitNorms();

    public String getComment();

}