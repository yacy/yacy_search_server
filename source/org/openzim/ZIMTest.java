/*
 * Copyright (C) 2011 Arunesh Mathur
 *
 * This file is a part of zimreader-java.
 *
 * zimreader-java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3.0 as
 * published by the Free Software Foundation.
 *
 * zimreader-java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with zimreader-java.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openzim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.openzim.ZIMReader.DirectoryEntry;

public class ZIMTest {

    public static void main(final String[] args) {
        if(args.length!=1) {
            System.out.println("Usage: java ZIMTest <ZIM_FILE>");
            System.exit(0);
        }

        try {
            // args[0] is the Zim File's location
            final ZIMFile file = new ZIMFile(args[0]);

            // Associate the Zim File with a Reader
            final ZIMReader zReader = new ZIMReader(file);

            // print a list of urls and titles
            final List<String> urls = zReader.getURLListByURL();
            final List<String> titles = zReader.getURLListByTitle();
            int c = Math.min(10, titles.size());
            for (int i = 0; i < c; i++) {
                System.out.println("URL by URL   " + i + ": " + urls.get(i));
                System.out.println("URL by Title " + i + ": " + titles.get(i));
                DirectoryEntry entry = zReader.getDirectoryInfo(i);
                System.out.println("URL   by Pos " + i + ": " + entry.url);
                System.out.println("Title by Pos " + i + ": " + entry.title);
                System.out.println("Namespace by Pos " + i + ": " + entry.namespace);
            }

            // print article c-1
            DirectoryEntry directory_entry = zReader.getDirectoryInfo(c - 1);
            ByteArrayOutputStream articleStream = zReader.getArticleData(directory_entry);
            String article = articleStream == null ? "NULL" : articleStream.toString(StandardCharsets.UTF_8.name());
            System.out.println(article);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

}
