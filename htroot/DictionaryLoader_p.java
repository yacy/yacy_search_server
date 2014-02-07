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

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.geo.GeonamesLocation;
import net.yacy.cora.geo.OpenGeoDBLocation;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class DictionaryLoader_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects(); // return variable that accumulates replacements

        /*
         * distinguish the following cases:
         * - dictionary file was not loaded -> actions: load the file
         * - dictionary file is loaded and enabled -> actions: disable or remove the file
         * - dictionary file is loaded but disabled -> actions: enable or remove the file
         */

        for (final LibraryProvider.Dictionary dictionary: LibraryProvider.Dictionary.values()) {
            prop.put(dictionary.nickname + "URL", dictionary.url);
            prop.put(dictionary.nickname + "Storage", dictionary.file().toString());
            prop.put(dictionary.nickname + "Status", dictionary.file().exists() ? 1 : dictionary.fileDisabled().exists() ? 2 : 0);
            prop.put(dictionary.nickname + "ActionLoaded", 0);
            prop.put(dictionary.nickname + "ActionRemoved", 0);
            prop.put(dictionary.nickname + "ActionActivated", 0);
            prop.put(dictionary.nickname + "ActionDeactivated", 0);
        }

        if (post == null) {
            return prop;
        }

        // GEON0
        if (post.containsKey("geon0Load")) {
            // load from the net
            try {
                final Response response = sb.loader.load(sb.loader.request(new DigestURL(LibraryProvider.Dictionary.GEON0.url), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
                final byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEON0.file());
                LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON0.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON0.file(), null, -1));
                LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
                prop.put("geon0Status", LibraryProvider.Dictionary.GEON0.file().exists() ? 1 : 0);
                prop.put("geon0ActionLoaded", 1);
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
                prop.put("geon0ActionLoaded", 2);
                prop.put("geon0ActionLoaded_error", e.getMessage());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                prop.put("geon0ActionLoaded", 2);
                prop.put("geon0ActionLoaded_error", e.getMessage());
            }
        }

        if (post.containsKey("geon0Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON0.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON0.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON0.nickname);
            prop.put("geon0ActionRemoved", 1);
        }

        if (post.containsKey("geon0Deactivate")) {
            LibraryProvider.Dictionary.GEON0.file().renameTo(LibraryProvider.Dictionary.GEON0.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON0.nickname);
            prop.put("geon0ActionDeactivated", 1);
        }

        if (post.containsKey("geon0Activate")) {
            LibraryProvider.Dictionary.GEON0.fileDisabled().renameTo(LibraryProvider.Dictionary.GEON0.file());
            LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON0.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON0.file(), null, -1));
            LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
            prop.put("geon0ActionActivated", 1);
        }

        // GEON1
        if (post.containsKey("geon1Load")) {
            // load from the net
            try {
                final Response response = sb.loader.load(sb.loader.request(new DigestURL(LibraryProvider.Dictionary.GEON1.url), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
                final byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEON1.file());
                LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON1.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON1.file(), null, -1));
                LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
                prop.put("geon1Status", LibraryProvider.Dictionary.GEON1.file().exists() ? 1 : 0);
                prop.put("geon1ActionLoaded", 1);
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
                prop.put("geon1ActionLoaded", 2);
                prop.put("geon1ActionLoaded_error", e.getMessage());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                prop.put("geon1ActionLoaded", 2);
                prop.put("geon1ActionLoaded_error", e.getMessage());
            }
        }

        if (post.containsKey("geon1Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON1.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON1.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON1.nickname);
            prop.put("geon1ActionRemoved", 1);
        }

        if (post.containsKey("geon1Deactivate")) {
            LibraryProvider.Dictionary.GEON1.file().renameTo(LibraryProvider.Dictionary.GEON1.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON1.nickname);
            prop.put("geon1ActionDeactivated", 1);
        }

        if (post.containsKey("geon1Activate")) {
            LibraryProvider.Dictionary.GEON1.fileDisabled().renameTo(LibraryProvider.Dictionary.GEON1.file());
            LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON1.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON1.file(), null, -1));
            LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
            prop.put("geon1ActionActivated", 1);
        }

        // GEON2
        if (post.containsKey("geon2Load")) {
            // load from the net
            try {
                final Response response = sb.loader.load(sb.loader.request(new DigestURL(LibraryProvider.Dictionary.GEON2.url), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
                final byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEON2.file());
                LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON2.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON2.file(), null, 100000));
                LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
                prop.put("geon2Status", LibraryProvider.Dictionary.GEON2.file().exists() ? 1 : 0);
                prop.put("geon2ActionLoaded", 1);
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
                prop.put("geon2ActionLoaded", 2);
                prop.put("geon2ActionLoaded_error", e.getMessage());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                prop.put("geon2ActionLoaded", 2);
                prop.put("geon2ActionLoaded_error", e.getMessage());
            }
        }

        if (post.containsKey("geon2Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON2.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEON2.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON2.nickname);
            prop.put("geon2ActionRemoved", 1);
        }

        if (post.containsKey("geon2Deactivate")) {
            LibraryProvider.Dictionary.GEON2.file().renameTo(LibraryProvider.Dictionary.GEON2.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEON2.nickname);
            prop.put("geon2ActionDeactivated", 1);
        }

        if (post.containsKey("geon2Activate")) {
            LibraryProvider.Dictionary.GEON2.fileDisabled().renameTo(LibraryProvider.Dictionary.GEON2.file());
            LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEON2.nickname, new GeonamesLocation(LibraryProvider.Dictionary.GEON2.file(), null, 100000));
            LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
            prop.put("geon2ActionActivated", 1);
        }

        // GEO1
        if (post.containsKey("geo1Load")) {
            // load from the net
            try {
                final Response response = sb.loader.load(sb.loader.request(new DigestURL(LibraryProvider.Dictionary.GEODB1.url), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
                final byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.GEODB1.file());
                LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEODB1.nickname);
                LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEODB1.nickname, new OpenGeoDBLocation(LibraryProvider.Dictionary.GEODB1.file(), null));
                LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
                prop.put("geo1Status", LibraryProvider.Dictionary.GEODB1.file().exists() ? 1 : 0);
                prop.put("geo1ActionLoaded", 1);
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
                prop.put("geo1ActionLoaded", 2);
                prop.put("geo1ActionLoaded_error", e.getMessage());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                prop.put("geo1ActionLoaded", 2);
                prop.put("geo1ActionLoaded_error", e.getMessage());
            }
        }

        if (post.containsKey("geo1Remove")) {
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEODB1.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.GEODB1.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEODB1.nickname);
            prop.put("geo1ActionRemoved", 1);
        }

        if (post.containsKey("geo1Deactivate")) {
            LibraryProvider.Dictionary.GEODB1.file().renameTo(LibraryProvider.Dictionary.GEODB1.fileDisabled());
            LibraryProvider.geoLoc.deactivateLocalization(LibraryProvider.Dictionary.GEODB1.nickname);
            prop.put("geo1ActionDeactivated", 1);
        }

        if (post.containsKey("geo1Activate")) {
            LibraryProvider.Dictionary.GEODB1.fileDisabled().renameTo(LibraryProvider.Dictionary.GEODB1.file());
            LibraryProvider.geoLoc.activateLocation(LibraryProvider.Dictionary.GEODB1.nickname, new OpenGeoDBLocation(LibraryProvider.Dictionary.GEODB1.file(), null));
            LibraryProvider.autotagging.addPlaces(LibraryProvider.geoLoc);
            prop.put("geo1ActionActivated", 1);
        }

        // DRW0
        if (post.containsKey("drw0Load")) {
            // load from the net
            try {
                final Response response = sb.loader.load(sb.loader.request(new DigestURL(LibraryProvider.Dictionary.DRW0.url), false, true), CacheStrategy.NOCACHE, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
                final byte[] b = response.getContent();
                FileUtils.copy(b, LibraryProvider.Dictionary.DRW0.file());
                LibraryProvider.activateDeReWo();
                LibraryProvider.initDidYouMean();
                prop.put("drw0Status", LibraryProvider.Dictionary.DRW0.file().exists() ? 1 : 0);
                prop.put("drw0ActionLoaded", 1);
            } catch (final MalformedURLException e) {
                ConcurrentLog.logException(e);
                prop.put("drw0ActionLoaded", 2);
                prop.put("drw0ActionLoaded_error", e.getMessage());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                prop.put("drw0ActionLoaded", 2);
                prop.put("drw0ActionLoaded_error", e.getMessage());
            }
        }

        if (post.containsKey("drw0Remove")) {
            LibraryProvider.deactivateDeReWo();
            LibraryProvider.initDidYouMean();
            FileUtils.deletedelete(LibraryProvider.Dictionary.DRW0.file());
            FileUtils.deletedelete(LibraryProvider.Dictionary.DRW0.fileDisabled());
            prop.put("drw0ActionRemoved", 1);
        }

        if (post.containsKey("drw0Deactivate")) {
            LibraryProvider.deactivateDeReWo();
            LibraryProvider.initDidYouMean();
            LibraryProvider.Dictionary.DRW0.file().renameTo(LibraryProvider.Dictionary.DRW0.fileDisabled());
            prop.put("drw0ActionDeactivated", 1);
        }

        if (post.containsKey("drw0Activate")) {
            LibraryProvider.Dictionary.DRW0.fileDisabled().renameTo(LibraryProvider.Dictionary.DRW0.file());
            LibraryProvider.activateDeReWo();
            LibraryProvider.initDidYouMean();
            prop.put("drw0ActionActivated", 1);
        }

        // check status again
        boolean keepPlacesTagging = false;
        for (final LibraryProvider.Dictionary dictionary: LibraryProvider.Dictionary.values()) {
            int newstatus = dictionary.file().exists() ? 1 : dictionary.fileDisabled().exists() ? 2 : 0;
            if (newstatus == 1) keepPlacesTagging = true;
            prop.put(dictionary.nickname + "Status", newstatus);
        }

        // if all locations are deleted or deactivated, remove also the vocabulary
        if (!keepPlacesTagging) {
            LibraryProvider.autotagging.removePlaces();
        }
        
        return prop; // return rewrite values for templates
    }
}
