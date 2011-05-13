package net.yacy;
// migration.java
// -----------------------
// (C) by Alexander Schier
//
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
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

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;

public class migration {
    //SVN constants
    public static final int USE_WORK_DIR=1389; //wiki & messages in DATA/WORK
    public static final int TAGDB_WITH_TAGHASH=1635; //tagDB keys are tagHashes instead of plain tagname.
    public static final int NEW_OVERLAYS=4422;
    public static void main(final String[] args) {

    }

    public static void migrate(final Switchboard sb, final int fromRev, final int toRev){
        if(fromRev < toRev){
            if(fromRev < TAGDB_WITH_TAGHASH){
                migrateBookmarkTagsDB(sb);
            }
            if(fromRev < NEW_OVERLAYS){
                migrateDefaultFiles(sb);
            }
            Log.logInfo("MIGRATION", "Migrating from "+ fromRev + " to " + toRev);
            presetPasswords(sb);
            migrateSwitchConfigSettings(sb);
            migrateWorkFiles(sb);
        }
        installSkins(sb); // FIXME: yes, bad fix for quick release 0.47
    }
    /*
     * remove the static defaultfiles. We use them through a overlay now.
     */
    public static void migrateDefaultFiles(final Switchboard sb){
        File file=new File(sb.htDocsPath, "share/dir.html");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "share/dir.class");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "share/dir.java");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.html");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.java");
        if(file.exists())
            delete(file);
        file=new File(sb.htDocsPath, "www/welcome.class");
        if(file.exists())
            delete(file);
    }
    
    /*
     * copy skins from the release to DATA/SKINS.
     */
    public static void installSkins(final Switchboard sb){
        final File skinsPath = sb.getDataPath("skinPath", "DATA/SKINS");
        final File defaultSkinsPath = new File(sb.getAppPath(), "skins");
        if (defaultSkinsPath.exists()) {
            final List<String> skinFiles = FileUtils.getDirListing(defaultSkinsPath.getAbsolutePath());
            mkdirs(skinsPath);
            for (String skinFile : skinFiles){
                if (skinFile.endsWith(".css")){
                    File from = new File(defaultSkinsPath, skinFile);
                    File to = new File(skinsPath, skinFile);
                    if (from.lastModified() > to.lastModified()) try {
                        FileUtils.copy(from, to);
                    } catch (final IOException e) {}
                }
            }
        }
        String skin=sb.getConfig("currentSkin", "default");
        if(skin.equals("")){
            skin="default";
        }
        final File skinsDir=sb.getDataPath("skinPath", "DATA/SKINS");
        final File skinFile=new File(skinsDir, skin+".css");
        final File htdocsPath=new File(sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT), "env");
        final File styleFile=new File(htdocsPath, "style.css");
        if(!skinFile.exists()){
            if(styleFile.exists()){
                Log.logInfo("MIGRATION", "Skin "+skin+" not found. Keeping old skin.");
            }else{
                Log.logSevere("MIGRATION", "Skin "+skin+" and no existing Skin found.");
            }
        }else{
            try {
                mkdirs(styleFile.getParentFile());
                FileUtils.copy(skinFile, styleFile);
                Log.logInfo("MIGRATION", "copied new Skinfile");
            } catch (final IOException e) {
                Log.logSevere("MIGRATION", "Cannot copy skinfile.");
            }
        }
    }

	/**
	 * @param path
	 */
	private static void mkdirs(final File path) {
		if (!path.exists()) {
			if(!path.mkdirs())
				Log.logWarning("MIGRATION", "could not create directories for "+ path);
		}
	}
    public static void migrateBookmarkTagsDB(final Switchboard sb){
        sb.bookmarksDB.close();
        final File tagsDBFile=new File(sb.workPath, "bookmarkTags.db");
        if(tagsDBFile.exists()){
            delete(tagsDBFile);
            Log.logInfo("MIGRATION", "Migrating bookmarkTags.db to use wordhashs as keys.");
        }
        try {
            sb.initBookmarks();
        } catch (IOException e) {
            Log.logException(e);
        }
    }

	/**
	 * @param filename
	 */
	private static void delete(final File filename) {
		if(!filename.delete())
			Log.logWarning("MIGRATION", "could not delete "+ filename);
	}
    public static void migrateWorkFiles(final Switchboard sb){
        File file=new File(sb.getDataPath(), "DATA/SETTINGS/wiki.db");
        File file2;
        if (file.exists()) {
            Log.logInfo("MIGRATION", "Migrating wiki.db to "+ sb.workPath);
            sb.wikiDB.close();
            file2 = new File(sb.workPath, "wiki.db");
            try {
                FileUtils.copy(file, file2);
                file.delete();
            } catch (final IOException e) {
            }
            
            file = new File(sb.getDataPath(), "DATA/SETTINGS/wiki-bkp.db");
            if (file.exists()) {
                Log.logInfo("MIGRATION", "Migrating wiki-bkp.db to "+ sb.workPath);
                file2 = new File(sb.workPath, "wiki-bkp.db");
                try {
                    FileUtils.copy(file, file2);
                    file.delete();
                } catch (final IOException e) {}        
            }
            try {
                sb.initWiki();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
        
        
        file=new File(sb.getDataPath(), "DATA/SETTINGS/message.db");
        if(file.exists()){
            Log.logInfo("MIGRATION", "Migrating message.db to "+ sb.workPath);
            sb.messageDB.close();
            file2=new File(sb.workPath, "message.db");
            try {
                FileUtils.copy(file, file2);
                file.delete();
            } catch (final IOException e) {}
            try {
                sb.initMessages();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }

    public static void presetPasswords(final Switchboard sb) {
        // set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(acc)));
            sb.setConfig("adminAccount", "");
        }
    
        // fix unsafe old passwords
        if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
            sb.setConfig("proxyAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("proxyAccountBase64", "");
        }
        if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
            sb.setConfig("uploadAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("uploadAccountBase64", "");
        }
        if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
            sb.setConfig("downloadAccountBase64MD5", Digest.encodeMD5Hex(acc));
            sb.setConfig("downloadAccountBase64", "");
        }
    }

    public static void migrateSwitchConfigSettings(final Switchboard sb) {
        
        // migration for additional parser settings
        String value = "";
        //Locales in DATA, because DATA must be writable, htroot not.
        if(sb.getConfig("locale.translated_html", "DATA/LOCALE/htroot").equals("htroot/locale")){
        	sb.setConfig("locale.translated_html", "DATA/LOCALE/htroot");
        }
        
        // migration for blacklists
        if ((value = sb.getConfig("proxyBlackListsActive","")).length() > 0) {
            sb.setConfig("proxy.BlackLists", value);
            sb.setConfig("crawler.BlackLists", value);
            sb.setConfig("dht.BlackLists", value);
            sb.setConfig("search.BlackLists", value);
            sb.setConfig("surftips.BlackLists", value);
            
            sb.setConfig("BlackLists.Shared",sb.getConfig("proxyBlackListsShared",""));
            sb.setConfig("proxyBlackListsActive", "");
        }
        
        // migration of http specific crawler settings
        if ((value = sb.getConfig("crawler.acceptLanguage","")).length() > 0) {
            sb.setConfig("crawler.http.acceptEncoding", sb.getConfig("crawler.acceptEncoding","gzip,deflate"));
            sb.setConfig("crawler.http.acceptLanguage", sb.getConfig("crawler.acceptLanguage","en-us,en;q=0.5"));
            sb.setConfig("crawler.http.acceptCharset",  sb.getConfig("crawler.acceptCharset","ISO-8859-1,utf-8;q=0.7,*;q=0.7"));            
        }        
    }
}
