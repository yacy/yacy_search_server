/**
 * YearNavigator.java
 * (C) 2016 by reger24; https://github.com/reger24
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.search.navigator;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.schema.CollectionSchema;

/**
 * Navigator for date fields, showing the year of the date, ordered by the key =
 * year string
 *
 * following fields are declared as SorlType.date (and can be used by YearNavigator)
 * last_modified  
 * dates_in_content_dts       
 * startDates_dts     
 * endDates_dts
 * load_date_dt       
 * fresh_date_dt
 *
 * as of now the config (in yacy.init/yacy.conf can be used to define the field
 * to use by adding : (colon) fieldname, optional :Titlestring
 * example: search.navigation=year:last_modified:Year
 *
 */
public class YearNavigator extends StringNavigator implements Navigator {

    public YearNavigator(final String title, final CollectionSchema field, final NavigatorSort sort) {
        super(title, field, sort == null ? NavigatorSort.LABEL_DESC : sort);
        if (field.getType() != SolrType.date) throw new IllegalArgumentException("field is not of type Date");
    }

    /**
     * For dates_in_content_dts a special modifier is returned to limit date for
     * the full year using from:YEAR-01-01  to:YEAR-12-31
     * @param key
     * @return from:key-01-01 to:key-12-31
     */
    @Override
    public String getQueryModifier(final String key) {
        if (this.field == CollectionSchema.dates_in_content_dts)
            return "from:" + key +"-01-01 to:" + key + "-12-31 ";
		return key;
    }

    /**
     * Shortens the facet date field to a year and increases count
     * @param facets 
     */
    @Override
    public void incFacet(Map<String, ReversibleScoreMap<String>> facets) {
        if (field != null && facets != null && !facets.isEmpty()) {
            ReversibleScoreMap<String> fcts = facets.get(field.getSolrFieldName());
            if (fcts != null) {
                Iterator<String> it = fcts.iterator();
                // loop through the facet to extract the year
                while (it.hasNext()) {
                    String datestring = it.next();
                    this.inc(datestring.substring(0, 4), fcts.get(datestring));
                }
            }
        }
    }

    @Override
    public void incDoc(URIMetadataNode doc) {
        if (field != null) {
            if (field.getType() == SolrType.date) {
                Object val = doc.getFieldValue(field.getSolrFieldName());
                if (val != null) {
                    Calendar cal = Calendar.getInstance();
                    if (val instanceof Collection) {
                        Collection<?> ll = (Collection<?>) val;
                        for (Object o : ll) {
                            if (o instanceof String) {
                                this.inc((String) o);
                            } else if (o instanceof Date) {
                                cal.setTime((Date) o);
                                String year = Integer.toString(cal.get(Calendar.YEAR));
                                this.inc(year);
                            }
                        }
                    } else {
                        cal.setTime((Date) val);
                        String year = Integer.toString(cal.get(Calendar.YEAR));
                        this.inc(year);
                    }
                }
            }
        }
    }


    /**
     * For date_in_content_dts it return true if form:YEAR and to:YEAR is part
     * of the modifier, otherwise false.
     * @param modifier the search query modifier
     * @param name 4 digit year string
     * @return true when when the modifier is active
     */
    @Override
    public boolean modifieractive(QueryModifier modifier, String name) {
        if (this.field == CollectionSchema.dates_in_content_dts) {
            if (modifier.toString().contains("from:" + name) && modifier.toString().contains("to:" + name)) {
                return true;
            }
        }
        return false;
    }

}
