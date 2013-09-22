/**
 *  torrentParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 03.01.2010 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.BDecoder;
import net.yacy.kelondro.util.BDecoder.BObject;
import net.yacy.kelondro.util.BDecoder.BType;
import net.yacy.kelondro.util.FileUtils;

// a BT parser according to http://wiki.theory.org/BitTorrentSpecification
public class torrentParser extends AbstractParser implements Parser {

    public torrentParser() {
        super("Torrent Metadata Parser");
        this.SUPPORTED_EXTENSIONS.add("torrent");
        this.SUPPORTED_MIME_TYPES.add("application/x-bittorrent");
    }

    @Override
    public Document[] parse(AnchorURL location, String mimeType, String charset, InputStream source)
            throws Parser.Failure, InterruptedException {
        byte[] b = null;
        try {
            b = FileUtils.read(source);
        } catch (final IOException e1) {
            throw new Parser.Failure(e1.toString(), location);
        }
        final BDecoder bd = new BDecoder(b);
        final BObject bo = bd.parse();
        if (bo == null) throw new Parser.Failure("BDecoder.parse returned null", location);
        if (bo.getType() != BType.dictionary) throw new Parser.Failure("BDecoder object is not a dictionary", location);
        final Map<String, BObject> map = bo.getMap();
        final BObject commento = map.get("comment");
        final String comment = (commento == null) ? "" : UTF8.String(commento.getString());
        //Date creation = new Date(map.get("creation date").getInteger());
        final BObject infoo = map.get("info");
        final StringBuilder filenames = new StringBuilder(80);
        String title = "";
        if (infoo != null) {
            final Map<String, BObject> info = infoo.getMap();
            final BObject fileso = info.get("files");
            if (fileso != null) {
                final List<BObject> filelist = fileso.getList();
                for (final BObject fo: filelist) {
                    final BObject patho = fo.getMap().get("path");
                    if (patho != null) {
                        final List<BObject> l = patho.getList(); // one file may have several names
                        for (final BObject fl: l) {
                            filenames.append(fl.toString()).append(" ");
                        }
                    }
                }
            }
            final BObject nameo = info.get("name");
            if (nameo != null) title = UTF8.String(nameo.getString());
        }
        if (title == null || title.isEmpty()) title = MultiProtocolURL.unescape(location.getFileName());
        return new Document[]{new Document(
		        location,
		        mimeType,
		        charset,
		        this,
		        null,
		        null,
		        singleList(title), // title
		        comment, // author
		        location.getHost(),
		        null,
		        null,
		        0.0f, 0.0f,
		        filenames.toString(),
		        null,
		        null,
		        null,
		        false,
                new Date())};
    }

    public static void main(String[] args) {
        try {
            byte[] b = FileUtils.read(new File(args[0]));
            torrentParser parser = new torrentParser();
            Document[] d = parser.parse(new AnchorURL("http://localhost/test.torrent"), null, "UTF-8", new ByteArrayInputStream(b));
            Condenser c = new Condenser(d[0], true, true, LibraryProvider.dymLib, LibraryProvider.synonyms, false);
            Map<String, Word> w = c.words();
            for (Map.Entry<String, Word> e: w.entrySet()) System.out.println("Word: " + e.getKey() + " - " + e.getValue().posInText);
        } catch (final IOException e) {
            e.printStackTrace();
        } catch (final Parser.Failure e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

}
