// URLMetadata.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.03.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.kelondro.text;

import java.net.MalformedURLException;

import de.anomic.yacy.yacyURL;

public class URLMetadata {
    private yacyURL url;
    private final String dc_title, dc_creator, dc_subject, ETag;
    
    public URLMetadata(final String url, final String urlhash, final String title, final String author, final String tags, final String ETag) {
        try {
            this.url = new yacyURL(url, urlhash);
        } catch (final MalformedURLException e) {
            this.url = null;
        }
        this.dc_title = title;
        this.dc_creator = author;
        this.dc_subject = tags;
        this.ETag = ETag;
    }
    public URLMetadata(final yacyURL url, final String descr, final String author, final String tags, final String ETag) {
        this.url = url;
        this.dc_title = descr;
        this.dc_creator = author;
        this.dc_subject = tags;
        this.ETag = ETag;
    }
    public yacyURL url()    { return this.url; }
    public String  dc_title()  { return this.dc_title; }
    public String  dc_creator() { return this.dc_creator; }
    public String  dc_subject()   { return this.dc_subject; }
    public String  ETag()   { return this.ETag; }
    
}
