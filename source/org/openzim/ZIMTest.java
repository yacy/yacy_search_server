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

import java.io.IOException;

public class ZIMTest {
    public static void main(final String[] args) {
        if(args.length!=2) {
            System.out.println("Usage: java ZIMTest <ZIM_FILE> <ARTICLE_NAME>");
            System.exit(0);
        }

        // args[0] is the Zim File's location
        final ZIMFile file = new ZIMFile(args[0]);

        // Associate the Zim File with a Reader
        final ZIMReader zReader = new ZIMReader(file);

        try {
            // args[1] is the name of the articles that is
             // to be fetched
            System.out.println(zReader.getArticleData(args[1],'A').toString("utf-8"));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
