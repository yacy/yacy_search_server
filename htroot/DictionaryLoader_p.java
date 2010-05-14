// DictionaryLoader_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published 12.05.2010 on http://yacy.net
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

import java.io.IOException;
import java.net.MalformedURLException;

import net.yacy.document.geolocalization.OpenGeoDB;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.data.LibraryProvider;
import de.anomic.http.server.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class DictionaryLoader_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects(); // return variable that accumulates replacements
       
        /*
         * distinguish the following cases:
         * - dictionary file was not loaded -> actions: load the file
         * - dictionary file is loaded and enabled -> actions: disable or remove the file
         * - dictionary file is loaded but disabled -> actions: enable or remove the file
         */
        
        for (LibraryProvider.Dictionary dictionary: LibraryProvider.Dictionary.values()) {
            prop.put(dictionary.nickname + "URL", dictionary.url);
            prop.put(dictionary.nickname + "Storage", dictionary.file().toString());
            prop.put(dictionary.nickname + "Status", dictionary.file().exists() ? 1 : dictionary.fileDisabled().exists() ? 2 : 0);
            prop.put(dictionary.nickname + "ActionLoaded", 0);
            prop.put(dictionary.nickname + "ActionRemoved", 0);
            prop.put(dictionary.nickname + "ActionActivated", 0);
            prop.put(dictionary.nickname + "ActionDeactivated", 0);
        }
        
        if (post == null) return prop;
        
        // GEO1
        if (post.containsKey("geo1Load")) {
            // load from the net
            try {
                Response response = sb.loader.load(new DigestURI(LibraryProvider.Dictionary.GEO1.url), false, true, CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE);
                byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEO1.file());
                LibraryProvider.geoDB = new OpenGeoDB(LibraryProvider.Dictionary.GEO1.file(), false);
                prop.put("geo1Status", LibraryProvider.Dictionary.GEO1.file().exists() ? 1 : 0);
                prop.put("geo1ActionLoaded", 1);
            } catch (MalformedURLException e) {
                Log.logException(e);
                prop.put("geo1ActionLoaded", 2);
                prop.put("geo1ActionLoaded_error", e.getMessage());
            } catch (IOException e) {
                Log.logException(e);
                prop.put("geo1ActionLoaded", 2);
                prop.put("geo1ActionLoaded_error", e.getMessage());
            }
        }
        
        if (post.containsKey("geo1Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEO1.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEO1.fileDisabled());
            LibraryProvider.geoDB = new OpenGeoDB(null, true);
            prop.put("geo1ActionRemoved", 1);
        }
        
        if (post.containsKey("geo1Deactivate")) {
            LibraryProvider.Dictionary.GEO1.file().renameTo(LibraryProvider.Dictionary.GEO1.fileDisabled());
            LibraryProvider.geoDB = new OpenGeoDB(null, true);
            prop.put("geo1ActionDeactivated", 1);
        }
        
        if (post.containsKey("geo1Activate")) {
            LibraryProvider.Dictionary.GEO1.fileDisabled().renameTo(LibraryProvider.Dictionary.GEO1.file());
            LibraryProvider.geoDB = new OpenGeoDB(LibraryProvider.Dictionary.GEO1.file(), false);
            prop.put("geo1ActionActivated", 1);
        }
        
        
        // check status again
        for (LibraryProvider.Dictionary dictionary: LibraryProvider.Dictionary.values()) {
            prop.put(dictionary.nickname + "Status", dictionary.file().exists() ? 1 : dictionary.fileDisabled().exists() ? 2 : 0);
        }
                
        return prop; // return rewrite values for templates
    }
}
