/**
 * NameSpaceNavigator.java
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

import java.util.List;
import net.yacy.kelondro.data.meta.URIMetadataNode;

/**
 * Navigator for name space used in some wikies by using a : (colon) in the path
 * e.g. http://wikiurl/Help:About  counted as name space = Help
 * (remark: the query uses the inurl: modifier (without the trailing : ) to filter results which matches http://host/Help.html too)
 */
public class NameSpaceNavigator extends StringNavigator implements Navigator {

    public NameSpaceNavigator(final String title, final NavigatorSort sort) {
        super(title, null, sort);
    }

    @Override
    public String getQueryModifier(final String key) {
        return "inurl:" + key;
    }

    @Override
    public void incDocList(List<URIMetadataNode> docs) {
    // we need to override, as StringNavigator expects a field definition
        for (URIMetadataNode doc : docs) {
            incDoc(doc);
        }
    }

    @Override
    public void incDoc(URIMetadataNode doc) {
        int p;
        String pagepath = doc.url().getPath();
        if ((p = pagepath.indexOf(':')) >= 0) {
            pagepath = pagepath.substring(0, p);
            p = pagepath.lastIndexOf('/');
            if (p >= 0) {
                pagepath = pagepath.substring(p + 1);
                this.inc(pagepath);
            }
        }
    }
}
