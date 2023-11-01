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
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.id.DigestURL;
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
        try {
            this.reader = new ZIMReader(this.file);
            this.guessedSource = getSource(this.reader);

            for (int i = 0; i < this.file.header_entryCount; i++) {
                if (this.abort) break;
                DirectoryEntry de = this.reader.getDirectoryInfo(i);
                if (!(de instanceof ZIMReader.ArticleEntry)) continue;
                ArticleEntry ae = (ArticleEntry) de;

                // check url
                String guessedUrl = guessURL(this.guessedSource, de);
                assert guessedUrl.startsWith("http");

                // check availability of text parser
                String mimeType = ae.getMimeType();
                if (TextParser.supportsMime(mimeType) != null) continue;

                // read the content
                byte[] b = this.reader.getArticleData(ae);

                // create artificial request and response headers for the indexer
                RequestHeader requestHeader = new RequestHeader();
                ResponseHeader responseHeader = new ResponseHeader(200);
                final Request request = new Request(new DigestURL(guessedUrl), null);
                final Response response = new Response(
                        request,
                        requestHeader,
                        responseHeader,
                        Switchboard.getSwitchboard().crawler.defaultSurrogateProfile,
                        false,
                        b
                );

                // throw this to the indexer
                String error = Switchboard.getSwitchboard().toIndexer(response);
                if (error != null) ConcurrentLog.info("ZimImporter", "error parsing: " + error);
                this.recordCnt++;
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
            case "gutenberg":
                return "gutenberg.org";
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
                return "ted.com";
            case "vikidia":
                return "vikidia.org";
            case "westeros":
                return "westeros.org";
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

    public static String guessURL(String guessedSource, DirectoryEntry de) {
        String url = de.url;
        if (url.equals("Main_Page")) url = "";
        if (guessedSource != null) return guessedSource + url;
        if (url.startsWith("A/")) return "https://" + url.substring(2);
        if (url.startsWith("H/")) return "https://" + url.substring(2);
        return guessedSource + url;
    }

    public static void main(String[] args) {
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
        for (File f: orderedFiles) {
            try {
                ZIMFile z = new ZIMFile(f.getAbsolutePath());
                ZIMReader r = new ZIMReader(z);
                DirectoryEntry de = r.getMainDirectoryEntry();
                System.out.println("ZIM file:  " + f.getAbsolutePath());
                for (String key: ZIMReader.METADATA_KEYS) {String s = r.getMetadata(key); if (s != null) System.out.println("Metadata " + key + ": " + s);};
                System.out.println("Namespace: " + de.namespace);
                System.out.println("Title:     " + de.title);
                System.out.println("URL:       " + de.url);
                System.out.println("guessed domain: " + guessDomainName(f.getName()));
                String source = getSource(r);
                System.out.println("guessed Source: " + source);
                System.out.println("guessed main article: " + guessURL(source, de));
                System.out.println();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
