/**
 *  share
 *  Copyright 2016 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 24.02.2016 at https://yacy.net
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

package net.yacy.htroot.api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.yacy.yacy;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.index.Fulltext;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class share {

    /**
     * Servlet to share any kind of binary to this peer.
     * That mean you can upload 'things'. While this is the generic view,
     * it will operate in the beginning only for full solr export files.
     * The servlet will decide if it wants that kind of data and if the sender is valid,
     * i.e. if the sender is within the own network and known.
     * Index dumps which are uploaded are placed to a specific folder
     * where they can be downloaded again by peers.
     * An optional operation is the immediate indexing of the shared index.
     * @param header
     * @param post
     * @param env
     * @return
     */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        // display mode: this only helps to display a nice input form for test cases
        final int c = post == null ? 1 : post.getInt("c", 0);
        if (c > 0) {
            prop.put("mode", 0);
            return prop;
        }

        // push mode: this does a document upload
        prop.put("mode", 1);
        prop.put("mode_success", 0);
        // init display variable for mode=1
        prop.put("mode_countsuccess", 0);
        prop.put("mode_countfail", 0);
        prop.put("mode_item", "");

        if (post == null) return prop;

        // check file name
        String filename = post.get("data", "");
        if (filename.isEmpty()) {
            prop.put("mode_success_message", "file name is empty");
            return prop;
        }
        if (!filename.startsWith(Fulltext.yacy_dump_prefix) || !filename.endsWith(".xml.gz")) {
            prop.put("mode_success_message", "no index dump file (" + Fulltext.yacy_dump_prefix + "*.xml.gz)");
            return prop;
        }

        // check data
        final String dataString = post.get("data$file", "");
        if (dataString.length() == 0) return prop;
        byte[] data;
        if (filename.endsWith(".base64")) {
            data = Base64Order.standardCoder.decode(dataString);
            filename = filename.substring(0, filename.length() - 7);
        } else {
            data = UTF8.getBytes(dataString);
        }
        if (data == null || data.length == 0) return prop;

        // modify the file name; ignore and replace the used transaction token
        final int ttp = filename.indexOf("_t");
        if (ttp < 0) return prop;
        if (filename.charAt(ttp + 3) != '.') return prop;
        filename = filename.substring(0, ttp) + "_ts" + filename.substring(ttp + 3); // transaction token: 's' as 'shared'.

        // process the data
        final File tmpFile = new File(yacy.shareDumpDefaultPath, filename + ".tmp");
        final File finalFile = new File(yacy.shareDumpDefaultPath, filename);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tmpFile.renameTo(finalFile);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return prop;
        }

        prop.put("mode_success", 1);
        return prop;
    }

}
