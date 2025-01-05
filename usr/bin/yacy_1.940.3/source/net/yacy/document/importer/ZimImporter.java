/**
 * ZimImporter.java
 * (C) 2023 by Michael Peter Christen @orbiter
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.TextParser;
import net.yacy.search.Switchboard;

import org.openzim.ZIMFile;
import org.openzim.ZIMReader;
import org.openzim.ZIMReader.ArticleEntry;
import org.openzim.ZIMReader.DirectoryEntry;

/**
 * ZIM importer
 * can import ZIM file i.e. from https://download.kiwix.org/zim/ or mirrors like https://ftp.fau.de/kiwix/zim/
 * A huge list is at https://wiki.kiwix.org/wiki/Content_in_all_languages
 * These files contains identifiers named "URL" which are not actually full URLs but just paths inside a well-known domains.
 * These domains are sometimes given by a "Source" metadata field, but that is rare - we have to guess them.
 * For that we have a guessing function, but we must check if the guessing was correct by testing some of the given
 * URLs against the actual internet-hosted document. Only if that check succeeds we should import the files.
 * In all other cases the import should work as well but should also only be done in a non-p2p environment to prevent
 * that such links are shared.
 */
public class ZimImporter extends Thread implements Importer {

    static public ZimImporter job;

    private ZIMFile file;
    private ZIMReader reader;
    private String path; 
    private String guessedSource;

    private int recordCnt;
    private long startTime;
    private final long sourceSize;
    private long consumed;
    private boolean abort = false;

    public ZimImporter(String path) throws IOException {
       super("ZimImporter - from file " + path);
       this.path = path;
       this.file = new ZIMFile(this.path); // this will read already some of the metadata and could consume some time
       this.sourceSize = this.file.length();
    }

