/**
 *  ConfigurationSet
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Files {

    /**
     * copy a file or a complete directory
     * @param from the source file or directory
     * @param to the destination file or directory
     * @throws IOException
     */
    public static void copy(final File from, final File to) throws IOException {
        if (!from.exists()) {
            throw new IOException("Can not find source: " + from.getAbsolutePath()+".");
        } else if (!from.canRead()) {
            throw new IOException("No right to source: " + from.getAbsolutePath()+".");
        }
        if (from.isDirectory())  {
            if (!to.exists() && !to.mkdirs()) {
                throw new IOException("Could not create directory: " + to.getAbsolutePath() + ".");
            }
            for (final String f : from.list()) {
                copy(new File(from, f) , new File(to, f));
            }
        } else {
            if (to.isDirectory()) throw new IOException("Cannot copy a file to an existing directory");
            if (to.exists()) to.delete();
            final byte[] buffer = new byte[4096];
            int bytesRead;
            final InputStream in =  new BufferedInputStream(new FileInputStream(from));
            final OutputStream out = new BufferedOutputStream(new FileOutputStream (to));
            while ((bytesRead = in.read(buffer)) >= 0) {
                out.write(buffer,0,bytesRead);
            }
            in.close();
            out.close();
        }
    }
}
