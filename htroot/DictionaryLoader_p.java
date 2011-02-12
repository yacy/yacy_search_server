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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.LibraryProvider;
import net.yacy.document.geolocalization.GeonamesLocalization;
import net.yacy.document.geolocalization.OpenGeoDBLocalization;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
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
        
        // GEON0
        if (post.containsKey("geon0Load")) {
            // load from the net
            try {
                Response response = sb.loader.load(sb.loader.request(new DigestURI(LibraryProvider.Dictionary.GEON0.url), false, true), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE, false);
                byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEON0.file());
                LibraryProvider.geoLoc.addLocalization(LibraryProvider.Dictionary.GEON0.nickname, new GeonamesLocalization(LibraryProvider.Dictionary.GEON0.file()));
                prop.put("geon0Status", LibraryProvider.Dictionary.GEON0.file().exists() ? 1 : 0);
                prop.put("geon0ActionLoaded", 1);
            } catch (MalformedURLException e) {
                Log.logException(e);
                prop.put("geon0ActionLoaded", 2);
                prop.put("geon0ActionLoaded_error", e.getMessage());
            } catch (IOException e) {
                Log.logException(e);
                prop.put("geon0ActionLoaded", 2);
                prop.put("geon0ActionLoaded_error", e.getMessage());
            }
        }
        
        if (post.containsKey("geon0Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON0.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON0.fileDisabled());
            LibraryProvider.geoLoc.removeLocalization(LibraryProvider.Dictionary.GEON0.nickname);
            prop.put("geon0ActionRemoved", 1);
        }
        
        if (post.containsKey("geon0Deactivate")) {
            LibraryProvider.Dictionary.GEON0.file().renameTo(LibraryProvider.Dictionary.GEON0.fileDisabled());
            LibraryProvider.geoLoc.removeLocalization(LibraryProvider.Dictionary.GEON0.nickname);
            prop.put("geon0ActionDeactivated", 1);
        }
        
        if (post.containsKey("geon0Activate")) {
            LibraryProvider.Dictionary.GEON0.fileDisabled().renameTo(LibraryProvider.Dictionary.GEON0.file());
            LibraryProvider.geoLoc.addLocalization(LibraryProvider.Dictionary.GEON0.nickname, new GeonamesLocalization(LibraryProvider.Dictionary.GEON0.file()));
            prop.put("geon0ActionActivated", 1);
        }
        
        // GEO1
        if (post.containsKey("geo1Load")) {
            // load from the net
            try {
                Response response = sb.loader.load(sb.loader.request(new DigestURI(LibraryProvider.Dictionary.GEODB1.url), false, true), CrawlProfile.CacheStrategy.NOCACHE, Long.MAX_VALUE, false);
                byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEODB1.file());
                LibraryProvider.geoLoc.removeLocalization(LibraryProvider.Dictionary.GEODB0.nickname);
                LibraryProvider.geoLoc.addLocalization(LibraryProvider.Dictionary.GEODB1.nickname, new OpenGeoDBLocalization(LibraryProvider.Dictionary.GEODB1.file(), false));
                prop.put("geo1Status", LibraryProvider.Dictionary.GEODB1.file().exists() ? 1 : 0);
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
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEODB1.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEODB1.fileDisabled());
            LibraryProvider.geoLoc.removeLocalization(LibraryProvider.Dictionary.GEODB1.nickname);
            prop.put("geo1ActionRemoved", 1);
        }
        
        if (post.containsKey("geo1Deactivate")) {
            LibraryProvider.Dictionary.GEODB1.file().renameTo(LibraryProvider.Dictionary.GEODB1.fileDisabled());
            LibraryProvider.geoLoc.removeLocalization(LibraryProvider.Dictionary.GEODB1.nickname);
            prop.put("geo1ActionDeactivated", 1);
        }
        
        if (post.containsKey("geo1Activate")) {
            LibraryProvider.Dictionary.GEODB1.fileDisabled().renameTo(LibraryProvider.Dictionary.GEODB1.file());
            LibraryProvider.geoLoc.addLocalization(LibraryProvider.Dictionary.GEODB1.nickname, new OpenGeoDBLocalization(LibraryProvider.Dictionary.GEODB1.file(), false));
            prop.put("geo1ActionActivated", 1);
        }
        
        // check status again
        for (LibraryProvider.Dictionary dictionary: LibraryProvider.Dictionary.values()) {
            prop.put(dictionary.nickname + "Status", dictionary.file().exists() ? 1 : dictionary.fileDisabled().exists() ? 2 : 0);
        }
                
        return prop; // return rewrite values for templates
    }
}
