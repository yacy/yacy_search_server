// migration.java
// -----------------------
// (C) by Alexander Schier
//
// last change: $LastChangedDate: $ by $LastChangedBy: $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

import java.io.File;
import java.io.IOException;

import de.anomic.data.listManager;
import de.anomic.http.httpd;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class migration {
    //SVN constants
    public static final int USE_WORK_DIR=1389; //wiki & messages in DATA/WORK
    public static final int TAGDB_WITH_TAGHASH=1635; //tagDB keys are tagHashes instead of plain tagname.
    public static final int NEW_OVERLAYS=3675;
    public static void main(String[] args) {

    }

    public static void migrate(plasmaSwitchboard sb, int fromRev, int toRev){
        if(fromRev < toRev){
            if(fromRev < TAGDB_WITH_TAGHASH){
                migrateBookmarkTagsDB(sb);
            }
            if(fromRev < NEW_OVERLAYS){
                migrateDefaultFiles(sb);
            }
            serverLog.logInfo("MIGRATION", "Migrating from "+String.valueOf(fromRev)+ " to "+String.valueOf(toRev));
            presetPasswords(sb);
            migrateSwitchConfigSettings(sb);
            migrateWorkFiles(sb);
        }
        installSkins(sb); // FIXME: yes, bad fix for quick release 0.47
    }
    /*
     * remove the static defaultfiles. We use them through a overlay now.
     */
    public static void migrateDefaultFiles(plasmaSwitchboard sb){
        File file=new File(sb.htDocsPath, "share/dir.html");
        if(file.exists())
            file.delete();
        file=new File(sb.htDocsPath, "share/dir.class");
        if(file.exists())
            file.delete();
        file=new File(sb.htDocsPath, "share/dir.java");
        if(file.exists())
            file.delete();
        file=new File(sb.htDocsPath, "www/welcome.html");
        if(file.exists())
            file.delete();
        file=new File(sb.htDocsPath, "www/welcome.java");
        if(file.exists())
            file.delete();
        file=new File(sb.htDocsPath, "www/welcome.class");
        if(file.exists())
            file.delete();
    }
    public static void installSkins(plasmaSwitchboard sb){
        final File skinsPath = new File(sb.getRootPath(), sb.getConfig("skinsPath", "DATA/SKINS"));
        final File defaultSkinsPath = new File(sb.getRootPath(), "skins");
        if(defaultSkinsPath.exists()){
            final String[] skinFiles = listManager.getDirListing(defaultSkinsPath.getAbsolutePath());
            skinsPath.mkdirs();
            for(int i=0;i<skinFiles.length;i++){
                if(skinFiles[i].endsWith(".css")){
                    try{
                        serverFileUtils.copy(new File(defaultSkinsPath, skinFiles[i]), new File(skinsPath, skinFiles[i]));
                    }catch(IOException e){}
                }
            }
        }
        String skin=sb.getConfig("currentSkin", "default");
        if(skin.equals("")){
            skin="default";
        }
        File skinsDir=new File(sb.getRootPath(), sb.getConfig("skinsPath", "DATA/SKINS"));
        File skinFile=new File(skinsDir, skin+".css");
        File htdocsPath=new File(sb.getRootPath(), sb.getConfig("htdocsPath", "DATA/HTDOCS")+"/env");
        File styleFile=new File(htdocsPath, "style.css");
        if(!skinFile.exists()){
            if(styleFile.exists()){
                serverLog.logInfo("MIGRATION", "Skin "+skin+" not found. Keeping old skin.");
            }else{
                serverLog.logSevere("MIGRATION", "Skin "+skin+" and no existing Skin found.");
            }
        }else{
            try {
                styleFile.getParentFile().mkdirs();
                serverFileUtils.copy(skinFile, styleFile);
                serverLog.logInfo("MIGRATION", "copied new Skinfile");
            } catch (IOException e) {
                serverLog.logSevere("MIGRATION", "Cannot copy skinfile.");
            }
        }
    }
    public static void migrateBookmarkTagsDB(plasmaSwitchboard sb){
        sb.bookmarksDB.close();
        File tagsDBFile=new File(sb.workPath, "bookmarkTags.db");
        if(tagsDBFile.exists()){
            tagsDBFile.delete();
            serverLog.logInfo("MIGRATION", "Migrating bookmarkTags.db to use wordhashs as keys.");
        }
        sb.initBookmarks();
    }
    public static void migrateWorkFiles(plasmaSwitchboard sb){
        File file=new File(sb.getRootPath(), "DATA/SETTINGS/wiki.db");
        File file2;
        if (file.exists()) {
            serverLog.logInfo("MIGRATION", "Migrating wiki.db to "+ sb.workPath);
            sb.wikiDB.close();
            file2 = new File(sb.workPath, "wiki.db");
            try {
                serverFileUtils.copy(file, file2);
                file.delete();
            } catch (IOException e) {
            }
            
            file = new File(sb.getRootPath(), "DATA/SETTINGS/wiki-bkp.db");
            if (file.exists()) {
                serverLog.logInfo("MIGRATION", "Migrating wiki-bkp.db to "+ sb.workPath);
                file2 = new File(sb.workPath, "wiki-bkp.db");
                try {
                    serverFileUtils.copy(file, file2);
                    file.delete();
                } catch (IOException e) {}        
            }
            sb.initWiki(sb.getConfigLong("ramCacheWiki_time", 1000));
        }
        
        
        file=new File(sb.getRootPath(), "DATA/SETTINGS/message.db");
        if(file.exists()){
            serverLog.logInfo("MIGRATION", "Migrating message.db to "+ sb.workPath);
            sb.messageDB.close();
            file2=new File(sb.workPath, "message.db");
            try {
                serverFileUtils.copy(file, file2);
                file.delete();
            } catch (IOException e) {}
            sb.initMessages(sb.getConfigLong("ramCacheMessage_time", 1000));
        }
    }

    public static void presetPasswords(plasmaSwitchboard sb) {
        // set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig("serverAccount", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(acc)));
            sb.setConfig("serverAccount", "");
        }
        if ((acc = sb.getConfig("adminAccount", "")).length() > 0) {
            sb.setConfig(httpd.ADMIN_ACCOUNT_B64MD5, de.anomic.server.serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(acc)));
            sb.setConfig("adminAccount", "");
        }
    
        // fix unsafe old passwords
        if ((acc = sb.getConfig("proxyAccountBase64", "")).length() > 0) {
            sb.setConfig("proxyAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("proxyAccountBase64", "");
        }
        if ((acc = sb.getConfig("serverAccountBase64", "")).length() > 0) {
            sb.setConfig("serverAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("serverAccountBase64", "");
        }
        if ((acc = sb.getConfig("adminAccountBase64", "")).length() > 0) {
            sb.setConfig(httpd.ADMIN_ACCOUNT_B64MD5, de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("adminAccountBase64", "");
        }
        if ((acc = sb.getConfig("uploadAccountBase64", "")).length() > 0) {
            sb.setConfig("uploadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("uploadAccountBase64", "");
        }
        if ((acc = sb.getConfig("downloadAccountBase64", "")).length() > 0) {
            sb.setConfig("downloadAccountBase64MD5", de.anomic.server.serverCodings.encodeMD5Hex(acc));
            sb.setConfig("downloadAccountBase64", "");
        }
    }

    public static void migrateSwitchConfigSettings(plasmaSwitchboard sb) {
        
        // migration for additional parser settings
        String value = "";
        if ((value = sb.getConfig("parseableMimeTypes","")).length() > 0) {
            sb.setConfig("parseableMimeTypes.CRAWLER", value);
            sb.setConfig("parseableMimeTypes.PROXY", value);
            sb.setConfig("parseableMimeTypes.URLREDIRECTOR", value);
            sb.setConfig("parseableMimeTypes.ICAP", value);
        }
        //Locales in DATA, because DATA must be writable, htroot not.
        if(sb.getConfig("locale.translated_html", "DATA/LOCALE/htroot").equals("htroot/locale")){
        	sb.setConfig("locale.translated_html", "DATA/LOCALE/htroot");
        }
        
        // migration for port forwarding settings
        if ((value = sb.getConfig("portForwardingHost","")).length() > 0) {
            sb.setConfig("portForwarding.Enabled", sb.getConfig("portForwardingEnabled",""));
            if (sb.getConfigBool("portForwardingEnabled", false)) {
                sb.setConfig("portForwarding.Type", "sch");
            }
                        
            sb.setConfig("portForwarding.sch.UseProxy", sb.getConfig("portForwardingUseProxy",""));
            sb.setConfig("portForwarding.sch.Port", sb.getConfig("portForwardingPort",""));
            sb.setConfig("portForwarding.sch.Host", sb.getConfig("portForwardingHost",""));
            sb.setConfig("portForwarding.sch.HostPort", sb.getConfig("portForwardingHostPort",""));
            sb.setConfig("portForwarding.sch.HostUser", sb.getConfig("portForwardingHostUser",""));
            sb.setConfig("portForwarding.sch.HostPwd", sb.getConfig("portForwardingHostPwd",""));
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
