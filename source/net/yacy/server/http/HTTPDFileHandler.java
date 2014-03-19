// HTTPDFileHandler.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

/*
 Class documentation:
 this class provides a file servlet and CGI interface
 for the httpd server.
 Whenever this server is addressed to load a local file,
 this class searches for the file in the local path as
 configured in the setting property 'rootPath'
 The servlet loads the file and returns it to the client.
 Every file can also act as an template for the built-in
 CGI interface. There is no specific path for CGI functions.
 CGI functionality is triggered, if for the file to-be-served
 'template.html' also a file 'template.class' exists. Then,
 the class file is called with the GET/POST properties that
 are attached to the http call.
 Possible variable hand-over are:
 - form method GET
 - form method POST, enctype text/plain
 - form method POST, enctype multipart/form-data
 The class that creates the CGI respond must have at least one
 static method of the form
 public static java.util.Hashtable respond(java.util.HashMap, serverSwitch)
 In the HashMap, the GET/POST variables are handed over.
 The return value is a Property object that contains replacement
 key/value pairs for the patterns in the template file.
 The templates must have the form
 either '#['<name>']#' for single attributes, or
 '#{'<enumname>'}#' and '#{/'<enumname>'}#' for enumerations of
 values '#['<value>']#'.
 A single value in repetitions/enumerations in the template has
 the property key '_'<enumname><count>'_'<value>
 Please see also the example files 'test.html' and 'test.java'
 */

package net.yacy.server.http;

import java.io.File;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverSwitch;

public final class HTTPDFileHandler {

    // create a class loader
    private static serverSwitch switchboard = null;

    public  static File     htDocsPath     = null;
    public  static String[] defaultFiles   = null;
    private static File     htDefaultPath  = null;
    private static File     htLocalePath   = null;
    public  static String   indexForward   = "";

    //private Properties connectionProperties = null;
    // creating a logger

    static {
        final serverSwitch theSwitchboard = Switchboard.getSwitchboard();

        if (switchboard == null) {
            switchboard = theSwitchboard;

            if (Classification.countMimes() == 0) {
                // load the mime table
                final String mimeTablePath = theSwitchboard.getConfig("mimeTable","");
                ConcurrentLog.config("HTTPDFiles", "Loading mime mapping file " + mimeTablePath);
                Classification.init(new File(theSwitchboard.getAppPath(), mimeTablePath));
            }

            // create default files array
            initDefaultPath();

            // create a htDocsPath: user defined pages
            if (htDocsPath == null) {
                htDocsPath = theSwitchboard.getDataPath(SwitchboardConstants.HTDOCS_PATH, SwitchboardConstants.HTDOCS_PATH_DEFAULT);
                if (!(htDocsPath.exists())) htDocsPath.mkdirs();
            }

            // create a repository path
            final File repository = new File(htDocsPath, "repository");
            if (!repository.exists()) repository.mkdirs();

            // create htLocaleDefault, htLocalePath
            if (htDefaultPath == null) htDefaultPath = theSwitchboard.getAppPath("htDefaultPath", SwitchboardConstants.HTROOT_PATH_DEFAULT);
            if (htLocalePath == null) htLocalePath = theSwitchboard.getDataPath("locale.translated_html", "DATA/LOCALE/htroot");
        }
    }

    public static final void initDefaultPath() {
        // create default files array
        defaultFiles = switchboard.getConfig(SwitchboardConstants.BROWSER_DEFAULT,"index.html").split(",");
        if (defaultFiles.length == 0) defaultFiles = new String[] {"index.html"};
        indexForward = switchboard.getConfig(SwitchboardConstants.INDEX_FORWARD, "");
        if (indexForward.startsWith("/")) indexForward = indexForward.substring(1);
    }

    /** Returns a path to the localized or default file according to the locale.language (from he switchboard)
     * @param path relative from htroot */
    public static File getLocalizedFile(final String path){
        String localeSelection = switchboard.getConfig("locale.language","default");
        if (path.startsWith("/repository/"))
            return new File(switchboard.getConfig("repositoryPath", "DATA/HTDOCS/repository"), path.substring(11));
        if (!(localeSelection.equals("default"))) {
            final File localePath = new File(htLocalePath, localeSelection + '/' + path);
            if (localePath.exists()) return localePath;  // avoid "NoSuchFile" troubles if the "localeSelection" is misspelled
        }
        
        final File docsPath  = new File(htDocsPath, path);
        if (docsPath.exists()) return docsPath;
        return new File(htDefaultPath, path);
    }

    public static final File getOverlayedFile(final String path) {
        File targetFile;
        targetFile = getLocalizedFile(path);
        if (!targetFile.exists()) {
            targetFile = new File(htDocsPath, path);
        }
        return targetFile;
    }

}
