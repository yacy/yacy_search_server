/**
 *  URIMetadata
 *  Copyright 2012 by Michael Peter Christen
 *  First released 3.4.2012 at http://yacy.net
 *
 *  This file is part of YaCy Content Integration
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

package net.yacy.kelondro.data.meta;

import java.util.Date;
import java.util.regex.Pattern;

import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.order.Bitfield;
import de.anomic.crawler.retrieval.Request;


public interface URIMetadata {

	/**
	 * The hash of a URIReference is a unique key for the stored URL.
	 * It is in fact equal to url().hash()
	 * @return the hash of the stored url
	 */
    public byte[] hash();

    /**
     * the second half of a uri hash is the host hash
     * @return
     */
    public String hosthash();

    /**
     * The modification date of the URIReference is given if
     * the record was created first and is defined with the
     * creation date. If the record is modified later, the date shall change.
     * @return the modification date of this record
     */
    public Date moddate();

    /**
     * The DigestURI is the payload of the URIReference
     * @return the url as DigestURI with assigned URL hash according to the record hash
     */
    public DigestURI url();

    /**
     * check if the url matches agains a given matcher
     * @param matcher
     * @return true if the url() matches
     */
    public boolean matches(final Pattern matcher);

    /**
     * produce a visible representation of the record
     * @return a string for the url()
     */
    @Override
    public String toString();

    public String dc_title();

    public String dc_creator();

    public String dc_publisher();

    public String dc_subject();

    public double lat();

    public double lon();

    public long ranking();

    public Date loaddate();

    public Date freshdate();

    public String md5();

    public char doctype();

    public byte[] language();

    public int size();

    public Bitfield flags();

    public int wordCount();

    public int llocal();

    public int lother();

    public int limage();

    public int laudio();

    public int lvideo();

    public int lapp();

    public String snippet();

    public String[] collections();

    public WordReference word();

    public boolean isOlder(final URIMetadata other);

    public String toString(final String snippet);

    public byte[] referrerHash();

    public Request toBalancerEntry(final String initiatorHash);

}
