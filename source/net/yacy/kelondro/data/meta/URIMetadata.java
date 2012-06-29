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

import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.Reference;


public interface URIMetadata extends URIReference {

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

    public Reference word();

    public boolean isOlder(final URIMetadata other);

    public String toString(final String snippet);

}