    @Override
    public void run() {
        job = this;
        this.startTime = System.currentTimeMillis();
        Switchboard sb = Switchboard.getSwitchboard();
        try {
            this.reader = new ZIMReader(this.file);
            this.guessedSource = getSource(this.reader);
            Date guessedDate = getDate(this.reader);
            String dates = HeaderFramework.newRfc1123Format().format(guessedDate);

            // verify the source
            DirectoryEntry mainEntry = this.reader.getMainDirectoryEntry();
            DigestURL mainURL = guessURL(this.guessedSource, mainEntry);
            if (!mainURL.exists(ClientIdentification.browserAgent)) {
                sb.log.info("zim importer: file " + this.file.getName() + " failed main url existence test: " + mainURL);
                return; 
            }

            // read all documents
            for (int i = 0; i < this.file.header_entryCount; i++) {
                try {
                    if (this.abort) break;
                    DirectoryEntry de = this.reader.getDirectoryInfo(i);
                    if (!(de instanceof ZIMReader.ArticleEntry)) continue;
                    ArticleEntry ae = (ArticleEntry) de;
                    if (ae.namespace != 'C' && ae.namespace != 'A') continue;
    
                    // check url
                    DigestURL guessedUrl = guessURL(this.guessedSource, de);
                    if (recordCnt < 10) {
                        // critical test for the first 10 urls
                        if (!guessedUrl.exists(ClientIdentification.browserAgent)) {
                            sb.log.info("zim importer: file " + this.file.getName() + " failed url " + recordCnt + " existence test: " + guessedUrl);
                            return; 
                        }
                    }
    
                    // check availability of text parser
                    String mimeType = ae.getMimeType();
                    if (!mimeType.startsWith("text/") && !mimeType.equals("application/epub+zip")) continue; // in this import we want only text, not everything that is possible
                    if (TextParser.supportsMime(mimeType) != null) continue;
    
                    // read the content
                    byte[] b = this.reader.getArticleData(ae);
    
                    // create artificial request and response headers for the indexer
                    RequestHeader requestHeader = new RequestHeader();
                    ResponseHeader responseHeader = new ResponseHeader(200);
                    responseHeader.put(HeaderFramework.CONTENT_TYPE, de.getMimeType()); // very important to tell parser which kind of content
                    responseHeader.put(HeaderFramework.LAST_MODIFIED, dates); // put in the guessd date to have something that is not the current date
                    final Request request = new Request(
                            ASCII.getBytes(sb.peers.mySeed().hash),
                            guessedUrl,
                            null, // referrerhash the hash of the referrer URL
                            de.title, // name the name of the document to crawl
                            null, // appdate the time when the url was first time appeared
                            sb.crawler.defaultSurrogateProfile.handle(),        // profileHandle the name of the prefetch profile. This must not be null!
                            0,    // depth the crawling depth of the entry
                            sb.crawler.defaultSurrogateProfile.timezoneOffset() // timezone offset
                    );
                    final Response response = new Response(
                            request,
                            requestHeader,
                            responseHeader,
                            Switchboard.getSwitchboard().crawler.defaultSurrogateProfile,
                            false,
                            b
                    );
    
                    // throw this to the indexer
                    String error = sb.toIndexer(response);
                    if (error != null) ConcurrentLog.info("ZimImporter", "error parsing: " + error);
                    this.recordCnt++;
                } catch (Exception e) {
                    // catch any error that could stop the importer
                    ConcurrentLog.info("ZimImporter", "error loading: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ConcurrentLog.info("ZimImporter", "error reading: " + e.getMessage());
        }
        ConcurrentLog.info("ZimImporter", "Indexed " + this.recordCnt + " documents");
        job = null;
    }

    public void quit() {
        this.abort = true;
    }

    @Override
    public String source() {
        return this.path;
    }

    @Override
    public int count() {
        return this.recordCnt;
    }

    @Override
    public int speed() {
        if (this.recordCnt == 0) return 0;
        return (int) (this.recordCnt / Math.max(0L, runningTime() ));
    }

    @Override
    public long runningTime() {
        return (System.currentTimeMillis() - this.startTime) / 1000L;
    }

    @Override
    public long remainingTime() {
        if (this.consumed == 0) {
            return 0;
        }
        long speed = this.consumed / runningTime();
        return (this.sourceSize - this.consumed) / speed;
    }

    @Override
    public String status() {
        return "";
    }

    public static String guessDomainName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null; // Handle null or empty input
        }

        String[] parts = fileName.split("_");
        if (parts.length == 0) {
            return null;
        }
        String firstPart = parts[0];

        // Handling special cases where the domain name might not be obvious
        // These are based on your provided list and can be expanded as needed
        switch (firstPart) {
            case "100r-off-the-grid":
                return "100resilientcities.org";
            case "armypubs":
                return "armypubs.army.mil";
            case "artofproblemsolving":
                return "artofproblemsolving.com";
            case "based":
                return "based.cooking";
            case "booksdash":
                return "booksdash.com";
            case "coopmaths":
                return "coopmaths.fr";
            case "fas-military-medicine":
                return "fas.org";
            case "fonts":
                return "fonts.google.com";
            case "ifixit":
                return "ifixit.com";
            case "lesfondamentaux":
                return "reseau-canope.fr";
            case "lowtechmagazine":
                return "lowtechmagazine.com";
            case "mutopiaproject":
                return "mutopiaproject.org";
            case "openstreetmap-wiki":
                return "wiki.openstreetmap.org";
            case "opentextbooks":
                return "opentextbooks.org";
            case "phet":
                return "phet.colorado.edu";
            case "practical_action":
                return "practicalaction.org";
            case "rapsberry_pi_docs":
                return "raspberrypi.org";
            case "ted":
                return "www.ted.com/search?q=";
            case "vikidia":
                return parts[1] + ".vikidia.org/wiki";
            case "westeros":
                return "westeros.org";
            case "mdwiki":
                return "mdwiki.org/wiki";
            case "wikihow":
                return parts[1].equals("en") ? "wikihow.com" : parts[1] + ".wikihow.com";
            case "wikisource":
                return parts[1] + ".wikisource.org/wiki";
            case "wikiversity":
                return parts[1] + ".wikiversity.org/wiki";
            case "wikivoyage":
                return parts[1] + ".wikivoyage.org/wiki";
            case "wiktionary":
                return parts[1] + ".wiktionary.org/wiki";
            case "wikiquote":
                return parts[1] + ".wikiquote.org/wiki";
            case "wikibooks":
                return parts[1] + ".wikibooks.org/wiki";
            case "wikinews":
                return parts[1] + ".wikinews.org/wiki";
            case "wikipedia":
                return parts[1] + ".wikipedia.org/wiki";
            case "www.ready.gov":
                return "ready.gov";
        }

        // Handling domain patterns
        if (firstPart.contains(".stackexchange.com")) {
            return firstPart;
        } else if (firstPart.endsWith(".com") || firstPart.endsWith(".org") || firstPart.endsWith(".de") || 
                   firstPart.endsWith(".fr") || firstPart.endsWith(".pt") || firstPart.endsWith(".it") || 
                   firstPart.endsWith(".ja") || firstPart.endsWith(".es") || firstPart.endsWith(".eo")) {
            return firstPart;
        } else if (firstPart.contains("-")) {
            return firstPart.substring(0, firstPart.indexOf("-"));
        }

        // Additional general domain extraction logic
        if (firstPart.contains(".")) {
            int lastDotIndex = firstPart.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < firstPart.length() - 1) {
                // Extract up to the next character beyond the TLD, to support TLDs of variable length
                int endIndex = firstPart.indexOf('.', lastDotIndex + 1);
                if (endIndex == -1) {
                    endIndex = firstPart.length();
                }
                return firstPart.substring(0, endIndex);
            }
        }

        // Default return if none of the above conditions meet
        return null;
    }

