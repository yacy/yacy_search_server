/**
 *  IndexImportJsonList_p
 *  Copyright 23.10.2022 by Michael Peter Christen, @orbiterlab
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

package net.yacy.htroot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.JsonListImporter;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexImportJsonList_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();

        if (JsonListImporter.job != null && JsonListImporter.job.isAlive()) {
            // one import is running, no option to insert anything
            prop.put("import", 1);
            prop.put("import_thread", "running");
            prop.put("import_jsonlistfile", JsonListImporter.job.source());
            prop.put("import_count", JsonListImporter.job.count());
            prop.put("import_speed", JsonListImporter.job.speed());
            prop.put("import_runningHours", (JsonListImporter.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (JsonListImporter.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (JsonListImporter.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (JsonListImporter.job.remainingTime() / 60) % 60);
            if (post != null && post.containsKey("abort")) {
                JsonListImporter.job.quit();
            }
        } else {
            prop.put("import", 0);
            if (post != null) {
                if (post.containsKey("file") || post.containsKey("url")) {
                    final String filename = post.get("file");
                    if (filename != null && filename.length() > 0) {
                        final File sourcefile = new File(filename);
                        if (sourcefile.exists()) {
                            try {
                                final JsonListImporter wi = new JsonListImporter(sourcefile, false, false);
                                wi.start();
                                prop.put("import_thread", "started");
                            } catch (final IOException ex) {
                                prop.put("import_thread", "Error: file not found [" + filename + "]");
                            }
                            prop.put("import", 1);
                            prop.put("import_jsonlistfile", filename);
                        } else {
                            prop.put("import_jsonlistfile", "");
                            prop.put("import_thread", "Error: file not found [" + filename + "]");
                        }
                    } else {
                        final String urlstr = post.get("url");
/*
                                final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
                                final byte[] b = client.GETbytes(urlstr, null, null, true);
                                final File tempfile = File.createTempFile("jsonlistimporter", "");
                                final FileOutputStream fos = new FileOutputStream(tempfile);
                                fos.write(b);
                                fos.close();
                                client.close();
 */
                        if (urlstr != null && urlstr.length() > 0) {
                            try {
                                final URL url = new URI(urlstr).toURL();
                                final String tempfilename = "jsonlistimporter";
                                final boolean gz = urlstr.endsWith(".gz");
                                final File tempfile = File.createTempFile(tempfilename, "");
                                final FileOutputStream fos = new FileOutputStream(tempfile);
                                fos.getChannel().transferFrom(Channels.newChannel(url.openStream()), 0, Long.MAX_VALUE);
                                fos.close();
                                final JsonListImporter wi = new JsonListImporter(tempfile, gz, true);
                                wi.start();
                                prop.put("import_thread", "started");
                            } catch (final IOException | URISyntaxException ex) {
                                prop.put("import_thread", ex.getMessage());
                            }
                            prop.put("import", 1);
                            prop.put("import_jsonlistfile", urlstr);
                        }
                    }

                    prop.put("import_count", 0);
                    prop.put("import_speed", 0);
                    prop.put("import_runningHours", 0);
                    prop.put("import_runningMinutes", 0);
                    prop.put("import_remainingHours", 0);
                    prop.put("import_remainingMinutes", 0);
                }
            }
        }
        return prop;
    }
}
