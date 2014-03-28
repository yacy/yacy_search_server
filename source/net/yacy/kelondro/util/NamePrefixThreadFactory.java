// NamePrefixThreadFactory.java
// (C) 2008 by Daniel Raap; danielr@users.berlios.de
// first published 13.06.2008 on http://yacy.net
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package net.yacy.kelondro.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public /**
 * creates threads whose names begin with a specified prefix for identifing purpose
 *
 * @author daniel
 */
class NamePrefixThreadFactory implements ThreadFactory {

    /**
     * default as backend
     */
    private final static ThreadFactory defaultFactory = Executors.defaultThreadFactory();

    /**
     * pefix of each threadname
     */
    private final String prefix;

    /**
     * constructor
     *
     * @param prefix each thread is named 'prefix' + defaultName
     */
    public NamePrefixThreadFactory(final String prefix) {
        this.prefix = prefix;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
     */
    @Override
    public Thread newThread(final Runnable r) {
        final Thread t = defaultFactory.newThread(r);
        t.setName(this.prefix + "_" + t.getName());
        return t;
    }

}
