// TorrentParser
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.01.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
// $LastChangedBy: low012 $
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


package net.yacy.document.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.BDecoder;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.BDecoder.BObject;
import net.yacy.kelondro.util.BDecoder.BType;

// a BT parser according to http://wiki.theory.org/BitTorrentSpecification
public class torrentParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("torrent");
        SUPPORTED_MIME_TYPES.add("application/x-bittorrent");
    }

    public torrentParser() {
        super("Torrent Metadata Parser");
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    @Override
    public Document parse(DigestURI location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        byte[] b = null;
        try {
            b = FileUtils.read(source);
        } catch (IOException e1) {
            throw new ParserException(e1.toString(), location);
        }
        BDecoder bd = new BDecoder(b);
        BObject bo = bd.parse();
        if (bo == null) throw new ParserException("BDecoder.parse returned null", location);
        if (bo.getType() != BType.dictionary) throw new ParserException("BDecoder object is not a dictionary", location);
        Map<String, BObject> map = bo.getMap();
        String comment = map.get("comment").getString();
        //Date creation = new Date(map.get("creation date").getInteger());
        Map<String, BObject> info = map.get("info").getMap();
        List<BObject> filelist = info.get("files").getList();
        StringBuilder filenames = new StringBuilder(40 * filelist.size());
        for (BObject fo: filelist) {
            List<BObject> l = fo.getMap().get("path").getList(); // one file may have several names
            for (BObject fl: l) filenames.append(fl.toString()).append(" ");
        }
        String name = info.get("name").getString();
        try {
            return new Document(
                    location,
                    mimeType,
                    charset,
                    null,
                    null,
                    name, // title
                    comment, // author 
                    null,
                    null,
                    filenames.toString().getBytes(charset),
                    null,
                    null);
        } catch (UnsupportedEncodingException e) {
            throw new ParserException("error in torrentParser, getBytes: " + e.getMessage(), location);
        }
    }
/* public Document(final DigestURI location, final String mimeType, final String charset, final Set<String> languages,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final byte[] text, final Map<DigestURI, String> anchors, final HashMap<String, ImageEntry> images) {
        this(location, mimeType, charset, languages, keywords, title, author, sections, abstrct, (Object)text, anchors, images);
    }
 */
    
    public static void main(String[] args) {
        try {
            byte[] b = FileUtils.read(new File(args[0]));
            torrentParser parser = new torrentParser();
            Document d = parser.parse(new DigestURI("http://localhost/test.torrent", null), null, "utf-8", b);
            Condenser c = new Condenser(d, true, true);
            Map<String, Word> w = c.words();
            for (Map.Entry<String, Word> e: w.entrySet()) System.out.println("Word: " + e.getKey() + " - " + e.getValue().posInText);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
