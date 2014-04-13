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

import net.yacy.search.index.ReindexSolrBusyThread;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

import com.google.common.io.Files;
import java.util.concurrent.ExecutionException;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.LukeResponse.FieldInfo;

public class migration {
    //SVN constants
    public static final int USE_WORK_DIR=1389; //wiki & messages in DATA/WORK
    public static final int TAGDB_WITH_TAGHASH=1635; //tagDB keys are tagHashes instead of plain tagname.
    public static final int NEW_OVERLAYS=4422;
    public static final int IDX_HOST=7724; // api for index retrieval: host index
   
    public static void migrate(final Switchboard sb, final int fromRev, final int toRev){
        if(fromRev < toRev){
            if(fromRev < TAGDB_WITH_TAGHASH){
                migrateBookmarkTagsDB(sb);
            }
            if(fromRev < NEW_OVERLAYS){
                migrateDefaultFiles(sb);
            }
            ConcurrentLog.info("MIGRATION", "Migrating from "+ fromRev + " to " + toRev);
            presetPasswords(sb);
            migrateSwitchConfigSettings(sb);
            migrateWorkFiles(sb);
        }
        installSkins(sb); // FIXME: yes, bad fix for quick release 0.47

        // ssl/https support currently on hardcoded default port 8443 (v1.67/9563)
        // make sure YaCy can start (disable ssl/https support if port is used)
        if (sb.getConfigBool("server.https", false)) {
            try {
                if (TimeoutRequest.ping("127.0.0.1", 8443, 3000)) {
                    sb.setConfig("server.https", false);
                    ConcurrentLog.info("MIGRATION", "disabled https support (reason: default port 8443 already used)");
                }
            } catch (ExecutionException ex) { }
        }
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
        final File skinsPath = sb.getDataPath("skinPath", SwitchboardConstants.SKINS_PATH_DEFAULT);
        final File defaultSkinsPath = new File(sb.getAppPath(), "skins");
        if (defaultSkinsPath.exists()) {
            final List<String> skinFiles = FileUtils.getDirListing(defaultSkinsPath.getAbsolutePath());
            mkdirs(skinsPath);
            for (final String skinFile : skinFiles){
                if (skinFile.endsWith(".css")){
                    final File from = new File(defaultSkinsPath, skinFile);
                    final File to = new File(skinsPath, skinFile);
                    if (from.lastModified() > to.lastModified()) try {
                        Files.copy(from, to);
                    } catch (final IOException e) {}
                }
            }
        }
        String skin=sb.getConfig("currentSkin", "default");
        if(skin.equals("")){
            skin="default";
        }
        final File skinsDir=sb.getDataPath("skinPath", SwitchboardConstants.SKINS_PATH_DEFAULT);
        final File skinFile=new File(skinsDir, skin+".css");
        final File htdocsPath=new File(sb.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTROOT_PATH_DEFAULT), "env");
        final File styleFile=new File(htdocsPath, "style.css");
        if(!skinFile.exists()){
            if(styleFile.exists()){
                ConcurrentLog.info("MIGRATION", "Skin "+skin+" not found. Keeping old skin.");
            }else{
                ConcurrentLog.severe("MIGRATION", "Skin "+skin+" and no existing Skin found.");
            }
        }else{
            try {
                mkdirs(styleFile.getParentFile());
                Files.copy(skinFile, styleFile);
                ConcurrentLog.info("MIGRATION", "copied new Skinfile");
            } catch (final IOException e) {
                ConcurrentLog.severe("MIGRATION", "Cannot copy skinfile.");
            }
        }
    }

	/**
	 * @param path
	 */
	private static void mkdirs(final File path) {
		if (!path.exists()) {
			if(!path.mkdirs())
				ConcurrentLog.warn("MIGRATION", "could not create directories for "+ path);
		}
	}
    public static void migrateBookmarkTagsDB(final Switchboard sb){
        if (sb.bookmarksDB != null) sb.bookmarksDB.close();
        final File tagsDBFile=new File(sb.workPath, "bookmarkTags.db");
        if(tagsDBFile.exists()){
            delete(tagsDBFile);
            ConcurrentLog.info("MIGRATION", "Migrating bookmarkTags.db to use wordhashs as keys.");
        }
        try {
            sb.initBookmarks();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

	/**
	 * @param filename
	 */
	private static void delete(final File filename) {
		if(!filename.delete())
			ConcurrentLog.warn("MIGRATION", "could not delete "+ filename);
	}
    public static void migrateWorkFiles(final Switchboard sb){
        File file=new File(sb.getDataPath(), "DATA/SETTINGS/wiki.db");
        File file2;
        if (file.exists()) {
            ConcurrentLog.info("MIGRATION", "Migrating wiki.db to "+ sb.workPath);
            sb.wikiDB.close();
            file2 = new File(sb.workPath, "wiki.db");
            try {
                Files.copy(file, file2);
                file.delete();
            } catch (final IOException e) {
            }

            file = new File(sb.getDataPath(), "DATA/SETTINGS/wiki-bkp.db");
            if (file.exists()) {
                ConcurrentLog.info("MIGRATION", "Migrating wiki-bkp.db to "+ sb.workPath);
                file2 = new File(sb.workPath, "wiki-bkp.db");
                try {
                    Files.copy(file, file2);
                    file.delete();
                } catch (final IOException e) {}
            }
            try {
                sb.initWiki();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }


        file=new File(sb.getDataPath(), "DATA/SETTINGS/message.db");
        if(file.exists()){
            ConcurrentLog.info("MIGRATION", "Migrating message.db to "+ sb.workPath);
            sb.messageDB.close();
            file2=new File(sb.workPath, "message.db");
            try {
                Files.copy(file, file2);
                file.delete();
            } catch (final IOException e) {}
            try {
                sb.initMessages();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static void presetPasswords(final Switchboard sb) {
        // set preset accounts/passwords
        String acc;
        if ((acc = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT, "")).length() > 0) {
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, Digest.encodeMD5Hex(Base64Order.standardCoder.encodeString(acc)));
            sb.setConfig(SwitchboardConstants.ADMIN_ACCOUNT, "");
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

        // patch the blacklist because of a release strategy change from 0.7 and up
        if ((value = sb.getConfig("update.blacklist","")).equals("....[123]")) {
            value = ""; // no default (remove prev. setting "...[123]" as it hits "1.71" release, added 2014-04-13)
            sb.setConfig("update.blacklist", value);
        }
    }
    
    /**
     * Reindex embedded solr index
     *   - all documents with inactive fields (according to current schema)
     *   - all documents with obsolete fields
     * A worker thread is initialized with fieldnames or a solr query which selects the documents for reindexing
     * implemented via deployed BusyThread which is called repeatedly by system
     * reindexes a fixed chunk of documents per cycle (allowing to easy interrupt process after completion of a chunck)
     * and monitoring in default process monitor (PerformanceQueues_p.html)
     */
    public static int reindexToschema (final Switchboard sb) {

        BusyThread bt = sb.getThread("reindexSolr");
        // a reindex job is already running 
        if (bt != null) {
            return bt.getJobCount();
        }
        
        boolean lukeCheckok = false;
        Set<String> omitFields = new HashSet<String>(4);
        omitFields.add(CollectionSchema.author_sxt.getSolrFieldName()); // special fields to exclude from disabled check
        omitFields.add(CollectionSchema.coordinate_p_0_coordinate.getSolrFieldName());
        omitFields.add(CollectionSchema.coordinate_p_1_coordinate.getSolrFieldName());
        omitFields.add("_version_"); // exclude internal Solr std. field from obsolete check
        Collection<Tagging> vocs = LibraryProvider.autotagging.getVocabularies();
        for (Tagging v: vocs) { //exclude configured vocabulary index fields (not in CollectionSchema but valid)
            omitFields.add(CollectionSchema.VOCABULARY_PREFIX + v.getName() + CollectionSchema.VOCABULARY_SUFFIX);
        }        
        CollectionConfiguration colcfg = Switchboard.getSwitchboard().index.fulltext().getDefaultConfiguration();
        ReindexSolrBusyThread reidx = new ReindexSolrBusyThread(null); // ("*:*" would reindex all);
        
        try { // get all fields contained in index
            Collection<FieldInfo> solrfields = Switchboard.getSwitchboard().index.fulltext().getDefaultEmbeddedConnector().getFields();
            for (FieldInfo solrfield : solrfields) {
                if (!colcfg.contains(solrfield.getName()) && !omitFields.contains(solrfield.getName())) { // add found fields not in config for reindexing
                    reidx.addSelectFieldname(solrfield.getName());
                }
            }
            lukeCheckok = true;
        } catch (final SolrServerException ex) {
            ConcurrentLog.logException(ex);
        }
  
        if (!lukeCheckok) {  // if luke failed alternatively use config and manual list                
            // add all disabled fields
            Iterator<Entry> itcol = colcfg.entryIterator();
            while (itcol.hasNext()) { // check for disabled fields in config
                Entry etr = itcol.next();
                if (!etr.enabled() && !omitFields.contains(etr.key())) {
                    reidx.addSelectFieldname(etr.key());
                }
            }

            // add obsolete fields (not longer part of main index)
            reidx.addSelectFieldname("author_s");
            reidx.addSelectFieldname("css_tag_txt");
            reidx.addSelectFieldname("css_url_txt");
            reidx.addSelectFieldname("scripts_txt");
            reidx.addSelectFieldname("images_tag_txt");
            reidx.addSelectFieldname("images_urlstub_txt");
            reidx.addSelectFieldname("canonical_t");
            reidx.addSelectFieldname("frames_txt");
            reidx.addSelectFieldname("iframes_txt");

            reidx.addSelectFieldname("inboundlinks_tag_txt");
            reidx.addSelectFieldname("inboundlinks_relflags_val");
            reidx.addSelectFieldname("inboundlinks_name_txt");
            reidx.addSelectFieldname("inboundlinks_rel_sxt");
            reidx.addSelectFieldname("inboundlinks_text_txt");
            reidx.addSelectFieldname("inboundlinks_text_chars_val");
            reidx.addSelectFieldname("inboundlinks_text_words_val");
            reidx.addSelectFieldname("inboundlinks_alttag_txt");

            reidx.addSelectFieldname("outboundlinks_tag_txt");
            reidx.addSelectFieldname("outboundlinks_relflags_val");
            reidx.addSelectFieldname("outboundlinks_name_txt");
            reidx.addSelectFieldname("outboundlinks_rel_sxt");
            reidx.addSelectFieldname("outboundlinks_text_txt");
            reidx.addSelectFieldname("outboundlinks_text_chars_val");
            reidx.addSelectFieldname("outboundlinks_text_words_val");
            reidx.addSelectFieldname("outboundlinks_alttag_txt");
        }
        sb.deployThread("reindexSolr", "Reindex Solr", "reindex documents with obsolete fields in embedded Solr index", "/IndexReIndexMonitor_p.html",reidx , 0);
        return 0;
    }
}
