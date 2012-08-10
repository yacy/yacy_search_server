/**
 *  URIReferenceNode
 *  Copyright 2012 by Michael Peter Christen
 *  First released 5.4.2012 at http://yacy.net
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

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.ASCII;

public class URIReferenceNode extends HashMap<String, byte[]> implements URIReference {

	private static final long serialVersionUID = -1580155759116466570L;

	private final byte[] hash;

	public URIReferenceNode(DigestURI uri, Date date) {
		this.hash = uri.hash();
		this.put(MetadataVocabulary.url.name(), ASCII.getBytes(uri.toNormalform(true, false)));
		this.put(MetadataVocabulary.moddate.name(), ASCII.getBytes(ISO8601Formatter.FORMATTER.format(date)));
	}

	@Override
	public byte[] hash() {
		return this.hash;
	}

	private String hostHash = null;
	@Override
    public String hosthash() {
        if (this.hostHash != null) return this.hostHash;
        this.hostHash = ASCII.String(this.hash, 6, 6);
        return this.hostHash;
    }

	@Override
	public Date moddate() {
		byte[] x = this.get(MetadataVocabulary.moddate.name());
		try {
			return x == null ? null : ISO8601Formatter.FORMATTER.parse(ASCII.String(x));
		} catch (ParseException e) {
			return null;
		}
	}

	@Override
	public DigestURI url() {
		byte[] x = this.get(MetadataVocabulary.moddate.name());
		try {
			return x == null ? null : new DigestURI(ASCII.String(x), this.hash);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	public boolean matches(Pattern matcher) {
		byte[] x = this.get(MetadataVocabulary.moddate.name());
		if (x == null) return false;
		return matcher.matcher(ASCII.String(x)).matches();
	}

}