    public static String getSource(ZIMReader r) throws IOException {
        String source = r.getMetadata("Source");
        if (source != null) return source;
        source = "https://" + guessDomainName(r.getZIMFile().getName()) + "/";
        return source;
    }

    public static Date getDate(ZIMReader r) throws IOException {
        String date = r.getMetadata("Date");
        if (date != null) try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return format.parse(date);
        } catch (ParseException e) {}
        // failover situation: use file date
        return new Date(r.getZIMFile().lastModified());
    }

    public static DigestURL guessURL(String guessedSource, DirectoryEntry de) throws MalformedURLException {
        String url = de.url;
        if (url.equals("Main_Page")) url = "";
        if (url.startsWith("A/")) return new DigestURL("https://" + url.substring(2));
        if (url.startsWith("H/")) return new DigestURL("https://" + url.substring(2));
        if (guessedSource != null) return new DigestURL(guessedSource + url);
        return new DigestURL(guessedSource + url);
    }

    private final static String[] skip_files = {
         "iota.stackexchange.com_en_all_2023-05.zim",
         "stellar.stackexchange.com_en_all_2023-10.zim",
         "vegetarianism.stackexchange.com_en_all_2023-05.zim",
         "esperanto.stackexchange.com_eo_all_2023-10.zim",
         "tezos.stackexchange.com_en_all_2023-10.zim",
         "eosio.stackexchange.com_en_all_2023-10.zim",
         "ebooks.stackexchange.com_en_all_2023-10.zim",
         "poker.stackexchange.com_en_all_2023-05.zim",
         "cseducators.stackexchange.com_en_all_2023-10.zim",
         "iot.stackexchange.com_en_all_2023-05.zim",
         "portuguese.stackexchange.com_pt_all_2023-04.zim",
         "portuguese.stackexchange.com_pt_all_2023-10.zim",
         "italian.stackexchange.com_it_all_2023-05.zim",
         "monero.stackexchange.com_en_all_2022-11.zim",
         "sustainability.stackexchange.com_en_all_2023-05.zim",
         "westeros_en_all_nopic_2021-03.zim",
         "opensource.stackexchange.com_en_all_2023-10.zim",
         "tor.stackexchange.com_en_all_2023-05.zim",
         "devops.stackexchange.com_en_all_2023-10.zim",
         "patents.stackexchange.com_en_all_2023-10.zim",
         "stackapps.com_en_all_2023-05.zim",
         "hardwarerecs.stackexchange.com_en_all_2023-05.zim",
         "hsm.stackexchange.com_en_all_2023-05.zim",
         "expatriates.stackexchange.com_en_all_2023-11.zim",
         "opendata.stackexchange.com_en_all_2023-10.zim",
         "sports.stackexchange.com_en_all_2023-05.zim",
         "wikinews_de_all_nopic_2023-10.zim",
         "computergraphics.stackexchange.com_en_all_2023-10.zim",
         "tridion.stackexchange.com_en_all_2023-10.zim",
         "bioinformatics.stackexchange.com_en_all_2023-10.zim",
         "expressionengine.stackexchange.com_en_all_2023-11.zim",
         "elementaryos.stackexchange.com_en_all_2023-10.zim",
         "cstheory.stackexchange.com_en_all_2023-10.zim",
         "chess.stackexchange.com_en_all_2023-05.zim",
         "vi.stackexchange.com_en_all_2023-05.zim",
         "fitness.stackexchange.com_en_all_2023-10.zim",
         "pets.stackexchange.com_en_all_2023-05.zim",
         "french.stackexchange.com_fr_all_2023-10.zim",
         "sqa.stackexchange.com_en_all_2023-05.zim",
         "islam.stackexchange.com_en_all_2023-05.zim",
         "scicomp.stackexchange.com_en_all_2023-05.zim",
         "wikinews_en_all_nopic_2023-09.zim",
         "ai.stackexchange.com_en_all_2023-10.zim",
         "boardgames.stackexchange.com_en_all_2023-05.zim",
         "economics.stackexchange.com_en_all_2023-05.zim",
         "3dprinting.stackexchange.com_en_all_2023-07.zim",
         "earthscience.stackexchange.com_en_all_2023-05.zim",
         "emacs.stackexchange.com_en_all_2023-10.zim",
         "bitcoin.stackexchange.com_en_all_2023-05.zim",
         "philosophy.stackexchange.com_en_all_2023-05.zim",
         "law.stackexchange.com_en_all_2023-05.zim",
         "astronomy.stackexchange.com_en_all_2023-05.zim",
         "artofproblemsolving_en_all_nopic_2021-03.zim",
         "engineering.stackexchange.com_en_all_2023-05.zim",
         "ja.stackoverflow.com_ja_all_2023-06.zim",
         "webmasters.stackexchange.com_en_all_2023-05.zim",
         "anime.stackexchange.com_en_all_2023-10.zim",
         "cooking.stackexchange.com_en_all_2023-05.zim",
         "arduino.stackexchange.com_en_all_2023-05.zim",
         "money.stackexchange.com_en_all_2023-05.zim",
         "judaism.stackexchange.com_en_all_2023-05.zim",
         "ethereum.stackexchange.com_en_all_2023-05.zim",
         "datascience.stackexchange.com_en_all_2023-10.zim",
         "academia.stackexchange.com_en_all_2023-10.zim",
         "music.stackexchange.com_en_all_2023-05.zim",
         "cs.stackexchange.com_en_all_2023-03.zim",
         "dsp.stackexchange.com_en_all_2023-05.zim",
         "biology.stackexchange.com_en_all_2023-05.zim",
         "android.stackexchange.com_en_all_2023-10.zim",
         "bicycles.stackexchange.com_en_all_2023-05.zim",
         "puzzling.stackexchange.com_en_all_2023-05.zim",
         "photo.stackexchange.com_en_all_2023-05.zim",
         "aviation.stackexchange.com_en_all_2023-05.zim",
         "drupal.stackexchange.com_en_all_2023-05.zim",
         "ux.stackexchange.com_en_all_2023-05.zim",
         "ell.stackexchange.com_en_all_2023-10.zim",
         "openstreetmap-wiki_en_all_nopic_2023-05.zim",
         "softwareengineering.stackexchange.com_en_all_2023-05.zim",
         "gaming.stackexchange.com_en_all_2023-10.zim",
         "mathematica.stackexchange.com_en_all_2023-10.zim",
         "pt.stackoverflow.com_pt_all_2023-06.zim",
         "apple.stackexchange.com_en_all_2023-05.zim",
         "diy.stackexchange.com_en_all_2023-08.zim",
         "es.stackoverflow.com_es_all_2023-06.zim",
         "gis.stackexchange.com_en_all_2023-05.zim",
         "stats.stackexchange.com_en_all_2023-05.zim",
         "physics.stackexchange.com_en_all_2023-05.zim",
         "serverfault.com_en_all_2023-05.zim",
         "electronics.stackexchange.com_en_all_2023-05.zim",
         "tex.stackexchange.com_en_all_2023-05.zim",
         "wikibooks_de_all_nopic_2021-03.zim",
         "askubuntu.com_en_all_2023-05.zim",
         "superuser.com_en_all_2023-05.zim",
         "lesfondamentaux.reseau-canope.fr_fr_all_2022-11.zim",
         "wikibooks_en_all_nopic_2021-03.zim",
         "courses.lumenlearning.com_en_all_2021-03.zim",
         "wikipedia_de_all_nopic_2023-10.zim",
         "wikipedia_en_all_nopic_2023-10.zim",
         "stackoverflow.com_en_all_nopic_2022-07.zim",
         "stackoverflow.com_en_all_2023-05.zim",
         "armypubs_en_all_2023-08.zim",
         "vikidia_en_all_nopic_2023-09.zim",
         "wikiquote_de_all_nopic_2023-10.zim",
         "wikiquote_en_all_nopic_2023-09.zim",
         "wiktionary_de_all_nopic_2023-10.zim",
         "wiktionary_en_all_nopic_2023-10.zim",
         "wikihow_de_maxi_2023-10.zim",
         "wikivoyage_de_all_nopic_2023-09.zim",
         "wikiversity_de_all_nopic_2021-03.zim",
         "wikiversity_en_all_nopic_2021-03.zim",
         "wikisource_de_all_nopic_2023-09.zim",
         "wikisource_en_all_nopic_2023-08.zim",
         "ted_countdown_global_2023-09.zim",
         "ted_en_design_2023-09.zim",
         "ted_en_business_2023-09.zim",
         "ted_en_global_issues_2023-09.zim",
         "opentextbooks_en_all_2023-08.zim",
         "bestedlessons.org_en_all_2023-08.zim",
         "wikivoyage_en_all_nopic_2023-10.zim",
         "based.cooking_en_all_2023-10.zim",
         "wordnet_en_all_2023-04.zim",
         "internet-encyclopedia-philosophy_en_all_2023-08.zim",
         "100r-off-the-grid_en_2023-09.zim",
         "coopmaths_2023-04.zim",
         "birds-of-ladakh_en_all_2023-02.zim",
         "storyweaver.org_en_2023-09.zim",
         "developer.mozilla.org_en_all_2023-02.zim",
         "www.ready.gov_es_2023-06.zim",
         "teoria.com_en_2023-08.zim",
         "theworldfactbook_en_all_2023-06.zim",
         "mutopiaproject.org_en_2023-08.zim",
         "dp.la_en_all_2023-08.zim",

         // 302
         "moderators.stackexchange.com_en_all_2023-05.zim",
         "beer.stackexchange.com_en_all_2023-05.zim",
         "health.stackexchange.com_en_all_2023-05.zim",
         "avp.stackexchange.com_en_all_2023-05.zim",
         "lowtechmagazine.com_en_all_2023-08.zim",
         "ifixit_de_all_2023-07.zim",
         "ifixit_en_all_2023-10.zim",
         "der-postillon.com_de_all_2020-12.zim",
         "wikihow_en_maxi_2023-03.zim",
    };

    public static void main(String[] args) {
        Set<String> skip = new HashSet<>();
        for (String s: skip_files) skip.add(s);
        // zim file import test
        // will test mostly if domain names are included in zim file urls
        String zimFilesPath = args[0];
        File zimFiles = new File(zimFilesPath);

        // make ordered file list; order by file size (start with smallest)
        String[] filelist = zimFiles.list();
        Map<Long, File> orderedFileMap = new TreeMap<>();
        for (int i = 0; i < filelist.length; i++) {
            if (!filelist[i].endsWith(".zim")) continue;
            File f = new File(zimFiles, filelist[i]);
            orderedFileMap.put(f.length() * 1000 + i, f);
        }

        Collection<File> orderedFiles = orderedFileMap.values();
        Set<String> files_ok = new LinkedHashSet<>();
        Set<String> files_nok = new LinkedHashSet<>();
        for (File f: orderedFiles) {
            if (skip.contains(f.getName())) continue;
            try {
                ZIMFile z = new ZIMFile(f.getAbsolutePath());
                ZIMReader r = new ZIMReader(z);
                DirectoryEntry de = r.getMainDirectoryEntry();
                System.out.println("ZIM file:  " + f.getAbsolutePath());
                for (String key: ZIMReader.METADATA_KEYS) {String s = r.getMetadata(key); if (s != null) System.out.println("Metadata " + key + ": " + s);};
                System.out.println("Namespace: " + de.namespace);
                System.out.println("Title:     " + de.title);
                System.out.println("URL:       " + de.url);
                System.out.println("Mime Type  " + de.getMimeType());
                System.out.println("guessed domain: " + guessDomainName(f.getName())); // uses a table and rules that deduces a source from the file name
                String source = getSource(r);
                System.out.println("guessed Source: " + source); // this uses metadata stored in the zim file
                DigestURL mainURL = guessURL(source, de);
                System.out.println("guessed main article: " + mainURL);
                boolean ok = mainURL.exists(ClientIdentification.browserAgent);
                System.out.println("main article exists: " + ok);
                if (ok) files_ok.add(f.getName()); else files_nok.add(f.getName());
                System.out.println();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("ok files: " + files_ok.toString());
        System.out.println("not-ok files: " + files_nok.toString());
    }
}
